package com.example.englishvoicetutor.data.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Сколько ждём инициализации системного TTS, прежде чем считать её неудавшейся. */
private const val INIT_TIMEOUT_MILLIS = 5_000L

/**
 * Использует встроенный Android TextToSpeech. На большинстве устройств уже есть офлайн
 * нейронные английские голоса. Если качество системного голоса окажется неудовлетворительным
 * на части устройств — план Б: Piper TTS (см. README).
 *
 * Важно: движка синтеза может не быть вовсе (встречается на прошивках без сервисов Google —
 * проверить можно так: `adb shell pm query-services -a android.intent.action.TTS_SERVICE`).
 * Тогда озвучка недоступна в принципе, и вместо тишины мы сообщаем об этом через [status].
 */
@Singleton
class AndroidTtsEngine @Inject constructor(
    @ApplicationContext context: Context
) : TtsEngine {

    private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Initializing)
    override val status: StateFlow<TtsStatus> = _status.asStateFlow()

    /** Результат инициализации; ждём его в [speak], чтобы первый тап не пропадал впустую. */
    private val initialized = CompletableDeferred<Boolean>()

    private val tts: TextToSpeech = TextToSpeech(context) { onInit(it) }

    /**
     * Язык выставляем только здесь. Делать это сразу после конструктора нельзя:
     * движок ещё не подключён, вызов молча не срабатывает, и синтез уходит
     * на язык системы вместо английского.
     */
    private fun onInit(initStatus: Int) {
        if (initStatus != TextToSpeech.SUCCESS) {
            fail("На устройстве не установлен движок синтеза речи")
            return
        }
        val result = runCatching { tts.setLanguage(Locale.US) }.getOrElse {
            Log.e("VoiceTutor", "TTS setLanguage failed", it)
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        when (result) {
            TextToSpeech.LANG_MISSING_DATA ->
                fail("Не загружены данные английского голоса")
            TextToSpeech.LANG_NOT_SUPPORTED ->
                fail("Системный синтез речи не поддерживает английский")
            else -> {
                _status.value = TtsStatus.Ready
                initialized.complete(true)
            }
        }
    }

    private fun fail(reason: String) {
        Log.w("VoiceTutor", "TTS unavailable: $reason")
        _status.value = TtsStatus.Unavailable(reason)
        initialized.complete(false)
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        // Инициализация асинхронная: без ожидания самый первый тап по «произнести»
        // просто проваливался бы в тишину.
        val ready = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { initialized.await() }
        if (ready != true) {
            if (ready == null) fail("Синтез речи не отвечает")
            return
        }

        suspendCancellableCoroutine { cont ->
            val utteranceId = UUID.randomUUID().toString()

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }
                @Deprecated("Deprecated in API, оставлено для совместимости со старыми устройствами")
                override fun onError(utteranceId: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }
            })

            cont.invokeOnCancellation { tts.stop() }

            // Если speak() не принял запрос, колбэков не будет вообще — без этой
            // ветки корутина повисла бы навсегда (в диалоге это вешало бы
            // состояние «Speaking» до самого выхода с экрана).
            val code = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (code != TextToSpeech.SUCCESS && cont.isActive) {
                Log.w("VoiceTutor", "TTS speak() rejected the request, code=$code")
                cont.resume(Unit)
            }
        }
    }

    override fun stop() {
        runCatching { tts.stop() }
    }
}
