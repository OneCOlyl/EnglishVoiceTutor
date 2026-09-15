package com.example.englishvoicetutor.presentation.learning

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.engine.LlmEngine
import com.example.englishvoicetutor.data.engine.TtsEngine
import com.example.englishvoicetutor.data.engine.TtsStatus
import com.example.englishvoicetutor.data.model.ModelInstaller
import com.example.englishvoicetutor.data.repository.CurriculumRepository
import com.example.englishvoicetutor.domain.TutorPrompt
import com.example.englishvoicetutor.domain.model.CefrLevel
import com.example.englishvoicetutor.domain.model.GrammarExample
import com.example.englishvoicetutor.domain.model.GrammarRule
import com.example.englishvoicetutor.domain.model.LearningTopic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Правило вместе с темой, которой оно принадлежит — строка в списке правил. */
data class RuleListItem(
    val rule: GrammarRule,
    val level: CefrLevel,
    val topicTitleRu: String
)

/** Вкладка «Правила»: справочник по всем правилам курса, сгруппированный по уровням. */
@HiltViewModel
class GrammarViewModel @Inject constructor(
    private val curriculumRepository: CurriculumRepository
) : ViewModel() {

    private val _rules = MutableStateFlow<List<RuleListItem>>(emptyList())
    val rules: StateFlow<List<RuleListItem>> = _rules.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var allRules: List<RuleListItem> = emptyList()

    init {
        viewModelScope.launch {
            allRules = curriculumRepository.topics().flatMap { topic -> topic.toListItems() }
            _rules.value = allRules
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        _rules.value = if (value.isBlank()) {
            allRules
        } else {
            allRules.filter {
                it.rule.titleRu.contains(value, ignoreCase = true) ||
                    it.rule.summaryRu.contains(value, ignoreCase = true) ||
                    it.topicTitleRu.contains(value, ignoreCase = true)
            }
        }
    }
}

private fun LearningTopic.toListItems() = rules.map {
    RuleListItem(rule = it, level = level, topicTitleRu = titleRu)
}

/** Состояние генерации дополнительного примера к правилу. */
sealed interface ExtraExampleState {
    data object Idle : ExtraExampleState
    data object Loading : ExtraExampleState
    data class Ready(val examples: List<GrammarExample>) : ExtraExampleState
    data class Error(val message: String) : ExtraExampleState
}

/**
 * Экран одного правила: объяснение, примеры, типичная ошибка.
 * Кнопка «Ещё пример» — единственное место, где здесь участвует LLM:
 * статический контент остаётся основой, модель только дополняет его.
 */
@HiltViewModel
class RuleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val curriculumRepository: CurriculumRepository,
    private val llmEngine: LlmEngine,
    private val ttsEngine: TtsEngine,
    private val modelInstaller: ModelInstaller
) : ViewModel() {

    private val ruleId: String = checkNotNull(savedStateHandle["ruleId"])

    private val _rule = MutableStateFlow<GrammarRule?>(null)
    val rule: StateFlow<GrammarRule?> = _rule.asStateFlow()

    private val _extra = MutableStateFlow<ExtraExampleState>(ExtraExampleState.Idle)
    val extra: StateFlow<ExtraExampleState> = _extra.asStateFlow()

    /** Доступна ли озвучка — если нет, экран показывает причину вместо молчания. */
    val ttsStatus: StateFlow<TtsStatus> = ttsEngine.status

    private var level: CefrLevel = CefrLevel.B1

    init {
        viewModelScope.launch {
            val found = curriculumRepository.rule(ruleId)
            _rule.value = found
            found?.let { rule ->
                level = curriculumRepository.topic(rule.topicId)?.level ?: CefrLevel.B1
            }
        }
    }

    fun speak(text: String) {
        viewModelScope.launch { ttsEngine.speak(text) }
    }

    /** Просит модель сгенерировать ещё одно предложение по той же конструкции. */
    fun generateExample() {
        val rule = _rule.value ?: return
        if (_extra.value is ExtraExampleState.Loading) return
        viewModelScope.launch {
            val previous = (_extra.value as? ExtraExampleState.Ready)?.examples.orEmpty()
            _extra.value = ExtraExampleState.Loading
            try {
                modelInstaller.ensureInitialized()
                val patterns = rule.examples.map { it.en } + previous.map { it.en }
                val raw = llmEngine.ask(TutorPrompt.extraExample(patterns, level))
                val parsed = parseExample(raw)
                _extra.value = if (parsed == null) {
                    ExtraExampleState.Error("Модель ответила в неожиданном формате")
                } else {
                    ExtraExampleState.Ready(previous + parsed)
                }
            } catch (e: Exception) {
                Log.e("VoiceTutor", "Extra example failed", e)
                _extra.value = ExtraExampleState.Error(e.message ?: "Ошибка генерации примера")
            }
        }
    }

    /** Ожидаем формат `En: … / Ru: …`; без английской строки показывать нечего. */
    private fun parseExample(raw: String): GrammarExample? {
        val en = Regex("(?im)^\\s*En:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim()
        val ru = Regex("(?im)^\\s*Ru:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim()
        return en?.takeIf { it.isNotBlank() }?.let { GrammarExample(en = it, ru = ru.orEmpty()) }
    }

    override fun onCleared() {
        ttsEngine.stop()
        super.onCleared()
    }
}
