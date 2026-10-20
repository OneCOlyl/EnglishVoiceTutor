package com.example.englishvoicetutor.data.model

import com.example.englishvoicetutor.domain.model.ModelDownloadState
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

// Доли общей шкалы прогресса: скачивание → распаковка → загрузка в память. Раньше распаковка
// шла без прогресса, и пользователь видел скачок 0 → 99 на несколько минут.
private const val DOWNLOAD_PROGRESS_END = 80
private const val UNPACK_PROGRESS_END = 98

private const val MAX_REDIRECTS = 5

// Без таймаутов обрыв сети вешает загрузку навсегда: read() ждёт байты, которых не будет.
private const val CONNECT_TIMEOUT_MS = 30_000
private const val READ_TIMEOUT_MS = 60_000

/**
 * Префикс временного каталога распаковки. Начинается с точки, чтобы не попасть
 * под префиксы очистки старых моделей в `SherpaOnnxSttEngine` (`sherpa-onnx-`, `vosk-model-`).
 */
internal const val STAGING_DIR_PREFIX = ".unpack-"

/** Считает, сколько байт архива уже прочитано, — по этому и рисуем прогресс распаковки. */
private class CountingInputStream(
    private val delegate: InputStream
) : InputStream() {

    @Volatile var bytesRead = 0L
        private set

    override fun read(): Int = delegate.read().also { if (it != -1) bytesRead++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        delegate.read(b, off, len).also { if (it > 0) bytesRead += it }

    override fun available(): Int = delegate.available()

    override fun close() = delegate.close()
}

/**
 * Скачивает `.tar.bz2` с релизов sherpa-onnx и распаковывает его в каталог приложения.
 *
 * Вынесено из STT-движка, когда такой же архив понадобился движку озвучки:
 * логика с редиректами GitHub, прогрессом и потоковой распаковкой нетривиальная,
 * и дублировать её во второй раз было бы ошибкой.
 *
 * Каталог модели появляется в [destDir] только целиком: распаковка идёт во временный
 * каталог и переносится переименованием после успешного конца архива. Поэтому
 * проверка «файлы модели на месте» в движках не примет недораспакованную модель.
 *
 * Вызывать только из фонового потока — метод блокирующий.
 */
@Singleton
class ArchiveModelDownloader @Inject constructor() {

    /**
     * @param tmpArchiveName имя временного файла; у каждой модели своё, иначе две
     *   параллельные загрузки перетрут архив друг друга.
     * @param skipEntry какие записи архива не распаковывать (тестовые wav, карточки моделей).
     * @param onState обновления прогресса для UI.
     */
    fun downloadAndUnpack(
        url: String,
        destDir: File,
        tmpArchiveName: String,
        skipEntry: (String) -> Boolean = { false },
        onState: (ModelDownloadState) -> Unit
    ) {
        val logs = mutableListOf<String>()

        fun log(msg: String) {
            logs.add(msg)
            onState(ModelDownloadState.Downloading(0, logs.toList()))
        }

        log("Подключаемся к серверу…")
        val conn = openFollowingRedirects(url)

        log("Скачиваем модель…")
        val totalBytes = conn.contentLengthLong
        val tmpArchive = File(destDir, tmpArchiveName)
        var downloaded = 0L
        try {
            conn.inputStream.buffered().use { input ->
                FileOutputStream(tmpArchive).use { output ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        if (totalBytes > 0) {
                            val pct = (downloaded * DOWNLOAD_PROGRESS_END / totalBytes).toInt()
                            val mb = downloaded / 1024 / 1024
                            val totalMb = totalBytes / 1024 / 1024
                            onState(
                                ModelDownloadState.Downloading(
                                    pct,
                                    logs + "Загружено: $mb из $totalMb МБ"
                                )
                            )
                        }
                    }
                }
            }
            // Сервер может закрыть соединение раньше времени без исключения —
            // такой архив распаковался бы частично или упал бы на середине bzip2.
            if (totalBytes > 0 && downloaded != totalBytes) {
                throw IOException("Загрузка прервалась: получено $downloaded из $totalBytes байт")
            }
        } catch (e: Exception) {
            tmpArchive.delete()
            throw e
        } finally {
            conn.disconnect()
        }

        log("Распаковываем архив…")
        try {
            unpackArchive(
                archive = tmpArchive,
                destDir = destDir,
                stagingName = STAGING_DIR_PREFIX + tmpArchiveName,
                skipEntry = skipEntry,
                onProgress = { bytesRead, archiveBytes ->
                    val span = UNPACK_PROGRESS_END - DOWNLOAD_PROGRESS_END
                    val pct = DOWNLOAD_PROGRESS_END + (bytesRead * span / archiveBytes).toInt()
                    onState(
                        ModelDownloadState.Downloading(
                            pct.coerceAtMost(UNPACK_PROGRESS_END),
                            logs + "Распаковано: ${bytesRead / 1024 / 1024} из ${archiveBytes / 1024 / 1024} МБ"
                        )
                    )
                },
            )
        } finally {
            tmpArchive.delete()
        }
        log("Готово!")
    }

    /**
     * Открывает соединение, вручную проходя редиректы (GitHub отдаёт архивы через CDN
     * с другим хостом). Возвращает соединение с кодом 2xx — иначе бросает исключение
     * с понятным текстом, а не пытается сохранить HTML-страницу ошибки как архив.
     */
    private fun openFollowingRedirects(url: String): HttpURLConnection {
        var currentUrl = url
        repeat(MAX_REDIRECTS) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                connect()
            }
            val code = conn.responseCode
            when (code) {
                in 200..299 -> return conn
                in 300..399 -> {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    // Относительный Location разрешаем от текущего адреса.
                    currentUrl = URL(URL(currentUrl), location ?: throw IOException("Редирект без адреса")).toString()
                }
                else -> {
                    conn.disconnect()
                    throw IOException("Ошибка сервера: HTTP $code")
                }
            }
        }
        throw IOException("Слишком много перенаправлений")
    }
}

/**
 * Распаковывает `.tar.bz2` [archive] в [destDir] атомарно по верхнеуровневым записям.
 *
 * Сначала всё пишется в `destDir/stagingName`. Только когда архив прочитан до конца без
 * ошибок, каждый верхнеуровневый каталог переносится в [destDir] переименованием (старая
 * версия с тем же именем удаляется). При любой ошибке staging-каталог удаляется, а в
 * [destDir] не появляется ничего — недораспакованная модель не может выглядеть готовой.
 *
 * Записи, выходящие за пределы каталога распаковки (`../`, абсолютные пути), отклоняются:
 * архив приходит из сети, и такая запись перезаписала бы произвольный файл приложения.
 *
 * Вынесено из [ArchiveModelDownloader] отдельной функцией, чтобы проверять без сети.
 */
internal fun unpackArchive(
    archive: File,
    destDir: File,
    stagingName: String,
    skipEntry: (String) -> Boolean = { false },
    onProgress: (bytesRead: Long, archiveBytes: Long) -> Unit = { _, _ -> },
) {
    val staging = File(destDir, stagingName)
    // Остаток прошлой прерванной распаковки (закрыли приложение, кончилось место).
    staging.deleteRecursively()
    if (!staging.mkdirs()) throw IOException("Не удалось создать каталог распаковки")
    val stagingRoot = staging.canonicalFile

    try {
        // Распаковка bzip2 на телефоне занимает минуты, поэтому считаем прогресс по
        // прочитанной части архива — иначе полоса стоит на месте и кажется, что всё зависло.
        val archiveBytes = archive.length()
        var lastReportedPct = -1
        CountingInputStream(archive.inputStream().buffered()).use { counting ->
            fun reportProgress() {
                if (archiveBytes <= 0) return
                val pct = (counting.bytesRead * 100 / archiveBytes).toInt()
                if (pct != lastReportedPct) {
                    lastReportedPct = pct
                    onProgress(counting.bytesRead, archiveBytes)
                }
            }

            // Архив: bzip2 → tar. Внутри всё лежит под каталогом с именем модели.
            TarArchiveInputStream(BZip2CompressorInputStream(counting)).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!skipEntry(entry.name)) {
                        val outFile = File(staging, entry.name).canonicalFile
                        if (!outFile.path.startsWith(stagingRoot.path + File.separator)) {
                            throw IOException("Недопустимый путь в архиве: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            // Копируем вручную, а не через copyTo: отдельные файлы весят сотни
                            // мегабайт, и прогресс надо обновлять по ходу файла.
                            FileOutputStream(outFile).use { output ->
                                val buf = ByteArray(64 * 1024)
                                var n: Int
                                while (tar.read(buf).also { n = it } != -1) {
                                    output.write(buf, 0, n)
                                    reportProgress()
                                }
                            }
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }

        // Архив прочитан целиком — публикуем содержимое переименованием.
        for (item in staging.listFiles().orEmpty()) {
            val target = File(destDir, item.name)
            target.deleteRecursively()
            if (!item.renameTo(target)) {
                throw IOException("Не удалось перенести ${item.name} из каталога распаковки")
            }
        }
    } finally {
        staging.deleteRecursively()
    }
}
