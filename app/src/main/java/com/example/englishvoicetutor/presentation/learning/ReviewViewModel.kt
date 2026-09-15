package com.example.englishvoicetutor.presentation.learning

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.engine.LlmEngine
import com.example.englishvoicetutor.data.engine.SttEngine
import com.example.englishvoicetutor.data.engine.TtsEngine
import com.example.englishvoicetutor.data.engine.TtsStatus
import com.example.englishvoicetutor.data.model.ModelInstaller
import com.example.englishvoicetutor.data.repository.VocabularyRepository
import com.example.englishvoicetutor.domain.TutorPrompt
import com.example.englishvoicetutor.domain.model.CefrLevel
import com.example.englishvoicetutor.domain.model.VocabularyCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Состояние голосовой проверки внутри карточки повторения. */
sealed interface VoiceCheckState {
    data object Idle : VoiceCheckState
    data object Recording : VoiceCheckState
    data object Checking : VoiceCheckState
    data class Result(
        val heard: String,
        val ok: Boolean,
        val better: String,
        val note: String
    ) : VoiceCheckState
    data class Error(val message: String) : VoiceCheckState
}

data class ReviewUiState(
    val loading: Boolean = true,
    val cards: List<VocabularyCard> = emptyList(),
    val index: Int = 0,
    /** Показан ли перевод: сначала пользователь вспоминает сам, потом проверяет себя. */
    val revealed: Boolean = false,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val finished: Boolean = false,
    val voice: VoiceCheckState = VoiceCheckState.Idle
) {
    val current: VocabularyCard? get() = cards.getOrNull(index)
}

/**
 * Сессия интервального повторения.
 *
 * Карточка двухступенчатая: сначала слово, потом (по кнопке) перевод и пример.
 * Дополнительно можно проговорить своё предложение с этим словом — оно уходит
 * в STT и затем в LLM на проверку. Оценка «помню / не помню» ставится
 * пользователем и двигает слово по коробкам Лейтнера.
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val sttEngine: SttEngine,
    private val ttsEngine: TtsEngine,
    private val llmEngine: LlmEngine,
    private val modelInstaller: ModelInstaller
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /** Доступна ли озвучка — если нет, экран показывает причину вместо молчания. */
    val ttsStatus: StateFlow<TtsStatus> = ttsEngine.status

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val cards = vocabularyRepository.buildSession()
            _state.value = ReviewUiState(
                loading = false,
                cards = cards,
                finished = cards.isEmpty()
            )
        }
    }

    fun reveal() {
        _state.update { it.copy(revealed = true) }
    }

    /** Ответ пользователя: двигаем слово по SRS и переходим к следующей карточке. */
    fun answer(correct: Boolean) {
        val card = _state.value.current ?: return
        viewModelScope.launch {
            vocabularyRepository.recordAnswer(card, correct)
            _state.update { current ->
                val nextIndex = current.index + 1
                current.copy(
                    index = nextIndex,
                    revealed = false,
                    voice = VoiceCheckState.Idle,
                    correctCount = current.correctCount + if (correct) 1 else 0,
                    wrongCount = current.wrongCount + if (correct) 0 else 1,
                    finished = nextIndex >= current.cards.size
                )
            }
        }
    }

    fun speakCurrent() {
        val card = _state.value.current ?: return
        viewModelScope.launch { ttsEngine.speak(card.item.word) }
    }

    fun speak(text: String) {
        viewModelScope.launch { ttsEngine.speak(text) }
    }

    /**
     * Голосовая проверка: тап — начать запись, второй тап — остановить и проверить.
     * Push-to-talk такой же, как в основном диалоге, — отдельного VAD здесь тоже нет.
     */
    fun onVoiceTapped() {
        val card = _state.value.current ?: return
        when (_state.value.voice) {
            is VoiceCheckState.Recording -> {
                _state.update { it.copy(voice = VoiceCheckState.Checking) }
                viewModelScope.launch { checkSpokenSentence(card) }
            }
            is VoiceCheckState.Checking -> Unit // идёт проверка, игнорируем
            else -> viewModelScope.launch {
                try {
                    sttEngine.startRecording()
                    _state.update { it.copy(voice = VoiceCheckState.Recording) }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(voice = VoiceCheckState.Error(e.message ?: "Ошибка микрофона"))
                    }
                }
            }
        }
    }

    private suspend fun checkSpokenSentence(card: VocabularyCard) {
        try {
            // Распознавание тяжёлое — уводим с главного потока, как в основном диалоге.
            val heard = withContext(Dispatchers.Default) { sttEngine.stopRecording() }
            if (heard.isBlank()) {
                _state.update {
                    it.copy(voice = VoiceCheckState.Error("Не удалось расслышать фразу"))
                }
                return
            }
            modelInstaller.ensureInitialized()
            val level = levelOf(card)
            val raw = llmEngine.ask(TutorPrompt.wordUsage(card.item.word, heard, level))
            _state.update { it.copy(voice = parseUsage(heard, raw)) }
        } catch (e: Exception) {
            Log.e("VoiceTutor", "Word usage check failed", e)
            _state.update {
                it.copy(voice = VoiceCheckState.Error(e.message ?: "Ошибка проверки"))
            }
        }
    }

    /** Уровень слова достаём из id вида `a1-greetings:hello` — он всегда начинается с уровня. */
    private fun levelOf(card: VocabularyCard): CefrLevel {
        val prefix = card.item.topicId.substringBefore('-').uppercase()
        return runCatching { CefrLevel.valueOf(prefix) }.getOrDefault(CefrLevel.B1)
    }

    /**
     * Разбирает ответ формата `Verdict: … / Better: … / Note: …`.
     * Модель маленькая и формат иногда ломает, поэтому отсутствие полей —
     * не ошибка: показываем что есть.
     */
    private fun parseUsage(heard: String, raw: String): VoiceCheckState.Result {
        val verdict = Regex("(?im)^\\s*Verdict:\\s*(\\w+)").find(raw)?.groupValues?.get(1)?.trim()
        val better = Regex("(?im)^\\s*Better:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim()
        val note = Regex("(?im)^\\s*Note:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim()
        return VoiceCheckState.Result(
            heard = heard,
            ok = verdict.equals("ok", ignoreCase = true),
            better = better.orEmpty(),
            note = note ?: raw.trim()
        )
    }

    fun dismissVoiceResult() {
        _state.update { it.copy(voice = VoiceCheckState.Idle) }
    }

    fun restart() {
        _state.value = ReviewUiState()
        loadSession()
    }

    override fun onCleared() {
        ttsEngine.stop()
        super.onCleared()
    }
}
