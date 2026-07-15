package com.example.englishvoicetutor.presentation.conversation

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishvoicetutor.data.engine.LlmEngine
import com.example.englishvoicetutor.data.engine.SttEngine
import com.example.englishvoicetutor.data.engine.SherpaOnnxSttEngine
import com.example.englishvoicetutor.data.engine.TtsEngine
import com.example.englishvoicetutor.data.model.ModelInstaller
import com.example.englishvoicetutor.data.repository.ConversationRepository
import com.example.englishvoicetutor.domain.TutorPrompt
import com.example.englishvoicetutor.domain.model.CefrLevel
import com.example.englishvoicetutor.domain.model.Conversation
import com.example.englishvoicetutor.domain.model.Message
import com.example.englishvoicetutor.domain.model.MessageRole
import com.example.englishvoicetutor.domain.model.ModelDownloadState
import com.example.englishvoicetutor.domain.model.NEW_CONVERSATION_ID
import com.example.englishvoicetutor.domain.model.VoiceUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ConversationRepository,
    private val sttEngine: SttEngine,
    private val ttsEngine: TtsEngine,
    private val llmEngine: LlmEngine,
    private val sttModelEngine: SherpaOnnxSttEngine,
    private val modelInstaller: ModelInstaller,
) : ViewModel() {

    private val navArgId: Long = savedStateHandle.get<Long>("conversationId") ?: NEW_CONVERSATION_ID
    val isNewConversation: Boolean = navArgId == NEW_CONVERSATION_ID
    val modelDownloadState: StateFlow<ModelDownloadState> = sttModelEngine.downloadState

    private val _conversationId = MutableStateFlow(navArgId.takeIf { it != NEW_CONVERSATION_ID })
    private val _conversationMeta = MutableStateFlow<Conversation?>(null)

    private val _voiceState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val voiceState: StateFlow<VoiceUiState> = _voiceState.asStateFlow()

    val conversationId: StateFlow<Long?> = _conversationId.asStateFlow()
    val conversationMeta: StateFlow<Conversation?> = _conversationMeta.asStateFlow()

    var scenarioInput by mutableStateOf("Ordering food at a restaurant")
        private set
    var levelInput by mutableStateOf(CefrLevel.B1)
        private set

    fun updateScenario(value: String) { scenarioInput = value }
    fun updateLevel(value: CefrLevel) { levelInput = value }

    val messages: StateFlow<List<Message>> = _conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (!isNewConversation) {
            viewModelScope.launch {
                _conversationMeta.value = repository.getConversation(navArgId)
            }
        }
        // Прогреваем LLM заранее: после рестарта приложения модель есть на диске,
        // но не подключена к движку в этом процессе. Без этого первый вызов LLM
        // упал бы с «Модель не загружена». Идемпотентно, тяжёлую инициализацию
        // (~30 сек) делаем один раз в фоне, пока пользователь читает историю.
        viewModelScope.launch {
            runCatching { modelInstaller.ensureInitialized() }
                .onFailure { Log.e("VoiceTutor", "LLM warm-up failed", it) }
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val conversation = repository.createConversation(scenarioInput, levelInput)
            _conversationMeta.value = conversation
            _conversationId.value = conversation.id
        }
    }

    /** Запускает полный цикл: STT → LLM → TTS. */
    fun onMicTapped() {
        val convId = _conversationId.value ?: return
        val meta = _conversationMeta.value ?: return

        when (_voiceState.value) {
            is VoiceUiState.Idle, is VoiceUiState.Error -> {
                viewModelScope.launch {
                    try {
                        sttEngine.startRecording()
                        _voiceState.value = VoiceUiState.Recording
                    } catch (e: Exception) {
                        _voiceState.value = VoiceUiState.Error(e.message ?: "Ошибка микрофона")
                    }
                }
            }
            is VoiceUiState.Recording -> {
                // Whisper-декод занимает несколько секунд — уводим его с главного потока
                // (иначе фриз UI/ANR) и показываем состояние «Распознаю речь…».
                _voiceState.value = VoiceUiState.Transcribing
                viewModelScope.launch {
                    val userText = withContext(Dispatchers.Default) { sttEngine.stopRecording() }
                    Log.d("VoiceTutor", "STT result: '$userText'")
                    onSpeechResult(userText)
                }
            }
            else -> { /* идёт обработка, игнорируем */ }
        }
    }

    /** Вызывается когда текст пользователя уже известен (из любого источника). */
    fun onSpeechResult(userText: String) {
        val convId = _conversationId.value ?: run {
            Log.d("VoiceTutor", "onSpeechResult: convId is null")
            return
        }
        val meta = _conversationMeta.value ?: run {
            Log.d("VoiceTutor", "onSpeechResult: meta is null")
            return
        }
        Log.d("VoiceTutor", "onSpeechResult: text='$userText'")
        if (userText.isBlank()) { _voiceState.value = VoiceUiState.Idle; return }
        viewModelScope.launch {
            try {
                Log.d("VoiceTutor", "Saving user message...")
                repository.appendMessage(convId, MessageRole.USER, userText)
                _voiceState.value = VoiceUiState.Thinking
                // На случай если прогрев из init ещё не завершился (или не стартовал) —
                // гарантируем, что движок подключён, прежде чем звать LLM.
                modelInstaller.ensureInitialized()
                Log.d("VoiceTutor", "Calling LLM...")
                val systemPrompt = TutorPrompt.system(meta.cefrLevel, meta.scenario)
                val history = repository.getContextForResume(convId)

                val replyBuilder = StringBuilder()
                llmEngine.generateReply(systemPrompt, history, userText)
                    .collect { chunk -> replyBuilder.append(chunk) }

                val replyText = replyBuilder.toString().trim().ifBlank {
                    "Sorry, could you say that again?"
                }

                repository.appendMessage(convId, MessageRole.TUTOR, replyText)
                _voiceState.value = VoiceUiState.Speaking(replyText)
                ttsEngine.speak(replyText)
                _voiceState.value = VoiceUiState.Idle
            } catch (e: Exception) {
                // Любая ошибка LLM/TTS не должна ронять процесс — показываем её в UI.
                Log.e("VoiceTutor", "Voice loop failed", e)
                _voiceState.value = VoiceUiState.Error(e.message ?: "Ошибка ответа репетитора")
            }
        }
    }

    fun resetToIdle() { _voiceState.value = VoiceUiState.Idle }

    override fun onCleared() {
        ttsEngine.stop()
        super.onCleared()
    }
}