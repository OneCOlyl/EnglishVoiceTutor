package com.example.englishvoicetutor.data.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AndroidSttEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : SttEngine {
    override suspend fun startRecording() {
        // Не используется — на этом устройстве нет системного STT
    }

    override fun stopRecording(): String = ""
}