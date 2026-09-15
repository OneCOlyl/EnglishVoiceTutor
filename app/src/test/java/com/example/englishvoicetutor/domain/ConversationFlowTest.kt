package com.example.englishvoicetutor.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Детектор прощания закрывает диалог, поэтому ложное срабатывание стоит дорого:
 * разговор оборвётся посреди фразы. Фиксируем обе границы поведения.
 */
class ConversationFlowTest {

    @Test
    fun `короткое прощание завершает диалог`() {
        assertTrue(ConversationFlow.isFarewell("Goodbye!"))
        assertTrue(ConversationFlow.isFarewell("ok, thank you, bye"))
        assertTrue(ConversationFlow.isFarewell("See you later"))
        assertTrue(ConversationFlow.isFarewell("Have a nice day"))
    }

    @Test
    fun `рассказ о прощании диалог не закрывает`() {
        assertFalse(
            ConversationFlow.isFarewell(
                "Yesterday I said goodbye to my friend at the station and went home"
            )
        )
    }

    @Test
    fun `похожие слова не считаются прощанием`() {
        assertFalse(ConversationFlow.isFarewell("Maybe I will buy a byte of memory"))
        assertFalse(ConversationFlow.isFarewell(""))
    }
}
