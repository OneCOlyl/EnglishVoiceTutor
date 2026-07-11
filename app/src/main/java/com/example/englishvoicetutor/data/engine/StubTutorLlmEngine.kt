package com.example.englishvoicetutor.data.engine

import com.example.englishvoicetutor.domain.model.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Заглушка вместо настоящей LLM — позволяет собрать и проверить весь пайплайн
 * (микрофон → STT → "LLM" → TTS → сохранение в Room) ещё до интеграции LiteRT-LM
 * и скачивания модели весом в гигабайты. Замените биндинг в di/AppModule.kt на
 * LiteRtLlmEngine, когда модель будет готова — остальной код менять не придётся,
 * это и есть смысл интерфейса LlmEngine.
 */
@Singleton
class StubTutorLlmEngine @Inject constructor() : LlmEngine {

    override fun generateReply(
        systemPrompt: String,
        history: List<Message>,
        userMessage: String
    ) = flow {
        // Имитация задержки и потоковой генерации, чтобы UI-состояния (Thinking/Speaking)
        // можно было проверить ещё на этапе, когда настоящей модели в проекте нет.
        delay(400)
        val reply = "That's interesting! Can you tell me more about \"${userMessage.take(40)}\"?"
        for (word in reply.split(" ")) {
            emit("$word ")
            delay(60)
        }
    }

    override suspend fun summarize(history: List<Message>): String =
        "Stub summary of ${history.size} messages."
}
