package com.example.englishvoicetutor.data.curriculum

import android.content.Context
import com.example.englishvoicetutor.domain.model.CefrLevel
import com.example.englishvoicetutor.domain.model.GrammarExample
import com.example.englishvoicetutor.domain.model.GrammarRule
import com.example.englishvoicetutor.domain.model.LearningTopic
import com.example.englishvoicetutor.domain.model.VocabularyItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Читает статический курс из `assets/curriculum/` (файл на уровень CEFR).
 *
 * Парсим через `org.json` — он есть в Android из коробки, и ради одного файла
 * конфигурации не хочется тянуть kotlinx.serialization с её Gradle-плагином.
 * Результат кешируется в памяти: контент неизменяемый, читать его с диска
 * повторно незачем.
 */
@Singleton
class CurriculumSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Порядок файлов задаёт порядок уровней в roadmap — от новичка к продвинутому. */
    private val levelFiles = listOf(
        CefrLevel.A1 to "curriculum/a1.json",
        CefrLevel.A2 to "curriculum/a2.json",
        CefrLevel.B1 to "curriculum/b1.json",
        CefrLevel.B2 to "curriculum/b2.json",
        CefrLevel.C1 to "curriculum/c1.json"
    )

    private val mutex = Mutex()
    private var cached: List<LearningTopic>? = null

    /** Все темы курса по порядку: сначала уровень, внутри уровня — поле order. */
    suspend fun topics(): List<LearningTopic> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { load() }.also { cached = it }
    }

    suspend fun topic(topicId: String): LearningTopic? = topics().firstOrNull { it.id == topicId }

    suspend fun vocabulary(): List<VocabularyItem> = topics().flatMap { it.vocabulary }

    suspend fun rules(): List<GrammarRule> = topics().flatMap { it.rules }

    private fun load(): List<LearningTopic> = levelFiles.flatMap { (level, path) ->
        val json = JSONObject(context.assets.open(path).bufferedReader().use { it.readText() })
        json.getJSONArray("topics").objects().map { parseTopic(it, level) }
    }.sortedWith(compareBy<LearningTopic> { it.level.ordinal }.thenBy { it.order })

    private fun parseTopic(json: JSONObject, level: CefrLevel): LearningTopic {
        val topicId = json.getString("id")
        return LearningTopic(
            id = topicId,
            level = level,
            order = json.getInt("order"),
            titleRu = json.getString("titleRu"),
            titleEn = json.getString("titleEn"),
            goalRu = json.getString("goalRu"),
            scenario = json.getString("scenario"),
            vocabulary = json.getJSONArray("vocabulary").objects()
                .map { parseVocabulary(it, topicId) },
            rules = json.getJSONArray("rules").objects()
                .mapIndexed { index, rule -> parseRule(rule, topicId, index) }
        )
    }

    private fun parseVocabulary(json: JSONObject, topicId: String): VocabularyItem {
        val word = json.getString("word")
        return VocabularyItem(
            // id должен пережить обновление контента, поэтому строим его из
            // темы и самого слова, а не из позиции в списке.
            id = "$topicId:$word",
            topicId = topicId,
            word = word,
            translationRu = json.getString("ru"),
            transcription = json.optStringOrNull("ipa"),
            exampleEn = json.getString("exEn"),
            exampleRu = json.getString("exRu")
        )
    }

    private fun parseRule(json: JSONObject, topicId: String, index: Int): GrammarRule = GrammarRule(
        id = "$topicId:rule$index",
        topicId = topicId,
        titleRu = json.getString("titleRu"),
        summaryRu = json.getString("summaryRu"),
        explanationRu = json.getString("explanationRu"),
        pitfallRu = json.optStringOrNull("pitfallRu"),
        examples = json.getJSONArray("examples").objects().map {
            GrammarExample(
                en = it.getString("en"),
                ru = it.getString("ru"),
                wrong = it.optStringOrNull("wrong")
            )
        }
    )
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

/** `optString` возвращает пустую строку вместо null — а нам нужен именно null. */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
