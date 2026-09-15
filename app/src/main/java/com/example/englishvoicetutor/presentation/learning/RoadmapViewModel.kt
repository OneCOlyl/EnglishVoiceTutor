package com.example.englishvoicetutor.presentation.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.repository.CurriculumRepository
import com.example.englishvoicetutor.data.repository.VocabularyRepository
import com.example.englishvoicetutor.domain.model.LevelSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Экран «Курс»: уровни CEFR с темами и прогрессом по каждой. */
@HiltViewModel
class RoadmapViewModel @Inject constructor(
    curriculumRepository: CurriculumRepository,
    vocabularyRepository: VocabularyRepository
) : ViewModel() {

    val sections: StateFlow<List<LevelSection>> = curriculumRepository.observeRoadmap()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Сколько слов ждут повторения — показываем подсказкой в шапке курса. */
    val dueCount: StateFlow<Int> = vocabularyRepository.observeDueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
