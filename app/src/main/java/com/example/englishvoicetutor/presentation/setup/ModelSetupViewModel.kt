package com.example.englishvoicetutor.presentation.setup

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.engine.LiteRtLlmEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

private const val MODEL_URL =
    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
private const val MODEL_FILE_NAME = "model.litertlm"

@HiltViewModel
class ModelSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmEngine: LiteRtLlmEngine
) : ViewModel() {

    private val _state = MutableStateFlow<ModelSetupState>(ModelSetupState.Idle)
    val state: StateFlow<ModelSetupState> = _state

    fun downloadAndLoad(hfToken: String, onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                val modelFile = File(context.filesDir, MODEL_FILE_NAME)

                if (!modelFile.exists()) {
                    download(MODEL_URL, hfToken, modelFile)
                }

                _state.value = ModelSetupState.Loading("Загружаем модель в память (~30 сек)…")
                llmEngine.initialize(modelFile.absolutePath)
                onReady()

            } catch (e: Exception) {
                Log.e("ModelSetup", "Failed", e)
                // Удаляем неполный файл
                File(context.filesDir, MODEL_FILE_NAME).delete()
                _state.value = ModelSetupState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    private suspend fun download(url: String, token: String, destFile: File) =
        withContext(Dispatchers.IO) {

            // Поддержка докачки: если файл уже частично скачан
            val existingBytes = if (destFile.exists()) destFile.length() else 0L

            var currentUrl = url
            lateinit var conn: HttpURLConnection

            // Следуем за редиректами HuggingFace (они используют CDN)
            repeat(10) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Authorization", "Bearer $token")
                    if (existingBytes > 0) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
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

            val totalBytes = conn.contentLengthLong + existingBytes
            var downloaded = existingBytes

            conn.inputStream.buffered().use { input ->
                destFile.outputStream().let { out ->
                    // Если докачиваем — открываем в режиме append
                    if (existingBytes > 0 && conn.responseCode == 206) {
                        destFile.appendBytes(ByteArray(0)) // просто убеждаемся что файл есть
                        java.io.FileOutputStream(destFile, true).use { appendOut ->
                            copyWithProgress(input, appendOut, downloaded, totalBytes) {
                                downloaded = it
                                _state.value = ModelSetupState.Downloading(
                                    progressPercent = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else 0,
                                    downloadedMb = downloaded / 1024 / 1024,
                                    totalMb = totalBytes / 1024 / 1024,
                                    status = "Скачиваем модель…"
                                )
                            }
                        }
                    } else {
                        java.io.FileOutputStream(destFile, false).use { freshOut ->
                            copyWithProgress(input, freshOut, downloaded, totalBytes) {
                                downloaded = it
                                _state.value = ModelSetupState.Downloading(
                                    progressPercent = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else 0,
                                    downloadedMb = downloaded / 1024 / 1024,
                                    totalMb = totalBytes / 1024 / 1024,
                                    status = "Скачиваем модель…"
                                )
                            }
                        }
                    }
                }
            }
        }

    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        startBytes: Long,
        totalBytes: Long,
        onProgress: (Long) -> Unit
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