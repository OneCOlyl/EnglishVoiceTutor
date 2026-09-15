package com.example.englishvoicetutor.data.model

import com.example.englishvoicetutor.domain.model.ModelDownloadState
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

// Доли общей шкалы прогресса: скачивание → распаковка → загрузка в память. Раньше распаковка
// шла без прогресса, и пользователь видел скачок 0 → 99 на несколько минут.
private const val DOWNLOAD_PROGRESS_END = 80
private const val UNPACK_PROGRESS_END = 98

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

        var currentUrl = url
        lateinit var stream: InputStream
        var totalBytes = -1L

        repeat(5) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
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
        val tmpArchive = File(destDir, tmpArchiveName)
        var downloaded = 0L
        stream.use { input ->
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

        log("Распаковываем архив…")
        // Распаковка bzip2 на телефоне занимает минуты, поэтому считаем прогресс по
        // прочитанной части архива — иначе полоса стоит на месте и кажется, что всё зависло.
        val archiveBytes = tmpArchive.length()
        val counting = CountingInputStream(tmpArchive.inputStream().buffered())
        var lastReportedPct = -1

        fun reportUnpackProgress() {
            if (archiveBytes <= 0) return
            val span = UNPACK_PROGRESS_END - DOWNLOAD_PROGRESS_END
            val pct = DOWNLOAD_PROGRESS_END + (counting.bytesRead * span / archiveBytes).toInt()
            if (pct != lastReportedPct) {
                lastReportedPct = pct
                val mb = counting.bytesRead / 1024 / 1024
                onState(
                    ModelDownloadState.Downloading(
                        pct.coerceAtMost(UNPACK_PROGRESS_END),
                        logs + "Распаковано: $mb из ${archiveBytes / 1024 / 1024} МБ"
                    )
                )
            }
        }

        // Архив: bzip2 → tar. Внутри всё лежит под каталогом с именем модели.
        TarArchiveInputStream(BZip2CompressorInputStream(counting)).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                if (skipEntry(entry.name)) {
                    entry = tar.nextEntry
                    continue
                }
                val outFile = File(destDir, entry.name)
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
                            reportUnpackProgress()
                        }
                    }
                }
                entry = tar.nextEntry
            }
        }
        counting.close()
        tmpArchive.delete()
        log("Готово!")
    }
}
