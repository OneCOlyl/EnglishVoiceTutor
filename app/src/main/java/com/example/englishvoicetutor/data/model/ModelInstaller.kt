package com.example.englishvoicetutor.data.model

import android.content.Context
import android.util.Log
import com.example.englishvoicetutor.data.engine.LiteRtLlmEngine
import com.example.englishvoicetutor.data.local.ModelPreferences
import com.example.englishvoicetutor.domain.model.LlmInstallState
import com.example.englishvoicetutor.domain.model.LlmModelOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

private const val MODEL_FILE_NAME = "model.litertlm"

/**
 * Недокачанная или ещё не проверенная модель. Активной становится только переименованием.
 *
 * Расширение `.litertlm` обязательно: LiteRT-LM определяет формат по имени файла
 * и на `model.litertlm.part` падает с «Unsupported or unknown file format», хотя
 * байты те же (найдено на устройстве, 2026-10-20).
 */
private const val PART_FILE_NAME = "model.pending.litertlm"

/** Прежнее имя временного файла — удаляем остатки при обновлении приложения. */
private const val LEGACY_PART_FILE_NAME = "model.litertlm.part"
private const val TAG = "ModelInstaller"

private const val MAX_REDIRECTS = 10
private const val CONNECT_TIMEOUT_MS = 30_000
private const val READ_TIMEOUT_MS = 60_000

/** Запас свободного места сверх размера файла: кеш движка и рабочие файлы приложения. */
private const val FREE_SPACE_MARGIN_BYTES = 200L * 1024 * 1024

/** Ошибка установки с уже готовым текстом для пользователя. */
class ModelInstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Общая логика скачивания и подключения LLM-модели. Переиспользуется экраном
 * первичной настройки и экраном настроек.
 *
 * Жизненный цикл файлов:
 * - загрузка идёт в `model.pending.litertlm`, рядом в [ModelPreferences] запоминается,
 *   какая модель (id + URL) в нём лежит — докачка через HTTP `Range` возможна только
 *   для той же самой модели, иначе в один файл склеились бы байты двух разных;
 * - после загрузки проверяется размер, затем движок поднимается прямо с временного файла;
 * - только если инициализация прошла, временный файл переименовывается в `model.litertlm`.
 *
 * Поэтому `model.litertlm` (по нему `MainActivity` решает, готово ли приложение)
 * всегда содержит модель, которая уже запускалась, а неудачная замена не трогает
 * ранее работавшую модель.
 */
@Singleton
class ModelInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmEngine: LiteRtLlmEngine,
    private val prefs: ModelPreferences,
) {

    /** Экран настройки и экран настроек не должны писать в один `.part` одновременно. */
    private val installMutex = Mutex()

    /**
     * Прогрев из `init` диалога и первый ход репетитора зовут [ensureInitialized]
     * одновременно: без замка оба видят пустой движок, и второй вызов закрывает
     * только что поднятую модель и поднимает её заново (замечено в logcat, EVT-502).
     */
    private val initMutex = Mutex()

    private val modelFile get() = File(context.filesDir, MODEL_FILE_NAME)
    private val partFile get() = File(context.filesDir, PART_FILE_NAME)

    /**
     * Скачивает модель по [url] и инициализирует движок. Прогресс отдаётся
     * потоком [LlmInstallState]; поток завершается после [LlmInstallState.Ready]
     * или [LlmInstallState.Error]. Отмена сбора потока прекращает загрузку,
     * недокачанный `.part` остаётся для докачки.
     *
     * @param token токен HuggingFace, добавляется в `Authorization` только если
     *   `option.requiresToken`.
     */
    fun install(option: LlmModelOption, url: String, token: String): Flow<LlmInstallState> =
        channelFlow {
            installMutex.withLock {
                try {
                    // Докачиваем только ту же модель с того же адреса — иначе начинаем заново.
                    val samePending = prefs.pendingModelId == option.id && prefs.pendingModelUrl == url
                    if (!samePending) partFile.delete()
                    // Файл под старым именем движок не откроет, а докачать его в новый нельзя.
                    File(context.filesDir, LEGACY_PART_FILE_NAME).delete()
                    prefs.setPendingModel(option.id, url)
                    // До первого байта проходит несколько секунд (редиректы, TLS);
                    // без этого экран после нажатия молчал, и кнопку жали повторно.
                    send(LlmInstallState.Loading("Подключаемся к серверу…"))

                    download(
                        url = url,
                        token = if (option.requiresToken) token else null,
                        destFile = partFile,
                        onProgress = { percent, downloadedMb, totalMb ->
                            trySend(LlmInstallState.Downloading(percent, downloadedMb, totalMb))
                        },
                    )

                    send(LlmInstallState.Loading("Загружаем модель в память (~30 сек)…"))
                    activateDownloadedModel()

                    prefs.setInstalledModel(option.id, option.displayName)
                    prefs.clearPendingModel()
                    if (option.isCustom) prefs.setCustomUrl(url)

                    send(LlmInstallState.Ready)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Install failed", e)
                    send(LlmInstallState.Error(userMessage(e)))
                }
            }
        }

    /**
     * Поднимает движок с `.part` и только при успехе делает его активной моделью.
     * Если модель не запустилась, файл бесполезен (битый или не тот формат) — удаляем его;
     * прежний `model.litertlm` остаётся, и [ensureInitialized] переподключит его.
     */
    private suspend fun activateDownloadedModel() {
        try {
            llmEngine.initialize(partFile.absolutePath)
        } catch (e: Exception) {
            // Сигнатура и размер — чтобы по логу отличить битый файл от HTML-страницы
            // ошибки или чужого формата: сам файл сейчас будет удалён.
            Log.w(TAG, "Model init failed: size=${partFile.length()}, header=${fileHeader(partFile)}")
            partFile.delete()
            prefs.clearPendingModel()
            val keepsOld = if (modelFile.exists()) " Прежняя модель сохранена." else ""
            throw ModelInstallException(
                "Модель скачана, но не запустилась на устройстве (повреждённый файл или " +
                    "неподдерживаемый формат).$keepsOld",
                e
            )
        }
        // rename(2) атомарен и замещает старый файл; уже открытая движком модель
        // при этом остаётся доступной по дескриптору.
        if (!partFile.renameTo(modelFile)) {
            throw ModelInstallException("Не удалось сохранить модель: ошибка записи на диск")
        }
        llmEngine.onModelFileMoved(modelFile.absolutePath)
    }

    private fun fileHeader(file: File): String = runCatching {
        file.inputStream().use { input ->
            val buf = ByteArray(16)
            val n = input.read(buf)
            buf.take(maxOf(n, 0)).joinToString("") { b ->
                val c = b.toInt() and 0xFF
                if (c in 0x20..0x7E) c.toChar().toString() else "\\x%02x".format(c)
            }
        }
    }.getOrDefault("?")

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
        initMutex.withLock {
            // Пока ждали замок, движок мог поднять параллельный вызов.
            if (llmEngine.isReady) return
            if (!modelFile.exists()) return
            llmEngine.initialize(modelFile.absolutePath)
        }
    }

    private suspend fun download(
        url: String,
        token: String?,
        destFile: File,
        onProgress: (percent: Int, downloadedMb: Long, totalMb: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // Поддержка докачки: если файл уже частично скачан.
        val existingBytes = if (destFile.exists()) destFile.length() else 0L
        val originHost = URL(url).host

        var currentUrl = url
        var conn: HttpURLConnection? = null

        // Следуем за редиректами HuggingFace (они используют CDN).
        for (attempt in 0 until MAX_REDIRECTS) {
            val target = URL(currentUrl)
            val c = (target.openConnection() as HttpURLConnection).apply {
                // Токен — только исходному хосту: CDN получает подписанную ссылку
                // и заголовок не нужен, а отдавать секрет чужому хосту незачем.
                if (token != null && target.host == originHost) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                connect()
            }
            if (c.responseCode in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                currentUrl = URL(target, location ?: throw IOException("Редирект без адреса")).toString()
            } else {
                conn = c
                break
            }
        }
        val connection = conn ?: throw ModelInstallException("Сервер перенаправляет запрос слишком много раз")

        try {
            when (val code = connection.responseCode) {
                401 -> throw ModelInstallException("Неверный токен HuggingFace")
                403 -> throw ModelInstallException(
                    "Нет доступа к модели: примите лицензию на странице модели на HuggingFace"
                )
                404 -> throw ModelInstallException("Файл модели не найден по ссылке (HTTP 404)")
                // Диапазон за концом файла: прошлый запуск докачал всё, но не успел
                // проверить модель (приложение закрыли). Файл целиком — проверяем его.
                416 -> {
                    if (existingBytes > 0) return@withContext
                    throw ModelInstallException("Ошибка сервера: HTTP 416")
                }
                !in 200..206 -> throw ModelInstallException("Ошибка сервера: HTTP $code")
            }

            val resuming = existingBytes > 0 && connection.responseCode == 206
            val startBytes = if (resuming) existingBytes else 0L
            val contentLength = connection.contentLengthLong
            val totalBytes = if (contentLength >= 0) contentLength + startBytes else -1L

            if (contentLength > 0) {
                val free = context.filesDir.usableSpace
                if (free < contentLength + FREE_SPACE_MARGIN_BYTES) {
                    throw ModelInstallException(
                        "Недостаточно места: нужно ~${(contentLength + FREE_SPACE_MARGIN_BYTES) / 1024 / 1024} МБ, " +
                            "свободно ${free / 1024 / 1024} МБ"
                    )
                }
            }

            var downloaded = startBytes
            connection.inputStream.buffered().use { input ->
                FileOutputStream(destFile, resuming).use { output ->
                    val buf = ByteArray(65536) // 64 КБ чанки
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        // Ушли с экрана — загрузка не должна тихо продолжаться в фоне.
                        ensureActive()
                        output.write(buf, 0, n)
                        downloaded += n
                        val percent = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else 0
                        onProgress(percent, downloaded / 1024 / 1024, totalBytes / 1024 / 1024)
                    }
                    output.flush()
                }
            }
            // Сервер мог закрыть соединение раньше времени без исключения. Такой файл
            // оставляем: следующая попытка докачает его через Range.
            if (totalBytes > 0 && downloaded != totalBytes) {
                throw IOException("Загрузка прервалась: получено $downloaded из $totalBytes байт")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Текст ошибки для UI: различаем сеть, диск и прочее (MM-010). */
    private fun userMessage(e: Exception): String = when {
        e is ModelInstallException -> e.message.orEmpty()
        e is UnknownHostException -> "Нет подключения к интернету. Повторите — загрузка продолжится с места остановки"
        e is SocketTimeoutException -> "Сервер не отвечает. Повторите — загрузка продолжится с места остановки"
        e.message?.contains("ENOSPC") == true || e.message?.contains("No space left") == true ->
            "Закончилось место на устройстве"
        // Своё сообщение о недокачке уже по-русски; системные (`unexpected end of
        // stream`, `Connection reset`) пользователю ничего не говорят — они в логе.
        e is IOException && e.message?.startsWith("Загрузка прервалась") == true ->
            "${e.message}. Повторите — загрузка продолжится с места остановки"
        e is IOException -> "Соединение прервалось. Повторите — загрузка продолжится с места остановки"
        else -> e.message ?: "Неизвестная ошибка"
    }
}
