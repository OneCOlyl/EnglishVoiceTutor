package com.example.englishvoicetutor.domain

/**
 * Разбор ответа модели на промпт [TutorPrompt.feedback] формата `Better: …` / `Note: …`.
 *
 * Модель маленькая и иногда игнорирует формат — тогда весь текст показываем
 * как пояснение, а исправленный вариант оставляем пустым: лучше показать
 * сырое пояснение, чем выдать выдуманное «как лучше».
 */
object FeedbackParser {

    data class Result(val better: String, val note: String)

    private val BETTER = Regex("(?im)^\\s*Better:\\s*(.+)$")
    private val NOTE = Regex("(?im)^\\s*Note:\\s*(.+)$")

    fun parse(raw: String): Result {
        val better = BETTER.find(raw)?.groupValues?.get(1)?.trim()
        val note = NOTE.find(raw)?.groupValues?.get(1)?.trim()
        return if (better != null || note != null) {
            Result(better.orEmpty().trim('"'), note.orEmpty())
        } else {
            Result("", raw.trim())
        }
    }

    /**
     * Считать ли реплику ошибочной. Сравниваем без учёта регистра и пунктуации:
     * модель часто возвращает ту же фразу с точкой в конце или с заглавной буквы —
     * это не ошибка учащегося, и в отчёте такие реплики показывать не нужно.
     */
    fun isMistake(original: String, better: String): Boolean {
        if (better.isBlank()) return false
        return normalize(original) != normalize(better)
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
