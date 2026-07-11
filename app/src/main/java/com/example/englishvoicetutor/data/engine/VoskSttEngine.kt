package com.example.englishvoicetutor.data.engine

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.englishvoicetutor.domain.model.ModelDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val MODEL_URL =
    "https://alphacephei.com/kaldi/models/vosk-model-en-us-0.22-lgraph.zip"
private const val MODEL_DIR_NAME = "vosk-model-en-us-0.22-lgraph"
private const val SAMPLE_RATE = 16_000

// Порог амплитуды для определения речи (0..32767).
// Если микрофон слишком чувствительный или шумный — поднять до ~1000.
private const val SPEECH_THRESHOLD = 500
// Количество «тихих» чанков подряд, после которых считаем, что пользователь замолчал.
// При bufferSize=3200 сэмплов @ 16kHz один чанк ≈ 200 мс → 15 чанков ≈ 3 сек тишины.
private const val SILENCE_CHUNKS_TO_STOP = 15

@Singleton
class VoskSttEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : SttEngine {

    @Volatile
    private var model: Model? = null
    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var recognizer: Recognizer? = null
    @Volatile private var recordingThread: Thread? = null
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    /**
     * Убеждаемся, что модель скачана и загружена.
     * Вызывается лениво при первом использовании.
     */
    private suspend fun ensureModel(): Model = withContext(Dispatchers.IO) {
        model?.let { return@withContext it }

        val modelDir = File(context.filesDir, MODEL_DIR_NAME)
        val modelReady = File(modelDir, "am/final.mdl").exists()

        if (!modelReady) {
            modelDir.deleteRecursively()
            try {
                downloadAndUnzip(MODEL_URL, context.filesDir)
            } catch (e: Exception) {
                _downloadState.value = ModelDownloadState.Error(e.message ?: "Ошибка загрузки")
                throw e
            }
        }

        _downloadState.value = ModelDownloadState.Downloading(99, listOf("Загружаем модель в память…"))
        val m = Model(modelDir.absolutePath)
        _downloadState.value = ModelDownloadState.Ready
        m.also { model = it }
    }

    override suspend fun startRecording() {
        val m = ensureModel() // теперь модель точно загружена

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val chunkSamples = 3200
        val bufferBytes = maxOf(minBuf, chunkSamples * 2)

        recognizer = Recognizer(m, SAMPLE_RATE.toFloat())
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
        } catch (e: SecurityException) {
            throw IllegalStateException("Нет разрешения на микрофон", e)
        }
        audioRecord!!.startRecording()

        recordingThread = Thread {
            val buffer = ShortArray(chunkSamples)
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    val bytes = shortsToLittleEndianBytes(buffer, read)
                    recognizer?.acceptWaveForm(bytes, bytes.size)
                }
            }
        }.also { it.start() }
    }

    override fun stopRecording(): String {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        recordingThread?.join(2000) // ждём пока поток допишет данные в recognizer
        recordingThread = null

        val json = recognizer?.finalResult ?: "{}"
        recognizer?.close()
        recognizer = null

        return JSONObject(json).optString("text", "").trim()
    }
    private fun shortsToLittleEndianBytes(shorts: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val v = shorts[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = (v shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    private fun downloadAndUnzip(url: String, destDir: File) {
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

        // Сначала сохраняем zip во временный файл, считая прогресс
        val tmpZip = File(destDir, "model_tmp.zip")
        var downloaded = 0L
        stream.use { input ->
            FileOutputStream(tmpZip).use { output ->
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
        ZipInputStream(tmpZip.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        tmpZip.delete()
        log("Готово!")
    }
}