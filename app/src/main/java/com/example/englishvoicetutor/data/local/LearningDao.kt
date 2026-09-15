package com.example.englishvoicetutor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к прогрессу обучения: слова (SRS) и темы курса.
 * Отделён от [ConversationDao], потому что это другая предметная область —
 * история диалогов живёт своей жизнью и может чиститься независимо.
 */
@Dao
interface LearningDao {

    @Query("SELECT * FROM vocab_progress")
    fun observeVocabProgress(): Flow<List<VocabProgressEntity>>

    @Query("SELECT * FROM vocab_progress")
    suspend fun getAllVocabProgress(): List<VocabProgressEntity>

    @Query("SELECT * FROM vocab_progress WHERE item_id = :itemId")
    suspend fun getVocabProgress(itemId: String): VocabProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVocabProgress(progress: VocabProgressEntity)

    /**
     * Слова, назначенные к повторению на сейчас. Новые слова (строки ещё нет в БД)
     * сюда не попадают — их добавляет репозиторий из статического контента,
     * ограничивая дневную порцию новых слов.
     */
    @Query("SELECT * FROM vocab_progress WHERE due_at <= :nowMillis AND status != 'KNOWN' ORDER BY due_at ASC")
    suspend fun getDueVocab(nowMillis: Long): List<VocabProgressEntity>

    @Query("SELECT COUNT(*) FROM vocab_progress WHERE due_at <= :nowMillis AND status != 'KNOWN'")
    fun observeDueCount(nowMillis: Long): Flow<Int>

    @Query("SELECT * FROM topic_progress")
    fun observeTopicProgress(): Flow<List<TopicProgressEntity>>

    @Query("SELECT * FROM topic_progress WHERE topic_id = :topicId")
    suspend fun getTopicProgress(topicId: String): TopicProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopicProgress(progress: TopicProgressEntity)
}
