package com.example.englishvoicetutor.data.engine

import android.content.Context
import android.util.Log
import com.example.englishvoicetutor.domain.model.CefrLevel
import com.example.englishvoicetutor.domain.model.Message
import com.example.englishvoicetutor.domain.model.MessageRole
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import com.google.ai.edge.litertlm.Message as LlmMessage

/**
 * Потолок контекста движка.
 *
 * Системный промпт с учебным фокусом плюс 20+ реплик диалога — это уже под тысячу
 * токенов, и при умолчании модель начинала терять начало разговора (здоровалась
 * заново посреди диалога). Задаём запас явно; если модель значение не примет,
 * `initialize` откатится на умолчание.
 */
private const val MAX_NUM_TOKENS = 4096

@Singleton
class LiteRtLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmEngine {

    @Volatile private var engine: Engine? = null

    /** Путь к активной модели — нужен, чтобы пересобрать движок при откате на CPU. */
    @Volatile private var modelPath: String? = null

    /**
     * Единственный поток для всей работы с движком.
     *
     * GPU-backend (ml_drift/OpenCL) привязывает контекст к потоку, который его создал:
     * вызов из другого потока падает с «Failed to set kernel arguments - Invalid context».
     * Поэтому и инициализация, и инференс идут строго здесь, а не на `Dispatchers.IO`
     * (произвольный поток пула) и не на главном потоке, куда раньше попадал
     * `generateReply` — он собирался в контексте вызывающего.
     */
    private val llmDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "litert-llm") }
            .asCoroutineDispatcher()

    /** Движок один на приложение и не умеет параллельные запросы — сериализуем доступ. */
    private val lock = Mutex()

    /**
     * Вызвать один раз после того как пользователь выбрал файл модели.
     *
     * Сначала пробуем GPU: на телефоне он даёт заметно больше токенов в секунду и не
     * занимает CPU, который в это время пишет звук и рисует UI. Но OpenCL есть не на
     * каждом устройстве, и там `initialize()` падает — тогда откатываемся на CPU,
     * иначе приложение становится нерабочим уже после скачивания модели.
     */
    suspend fun initialize(modelPath: String) = withContext(llmDispatcher) {
        lock.withLock {
            this@LiteRtLlmEngine.modelPath = modelPath
            closeEngine()
            Log.d("LiteRtLlm", "Initializing engine with model: $modelPath")
            _usingCpuFallback = false
            // Порядок попыток: GPU быстрее, увеличенный контекст важнее скорости.
            // Значение `maxNumTokens` модель может не принять — тогда пробуем
            // умолчание, иначе движок не поднимется вовсе.
            val attempts = listOf(
                Backend.GPU() to MAX_NUM_TOKENS,
                Backend.GPU() to null,
                Backend.CPU() to MAX_NUM_TOKENS,
                Backend.CPU() to null,
            )
            var lastError: Exception? = null
            for ((backend, maxTokens) in attempts) {
                try {
                    engine = createEngine(modelPath, backend, maxTokens)
                    _usingCpuFallback = backend is Backend.CPU
                    Log.d(
                        "LiteRtLlm",
                        "Engine ready (backend=${if (_usingCpuFallback) "CPU" else "GPU"}, " +
                            "maxNumTokens=${maxTokens ?: "default"})"
                    )
                    return@withLock
                } catch (e: Exception) {
                    Log.w("LiteRtLlm", "Engine init failed (backend=$backend, maxTokens=$maxTokens)", e)
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("Не удалось запустить модель")
        }
    }

    private fun createEngine(modelPath: String, backend: Backend, maxTokens: Int?): Engine {
        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = maxTokens,
            cacheDir = context.cacheDir.path
        )
        return Engine(config).also { it.initialize() }
    }

    @Volatile private var _usingCpuFallback = false

    /** true, если GPU не завёлся и модель считается на CPU — ответы будут заметно медленнее. */
    val usingCpuFallback: Boolean get() = _usingCpuFallback

    val isReady: Boolean get() = engine != null

    override fun generateReply(
        systemPrompt: String,
        history: List<Message>,
        userMessage: String
    ): Flow<String> = flow {
        lock.withLock {
            // Конвертируем историю в формат LiteRT-LM
            val initialMessages = history.map { msg ->
                when (msg.role) {
                    MessageRole.USER -> LlmMessage.user(msg.text)
                    MessageRole.TUTOR -> LlmMessage.model(msg.text)
                }
            }

            var emittedAnything = false
            try {
                streamReply(systemPrompt, initialMessages, userMessage) { chunk ->
                    emittedAnything = true
                    emit(chunk)
                }
            } catch (e: Exception) {
                // Перезапускать ответ, часть которого уже ушла в UI, нельзя — текст
                // задвоится. Такой сбой отдаём наверх как есть.
                if (emittedAnything || !recoverOnCpu(e)) throw e
                streamReply(systemPrompt, initialMessages, userMessage) { emit(it) }
            }
        }
    }.flowOn(llmDispatcher)

    private suspend fun streamReply(
        systemPrompt: String,
        initialMessages: List<LlmMessage>,
        userMessage: String,
        onChunk: suspend (String) -> Unit
    ) {
        val eng = engine ?: throw IllegalStateException("Модель не загружена")
        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            initialMessages = initialMessages
        )
        eng.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessageAsync(userMessage).collect { message ->
                val text = message.toString()
                if (text.isNotEmpty()) onChunk(text)
            }
        }
    }

    override suspend fun summarize(history: List<Message>): String {
        val text = history.joinToString("\n") {
            "${if (it.role == MessageRole.USER) "User" else "Tutor"}: ${it.text}"
        }
        return oneShot("Summarize this conversation in 2-3 sentences:\n$text")
    }

    override suspend fun translateToRussian(text: String): String =
        oneShot(com.example.englishvoicetutor.domain.TutorPrompt.translateToRussian(text)).trim()

    override suspend fun feedback(text: String, level: CefrLevel): String =
        oneShot(com.example.englishvoicetutor.domain.TutorPrompt.feedback(text, level)).trim()

    override suspend fun ask(prompt: String): String = oneShot(prompt).trim()

    /**
     * Разовый запрос к модели без истории диалога — для перевода, разбора ошибок
     * и суммаризации. Собирает потоковый ответ в одну строку.
     */
    private suspend fun oneShot(prompt: String): String = withContext(llmDispatcher) {
        lock.withLock {
            try {
                runOneShot(prompt)
            } catch (e: Exception) {
                // Ответ копится локально, наружу ещё ничего не ушло — можно
                // спокойно переподнять движок на CPU и повторить запрос.
                if (!recoverOnCpu(e)) throw e
                runOneShot(prompt)
            }
        }
    }

    private suspend fun runOneShot(prompt: String): String {
        val eng = engine ?: return ""
        var result = ""
        eng.createConversation().use { conversation ->
            conversation.sendMessageAsync(prompt).collect { result += it.toString() }
        }
        return result
    }

    /**
     * Откат на CPU уже во время работы.
     *
     * На части устройств OpenCL-делегат инициализируется успешно, а падает только на
     * инференсе («Failed to invoke the compiled model», «Invalid context»). Проверки
     * при `initialize()` такой случай не ловят, поэтому пересобираем движок на CPU
     * здесь. Возвращает true, если пересборка удалась и запрос можно повторить.
     */
    private fun recoverOnCpu(cause: Exception): Boolean {
        if (_usingCpuFallback) return false
        val path = modelPath ?: return false
        Log.w("LiteRtLlm", "GPU inference failed, rebuilding engine on CPU", cause)
        return try {
            closeEngine()
            engine = try {
                createEngine(path, Backend.CPU(), MAX_NUM_TOKENS)
            } catch (e: Exception) {
                Log.w("LiteRtLlm", "CPU rebuild with explicit context failed, using default", e)
                createEngine(path, Backend.CPU(), null)
            }
            _usingCpuFallback = true
            Log.d("LiteRtLlm", "Engine rebuilt on CPU")
            true
        } catch (e: Exception) {
            Log.e("LiteRtLlm", "CPU rebuild failed", e)
            false
        }
    }

    private fun closeEngine() {
        engine?.close()
        engine = null
    }

    suspend fun close() = withContext(llmDispatcher) {
        lock.withLock { closeEngine() }
    }
}
