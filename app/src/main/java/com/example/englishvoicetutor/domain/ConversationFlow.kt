package com.example.englishvoicetutor.domain

/**
 * Правила течения разговора, не зависящие от UI и движков: чем заканчивается диалог
 * и как понять, что учащийся попрощался.
 *
 * Держим это чистой функцией, чтобы поведение было предсказуемым и тестируемым:
 * ошибочное срабатывание стоит дорого — диалог закроется посреди фразы.
 */
object ConversationFlow {

    /**
     * Прощальные формулы. Ищем только те, что реально завершают разговор;
     * «bye» как часть другого слова (`maybe`, `byte`) отсекаем границами слова.
     */
    private val FAREWELL = Regex(
        """(^|\W)(good\s*bye|bye\s*bye|bye|farewell|see\s+(you|ya)( (later|soon|tomorrow|around))?|take\s+care|have\s+a\s+(good|nice|great)\s+(day|evening|night|one))(\W|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * true, если реплика учащегося — прощание.
     *
     * Требуем, чтобы прощание было короткой репликой (или её концовкой): в длинной
     * фразе вроде «I said goodbye to my friend yesterday» это рассказ, а не выход
     * из разговора. Порог в словах подобран так, чтобы «Ok, thank you, goodbye!»
     * ещё считалось прощанием.
     */
    fun isFarewell(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val words = trimmed.split(Regex("\\s+"))
        if (words.size > 8) return false
        // Смотрим на хвост реплики: прощание почти всегда стоит в конце.
        val tail = words.takeLast(5).joinToString(" ")
        return FAREWELL.containsMatchIn(tail)
    }
}
