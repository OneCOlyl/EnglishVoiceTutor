package com.example.englishvoicetutor.data.engine

import android.content.Context
import android.util.Log
import com.example.englishvoicetutor.domain.model.CefrLevel
import com.example.englishvoicetutor.domain.model.Message
import com.example.englishvoicetutor.domain.model.MessageRole
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.google.ai.edge.litertlm.Message as LlmMessage

@Singleton
class LiteRtLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmEngine {

    @Volatile private var engine: Engine? = null

    /** Вызвать один раз после того как пользователь выбрал файл модели. */
    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        engine?.close()
        Log.d("LiteRtLlm", "Initializing engine with model: $modelPath")
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.GPU(),
            cacheDir = context.cacheDir.path
        )
        engine = Engine(config).also { it.initialize() }
        Log.d("LiteRtLlm", "Engine ready")
    }

    val isReady: Boolean get() = engine != null

    override fun generateReply(
        systemPrompt: String,
        history: List<Message>,
        userMessage: String
    ): Flow<String> = flow {
        val eng = engine ?: throw IllegalStateException("Модель не загружена")

        // Конвертируем историю в формат LiteRT-LM
        val initialMessages = history.map { msg ->
            when (msg.role) {
                MessageRole.USER -> LlmMessage.user(msg.text)
                MessageRole.TUTOR -> LlmMessage.model(msg.text)
            }
        }

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            initialMessages = initialMessages
        )

        eng.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessageAsync(userMessage).collect { message ->
                val text = message.toString()
                if (text.isNotEmpty()) emit(text)
            }
        }
    }

    override suspend fun summarize(history: List<Message>): String {
        val text = history.joinToString("\n") {
            "${if (it.role == MessageRole.USER) "User" else "Tutor"}: ${it.text}"
        }
        return oneShot("Summarize this conversation in 2-3 sentences:\n$text")
    }

    override suspend fun translateToRussian(text: String): String =
        oneShot(com.example.englishvoicetutor.domain.TutorPrompt.translateToRussian(text)).trim()

    override suspend fun feedback(text: String, level: CefrLevel): String =
        oneShot(com.example.englishvoicetutor.domain.TutorPrompt.feedback(text, level)).trim()

    /**
     * Разовый запрос к модели без истории диалога — для перевода, разбора ошибок
     * и суммаризации. Собирает потоковый ответ в одну строку.
     */
    private suspend fun oneShot(prompt: String): String {
        val eng = engine ?: return ""
        return withContext(Dispatchers.IO) {
            var result = ""
            eng.createConversation().use { conversation ->
                conversation.sendMessageAsync(prompt).collect { result += it.toString() }
            }
            result
        }
    }

    fun close() {
        engine?.close()
        engine = null
    }
}