package com.example.englishvoicetutor.data.model

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Распаковка модели (MM-003, MM-004): каталог модели появляется в целевом каталоге
 * только целиком, а записи архива не могут выйти за его пределы.
 */
class ArchiveUnpackTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun archive(vararg files: Pair<String, String>): File {
        val out = tmp.newFile("model.tar.bz2")
        TarArchiveOutputStream(BZip2CompressorOutputStream(out.outputStream())).use { tar ->
            for ((name, content) in files) {
                val bytes = content.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return out
    }

    @Test
    fun `целый архив публикуется в целевой каталог, staging удаляется`() {
        val dest = tmp.newFolder("files")
        val arc = archive(
            "model-x/tokens.txt" to "tokens",
            "model-x/encoder.onnx" to "weights",
            "model-x/test_wavs/0.wav" to "wav",
        )

        unpackArchive(arc, dest, ".unpack-test", skipEntry = { it.contains("/test_wavs/") })

        assertEquals("tokens", File(dest, "model-x/tokens.txt").readText())
        assertEquals("weights", File(dest, "model-x/encoder.onnx").readText())
        assertFalse(File(dest, "model-x/test_wavs").exists())
        assertFalse(File(dest, ".unpack-test").exists())
    }

    @Test
    fun `запись с выходом за каталог отклоняется и ничего не публикуется`() {
        val dest = tmp.newFolder("files")
        val arc = archive(
            "model-x/tokens.txt" to "tokens",
            "../escaped.txt" to "evil",
        )

        try {
            unpackArchive(arc, dest, ".unpack-test")
            fail("ожидалась ошибка")
        } catch (e: IOException) {
            // ожидаемо
        }

        assertFalse(File(tmp.root, "escaped.txt").exists())
        assertFalse(File(dest, "model-x").exists())
        assertFalse(File(dest, ".unpack-test").exists())
    }

    @Test
    fun `обрезанный архив не заменяет прежнюю модель`() {
        val dest = tmp.newFolder("files")
        File(dest, "model-x").mkdirs()
        File(dest, "model-x/tokens.txt").writeText("old")
        val full = archive("model-x/tokens.txt" to "new".repeat(10_000)).readBytes()
        val truncated = tmp.newFile("truncated.tar.bz2").apply { writeBytes(full.copyOf(full.size / 2)) }

        try {
            unpackArchive(truncated, dest, ".unpack-test")
            fail("ожидалась ошибка")
        } catch (e: IOException) {
            // ожидаемо
        }

        assertEquals("old", File(dest, "model-x/tokens.txt").readText())
        assertFalse(File(dest, ".unpack-test").exists())
    }

    @Test
    fun `остаток прерванной распаковки очищается перед новой`() {
        val dest = tmp.newFolder("files")
        File(dest, ".unpack-test/model-x").mkdirs()
        File(dest, ".unpack-test/model-x/stale.bin").writeText("stale")
        val arc = archive("model-x/tokens.txt" to "tokens")

        unpackArchive(arc, dest, ".unpack-test")

        assertTrue(File(dest, "model-x/tokens.txt").exists())
        assertFalse(File(dest, "model-x/stale.bin").exists())
    }
}
