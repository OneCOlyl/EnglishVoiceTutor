package com.example.englishvoicetutor.data.repository

import com.example.englishvoicetutor.data.curriculum.CurriculumSource
import com.example.englishvoicetutor.data.local.LearningDao
import com.example.englishvoicetutor.data.local.TopicProgressEntity
import com.example.englishvoicetutor.data.local.VocabProgressEntity
import com.example.englishvoicetutor.domain.model.GrammarRule
import com.example.englishvoicetutor.domain.model.LearningTopic
import com.example.englishvoicetutor.domain.model.LevelSection
import com.example.englishvoicetutor.domain.model.TopicProgress
import com.example.englishvoicetutor.domain.model.TopicWithProgress
import com.example.englishvoicetutor.domain.model.VocabProgress
import com.example.englishvoicetutor.domain.model.VocabStatus
import com.example.englishvoicetutor.domain.model.VocabularyCard
import com.example.englishvoicetutor.domain.model.VocabularyItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Учебная программа = неизменяемый контент из assets + прогресс пользователя из Room.
 * Склейка этих двух источников живёт здесь, чтобы вьюмодели получали уже готовые
 * модели вида [TopicWithProgress] и не знали про два разных хранилища.
 */
@Singleton
class CurriculumRepository @Inject constructor(
    private val source: CurriculumSource,
    private val dao: LearningDao
) {

    /** Roadmap целиком: уровни по возрастанию, внутри — темы с прогрессом. */
    fun observeRoadmap(): Flow<List<LevelSection>> = flow {
        val topics = source.topics()
        emitAll(
            combine(
                dao.observeTopicProgress(),
                dao.observeVocabProgress()
            ) { topicRows, vocabRows ->
                val topicProgress = topicRows.associateBy { it.topicId }
                val known = vocabRows.filter { it.status == VocabStatus.KNOWN.name }
                    .map { it.itemId }
                    .toSet()
                topics.groupBy { it.level }
                    .toSortedMap(compareBy { it.ordinal })
                    .map { (level, levelTopics) ->
                        LevelSection(
                            level = level,
                            topics = levelTopics.map { topic ->
                                topic.withProgress(topicProgress[topic.id]?.toDomain(), known)
                            }
                        )
                    }
            }
        )
    }

    /** Одна тема с прогрессом — для экрана темы. */
    fun observeTopic(topicId: String): Flow<TopicWithProgress?> = flow {
        val topic = source.topic(topicId)
        if (topic == null) {
            emit(null)
            return@flow
        }
        emitAll(
            combine(
                dao.observeTopicProgress(),
                dao.observeVocabProgress()
            ) { topicRows, vocabRows ->
                val known = vocabRows.filter { it.status == VocabStatus.KNOWN.name }
                    .map { it.itemId }
                    .toSet()
                topic.withProgress(topicRows.firstOrNull { it.topicId == topicId }?.toDomain(), known)
            }
        )
    }

    /** Слова темы вместе с их прогрессом. */
    fun observeTopicCards(topicId: String): Flow<List<VocabularyCard>> = flow {
        val items = source.topic(topicId)?.vocabulary.orEmpty()
        emitAll(
            dao.observeVocabProgress().mapToCards(items)
        )
    }

    /** Все слова курса с прогрессом — для общего экрана словаря и фильтров. */
    fun observeAllCards(): Flow<List<VocabularyCard>> = flow {
        val items = source.vocabulary()
        emitAll(dao.observeVocabProgress().mapToCards(items))
    }

    suspend fun topic(topicId: String): LearningTopic? = source.topic(topicId)

    suspend fun topics(): List<LearningTopic> = source.topics()

    suspend fun rule(ruleId: String): GrammarRule? = source.rules().firstOrNull { it.id == ruleId }

    /**
     * Отмечает, что по теме прошёл ещё один диалог. Сама «пройденность» темы
     * в UI считается вместе с долей выученных слов (см. [TopicWithProgress.isDone]),
     * поэтому здесь только фиксируем факт разговора.
     */
    suspend fun registerTopicPractice(topicId: String) {
        val existing = dao.getTopicProgress(topicId)
        dao.upsertTopicProgress(
            TopicProgressEntity(
                topicId = topicId,
                completedAtMillis = existing?.completedAtMillis ?: System.currentTimeMillis(),
                conversationsCount = (existing?.conversationsCount ?: 0) + 1
            )
        )
    }
}

/** Приклеивает прогресс к теме: считает, сколько слов уже выучено. */
private fun LearningTopic.withProgress(
    progress: TopicProgress?,
    knownItemIds: Set<String>
) = TopicWithProgress(
    topic = this,
    progress = progress ?: TopicProgress(topicId = id),
    wordsTotal = vocabulary.size,
    wordsKnown = vocabulary.count { it.id in knownItemIds }
)

/**
 * Превращает поток строк прогресса в карточки для заданного набора слов.
 * Слова, которых ещё нет в БД, получают дефолтный прогресс со статусом NEW —
 * строки в Room создаются лениво, только при первом взаимодействии.
 */
private fun Flow<List<VocabProgressEntity>>.mapToCards(
    items: List<VocabularyItem>
): Flow<List<VocabularyCard>> = map { rows ->
    val byId = rows.associateBy { it.itemId }
    items.map { item ->
        VocabularyCard(
            item = item,
            progress = byId[item.id]?.toDomain() ?: VocabProgress(itemId = item.id)
        )
    }
}

internal fun VocabProgressEntity.toDomain() = VocabProgress(
    itemId = itemId,
    box = box,
    status = runCatching { VocabStatus.valueOf(status) }.getOrDefault(VocabStatus.NEW),
    dueAtMillis = dueAtMillis,
    lastReviewedAtMillis = lastReviewedAtMillis,
    timesCorrect = timesCorrect,
    timesWrong = timesWrong
)

internal fun VocabProgress.toEntity() = VocabProgressEntity(
    itemId = itemId,
    box = box,
    status = status.name,
    dueAtMillis = dueAtMillis,
    lastReviewedAtMillis = lastReviewedAtMillis,
    timesCorrect = timesCorrect,
    timesWrong = timesWrong
)

private fun TopicProgressEntity.toDomain() = TopicProgress(
    topicId = topicId,
    completedAtMillis = completedAtMillis,
    conversationsCount = conversationsCount
)
