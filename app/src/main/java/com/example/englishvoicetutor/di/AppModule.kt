package com.example.englishvoicetutor.di

import android.content.Context
import androidx.room.Room
import com.example.englishvoicetutor.data.engine.SherpaOnnxSttEngine
import com.example.englishvoicetutor.data.engine.SherpaOnnxTtsEngine
import com.example.englishvoicetutor.data.engine.AndroidTtsEngine
import com.example.englishvoicetutor.data.engine.LiteRtLlmEngine
import com.example.englishvoicetutor.data.engine.LlmEngine
import com.example.englishvoicetutor.data.engine.SttEngine
import com.example.englishvoicetutor.data.engine.StubTutorLlmEngine
import com.example.englishvoicetutor.data.engine.TtsEngine
import com.example.englishvoicetutor.data.local.AppDatabase
import com.example.englishvoicetutor.data.local.MIGRATION_1_2
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineBindModule {

    @Binds
    @Singleton
    abstract fun bindSttEngine(impl: SherpaOnnxSttEngine): SttEngine

    // Офлайн-синтез через sherpa-onnx: не зависит от наличия движка TTS в прошивке.
    // Системный AndroidTtsEngine оставлен как альтернатива — подменяется этой строкой.
    @Binds
    @Singleton
    abstract fun bindTtsEngine(impl: SherpaOnnxTtsEngine): TtsEngine

    // Когда интегрируете LiteRT-LM (см. README), замените StubTutorLlmEngine на
    // LiteRtLlmEngine — это единственное место, которое нужно поменять.
    @Binds
    @Singleton
    abstract fun bindLlmEngine(impl: LiteRtLlmEngine): LlmEngine
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "english_voice_tutor.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideConversationDao(db: AppDatabase) = db.conversationDao()

    @Provides
    fun provideLearningDao(db: AppDatabase) = db.learningDao()
}