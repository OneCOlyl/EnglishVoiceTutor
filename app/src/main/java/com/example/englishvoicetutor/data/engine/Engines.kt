package com.example.englishvoicetutor.data.engine

import com.example.englishvoicetutor.domain.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * Распознавание речи. Реализация по умолчанию — Android SpeechRecognizer (см. AndroidSttEngine).
 * Под замену на whisper.cpp для более устойчивого распознавания акцентов — см. README, п. "Дальше".
 */
interface SttEngine {
    suspend fun startRecording()
    fun stopRecording(): String
}

/**
 * Синтез речи. Реализация по умолчанию — Android TextToSpeech (см. AndroidTtsEngine).
 */
interface TtsEngine {
    suspend fun speak(text: String)
    fun stop()
}

/**
 * LLM-репетитор. Реализация по умолчанию — StubTutorLlmEngine (заглушка для разработки UI
 * без скачивания модели). Продакшен-реализация — LiteRtLlmEngine (Gemma 4 через LiteRT-LM).
 */
interface LlmEngine {
    /** Потоковая генерация ответа — токены/фразы приходят по мере готовности (для ранней озвучки). */
    fun generateReply(
        systemPrompt: String,
        history: List<Message>,
        userMessage: String
    ): Flow<String>

    /** Короткая суммаризация для схлопывания старой части длинного диалога (см. п.4.1 плана). */
    suspend fun summarize(history: List<Message>): String
}
