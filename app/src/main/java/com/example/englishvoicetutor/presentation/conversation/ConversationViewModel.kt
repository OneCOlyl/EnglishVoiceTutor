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
import com.example.englishvoicetutor.data.engine.TtsStatus
import com.example.englishvoicetutor.data.model.ModelInstaller
import com.example.englishvoicetutor.data.repository.ConversationRepository
import com.example.englishvoicetutor.data.repository.CurriculumRepository
import com.example.englishvoicetutor.data.repository.VocabularyRepository
import com.example.englishvoicetutor.domain.ConversationFlow
import com.example.englishvoicetutor.domain.FeedbackParser
import com.example.englishvoicetutor.domain.TutorPrompt
import com.example.englishvoicetutor.domain.model.CefrLevel
import com.example.englishvoicetutor.domain.model.Conversation
import com.example.englishvoicetutor.domain.model.LearningTopic
import com.example.englishvoicetutor.domain.model.Message
import com.example.englishvoicetutor.domain.model.MessageInsight
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
    private val curriculumRepository: CurriculumRepository,
    private val vocabularyRepository: VocabularyRepository,
) : ViewModel() {

    private val navArgId: Long = savedStateHandle.get<Long>("conversationId") ?: NEW_CONVERSATION_ID

    /** Тема курса, из которой открыт экран (передаётся навигацией при старте нового диалога). */
    private val navArgTopicId: String? = savedStateHandle.get<String>("topicId")?.takeIf { it.isNotBlank() }
    val isNewConversation: Boolean = navArgId == NEW_CONVERSATION_ID

    /** Тема текущего диалога — источник учебного фокуса для системного промпта. */
    private val _topic = MutableStateFlow<LearningTopic?>(null)
    val topic: StateFlow<LearningTopic?> = _topic.asStateFlow()

    /** Диалог по теме засчитываем один раз за сессию, а не на каждую реплику. */
    private var practiceRegistered = false
    val modelDownloadState: StateFlow<ModelDownloadState> = sttModelEngine.downloadState

    /**
     * Состояние озвучки: при первом ответе репетитора может качаться голос (~75 МБ),
     * и экран обязан это показать — иначе реплика молча остаётся только текстом.
     */
    val ttsStatus: StateFlow<TtsStatus> = ttsEngine.status

    private val _conversationId = MutableStateFlow(navArgId.takeIf { it != NEW_CONVERSATION_ID })
    private val _conversationMeta = MutableStateFlow<Conversation?>(null)

    private val _voiceState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val voiceState: StateFlow<VoiceUiState> = _voiceState.asStateFlow()

    /** Подсказки по сообщениям (перевод / разбор ошибок), по id сообщения. Живёт только в UI. */
    private val _insights = MutableStateFlow<Map<Long, MessageInsight>>(emptyMap())
    val insights: StateFlow<Map<Long, MessageInsight>> = _insights.asStateFlow()

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
                val meta = repository.getConversation(navArgId)
                _conversationMeta.value = meta
                meta?.topicId?.let { _topic.value = curriculumRepository.topic(it) }
            }
        }
        // Диалог запущен из темы курса — подставляем её сценарий и уровень
        // в форму старта, чтобы пользователю не пришлось ничего вводить.
        if (isNewConversation && navArgTopicId != null) {
            viewModelScope.launch {
                curriculumRepository.topic(navArgTopicId)?.let { topic ->
                    _topic.value = topic
                    scenarioInput = topic.scenario
                    levelInput = topic.level
                }
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
            val conversation = repository.createConversation(scenarioInput, levelInput, navArgTopicId)
            _conversationMeta.value = conversation
            _conversationId.value = conversation.id
            // В уроке курса первым говорит репетитор: учащемуся не нужно
            // придумывать, с чего начать, — он сразу отвечает на вопрос.
            if (navArgTopicId != null) speakOpening(conversation)
        }
    }

    /** Первая реплика репетитора в уроке: приветствие и вводный вопрос по сценарию. */
    private suspend fun speakOpening(meta: Conversation) {
        if (openingSpoken) return
        openingSpoken = true
        runTutorTurn(meta.id, meta, TutorPrompt.openingKick(), history = emptyList())
    }

    /** Приветствие генерируем один раз за жизнь вьюмодели, даже если экран пересобрался. */
    private var openingSpoken = false

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
            // Контекст берём ДО записи новой реплики: иначе она попадёт в модель
            // дважды — и как последняя строка истории, и как текущий ход,
            // а на двух одинаковых подряд ходах модель начинает сбиваться.
            val history = repository.getContextForResume(convId)
            Log.d("VoiceTutor", "Saving user message...")
            repository.appendMessage(convId, MessageRole.USER, userText)
            // «Goodbye» закрывает разговор: репетитор прощается в ответ, после чего
            // экран предлагает разбор — или продолжить, если прощание было случайным.
            val farewell = ConversationFlow.isFarewell(userText)
            val turnPrompt = if (farewell) TutorPrompt.farewellKick(userText) else userText
            val ok = runTutorTurn(convId, meta, turnPrompt, history = history)
            if (farewell && ok) {
                repository.setEnded(convId, ended = true)?.let { _conversationMeta.value = it }
            }
        }
    }

    /**
     * Один ход репетитора: LLM → запись реплики → озвучка.
     * [history] — контекст без текущей реплики учащегося (для первого хода пустой).
     * Возвращает true, если ход прошёл без ошибок.
     */
    private suspend fun runTutorTurn(
        convId: Long,
        meta: Conversation,
        turnPrompt: String,
        history: List<Message>
    ): Boolean = try {
        _voiceState.value = VoiceUiState.Thinking
        // На случай если прогрев из init ещё не завершился (или не стартовал) —
        // гарантируем, что движок подключён, прежде чем звать LLM.
        modelInstaller.ensureInitialized()
        Log.d("VoiceTutor", "Calling LLM...")
        val systemPrompt = TutorPrompt.system(
            level = meta.cefrLevel,
            scenario = meta.scenario,
            focus = buildFocus(meta),
            // Разговор уже идёт — напоминаем модели, что знакомство позади.
            // Без этого она на длинной истории заново представляется и спрашивает имя.
            resumed = history.isNotEmpty()
        )

        val replyBuilder = StringBuilder()
        llmEngine.generateReply(systemPrompt, history, turnPrompt)
            .collect { chunk -> replyBuilder.append(chunk) }

        val replyText = replyBuilder.toString().trim().ifBlank {
            "Sorry, could you say that again?"
        }

        repository.appendMessage(convId, MessageRole.TUTOR, replyText)
        registerTopicPracticeOnce(meta.topicId)
        _voiceState.value = VoiceUiState.Speaking(replyText)
        ttsEngine.speak(replyText)
        _voiceState.value = VoiceUiState.Idle
        true
    } catch (e: Exception) {
        // Любая ошибка LLM/TTS не должна ронять процесс — показываем её в UI.
        Log.e("VoiceTutor", "Voice loop failed", e)
        _voiceState.value = VoiceUiState.Error(e.message ?: "Ошибка ответа репетитора")
        false
    }

    /** Продолжить разговор после прощания — история и контекст сохраняются. */
    fun continueConversation() {
        val convId = _conversationId.value ?: return
        viewModelScope.launch {
            repository.setEnded(convId, ended = false)?.let { _conversationMeta.value = it }
            _voiceState.value = VoiceUiState.Idle
        }
    }

    /** Ручное завершение разговора кнопкой — тот же итог, что и «goodbye». */
    fun endConversation() {
        val convId = _conversationId.value ?: return
        viewModelScope.launch {
            ttsEngine.stop()
            repository.setEnded(convId, ended = true)?.let { _conversationMeta.value = it }
            _voiceState.value = VoiceUiState.Idle
        }
    }

    /**
     * Собирает учебный фокус для системного промпта: невыученные слова темы
     * и эталонные предложения из её правил. Для свободного диалога — null.
     */
    private suspend fun buildFocus(meta: Conversation): TutorPrompt.TopicFocus? {
        val topicId = meta.topicId ?: return null
        val topic = _topic.value ?: curriculumRepository.topic(topicId)?.also { _topic.value = it }
        ?: return null
        return TutorPrompt.TopicFocus(
            topicTitle = topic.titleEn,
            targetWords = vocabularyRepository.wordsToPractise(topicId),
            // Берём по одному эталону на правило: длинный список примеров
            // маленькая модель начинает цитировать дословно вместо разговора.
            targetStructures = topic.rules.mapNotNull { it.examples.firstOrNull()?.en }
        )
    }

    /** Засчитываем практику по теме после первого полноценного обмена репликами. */
    private suspend fun registerTopicPracticeOnce(topicId: String?) {
        if (topicId == null || practiceRegistered) return
        practiceRegistered = true
        curriculumRepository.registerTopicPractice(topicId)
    }

    private fun updateInsight(messageId: Long, transform: (MessageInsight) -> MessageInsight) {
        _insights.value = _insights.value.toMutableMap().apply {
            put(messageId, transform(get(messageId) ?: MessageInsight()))
        }
    }

    /** Перевод реплики на русский по нажатию кнопки под сообщением. Идемпотентно кешируется. */
    fun translateMessage(message: Message) {
        val current = _insights.value[message.id]
        if (current?.translation != null || current?.translationLoading == true) return
        viewModelScope.launch {
            updateInsight(message.id) { it.copy(translationLoading = true, error = null) }
            try {
                modelInstaller.ensureInitialized()
                val translation = llmEngine.translateToRussian(message.text)
                updateInsight(message.id) {
                    it.copy(translation = translation, translationLoading = false)
                }
            } catch (e: Exception) {
                Log.e("VoiceTutor", "Translate failed", e)
                updateInsight(message.id) {
                    it.copy(translationLoading = false, error = e.message ?: "Ошибка перевода")
                }
            }
        }
    }

    /**
     * Разбор ошибок в реплике учащегося + подсказка «как лучше сказать».
     * [anchor] — сообщение, под которым показываем результат (ответ бота),
     * [target] — реплика ученика, которую фактически проверяем.
     */
    fun reviewMessage(anchor: Message, target: Message) {
        val current = _insights.value[anchor.id]
        if (current?.better != null || current?.feedbackLoading == true) return
        val meta = _conversationMeta.value ?: return
        viewModelScope.launch {
            updateInsight(anchor.id) { it.copy(feedbackLoading = true, error = null) }
            try {
                modelInstaller.ensureInitialized()
                val raw = llmEngine.feedback(target.text, meta.cefrLevel)
                val parsed = FeedbackParser.parse(raw)
                updateInsight(anchor.id) {
                    it.copy(better = parsed.better, note = parsed.note, feedbackLoading = false)
                }
            } catch (e: Exception) {
                Log.e("VoiceTutor", "Feedback failed", e)
                updateInsight(anchor.id) {
                    it.copy(feedbackLoading = false, error = e.message ?: "Ошибка разбора")
                }
            }
        }
    }

    fun resetToIdle() { _voiceState.value = VoiceUiState.Idle }

    override fun onCleared() {
        ttsEngine.stop()
        super.onCleared()
    }
}