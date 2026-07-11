package com.example.englishvoicetutor.data.model

import android.content.Context
import android.util.Log
import com.example.englishvoicetutor.data.engine.LiteRtLlmEngine
import com.example.englishvoicetutor.data.local.ModelPreferences
import com.example.englishvoicetutor.domain.model.LlmInstallState
import com.example.englishvoicetutor.domain.model.LlmModelOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val MODEL_FILE_NAME = "model.litertlm"
private const val TAG = "ModelInstaller"

/**
 * Общая логика скачивания и подключения LLM-модели. Переиспользуется экраном
 * первичной настройки и экраном настроек. Активный файл всегда `model.litertlm`
 * в `filesDir`; докачка через HTTP `Range` работает только когда пользователь
 * ставит ту же самую модель, что уже (частично) скачана.
 */
@Singleton
class ModelInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmEngine: LiteRtLlmEngine,
    private val prefs: ModelPreferences,
) {

    /**
     * Скачивает модель по [url] и инициализирует движок. Прогресс отдаётся
     * потоком [LlmInstallState]; поток завершается после [LlmInstallState.Ready]
     * или [LlmInstallState.Error].
     *
     * @param token токен HuggingFace, добавляется в `Authorization` только если
     *   `option.requiresToken`.
     */
    fun install(option: LlmModelOption, url: String, token: String): Flow<LlmInstallState> =
        channelFlow {
            try {
                val modelFile = File(context.filesDir, MODEL_FILE_NAME)

                // Докачиваем только ту же модель. Если ставим другую — сносим старый файл.
                val sameModel = prefs.installedModelId == option.id
                if (!sameModel && modelFile.exists()) {
                    modelFile.delete()
                }

                download(
                    url = url,
                    token = if (option.requiresToken) token else null,
                    destFile = modelFile,
                    onProgress = { percent, downloadedMb, totalMb ->
                        trySend(LlmInstallState.Downloading(percent, downloadedMb, totalMb))
                    },
                )

                send(LlmInstallState.Loading("Загружаем модель в память (~30 сек)…"))
                llmEngine.initialize(modelFile.absolutePath)

                prefs.setInstalledModel(option.id, option.displayName)
                if (option.isCustom) prefs.setCustomUrl(url)

                send(LlmInstallState.Ready)
            } catch (e: Exception) {
                Log.e(TAG, "Install failed", e)
                // Неполный файл бесполезен — удаляем, чтобы не мешал следующей попытке.
                File(context.filesDir, MODEL_FILE_NAME).delete()
                send(LlmInstallState.Error(e.message ?: "Неизвестная ошибка"))
            }
            awaitClose { }
        }

    /**
     * Переподключает уже скачанную модель к движку после перезапуска процесса.
     * [LiteRtLlmEngine] держит модель в памяти процесса, а не на диске, поэтому при
     * холодном старте (файл `model.litertlm` есть, но движок пуст) её надо
     * инициализировать заново — иначе первый же вызов `generateReply` падает с
     * «Модель не загружена». Идемпотентно: ничего не делает, если движок уже готов
     * или файла модели нет. Тяжёлая (~30 сек) — звать вне UI-потока.
     */
    suspend fun ensureInitialized() {
        if (llmEngine.isReady) return
        val modelFile = File(context.filesDir, MODEL_FILE_NAME)
        if (!modelFile.exists()) return
        llmEngine.initialize(modelFile.absolutePath)
    }

    private suspend fun download(
        url: String,
        token: String?,
        destFile: File,
        onProgress: (percent: Int, downloadedMb: Long, totalMb: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // Поддержка докачки: если файл уже частично скачан.
        val existingBytes = if (destFile.exists()) destFile.length() else 0L

        var currentUrl = url
        lateinit var conn: HttpURLConnection

        // Следуем за редиректами HuggingFace (они используют CDN).
        repeat(10) {
            conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
                if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
                instanceFollowRedirects = false
                connect()
            }
            val code = conn.responseCode
            if (code in 300..399) {
                currentUrl = conn.getHeaderField("Location")
                conn.disconnect()
            } else {
                return@repeat
            }
        }

        if (conn.responseCode == 401) {
            throw IllegalArgumentException("Неверный токен HuggingFace")
        }
        if (conn.responseCode !in 200..206) {
            throw IllegalStateException("Ошибка сервера: ${conn.responseCode}")
        }

        val resuming = existingBytes > 0 && conn.responseCode == 206
        val totalBytes = conn.contentLengthLong + if (resuming) existingBytes else 0L

        conn.inputStream.buffered().use { input ->
            FileOutputStream(destFile, resuming).use { output ->
                copyWithProgress(input, output, startBytes = if (resuming) existingBytes else 0L, totalBytes) { downloaded ->
                    val percent = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else 0
                    onProgress(percent, downloaded / 1024 / 1024, totalBytes / 1024 / 1024)
                }
            }
        }
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        startBytes: Long,
        totalBytes: Long,
        onProgress: (Long) -> Unit,
    ) {
        val buf = ByteArray(65536) // 64 КБ чанки
        var downloaded = startBytes
        var n: Int
        while (input.read(buf).also { n = it } != -1) {
            output.write(buf, 0, n)
            downloaded += n
            onProgress(downloaded)
        }
        output.flush()
    }
}
