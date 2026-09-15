package com.example.englishvoicetutor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        VocabProgressEntity::class,
        TopicProgressEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun learningDao(): LearningDao
}

/**
 * v1 → v2: появился учебный курс (roadmap, словарь, правила).
 * Пишем миграцию, а не `fallbackToDestructiveMigration`, чтобы уже накопленная
 * история диалогов у пользователей с установленным APK не потерялась.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN topic_id TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vocab_progress (
                item_id TEXT NOT NULL PRIMARY KEY,
                box INTEGER NOT NULL,
                status TEXT NOT NULL,
                due_at INTEGER NOT NULL,
                last_reviewed_at INTEGER,
                times_correct INTEGER NOT NULL,
                times_wrong INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS topic_progress (
                topic_id TEXT NOT NULL PRIMARY KEY,
                completed_at INTEGER,
                conversations_count INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * v2 → v3: у диалога появилось состояние «завершён» (прощание «goodbye»).
 * NULL означает «идёт», число — время завершения; диалог можно продолжить,
 * и тогда колонка снова становится NULL.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN ended_at INTEGER")
    }
}
