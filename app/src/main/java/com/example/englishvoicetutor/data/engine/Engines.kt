package com.example.englishvoicetutor.data.engine

import com.example.englishvoicetutor.domain.model.CefrLevel
import com.example.englishvoicetutor.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Распознавание речи. Реализация по умолчанию — Android SpeechRecognizer (см. AndroidSttEngine).
 * Под замену на whisper.cpp для более устойчивого распознавания акцентов — см. README, п. "Дальше".
 */
interface SttEngine {
    suspend fun startRecording()
    fun stopRecording(): String
}

/**
 * Состояние движка синтеза речи.
 * Нужно наружу, потому что системный TTS может отсутствовать на устройстве
 * целиком (на части прошивок Xiaomi/HarmonyOS нет ни одного движка) — молча
 * не озвучивать в таком случае нельзя, пользователь должен понимать, почему тихо.
 */
sealed interface TtsStatus {
    data object Initializing : TtsStatus

    /** Идёт разовая загрузка модели голоса; [percent] 0..100. */
    data class Downloading(val percent: Int, val message: String) : TtsStatus

    data object Ready : TtsStatus

    /** [reason] — готовый текст для UI, по-русски. */
    data class Unavailable(val reason: String) : TtsStatus
}

/**
 * Синтез речи. Реализация по умолчанию — Android TextToSpeech (см. AndroidTtsEngine).
 */
interface TtsEngine {
    /** Текущее состояние движка — UI показывает предупреждение, если озвучка недоступна. */
    val status: StateFlow<TtsStatus>

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

    /** Перевод произвольной реплики на русский — для кнопки «Перевод» под сообщением. */
    suspend fun translateToRussian(text: String): String

    /**
     * Разбор реплики учащегося: исправленный вариант + краткое пояснение.
     * Возвращает сырой текст ответа модели в формате `Better: …` / `Note: …`;
     * парсинг для UI — на стороне вьюмодели.
     */
    suspend fun feedback(text: String, level: CefrLevel): String

    /**
     * Разовый запрос к модели без истории диалога — для учебных экранов
     * (ещё один пример к правилу, проверка употребления слова).
     * Промпт целиком собирает вызывающая сторона через `TutorPrompt`.
     */
    suspend fun ask(prompt: String): String
}
