package com.example.englishvoicetutor.presentation.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.engine.TtsEngine
import com.example.englishvoicetutor.data.engine.TtsStatus
import com.example.englishvoicetutor.data.repository.CurriculumRepository
import com.example.englishvoicetutor.data.repository.VocabularyRepository
import com.example.englishvoicetutor.domain.model.VocabStatus
import com.example.englishvoicetutor.domain.model.VocabularyCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Фильтр списка слов на вкладке «Слова». */
enum class VocabFilter(val label: String) {
    ALL("Все"),
    LEARNING("Учу"),
    KNOWN("Выучено")
}

/** Вкладка «Слова»: весь словарь курса с фильтром и точкой входа в повторение. */
@HiltViewModel
class VocabularyViewModel @Inject constructor(
    curriculumRepository: CurriculumRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val ttsEngine: TtsEngine
) : ViewModel() {

    private val _filter = MutableStateFlow(VocabFilter.ALL)
    val filter: StateFlow<VocabFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val dueCount: StateFlow<Int> = vocabularyRepository.observeDueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val cards: StateFlow<List<VocabularyCard>> =
        combine(curriculumRepository.observeAllCards(), _filter, _query) { cards, filter, query ->
            cards
                .filter { card ->
                    when (filter) {
                        VocabFilter.ALL -> true
                        VocabFilter.LEARNING -> card.progress.status == VocabStatus.LEARNING
                        VocabFilter.KNOWN -> card.progress.status == VocabStatus.KNOWN
                    }
                }
                .filter { card ->
                    query.isBlank() ||
                        card.item.word.contains(query, ignoreCase = true) ||
                        card.item.translationRu.contains(query, ignoreCase = true)
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Доступна ли озвучка — если нет, экран показывает причину вместо молчания. */
    val ttsStatus: StateFlow<TtsStatus> = ttsEngine.status

    fun setFilter(value: VocabFilter) { _filter.value = value }

    fun setQuery(value: String) { _query.value = value }

    fun toggleKnown(card: VocabularyCard) {
        viewModelScope.launch { vocabularyRepository.toggleKnown(card) }
    }

    fun speak(text: String) {
        viewModelScope.launch { ttsEngine.speak(text) }
    }

    override fun onCleared() {
        ttsEngine.stop()
        super.onCleared()
    }
}
