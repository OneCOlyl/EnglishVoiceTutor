package com.example.englishvoicetutor.domain

/**
 * Разбиение учебного текста на абзацы для чтения с экрана.
 *
 * Объяснения правил в `assets/curriculum` написаны сплошным текстом, а сплошной
 * текст на телефоне читается плохо. Переносы в контенте (если они появятся)
 * имеют приоритет; иначе режем по предложениям и собираем по паре в абзац —
 * это даёт ритм страницы, не требуя переписывать все объяснения.
 */
object TextLayout {

    /** Сколько предложений склеиваем в один абзац, когда переносов в тексте нет. */
    private const val SENTENCES_PER_PARAGRAPH = 2

    /** Граница предложения: точка/!/? и пробел перед началом следующей фразы. */
    private val SENTENCE_BREAK = Regex("(?<=[.!?…])\\s+(?=[«\"(\\p{Lu}])")

    fun paragraphs(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val explicit = trimmed.split(Regex("\\n\\s*\\n|\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (explicit.size > 1) return explicit

        return trimmed.split(SENTENCE_BREAK)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .chunked(SENTENCES_PER_PARAGRAPH)
            .map { it.joinToString(" ") }
    }
}
