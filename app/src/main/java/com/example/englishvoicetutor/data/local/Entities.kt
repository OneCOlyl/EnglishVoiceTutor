package com.example.englishvoicetutor.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val scenario: String,
    @ColumnInfo(name = "cefr_level") val cefrLevel: String,
    val summary: String?,
    /** id темы курса, если диалог начат из roadmap; null — свободный разговор. */
    @ColumnInfo(name = "topic_id") val topicId: String? = null,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
    /**
     * Момент прощания («goodbye») — диалог считается завершённым и ждёт разбора.
     * Обнуляется, когда пользователь решает продолжить разговор.
     */
    @ColumnInfo(name = "ended_at") val endedAtMillis: Long? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    val role: String, // "USER" | "TUTOR"
    val text: String,
    @ColumnInfo(name = "timestamp") val timestampMillis: Long
)

/**
 * Прогресс по слову для интервальных повторений.
 * Сам текст слова здесь не дублируем — он в статическом контенте (`assets/curriculum`),
 * в БД лежит только то, что меняется: коробка Лейтнера и дата следующего показа.
 * Строки создаются лениво, при первом взаимодействии со словом.
 */
@Entity(tableName = "vocab_progress")
data class VocabProgressEntity(
    @PrimaryKey @ColumnInfo(name = "item_id") val itemId: String,
    val box: Int,
    val status: String, // "NEW" | "LEARNING" | "KNOWN"
    @ColumnInfo(name = "due_at") val dueAtMillis: Long,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAtMillis: Long?,
    @ColumnInfo(name = "times_correct") val timesCorrect: Int,
    @ColumnInfo(name = "times_wrong") val timesWrong: Int
)

/** Прогресс по теме курса: когда тема была отработана в диалоге и сколько раз. */
@Entity(tableName = "topic_progress")
data class TopicProgressEntity(
    @PrimaryKey @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "completed_at") val completedAtMillis: Long?,
    @ColumnInfo(name = "conversations_count") val conversationsCount: Int
)
