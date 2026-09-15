package com.example.englishvoicetutor.presentation.learning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.engine.TtsEngine
import com.example.englishvoicetutor.data.engine.TtsStatus
import com.example.englishvoicetutor.data.repository.CurriculumRepository
import com.example.englishvoicetutor.data.repository.VocabularyRepository
import com.example.englishvoicetutor.domain.model.TopicWithProgress
import com.example.englishvoicetutor.domain.model.VocabularyCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Экран одной темы курса: цель, слова, правила и кнопка запуска диалога. */
@HiltViewModel
class TopicViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    curriculumRepository: CurriculumRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val ttsEngine: TtsEngine
) : ViewModel() {

    val topicId: String = checkNotNull(savedStateHandle["topicId"])

    val topic: StateFlow<TopicWithProgress?> = curriculumRepository.observeTopic(topicId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val cards: StateFlow<List<VocabularyCard>> = curriculumRepository.observeTopicCards(topicId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Доступна ли озвучка — если нет, экран показывает причину вместо молчания. */
    val ttsStatus: StateFlow<TtsStatus> = ttsEngine.status

    fun toggleKnown(card: VocabularyCard) {
        viewModelScope.launch { vocabularyRepository.toggleKnown(card) }
    }

    /** Озвучка слова системным TTS — тем же движком, что читает реплики репетитора. */
    fun speak(text: String) {
        viewModelScope.launch { ttsEngine.speak(text) }
    }

    override fun onCleared() {
        ttsEngine.stop()
        super.onCleared()
    }
}
