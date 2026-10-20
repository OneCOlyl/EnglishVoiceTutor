package com.example.englishvoicetutor.domain

import com.example.englishvoicetutor.domain.model.Message

/**
 * Какие реплики диалога отдать в контекст LLM на очередном ходе.
 *
 * Чистая функция, чтобы размер контекста был предсказуем и покрыт тестом: перебор
 * упирается в `maxNumTokens` движка (4096 на всё — системный промпт, историю и ответ),
 * а недобор заставляет репетитора забывать сказанное (EVT-110).
 */
object ConversationContext {

    /**
     * Сколько последних сообщений берём максимум.
     *
     * Замер на устройстве (EVT-501): время до первого токена почти не растёт с длиной
     * истории, поэтому окно шире прежних 20 сообщений почти ничего не стоит по скорости.
     */
    const val MAX_RECENT_MESSAGES = 50

    /**
     * Сколько сообщений от начала диалога тянем в контекст всегда.
     *
     * Знакомство (имя, откуда, чем занимается) происходит в первых репликах, и когда
     * окно уезжает вперёд, модель теряет этот факт и здоровается заново.
     */
    const val OPENING_MESSAGES = 4

    /**
     * Бюджет истории в символах. Английский текст — около 4 символов на токен, так что
     * это ≈ 2500 токенов: остаток из 4096 уходит на системный промпт (≈ 350–600),
     * текущую реплику и ответ репетитора.
     */
    const val CHAR_BUDGET = 10_000

    /**
     * [messages] — весь диалог в хронологическом порядке. Возвращает начало диалога
     * и как можно больше последних реплик в пределах [maxRecent] и [charBudget].
     *
     * Середина длинного разговора выпадает — это осознанный размен: знакомство и
     * свежие реплики важнее. Самая последняя реплика попадает всегда, даже если
     * одна превышает бюджет: без неё модель потеряет нить текущего хода.
     */
    fun select(
        messages: List<Message>,
        openingCount: Int = OPENING_MESSAGES,
        maxRecent: Int = MAX_RECENT_MESSAGES,
        charBudget: Int = CHAR_BUDGET
    ): List<Message> {
        if (messages.size <= maxRecent && messages.sumOf { it.text.length } <= charBudget) {
            return messages
        }

        val opening = messages.take(openingCount)
        var used = opening.sumOf { it.text.length }
        val recent = ArrayDeque<Message>()
        for (i in messages.indices.reversed()) {
            // Окно дотянулось до начала — дальше пошли бы реплики, уже взятые в opening.
            if (i < opening.size || recent.size >= maxRecent) break
            val msg = messages[i]
            if (recent.isNotEmpty() && used + msg.text.length > charBudget) break
            recent.addFirst(msg)
            used += msg.text.length
        }
        return opening + recent
    }
}
