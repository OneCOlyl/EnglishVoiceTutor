package com.example.englishvoicetutor.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.engine.TtsEngine
import com.example.englishvoicetutor.data.engine.TtsStatus
import com.example.englishvoicetutor.data.local.ModelPreferences
import com.example.englishvoicetutor.data.local.VoicePreferences
import com.example.englishvoicetutor.data.model.ModelInstaller
import com.example.englishvoicetutor.domain.model.LlmInstallState
import com.example.englishvoicetutor.domain.model.LlmModelCatalog
import com.example.englishvoicetutor.domain.model.LlmModelOption
import com.example.englishvoicetutor.domain.model.VoiceCatalog
import com.example.englishvoicetutor.domain.model.VoiceOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val installer: ModelInstaller,
    private val prefs: ModelPreferences,
    private val voicePreferences: VoicePreferences,
    private val ttsEngine: TtsEngine,
) : ViewModel() {

    val models: List<LlmModelOption> = LlmModelCatalog.all

    // --- Озвучка ---

    val voices: List<VoiceOption> = VoiceCatalog.options
    val speakerId: StateFlow<Int> = voicePreferences.speakerId
    val speed: StateFlow<Float> = voicePreferences.speed
    val ttsStatus: StateFlow<TtsStatus> = ttsEngine.status

    /**
     * Выбор голоса применяется сразу и тут же проговаривает пробную фразу:
     * дикторы модели анонимные, и выбрать их можно только на слух.
     */
    fun selectVoice(option: VoiceOption) {
        voicePreferences.setSpeakerId(option.id)
        previewVoice()
    }

    fun setSpeed(value: Float) {
        voicePreferences.setSpeed(value)
    }

    fun previewVoice() {
        viewModelScope.launch {
            ttsEngine.stop() // обрываем предыдущую пробу, если ещё звучит
            ttsEngine.speak(VoiceCatalog.PREVIEW_TEXT)
        }
    }

    /** id и имя сейчас установленной модели — для подсказки в UI. */
    val installedModelId: String? = prefs.installedModelId
    val installedModelLabel: String? = prefs.installedModelLabel

    /**
     * Последний выбор в форме. Пока идёт загрузка, форма не показывается и теряет
     * своё состояние; без этого после ошибки выбор и ссылка сбрасывались, и
     * «Повторите» требовало заполнить всё заново. Токен — только в памяти
     * ViewModel: на диск и в saved state он отсюда не попадает.
     */
    var formSelectedId: String? = null
        private set
    var formCustomUrl: String = prefs.customUrl.orEmpty()
        private set
    var formToken: String = ""
        private set

    private val _state = MutableStateFlow<LlmInstallState>(LlmInstallState.Idle)
    val state: StateFlow<LlmInstallState> = _state

    private var installJob: Job? = null

    fun install(option: LlmModelOption, token: String, customUrl: String) {
        // Повторное нажатие, пока идёт установка, не должно ставить вторую в очередь.
        if (installJob?.isActive == true) return
        formSelectedId = option.id
        if (option.isCustom) formCustomUrl = customUrl.trim()
        if (option.requiresToken) formToken = token
        val url = if (option.isCustom) customUrl.trim() else option.url
        installJob = viewModelScope.launch {
            installer.install(option, url, token).collect { _state.value = it }
        }
    }

    fun resetState() {
        _state.value = LlmInstallState.Idle
    }

    override fun onCleared() {
        ttsEngine.stop()
        super.onCleared()
    }
}
