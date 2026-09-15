package com.example.englishvoicetutor.domain

import com.example.englishvoicetutor.domain.model.VocabProgress
import com.example.englishvoicetutor.domain.model.VocabStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Планировщик повторений — чистая логика без Android, поэтому проверяем его
 * обычным unit-тестом. Именно от него зависит, вернётся ли слово к пользователю
 * вовремя, а ошибка здесь тихо ломает всё обучение.
 */
class SrsScheduleTest {

    private val now = 1_700_000_000_000L
    private val fresh = VocabProgress(itemId = "a1-greetings:hello")

    @Test
    fun `первый верный ответ переводит слово в изучаемые и назначает повтор через сутки`() {
        val result = SrsSchedule.onCorrect(fresh, now)

        assertEquals(1, result.box)
        assertEquals(VocabStatus.LEARNING, result.status)
        assertEquals(now + TimeUnit.DAYS.toMillis(1), result.dueAtMillis)
        assertEquals(1, result.timesCorrect)
    }

    @Test
    fun `серия верных ответов доводит слово до статуса «выучено»`() {
        var progress = fresh
        repeat(4) { progress = SrsSchedule.onCorrect(progress, now) }

        assertEquals(VocabStatus.KNOWN, progress.status)
        assertEquals(4, progress.box)
    }

    @Test
    fun `коробка не растёт выше максимальной`() {
        var progress = fresh
        repeat(20) { progress = SrsSchedule.onCorrect(progress, now) }

        assertEquals(SrsSchedule.maxBox, progress.box)
        assertEquals(
            now + TimeUnit.DAYS.toMillis(SrsSchedule.INTERVALS_DAYS.last().toLong()),
            progress.dueAtMillis
        )
    }

    @Test
    fun `ошибка сбрасывает прогресс и возвращает слово в текущую сессию`() {
        val learned = SrsSchedule.onCorrect(SrsSchedule.onCorrect(fresh, now), now)

        val result = SrsSchedule.onWrong(learned, now)

        assertEquals(0, result.box)
        assertEquals(VocabStatus.LEARNING, result.status)
        assertEquals(now, result.dueAtMillis)
        assertEquals(1, result.timesWrong)
        assertTrue("слово должно быть доступно сразу", result.dueAtMillis <= now)
    }

    @Test
    fun `ручная отметка «выучено» и её снятие`() {
        val known = SrsSchedule.markKnown(fresh, now)
        assertEquals(VocabStatus.KNOWN, known.status)
        assertEquals(SrsSchedule.maxBox, known.box)

        val reset = SrsSchedule.reset(known)
        assertEquals(VocabStatus.NEW, reset.status)
        assertEquals(0, reset.box)
        assertEquals(0, reset.dueAtMillis)
    }
}
