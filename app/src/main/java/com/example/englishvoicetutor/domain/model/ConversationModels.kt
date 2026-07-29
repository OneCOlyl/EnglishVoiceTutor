package com.example.englishvoicetutor.domain.model

/** Используется в навигации как ID маршрута "создать новый диалог" (вместо открытия существующего). */
const val NEW_CONVERSATION_ID = -1L

/** Уровень владения языком по шкале CEFR — влияет на системный промпт репетитора. */
enum class CefrLevel(val label: String) {
    A1("Начинающий (A1)"),
    A2("Элементарный (A2)"),
    B1("Средний (B1)"),
    B2("Выше среднего (B2)"),
    C1("Продвинутый (C1)")
}

/** Роль автора реплики в диалоге. */
enum class MessageRole { USER, TUTOR }

/** Одна реплика диалога — то, что хранится и показывается в истории. */
data class Message(
    val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val text: String,
    val timestampMillis: Long
)

/** Диалог (сессия общения с репетитором), к которому можно вернуться позже. */
data class Conversation(
    val id: Long = 0,
    val title: String,
    val scenario: String,
    val cefrLevel: CefrLevel,
    val summary: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

/**
 * Дополнительный разбор одной реплики, который подгружается по запросу пользователя
 * (перевод на русский и/или проверка ошибок с подсказкой «как лучше сказать»).
 * Хранится в UI-слое по id сообщения, в БД не пишется — это вспомогательная подсказка.
 */
data class MessageInsight(
    val translation: String? = null,
    val translationLoading: Boolean = false,
    /** Исправленный/более естественный вариант фразы (из feedback). */
    val better: String? = null,
    /** Краткое пояснение по-русски, что было не так. */
    val note: String? = null,
    val feedbackLoading: Boolean = false,
    val error: String? = null
)

/**
 * Состояние голосового пайплайна, которое отображает UI.
 * Idle -> Listening -> Transcribing -> Thinking -> Speaking -> Idle (цикл).
 */
sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data object Listening : VoiceUiState
    data object Transcribing : VoiceUiState
    data object Thinking : VoiceUiState
    data object Recording : VoiceUiState
    data class Speaking(val partialText: String) : VoiceUiState
    data class Error(val message: String) : VoiceUiState
}
