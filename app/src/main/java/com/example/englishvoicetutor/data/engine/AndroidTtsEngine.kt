package com.example.englishvoicetutor.data.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Использует встроенный Android TextToSpeech. На большинстве устройств уже есть офлайн
 * нейронные английские голоса. Если качество системного голоса окажется неудовлетворительным
 * на части устройств — план Б: Piper TTS (см. README).
 */
@Singleton
class AndroidTtsEngine @Inject constructor(
    @ApplicationContext context: Context
) : TtsEngine {

    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    init {
        tts.language = Locale.ENGLISH
    }

    override suspend fun speak(text: String) {
        if (!ready || text.isBlank()) return

        suspendCancellableCoroutine<Unit> { cont ->
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
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    override fun stop() {
        tts.stop()
    }
}
