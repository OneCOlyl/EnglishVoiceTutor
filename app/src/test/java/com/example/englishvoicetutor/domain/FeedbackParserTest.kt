package com.example.englishvoicetutor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор ответа модели: от него зависит и подсказка под сообщением, и итоговый
 * отчёт по диалогу. Модель маленькая и формат иногда нарушает — это часть контракта.
 */
class FeedbackParserTest {

    @Test
    fun `ответ в ожидаемом формате разбирается на части`() {
        val result = FeedbackParser.parse("Better: I went to the shop.\nNote: нужен past simple.")

        assertEquals("I went to the shop.", result.better)
        assertEquals("нужен past simple.", result.note)
    }

    @Test
    fun `ответ без меток целиком уходит в пояснение`() {
        val result = FeedbackParser.parse("Всё хорошо, звучит естественно.")

        assertEquals("", result.better)
        assertEquals("Всё хорошо, звучит естественно.", result.note)
    }

    @Test
    fun `отличия только в пунктуации и регистре ошибкой не считаются`() {
        assertFalse(FeedbackParser.isMistake("i like coffee", "I like coffee."))
        assertFalse(FeedbackParser.isMistake("I like coffee", ""))
        assertTrue(FeedbackParser.isMistake("I go yesterday", "I went yesterday."))
    }
}
