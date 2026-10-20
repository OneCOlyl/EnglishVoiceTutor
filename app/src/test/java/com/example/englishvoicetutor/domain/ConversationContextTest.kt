package com.example.englishvoicetutor.domain

import com.example.englishvoicetutor.domain.model.Message
import com.example.englishvoicetutor.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Отбор контекста решает, что репетитор «помнит»: перебор переполнит контекст движка,
 * недобор — заставит забыть знакомство. Фиксируем обе границы (EVT-110).
 */
class ConversationContextTest {

    private fun dialog(count: Int, textLength: Int = 10): List<Message> =
        (1..count).map { i ->
            Message(
                id = i.toLong(),
                conversationId = 1,
                role = if (i % 2 == 1) MessageRole.TUTOR else MessageRole.USER,
                text = "x".repeat(textLength),
                timestampMillis = i.toLong()
            )
        }

    @Test
    fun `короткий диалог уходит целиком`() {
        val messages = dialog(30)
        assertEquals(messages, ConversationContext.select(messages))
    }

    @Test
    fun `длинный диалог - начало плюс последние реплики без дублей`() {
        val messages = dialog(80)
        val ids = ConversationContext.select(messages, openingCount = 4, maxRecent = 50).map { it.id }

        assertEquals((1L..4L) + (31L..80L), ids)
    }

    @Test
    fun `бюджет символов обрезает окно с хвоста истории`() {
        val messages = dialog(20, textLength = 100)
        val ids = ConversationContext.select(
            messages, openingCount = 2, maxRecent = 50, charBudget = 700
        ).map { it.id }

        // 2 реплики начала (200) + 5 последних (500) = 700.
        assertEquals(listOf(1L, 2L, 16L, 17L, 18L, 19L, 20L), ids)
    }

    @Test
    fun `последняя реплика попадает даже сверх бюджета`() {
        val messages = dialog(10, textLength = 10) +
            dialog(1, textLength = 5_000).map { it.copy(id = 11, timestampMillis = 11) }
        val ids = ConversationContext.select(
            messages, openingCount = 2, maxRecent = 50, charBudget = 1_000
        ).map { it.id }

        assertEquals(listOf(1L, 2L, 11L), ids)
    }

    @Test
    fun `порядок сообщений хронологический`() {
        val result = ConversationContext.select(dialog(120))
        assertTrue(result.zipWithNext().all { (a, b) -> a.timestampMillis < b.timestampMillis })
    }
}
