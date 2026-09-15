package com.example.englishvoicetutor.presentation.conversation

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.engine.LlmEngine
import com.example.englishvoicetutor.data.model.ModelInstaller
import com.example.englishvoicetutor.data.repository.ConversationRepository
import com.example.englishvoicetutor.domain.FeedbackParser
import com.example.englishvoicetutor.domain.model.MessageRole
import com.example.englishvoicetutor.domain.model.ReplyReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Сколько последних реплик учащегося разбираем: каждая — отдельный запрос к LLM. */
private const val MAX_REPLIES_TO_REVIEW = 20

/** Состояние экрана разбора завершённого диалога. */
sealed interface SessionReviewState {
    /** Идёт разбор: [done] из [total] реплик уже проверены. */
    data class Running(val done: Int, val total: Int) : SessionReviewState

    /** Разбор закончен. [reviews] — все проверенные реплики, ошибочные идут первыми. */
    data class Ready(val reviews: List<ReplyReview>) : SessionReviewState

    /** В диалоге нет реплик учащегося — разбирать нечего. */
    data object Empty : SessionReviewState

    data class Error(val message: String) : SessionReviewState
}

/**
 * Разбор всего диалога после его завершения: каждая реплика учащегося проверяется
 * тем же промптом [com.example.englishvoicetutor.domain.TutorPrompt.feedback],
 * что и кнопка «Проверить» под сообщением.
 *
 * Проверяем по одной реплике, а не весь диалог одним запросом: маленькая модель
 * на длинном тексте теряет формат и начинает «исправлять» правильные фразы.
 * Результат живёт только в этой вьюмодели — повторный вход на экран считает заново.
 */
@HiltViewModel
class SessionReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ConversationRepository,
    private val llmEngine: LlmEngine,
    private val modelInstaller: ModelInstaller,
) : ViewModel() {

    private val conversationId: Long = savedStateHandle.get<Long>("conversationId") ?: 0L

    private val _state = MutableStateFlow<SessionReviewState>(SessionReviewState.Running(0, 0))
    val state: StateFlow<SessionReviewState> = _state.asStateFlow()

    private val _scenario = MutableStateFlow<String?>(null)
    val scenario: StateFlow<String?> = _scenario.asStateFlow()

    init {
        analyse()
    }

    fun retry() = analyse()

    private fun analyse() {
        viewModelScope.launch {
            try {
                val meta = repository.getConversation(conversationId)
                    ?: throw IllegalStateException("Диалог не найден")
                _scenario.value = meta.scenario

                val replies = repository.getAllMessages(conversationId)
                    .filter { it.role == MessageRole.USER }
                    .takeLast(MAX_REPLIES_TO_REVIEW)
                if (replies.isEmpty()) {
                    _state.value = SessionReviewState.Empty
                    return@launch
                }

                _state.value = SessionReviewState.Running(0, replies.size)
                modelInstaller.ensureInitialized()

                val results = mutableListOf<ReplyReview>()
                replies.forEachIndexed { index, message ->
                    val parsed = FeedbackParser.parse(llmEngine.feedback(message.text, meta.cefrLevel))
                    results += ReplyReview(
                        original = message.text,
                        better = parsed.better,
                        note = parsed.note,
                        hasMistake = FeedbackParser.isMistake(message.text, parsed.better)
                    )
                    _state.value = SessionReviewState.Running(index + 1, replies.size)
                }

                // Ошибки — наверх: ради них экран и открывают.
                _state.value = SessionReviewState.Ready(
                    results.sortedByDescending { it.hasMistake }
                )
            } catch (e: Exception) {
                Log.e("VoiceTutor", "Session review failed", e)
                _state.value = SessionReviewState.Error(e.message ?: "Не удалось разобрать диалог")
            }
        }
    }
}
