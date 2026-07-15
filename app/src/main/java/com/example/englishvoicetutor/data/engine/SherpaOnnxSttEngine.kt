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
import com.example.englishvoicetutor.domain.model.ModelDownloadState
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

// Whisper small.en (int8) — английская офлайн-модель. Заметно устойчивее Moonshine base
// на коротких фразах, акценте и названиях; non-streaming — идеально ложится на push-to-talk.
private const val MODEL_URL =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
        "sherpa-onnx-whisper-small.en.tar.bz2"
private const val MODEL_DIR_NAME = "sherpa-onnx-whisper-small.en"
private const val SAMPLE_RATE = 16_000
private const val TAG = "SherpaOnnxSttEngine"

@Singleton
class SherpaOnnxSttEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : SttEngine {

    @Volatile
    private var recognizer: OfflineRecognizer? = null
    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var recordingThread: Thread? = null

    // Аккумулятор аудио за одно нажатие: Moonshine распознаёт всю фразу разом на stopRecording.
    private val recordedChunks = mutableListOf<ShortArray>()
    @Volatile private var recordedSamples = 0

    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    /**
     * Убеждаемся, что модель скачана, распакована и загружена в память.
     * Вызывается лениво при первом использовании микрофона.
     */
    private suspend fun ensureRecognizer(): OfflineRecognizer = withContext(Dispatchers.IO) {
        recognizer?.let { return@withContext it }

        val modelDir = File(context.filesDir, MODEL_DIR_NAME)
        val modelReady = File(modelDir, "small.en-tokens.txt").exists() &&
            File(modelDir, "small.en-encoder.int8.onnx").exists()

        if (!modelReady) {
            modelDir.deleteRecursively()
            try {
                downloadAndUnpack(MODEL_URL, context.filesDir)
            } catch (e: Exception) {
                _downloadState.value = ModelDownloadState.Error(e.message ?: "Ошибка загрузки")
                throw e
            }
        }

        _downloadState.value = ModelDownloadState.Downloading(99, listOf("Загружаем модель в память…"))
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(modelDir, "small.en-encoder.int8.onnx").absolutePath,
                    decoder = File(modelDir, "small.en-decoder.int8.onnx").absolutePath,
                    language = "en",
                    task = "transcribe",
                ),
                tokens = File(modelDir, "small.en-tokens.txt").absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            // assetManager не передаём — модель лежит в filesDir, пути абсолютные.
        )
        val r = OfflineRecognizer(config = config)
        _downloadState.value = ModelDownloadState.Ready
        r.also { recognizer = it }
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

        // Whisper — офлайн-модель: скармливаем всю фразу разом одним стримом.
        val stream = r.createStream()
        stream.acceptWaveform(samples, SAMPLE_RATE)
        r.decode(stream)
        val text = r.getResult(stream).text
        stream.release()

        Log.d(TAG, "Whisper result: '$text'")
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

    private fun downloadAndUnpack(url: String, destDir: File) {
        val logs = mutableListOf<String>()

        fun log(msg: String) {
            logs.add(msg)
            _downloadState.value = ModelDownloadState.Downloading(0, logs.toList())
        }

        log("Подключаемся к серверу…")

        var currentUrl = url
        lateinit var stream: java.io.InputStream
        var totalBytes = -1L

        repeat(5) {
            val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                currentUrl = conn.getHeaderField("Location")
                conn.disconnect()
            } else {
                totalBytes = conn.contentLengthLong
                stream = conn.inputStream.buffered()
                return@repeat
            }
        }

        log("Скачиваем модель…")

        // Сохраняем архив во временный файл, считая прогресс.
        val tmpArchive = File(destDir, "model_tmp.tar.bz2")
        var downloaded = 0L
        stream.use { input ->
            FileOutputStream(tmpArchive).use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    if (totalBytes > 0) {
                        val pct = (downloaded * 100 / totalBytes).toInt()
                        val mb = downloaded / 1024 / 1024
                        _downloadState.value = ModelDownloadState.Downloading(
                            pct,
                            logs + "Загружено: $mb МБ ($pct%)"
                        )
                    }
                }
            }
        }

        log("Распаковываем архив…")
        // Архив: bzip2 → tar. Внутри всё лежит под каталогом MODEL_DIR_NAME/.
        TarArchiveInputStream(
            BZip2CompressorInputStream(tmpArchive.inputStream().buffered())
        ).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { tar.copyTo(it) }
                }
                entry = tar.nextEntry
            }
        }
        tmpArchive.delete()
        log("Готово!")
    }
}
