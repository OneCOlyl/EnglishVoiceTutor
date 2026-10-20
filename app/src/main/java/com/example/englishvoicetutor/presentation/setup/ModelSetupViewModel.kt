package com.example.englishvoicetutor.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.local.ModelPreferences
import com.example.englishvoicetutor.data.model.ModelInstaller
import com.example.englishvoicetutor.domain.model.LlmInstallState
import com.example.englishvoicetutor.domain.model.LlmModelCatalog
import com.example.englishvoicetutor.domain.model.LlmModelOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Первичная настройка модели. Логика скачивания/подключения — в общем
 * [ModelInstaller]; экран показывает [ModelPickerForm] (каталог + своя ссылка),
 * так что HuggingFace нужен только для источников с `requiresToken`.
 */
@HiltViewModel
class ModelSetupViewModel @Inject constructor(
    private val installer: ModelInstaller,
    private val prefs: ModelPreferences,
) : ViewModel() {

    val models: List<LlmModelOption> = LlmModelCatalog.all
    val lastCustomUrl: String = prefs.customUrl.orEmpty()

    private val _state = MutableStateFlow<LlmInstallState>(LlmInstallState.Idle)
    val state: StateFlow<LlmInstallState> = _state

    private var installJob: Job? = null

    fun install(option: LlmModelOption, token: String, customUrl: String) {
        // Повторное нажатие, пока идёт установка, не должно ставить вторую в очередь.
        if (installJob?.isActive == true) return
        val url = if (option.isCustom) customUrl.trim() else option.url
        installJob = viewModelScope.launch {
            installer.install(option, url, token).collect { _state.value = it }
        }
    }

    fun resetState() {
        _state.value = LlmInstallState.Idle
    }
}
