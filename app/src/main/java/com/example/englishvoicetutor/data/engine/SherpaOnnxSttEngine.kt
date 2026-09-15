package com.example.englishvoicetutor.data.engine

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.example.englishvoicetutor.data.model.ArchiveModelDownloader
import com.example.englishvoicetutor.domain.model.ModelDownloadState
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Parakeet unified-en 0.6B (int8, non-streaming) — NeMo-трансдьюсер от NVIDIA под английский.
// В разы быстрее Whisper small.en на CPU (важно для слабых устройств), сам расставляет
// пунктуацию и регистр и не склонен «выдумывать» текст на тишине, чем грешит Whisper.
private const val MODEL_URL =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
        "sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming.tar.bz2"
private const val MODEL_DIR_NAME = "sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming"

// По этим префиксам узнаём каталоги STT-моделей в filesDir. Всё, что подходит под префикс,
// но не равно MODEL_DIR_NAME, — модель прошлой версии приложения: она больше не нужна и
// занимает сотни мегабайт. Список префиксов, а не конкретных имён, чтобы следующая смена
// модели чистилась сама, без правок кода.
private val MODEL_DIR_PREFIXES = listOf("sherpa-onnx-", "vosk-model-")

private const val TMP_ARCHIVE_NAME = "model_tmp.tar.bz2"
private const val SAMPLE_RATE = 16_000
private const val TAG = "SherpaOnnxSttEngine"

@Singleton
class SherpaOnnxSttEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: ArchiveModelDownloader,
) : SttEngine {

    @Volatile
    private var recognizer: OfflineRecognizer? = null
    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var recordingThread: Thread? = null

    // Аккумулятор аудио за одно нажатие: модель распознаёт всю фразу разом на stopRecording.
    private val recordedChunks = mutableListOf<ShortArray>()
    @Volatile private var recordedSamples = 0

    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    /**
     * Убеждаемся, что модель скачана, распакована и загружена в память.
     * Вызывается лениво при первом использовании микрофона.
     */
    private suspend fun ensureRecognizer(): OfflineRecognizer = withContext(Dispatchers.IO) {
        recognizer?.let { return@withContext it }

        deleteOutdatedModels()

        val modelDir = File(context.filesDir, MODEL_DIR_NAME)
        val modelReady = File(modelDir, "tokens.txt").exists() &&
            File(modelDir, "encoder.int8.onnx").exists()

        if (!modelReady) {
            modelDir.deleteRecursively()
            try {
                downloader.downloadAndUnpack(
                    url = MODEL_URL,
                    destDir = context.filesDir,
                    tmpArchiveName = TMP_ARCHIVE_NAME,
                    // В архиве кроме весов лежат тестовые wav-ы и карточки модели —
                    // на устройстве они не нужны.
                    skipEntry = { it.contains("/test_wavs/") || it.endsWith(".md") },
                    onState = { _downloadState.value = it },
                )
            } catch (e: Exception) {
                _downloadState.value = ModelDownloadState.Error(e.message ?: "Ошибка загрузки")
                throw e
            }
        }

        _downloadState.value = ModelDownloadState.Downloading(99, listOf("Загружаем модель в память…"))
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(modelDir, "encoder.int8.onnx").absolutePath,
                    decoder = File(modelDir, "decoder.int8.onnx").absolutePath,
                    joiner = File(modelDir, "joiner.int8.onnx").absolutePath,
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                // Трансдьюсеры NeMo не распознаются по структуре графа — тип задаём явно.
                modelType = "nemo_transducer",
                // Энкодер 0.6B считается на CPU: берём до 4 потоков, но не больше, чем ядер.
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                debug = false,
                provider = "cpu",
            ),
            // assetManager не передаём — модель лежит в filesDir, пути абсолютные.
        )
        val r = OfflineRecognizer(config = config)
        _downloadState.value = ModelDownloadState.Ready
        r.also { recognizer = it }
    }

    /**
     * Удаляет каталоги STT-моделей, оставшиеся от прошлых версий приложения, и недокачанные
     * временные архивы. Вызывается перед проверкой актуальной модели, так что смена модели
     * освобождает место сама — пользователю не нужно чистить данные приложения руками.
     */
    private fun deleteOutdatedModels() {
        val files = context.filesDir.listFiles() ?: return
        for (file in files) {
            val isOutdatedModel = file.isDirectory &&
                file.name != MODEL_DIR_NAME &&
                MODEL_DIR_PREFIXES.any { file.name.startsWith(it) }
            // Архив остаётся, если распаковку прервали (закрыли приложение, кончилось место).
            val isLeftoverArchive = file.isFile && file.name == TMP_ARCHIVE_NAME

            if (isOutdatedModel || isLeftoverArchive) {
                val freedMb = file.walkBottomUp().filter { it.isFile }.sumOf { it.length() } / 1024 / 1024
                if (file.deleteRecursively()) {
                    Log.d(TAG, "Удалено ${file.name} — освобождено $freedMb МБ")
                } else {
                    Log.w(TAG, "Не удалось удалить ${file.name}")
                }
            }
        }
    }

    override suspend fun startRecording() {
        ensureRecognizer() // гарантируем, что модель загружена, до старта записи

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val chunkSamples = 3200
        // Кольцевой буфер держим больше одного чанка: при задержке потока чтения (GC,
        // планировщик ОС) микрофонный буфер иначе переполняется и теряет сэмплы. Запас ≈ 4 чанка.
        val bufferBytes = maxOf(minBuf, chunkSamples * 2 * 4)

        synchronized(recordedChunks) {
            recordedChunks.clear()
            recordedSamples = 0
        }

        // UNPROCESSED отдаёт по-настоящему сырой сигнал; VOICE_RECOGNITION на многих прошивках
        // тихо включает шумодав/AGC, которые «замазывают» фонемы. Берём UNPROCESSED, если заявлен.
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessedSupported =
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val audioSource =
            if (unprocessedSupported) MediaRecorder.AudioSource.UNPROCESSED
            else MediaRecorder.AudioSource.VOICE_RECOGNITION
        Log.d(TAG, "AudioSource: ${if (unprocessedSupported) "UNPROCESSED" else "VOICE_RECOGNITION"}")

        audioRecord = try {
            AudioRecord(
                audioSource,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
        } catch (e: SecurityException) {
            throw IllegalStateException("Нет разрешения на микрофон", e)
        }

        // Гасим системные аудиоэффекты: они настроены под человеческий слух, а не под ASR.
        audioRecord?.audioSessionId?.let { disableAudioEffects(it) }
        audioRecord!!.startRecording()

        recordingThread = Thread {
            val buffer = ShortArray(chunkSamples)
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    synchronized(recordedChunks) {
                        recordedChunks.add(buffer.copyOf(read))
                        recordedSamples += read
                    }
                }
            }
        }.also { it.start() }
    }

    override fun stopRecording(): String {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        recordingThread?.join(2000) // ждём, пока поток допишет последние чанки
        recordingThread = null

        val samples = drainRecordedSamples()
        val r = recognizer
        if (r == null || samples.isEmpty()) return ""

        // Модель офлайновая (non-streaming): скармливаем всю фразу разом одним стримом.
        val stream = r.createStream()
        stream.acceptWaveform(samples, SAMPLE_RATE)
        r.decode(stream)
        val text = r.getResult(stream).text
        stream.release()

        Log.d(TAG, "ASR result: '$text'")
        return text.trim()
    }

    /** Склеивает накопленные чанки в один FloatArray в диапазоне [-1, 1], как ждёт sherpa-onnx. */
    private fun drainRecordedSamples(): FloatArray = synchronized(recordedChunks) {
        val out = FloatArray(recordedSamples)
        var i = 0
        for (chunk in recordedChunks) {
            for (s in chunk) {
                out[i++] = s / 32768.0f
            }
        }
        recordedChunks.clear()
        recordedSamples = 0
        out
    }

    /**
     * Отключает системные эффекты обработки звука на сессии записи (AGC, шумодав, эхоподавление).
     * Каждый вызов защищён: часть эффектов может быть недоступна на устройстве.
     */
    private fun disableAudioEffects(sessionId: Int) {
        runCatching {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.enabled = false
            }
        }.onFailure { Log.w(TAG, "AGC off failed", it) }
        runCatching {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.enabled = false
            }
        }.onFailure { Log.w(TAG, "NoiseSuppressor off failed", it) }
        runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(sessionId)?.enabled = false
            }
        }.onFailure { Log.w(TAG, "AEC off failed", it) }
    }
}
