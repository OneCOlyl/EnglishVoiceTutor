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

@Singleton
class ConversationRepository @Inject constructor(
    private val dao: ConversationDao
) {

    fun observeConversations(): Flow<List<Conversation>> =
        dao.observeConversations().map { list -> list.map { it.toDomain() } }

    fun observeMessages(conversationId: Long): Flow<List<Message>> =
        dao.observeMessages(conversationId).map { list -> list.map { it.toDomain() } }

    suspend fun createConversation(scenario: String, level: CefrLevel): Conversation {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            title = scenario, // заменяется на сгенерированный LLM заголовок после первых реплик
            scenario = scenario,
            cefrLevel = level.name,
            summary = null,
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

    /** Контекст для LLM при продолжении старого диалога: последние сообщения + summary остального. */
    suspend fun getContextForResume(conversationId: Long): List<Message> =
        dao.getRecentMessages(conversationId, RECENT_MESSAGES_FOR_CONTEXT)
            .reversed()
            .map { it.toDomain() }

    suspend fun saveSummary(conversationId: Long, summary: String) {
        dao.getConversation(conversationId)?.let {
            dao.updateConversation(it.copy(summary = summary))
        }
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
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

private fun MessageEntity.toDomain() = Message(
    id = id,
    conversationId = conversationId,
    role = MessageRole.valueOf(role),
    text = text,
    timestampMillis = timestampMillis
)
