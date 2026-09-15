package com.example.englishvoicetutor.data.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.englishvoicetutor.data.local.VoicePreferences
import com.example.englishvoicetutor.data.model.ArchiveModelDownloader
import com.example.englishvoicetutor.domain.model.ModelDownloadState
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Piper (VITS) en_US LibriTTS-R medium: ~75 МБ, 904 голоса, 22.05 кГц.
// Взят вместо системного TextToSpeech, потому что движка синтеза в прошивке может
// не быть вовсе, а приложение обязано работать офлайн и одинаково на всех устройствах.
private const val MODEL_URL =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
        "vits-piper-en_US-libritts_r-medium.tar.bz2"
private const val MODEL_DIR_NAME = "vits-piper-en_US-libritts_r-medium"
private const val MODEL_FILE_NAME = "en_US-libritts_r-medium.onnx"
private const val TMP_ARCHIVE_NAME = "tts_model_tmp.tar.bz2"

/** Предел длины куска для синтеза — компромисс между задержкой и естественностью интонации. */
private const val MAX_CHUNK_CHARS = 160

private const val TAG = "SherpaOnnxTtsEngine"

/**
 * Офлайн-синтез речи через sherpa-onnx (модель Piper/VITS).
 *
 * Зачем не системный `TextToSpeech`: на части прошивок движка синтеза нет вообще
 * (проверяется как `adb shell pm query-services -a android.intent.action.TTS_SERVICE`),
 * и озвучка молча не работает. Здесь голос — такая же скачиваемая модель, как STT и LLM,
 * поэтому поведение одинаково на любом устройстве и не зависит от сервисов Google.
 *
 * Системный [AndroidTtsEngine] остаётся в проекте как альтернативная реализация —
 * переключается одной строкой в `di/AppModule.kt`.
 */
@Singleton
class SherpaOnnxTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: ArchiveModelDownloader,
    private val voicePreferences: VoicePreferences,
) : TtsEngine {

    private val _status = MutableStateFlow<TtsStatus>(TtsStatus.Initializing)
    override val status: StateFlow<TtsStatus> = _status.asStateFlow()

    @Volatile private var tts: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null

    /** Флаг досрочной остановки: проверяется между предложениями, чтобы оборвать озвучку. */
    @Volatile private var stopRequested = false

    /** Генерация не потокобезопасна и тяжела — пускаем озвучку строго по одной. */
    private val speakMutex = Mutex()

    private val modelDir get() = File(context.filesDir, MODEL_DIR_NAME)

    /** Готова ли модель на диске — без скачивания и загрузки в память. */
    val isModelDownloaded: Boolean
        get() = File(modelDir, MODEL_FILE_NAME).exists() && File(modelDir, "tokens.txt").exists()

    init {
        // Если модель уже скачана, статус должен стать Ready без ожидания первой фразы,
        // иначе UI покажет предупреждение на пустом месте.
        if (isModelDownloaded) _status.value = TtsStatus.Ready
    }

    /** Скачивает (при необходимости) и загружает модель в память. */
    private suspend fun ensureTts(): OfflineTts = withContext(Dispatchers.IO) {
        tts?.let { return@withContext it }

        if (!isModelDownloaded) {
            modelDir.deleteRecursively()
            try {
                downloader.downloadAndUnpack(
                    url = MODEL_URL,
                    destDir = context.filesDir,
                    tmpArchiveName = TMP_ARCHIVE_NAME,
                    // В архиве едут примеры и карточка модели — на устройстве они не нужны.
                    skipEntry = { it.contains("/test_wavs/") || it.endsWith(".md") },
                    onState = { state -> _status.value = state.toTtsStatus() },
                )
            } catch (e: Exception) {
                Log.e(TAG, "TTS model download failed", e)
                _status.value = TtsStatus.Unavailable(
                    "Не удалось скачать голос: ${e.message ?: "ошибка сети"}"
                )
                throw e
            }
        }

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(modelDir, MODEL_FILE_NAME).absolutePath,
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    // espeak-ng-data нужен Piper для преобразования букв в фонемы.
                    dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                ),
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                debug = false,
                provider = "cpu",
            ),
        )
        // assetManager не передаём — модель лежит в filesDir, пути абсолютные.
        val engine = OfflineTts(config = config)
        Log.d(TAG, "TTS ready: sampleRate=${engine.sampleRate()}, speakers=${engine.numSpeakers()}")
        _status.value = TtsStatus.Ready
        engine.also { tts = it }
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        speakMutex.withLock {
            val engine = try {
                ensureTts()
            } catch (e: Exception) {
                return // причина уже показана через status
            }
            stopRequested = false
            withContext(Dispatchers.Default) { synthesizeAndPlay(engine, text) }
        }
    }

    /**
     * Синтезируем и проигрываем по предложению, а не всю реплику разом: озвучка
     * начинается почти сразу, а не после генерации целого ответа репетитора.
     *
     * Потоковый `generateWithCallback` использовать нельзя: нативная часть ищет у
     * колбэка метод с точной сигнатурой `invoke([F)Ljava/lang/Integer;`, а Kotlin 2.x
     * компилирует лямбды через invokedynamic, и у синтетического класса остаётся только
     * стёртый `invoke(Object)Object` — процесс падает с «JNI DETECTED ERROR».
     */
    private suspend fun synthesizeAndPlay(engine: OfflineTts, text: String) {
        val sampleRate = engine.sampleRate()
        val player = createTrack(sampleRate)
        track = player
        var framesWritten = 0L

        try {
            player.play()
            for (sentence in splitIntoSentences(text)) {
                if (stopRequested) break
                // Диктора и скорость читаем на каждое предложение: смена настройки
                // должна слышаться сразу, без пересоздания движка.
                val audio = engine.generate(
                    sentence,
                    voicePreferences.speakerId.value,
                    voicePreferences.speed.value
                )
                if (stopRequested) break
                // WRITE_BLOCKING притормаживает нас на длину буфера — следующее
                // предложение успевает синтезироваться, пока играет предыдущее.
                val written = player.write(
                    audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING
                )
                if (written > 0) framesWritten += written
            }
            if (!stopRequested) awaitPlaybackEnd(player, framesWritten, sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "TTS synthesis failed", e)
            _status.value = TtsStatus.Unavailable("Ошибка синтеза речи: ${e.message}")
        } finally {
            runCatching {
                player.pause()
                player.flush()
                player.stop()
            }
            player.release()
            track = null
        }
    }

    /**
     * Ждём, пока буфер доиграет: [AudioTrack.write] только ставит данные в очередь,
     * и без ожидания конец фразы обрезался бы на release().
     */
    private suspend fun awaitPlaybackEnd(player: AudioTrack, totalFrames: Long, sampleRate: Int) {
        if (totalFrames <= 0) return
        // Страховка от зависания, если позиция воспроизведения перестанет расти.
        val timeoutMillis = totalFrames * 1000 / sampleRate + 2_000
        val startedAt = System.currentTimeMillis()
        while (currentCoroutineContext().isActive && !stopRequested) {
            val played = player.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (played >= totalFrames) return
            if (System.currentTimeMillis() - startedAt > timeoutMillis) {
                Log.w(TAG, "Playback wait timed out: $played / $totalFrames frames")
                return
            }
            delay(20)
        }
    }

    /**
     * Режет реплику на предложения. Слишком длинные куски (модель иногда получает
     * текст без пунктуации) дополнительно дробим по запятым, иначе первая же фраза
     * синтезируется секундами и преимущество пословной озвучки теряется.
     */
    private fun splitIntoSentences(text: String): List<String> {
        val sentences = Regex("[^.!?]+[.!?]*").findAll(text)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
            .ifEmpty { listOf(text.trim()) }

        return sentences.flatMap { sentence ->
            if (sentence.length <= MAX_CHUNK_CHARS) listOf(sentence) else sentence.chunkedByComma()
        }
    }

    private fun String.chunkedByComma(): List<String> {
        val parts = split(",")
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for ((index, part) in parts.withIndex()) {
            if (current.isNotEmpty() && current.length + part.length > MAX_CHUNK_CHARS) {
                chunks += current.toString().trim()
                current.clear()
            }
            current.append(part)
            if (index != parts.lastIndex) current.append(',')
        }
        if (current.isNotBlank()) chunks += current.toString().trim()
        return chunks
    }

    private fun createTrack(sampleRate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            // Запас буфера: синтез идёт кусками, и при коротком буфере слышны щелчки.
            .setBufferSizeInBytes(maxOf(minBuffer, sampleRate * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    override fun stop() {
        stopRequested = true
        runCatching {
            track?.pause()
            track?.flush()
        }
    }

    /** Освобождает модель — вызывается, если движок больше не нужен. */
    fun release() {
        stop()
        tts?.release()
        tts = null
    }
}

/** Прогресс загрузки модели в термины, понятные UI озвучки. */
private fun ModelDownloadState.toTtsStatus(): TtsStatus = when (this) {
    is ModelDownloadState.Downloading -> TtsStatus.Downloading(
        percent = progressPercent,
        message = logs.lastOrNull() ?: "Скачиваем голос…"
    )
    is ModelDownloadState.Error -> TtsStatus.Unavailable(message)
    ModelDownloadState.Ready -> TtsStatus.Ready
    ModelDownloadState.Idle -> TtsStatus.Initializing
}
