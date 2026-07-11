package com.example.englishvoicetutor.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.local.ModelPreferences
import com.example.englishvoicetutor.data.model.ModelInstaller
import com.example.englishvoicetutor.domain.model.LlmInstallState
import com.example.englishvoicetutor.domain.model.LlmModelCatalog
import com.example.englishvoicetutor.domain.model.LlmModelOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val installer: ModelInstaller,
    private val prefs: ModelPreferences,
) : ViewModel() {

    val models: List<LlmModelOption> = LlmModelCatalog.all

    /** id и имя сейчас установленной модели — для подсказки в UI. */
    val installedModelId: String? = prefs.installedModelId
    val installedModelLabel: String? = prefs.installedModelLabel
    val lastCustomUrl: String = prefs.customUrl.orEmpty()

    private val _state = MutableStateFlow<LlmInstallState>(LlmInstallState.Idle)
    val state: StateFlow<LlmInstallState> = _state

    fun install(option: LlmModelOption, token: String, customUrl: String) {
        val url = if (option.isCustom) customUrl.trim() else option.url
        viewModelScope.launch {
            installer.install(option, url, token).collect { _state.value = it }
        }
    }

    fun resetState() {
        _state.value = LlmInstallState.Idle
    }
}
