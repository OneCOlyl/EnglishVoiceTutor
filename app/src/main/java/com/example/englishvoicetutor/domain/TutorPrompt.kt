package com.example.englishvoicetutor.domain

import com.example.englishvoicetutor.domain.model.CefrLevel

/**
 * Собирает системный промпт для LLM-репетитора.
 * Вынесено в отдельный класс, чтобы промпт можно было итерировать и A/B-тестировать
 * отдельно от остального кода — это то, что реально определяет качество "обучения"
 * на маленьких моделях, см. п.6 архитектурного плана.
 */
object TutorPrompt {

    fun system(level: CefrLevel, scenario: String): String = buildString {
        appendLine("You are a friendly, patient English conversation tutor.")
        appendLine("The learner's current level is roughly $level (${level.label}).")
        appendLine("Scenario for this conversation: $scenario.")
        appendLine()
        appendLine("Rules:")
        appendLine("- Keep replies short (1-3 sentences) — this is a spoken conversation, not an essay.")
        appendLine("- Stay in the scenario and speak naturally, like a real conversation partner.")
        appendLine("- If the learner makes a grammar or word-choice mistake, briefly and gently")
        appendLine("  restate the correct form inside your reply, then continue the conversation.")
        appendLine("  Do not lecture or break the flow with long explanations.")
        appendLine("- Adjust vocabulary and sentence complexity to the learner's level.")
        appendLine("- Ask a follow-up question often, to keep the learner talking.")
        appendLine("- Reply only in English.")
    }

    /** Короткий промпт для фоновой суммаризации старой части длинного диалога (см. п.4.1). */
    fun summarization(): String =
        "Summarize the conversation so far in 2-3 short sentences, keeping names, topic, " +
            "and any important facts about the learner. Be concise."
}
