package com.example.englishvoicetutor.data.local

import android.content.Context
import com.example.englishvoicetutor.domain.model.VoiceCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Настройки озвучки: выбранный диктор и скорость речи.
 *
 * Отдаём состояние потоком, чтобы движок синтеза подхватывал изменение сразу —
 * пользователь выбирает голос и тут же слышит его в пробной фразе, без перезапуска.
 */
@Singleton
class VoicePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _speakerId = MutableStateFlow(
        prefs.getInt(KEY_SPEAKER_ID, VoiceCatalog.DEFAULT_SPEAKER_ID)
    )
    val speakerId: StateFlow<Int> = _speakerId.asStateFlow()

    private val _speed = MutableStateFlow(prefs.getFloat(KEY_SPEED, VoiceCatalog.DEFAULT_SPEED))
    val speed: StateFlow<Float> = _speed.asStateFlow()

    fun setSpeakerId(value: Int) {
        _speakerId.value = value
        prefs.edit().putInt(KEY_SPEAKER_ID, value).apply()
    }

    fun setSpeed(value: Float) {
        val clamped = value.coerceIn(VoiceCatalog.MIN_SPEED, VoiceCatalog.MAX_SPEED)
        _speed.value = clamped
        prefs.edit().putFloat(KEY_SPEED, clamped).apply()
    }

    private companion object {
        const val PREFS_NAME = "voice_prefs"
        const val KEY_SPEAKER_ID = "tts_speaker_id"
        const val KEY_SPEED = "tts_speed"
    }
}
