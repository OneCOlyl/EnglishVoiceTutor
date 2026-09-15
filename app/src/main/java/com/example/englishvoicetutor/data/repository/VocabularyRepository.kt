package com.example.englishvoicetutor.data.repository

import com.example.englishvoicetutor.data.curriculum.CurriculumSource
import com.example.englishvoicetutor.data.local.LearningDao
import com.example.englishvoicetutor.domain.SrsSchedule
import com.example.englishvoicetutor.domain.model.VocabProgress
import com.example.englishvoicetutor.domain.model.VocabStatus
import com.example.englishvoicetutor.domain.model.VocabularyCard
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Сколько новых слов добавляем в одну сессию повторения. */
private const val NEW_WORDS_PER_SESSION = 5

/** Потолок на размер сессии — чтобы список «на сегодня» не превращался в сотню карточек. */
private const val MAX_SESSION_SIZE = 20

/**
 * Словарь и интервальные повторения.
 *
 * Сессию собираем здесь, а не в вьюмодели: правило «сначала просроченные,
 * потом немного новых» — это предметная логика обучения, а не логика экрана.
 */
@Singleton
class VocabularyRepository @Inject constructor(
    private val source: CurriculumSource,
    private val dao: LearningDao
) {

    /** Сколько слов ждут повторения прямо сейчас — для бейджа на вкладке. */
    fun observeDueCount(): Flow<Int> = dao.observeDueCount(System.currentTimeMillis())

    /**
     * Карточки на текущую сессию: сперва те, у кого подошёл срок, затем новые
     * слова в порядке курса (от A1 к C1), чтобы учить по нарастанию сложности.
     */
    suspend fun buildSession(): List<VocabularyCard> {
        val now = System.currentTimeMillis()
        val items = source.vocabulary().associateBy { it.id }

        val due = dao.getDueVocab(now)
            .mapNotNull { row ->
                items[row.itemId]?.let { VocabularyCard(it, row.toDomain()) }
            }
            .take(MAX_SESSION_SIZE)

        if (due.size >= MAX_SESSION_SIZE) return due

        // Новые = те, по которым ещё нет ни одной строки прогресса.
        // `source.vocabulary()` уже отсортирован по уровням, поэтому take даёт
        // самые простые из невыученных.
        val touched = dao.getAllVocabProgress().map { it.itemId }.toSet()
        val fresh = source.vocabulary()
            .filter { it.id !in touched }
            .take(minOf(NEW_WORDS_PER_SESSION, MAX_SESSION_SIZE - due.size))
            .map { VocabularyCard(it, VocabProgress(itemId = it.id)) }

        return due + fresh
    }

    /** Ответ в сессии повторения: сдвигаем слово по коробкам Лейтнера. */
    suspend fun recordAnswer(card: VocabularyCard, correct: Boolean) {
        val now = System.currentTimeMillis()
        val current = dao.getVocabProgress(card.item.id)?.toDomain()
            ?: VocabProgress(itemId = card.item.id)
        val updated = if (correct) {
            SrsSchedule.onCorrect(current, now)
        } else {
            SrsSchedule.onWrong(current, now)
        }
        dao.upsertVocabProgress(updated.toEntity())
    }

    /** Ручная отметка «выучено» / снятие отметки в списке слов. */
    suspend fun toggleKnown(card: VocabularyCard) {
        val now = System.currentTimeMillis()
        val current = dao.getVocabProgress(card.item.id)?.toDomain()
            ?: VocabProgress(itemId = card.item.id)
        val updated = if (current.status == VocabStatus.KNOWN) {
            SrsSchedule.reset(current)
        } else {
            SrsSchedule.markKnown(current, now)
        }
        dao.upsertVocabProgress(updated.toEntity())
    }

    /**
     * Слова, которые репетитору стоит подмешать в диалог по теме:
     * ещё не выученные. Именно они уходят в системный промпт.
     */
    suspend fun wordsToPractise(topicId: String, limit: Int = 8): List<String> {
        val items = source.topic(topicId)?.vocabulary.orEmpty()
        val known = dao.getAllVocabProgress()
            .filter { it.status == VocabStatus.KNOWN.name }
            .map { it.itemId }
            .toSet()
        val notKnown = items.filterNot { it.id in known }
        return (notKnown.ifEmpty { items }).take(limit).map { it.word }
    }
}
