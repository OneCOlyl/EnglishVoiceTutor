package com.example.englishvoicetutor.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Разбиение объяснений правил на абзацы — от него зависит читаемость экрана правила. */
class TextLayoutTest {

    @Test
    fun `переносы в контенте имеют приоритет над автоматическим разбиением`() {
        val result = TextLayout.paragraphs("Первый абзац.\n\nВторой абзац. И ещё фраза.")

        assertEquals(listOf("Первый абзац.", "Второй абзац. И ещё фраза."), result)
    }

    @Test
    fun `сплошной текст режется по предложениям парами`() {
        val result = TextLayout.paragraphs("Раз. Два. Три. Четыре. Пять.")

        assertEquals(listOf("Раз. Два.", "Три. Четыре.", "Пять."), result)
    }

    @Test
    fun `пустой текст даёт пустой список`() {
        assertEquals(emptyList<String>(), TextLayout.paragraphs("   "))
    }
}
