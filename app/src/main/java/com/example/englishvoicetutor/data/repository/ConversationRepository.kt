package com.example.englishvoicetutor.data.repository

import com.example.englishvoicetutor.data.local.ConversationDao
import com.example.englishvoicetutor.data.local.ConversationEntity
import com.example.englishvoicetutor.data.local.MessageEntity
import com.example.englishvoicetutor.domain.model.CefrLevel
import com.example.englishvoicetutor.domain.model.Conversation
import com.example.englishvoicetutor.domain.model.Message
import com.example.englishvoicetutor.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Сколько последних сообщений отдаём в контекст LLM при продолжении диалога (см. п.4.1 плана). */
private const val RECENT_MESSAGES_FOR_CONTEXT = 20

/**
 * Сколько сообщений от начала диалога тянем в контекст всегда.
 *
 * Знакомство (имя, откуда, чем занимается) происходит в первых репликах, и когда
 * окно уезжает вперёд, модель теряет этот факт и здоровается заново. Дешевле
 * прибить начало к контексту, чем гонять суммаризацию на каждый ход.
 */
private const val OPENING_MESSAGES_FOR_CONTEXT = 4

@Singleton
class ConversationRepository @Inject constructor(
    private val dao: ConversationDao
) {

    fun observeConversations(): Flow<List<Conversation>> =
        dao.observeConversations().map { list -> list.map { it.toDomain() } }

    fun observeMessages(conversationId: Long): Flow<List<Message>> =
        dao.observeMessages(conversationId).map { list -> list.map { it.toDomain() } }

    suspend fun createConversation(
        scenario: String,
        level: CefrLevel,
        topicId: String? = null
    ): Conversation {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            title = scenario, // заменяется на сгенерированный LLM заголовок после первых реплик
            scenario = scenario,
            cefrLevel = level.name,
            summary = null,
            topicId = topicId,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        val id = dao.insertConversation(entity)
        return entity.copy(id = id).toDomain()
    }

    suspend fun appendMessage(conversationId: Long, role: MessageRole, text: String) {
        dao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = role.name,
                text = text,
                timestampMillis = System.currentTimeMillis()
            )
        )
        dao.getConversation(conversationId)?.let {
            dao.updateConversation(it.copy(updatedAtMillis = System.currentTimeMillis()))
        }
    }

    /** Все реплики диалога — нужны для разбора ошибок по завершении разговора. */
    suspend fun getAllMessages(conversationId: Long): List<Message> =
        dao.getMessages(conversationId).map { it.toDomain() }

    /**
     * Контекст для LLM: начало диалога + последние сообщения.
     *
     * Середина длинного разговора выпадает — это осознанный размен: маленькая модель
     * всё равно не удержит полную историю, а знакомство и свежие реплики важнее.
     */
    suspend fun getContextForResume(conversationId: Long): List<Message> {
        val recent = dao.getRecentMessages(conversationId, RECENT_MESSAGES_FOR_CONTEXT)
            .reversed()
            .map { it.toDomain() }
        val total = dao.countMessages(conversationId)
        if (total <= RECENT_MESSAGES_FOR_CONTEXT) return recent

        val opening = dao.getFirstMessages(conversationId, OPENING_MESSAGES_FOR_CONTEXT)
            .map { it.toDomain() }
        // Если окно уже дотянулось до начала, склейка только задвоила бы реплики.
        val recentIds = recent.map { it.id }.toSet()
        return opening.filterNot { it.id in recentIds } + recent
    }

    suspend fun saveSummary(conversationId: Long, summary: String) {
        dao.getConversation(conversationId)?.let {
            dao.updateConversation(it.copy(summary = summary))
        }
    }

    /**
     * Помечает диалог завершённым (учащийся попрощался) или снова открытым.
     * Отдельного «архива» нет: продолжение просто снимает отметку, история реплик
     * при этом сохраняется целиком, и разговор идёт дальше с тем же контекстом.
     */
    suspend fun setEnded(conversationId: Long, ended: Boolean): Conversation? {
        val current = dao.getConversation(conversationId) ?: return null
        val updated = current.copy(
            endedAtMillis = if (ended) System.currentTimeMillis() else null
        )
        dao.updateConversation(updated)
        return updated.toDomain()
    }

    suspend fun getConversation(conversationId: Long): Conversation? =
        dao.getConversation(conversationId)?.toDomain()

    suspend fun deleteConversation(conversationId: Long) =
        dao.deleteConversationById(conversationId)
}

private fun ConversationEntity.toDomain() = Conversation(
    id = id,
    title = title,
    scenario = scenario,
    cefrLevel = CefrLevel.valueOf(cefrLevel),
    summary = summary,
    topicId = topicId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    endedAtMillis = endedAtMillis
)

private fun MessageEntity.toDomain() = Message(
    id = id,
    conversationId = conversationId,
    role = MessageRole.valueOf(role),
    text = text,
    timestampMillis = timestampMillis
)
