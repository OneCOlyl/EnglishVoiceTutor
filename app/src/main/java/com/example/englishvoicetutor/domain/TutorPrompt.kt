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
     * Учебный фокус диалога, когда он запущен из темы курса.
     *
     * Правила намеренно передаём не текстом объяснения (оно на русском и только
     * запутает маленькую модель), а готовыми английскими примерами-образцами:
     * модель гораздо надёжнее копирует структуру, чем следует метаописанию.
     */
    data class TopicFocus(
        val topicTitle: String,
        val targetWords: List<String>,
        val targetStructures: List<String>
    )

    /**
     * Системный промпт для основного диалога.
     * Держим его коротким и предельно конкретным: маленькая модель (Gemma 4 E2B)
     * плохо держит длинные и абстрактные инструкции, поэтому правила даём
     * императивно, с примерами формата, и явно запрещаем то, что ломает
     * голосовой формат (списки, разметку, эмодзи, метакомментарии).
     */
    fun system(
        level: CefrLevel,
        scenario: String,
        focus: TopicFocus? = null,
        resumed: Boolean = false
    ): String = buildString {
        appendLine("You are Alex, a warm and encouraging English conversation partner.")
        appendLine("You are having a SPOKEN role-play conversation with a learner.")
        appendLine("Scenario: $scenario.")
        appendLine("Learner's CEFR level: $level. Match your English to this level: ${levelGuidance(level)}")
        appendLine()
        appendLine("How to reply:")
        appendLine("- Reply ONLY in English. This is a spoken conversation, so speak naturally")
        appendLine("  and say as much as the moment needs — do not force yourself to be short.")
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

        if (resumed) {
            // В контекст уходят только последние сообщения, поэтому начало разговора
            // модель не видит и на длинной истории норовит представиться заново.
            appendLine()
            appendLine("You are in the MIDDLE of this conversation:")
            appendLine("- You have already met and introduced yourselves.")
            appendLine("- Never greet the learner again, never say your name again,")
            appendLine("  and never ask for their name or where they are from a second time.")
            appendLine("- Continue from the last thing they said. Ask about something new.")
        }

        if (focus != null) {
            appendLine()
            appendLine("This is a lesson on: ${focus.topicTitle}.")
            if (focus.targetWords.isNotEmpty()) {
                appendLine(
                    "Weave these words into your replies naturally, a few per turn: " +
                        focus.targetWords.joinToString(", ") + "."
                )
                appendLine("Ask questions that make the learner use these words too.")
            }
            if (focus.targetStructures.isNotEmpty()) {
                appendLine("Model these sentence patterns in your own speech:")
                focus.targetStructures.forEach { appendLine("  $it") }
                appendLine(
                    "If the learner breaks one of these patterns, recast it correctly " +
                        "inside your reply — still without explaining grammar."
                )
            }
        }
    }

    /**
     * Реплика-«затравка» для первого хода репетитора в уроке.
     *
     * Идёт в модель как ход пользователя, но в БД не пишется: это служебная
     * инструкция, а не слова учащегося. Скобки и слово «instruction» помогают
     * маленькой модели не принять текст за реплику собеседника и не ответить на него.
     */
    fun openingKick(): String =
        "(Instruction, not a line of dialogue: the learner has just joined and is waiting. " +
            "Greet them in character, set the scene in one or two sentences, " +
            "and ask your first simple question.)"

    /**
     * Затравка для прощания: учащийся сказал «goodbye», разговор пора закрыть.
     * Отдельный ход нужен, чтобы модель не задавала очередной вопрос
     * и не тянула диалог дальше.
     */
    fun farewellKick(userText: String): String =
        "The learner said: \"$userText\". They are ending the conversation. " +
            "Say a short, warm goodbye in character. Do NOT ask any question."

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

    /**
     * Ещё один пример к правилу («не понял — покажи иначе»).
     * Образцы даём самими предложениями из статического контента: так модель
     * копирует нужную конструкцию, а не изобретает своё понимание правила.
     */
    fun extraExample(patterns: List<String>, level: CefrLevel): String = buildString {
        appendLine("Here are example sentences that all follow the same English grammar pattern:")
        patterns.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Write ONE new sentence that follows exactly the same pattern.")
        appendLine("Use simple everyday vocabulary suitable for a CEFR $level learner.")
        appendLine("Reply in EXACTLY this format, nothing else:")
        appendLine("En: <the new English sentence>")
        appendLine("Ru: <its Russian translation>")
    }

    /**
     * Проверка того, как учащийся употребил новое слово в своей фразе
     * (кнопка «сказать предложение» в карточке слова).
     */
    fun wordUsage(word: String, sentence: String, level: CefrLevel): String = buildString {
        appendLine("A CEFR $level learner is practising the English word \"$word\".")
        appendLine("They produced this sentence: \"$sentence\"")
        appendLine()
        appendLine("Decide whether the sentence uses \"$word\" correctly and sounds natural.")
        appendLine("Reply in EXACTLY this format, nothing else:")
        appendLine("Verdict: ok | wrong")
        appendLine("Better: <the most natural version of their sentence>")
        appendLine("Note: <one short comment IN RUSSIAN; if everything is fine, praise briefly>")
    }

    /** Короткий промпт для фоновой суммаризации старой части длинного диалога (см. п.4.1). */
    fun summarization(): String =
        "Summarize the conversation so far in 2-3 short sentences, keeping names, topic, " +
            "and any important facts about the learner. Be concise."
}
