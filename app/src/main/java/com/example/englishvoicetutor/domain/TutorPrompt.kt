package com.example.englishvoicetutor.domain

import com.example.englishvoicetutor.domain.model.CefrLevel

/**
 * Собирает системные промпты для LLM-репетитора.
 * Вынесено в отдельный класс, чтобы промпты можно было итерировать и A/B-тестировать
 * отдельно от остального кода — это то, что реально определяет качество "обучения"
 * на маленьких моделях, см. п.6 архитектурного плана.
 */
object TutorPrompt {

    /**
     * Системный промпт для основного диалога.
     * Держим его коротким и предельно конкретным: маленькая модель (Gemma 4 E2B)
     * плохо держит длинные и абстрактные инструкции, поэтому правила даём
     * императивно, с примерами формата, и явно запрещаем то, что ломает
     * голосовой формат (списки, разметку, эмодзи, метакомментарии).
     */
    fun system(level: CefrLevel, scenario: String): String = buildString {
        appendLine("You are Alex, a warm and encouraging English conversation partner.")
        appendLine("You are having a SPOKEN role-play conversation with a learner.")
        appendLine("Scenario: $scenario.")
        appendLine("Learner's CEFR level: $level. Match your English to this level: ${levelGuidance(level)}")
        appendLine()
        appendLine("How to reply:")
        appendLine("- Reply ONLY in English, 1-2 short sentences. This is speech, not writing.")
        appendLine("- Stay fully in character and in the scenario. Never mention that you are an AI or a model.")
        appendLine("- End almost every reply with ONE simple follow-up question to keep the learner talking.")
        appendLine("- If the learner makes a clear mistake, model the correct form naturally in your")
        appendLine("  own reply (recast), then continue. Do NOT stop to explain grammar.")
        appendLine("  Example — learner: \"I go yesterday to shop.\" you: \"Oh, you went to the shop yesterday? What did you buy?\"")
        appendLine("- If the learner is silent, confused, or off-topic, gently steer back with a question.")
        appendLine()
        appendLine("Never do this:")
        appendLine("- No lists, no markdown, no emoji, no stage directions, no translations.")
        appendLine("- Do not write the learner's lines for them or continue past your own turn.")
    }

    /** Подсказка по сложности языка под уровень — подставляется в системный промпт. */
    private fun levelGuidance(level: CefrLevel): String = when (level) {
        CefrLevel.A1 -> "use very basic words and the present tense; speak slowly and simply."
        CefrLevel.A2 -> "use common everyday words and simple sentences."
        CefrLevel.B1 -> "use everyday vocabulary and a natural but clear style."
        CefrLevel.B2 -> "speak naturally with a good range of vocabulary."
        CefrLevel.C1 -> "speak fully naturally, with idioms and nuance where it fits."
    }

    /**
     * Промпт для перевода реплики на русский (см. фичу «перевод текста»).
     * Требуем только сам перевод, без пояснений и кавычек — результат идёт прямо в UI.
     */
    fun translateToRussian(text: String): String = buildString {
        appendLine("Translate the following English text into natural, fluent Russian.")
        appendLine("Output ONLY the Russian translation — no quotes, no notes, no original text.")
        appendLine()
        appendLine("Text: $text")
    }

    /**
     * Промпт для разбора реплики учащегося: находит ошибки и предлагает,
     * как сказать лучше (см. фичу «показ ошибок»).
     * Формат ответа фиксируем строками с метками, чтобы UI мог показать
     * исправленный вариант и пояснение раздельно.
     */
    fun feedback(text: String, level: CefrLevel): String = buildString {
        appendLine("You are an English teacher. A learner (CEFR level $level) said the sentence below.")
        appendLine("Check it for grammar, word choice, and naturalness.")
        appendLine()
        appendLine("Reply in EXACTLY this format, nothing else:")
        appendLine("Better: <the most natural corrected English version of the sentence>")
        appendLine("Note: <one short explanation IN RUSSIAN of what was wrong; if nothing was wrong, write \"Ошибок нет, звучит естественно.\">")
        appendLine()
        appendLine("Sentence: $text")
    }

    /** Короткий промпт для фоновой суммаризации старой части длинного диалога (см. п.4.1). */
    fun summarization(): String =
        "Summarize the conversation so far in 2-3 short sentences, keeping names, topic, " +
            "and any important facts about the learner. Be concise."
}
