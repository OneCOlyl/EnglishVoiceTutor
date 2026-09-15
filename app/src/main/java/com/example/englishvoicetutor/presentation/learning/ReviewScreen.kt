package com.example.englishvoicetutor.presentation.learning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.englishvoicetutor.presentation.components.TtsStatusBanner
import com.example.englishvoicetutor.data.engine.TtsStatus
import com.example.englishvoicetutor.domain.model.VocabularyCard

/**
 * Сессия повторения. Карточка раскрывается в два шага: сначала слово,
 * потом перевод с примером — так проверяется реальное вспоминание, а не узнавание.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val ttsStatus by viewModel.ttsStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.cards.isEmpty()) "Повторение"
                        else "Повторение ${minOf(state.index + 1, state.cards.size)} / ${state.cards.size}"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.finished -> FinishedState(
                    hadCards = state.cards.isNotEmpty(),
                    correct = state.correctCount,
                    wrong = state.wrongCount,
                    onRestart = viewModel::restart,
                    onBack = onBack
                )

                else -> state.current?.let { card ->
                    ReviewCardContent(
                        card = card,
                        ttsStatus = ttsStatus,
                        revealed = state.revealed,
                        progress = (state.index + 1).toFloat() / state.cards.size,
                        voice = state.voice,
                        micPermissionGranted = micPermissionGranted,
                        onRequestMicPermission = onRequestMicPermission,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewCardContent(
    card: VocabularyCard,
    ttsStatus: TtsStatus,
    revealed: Boolean,
    progress: Float,
    voice: VoiceCheckState,
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    viewModel: ReviewViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
        Spacer(Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                card.item.word,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = viewModel::speakCurrent) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Произнести")
            }
        }
        card.item.transcription?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TtsStatusBanner(ttsStatus, modifier = Modifier.padding(top = 16.dp))

        Spacer(Modifier.height(24.dp))

        if (!revealed) {
            Text(
                "Вспомните перевод и пример употребления, затем проверьте себя.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::reveal, modifier = Modifier.fillMaxWidth()) {
                Text("Показать перевод")
            }
        } else {
            Text(card.item.translationRu, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(card.item.exampleEn, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        card.item.exampleRu,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            VoiceCheckBlock(
                word = card.item.word,
                voice = voice,
                micPermissionGranted = micPermissionGranted,
                onRequestMicPermission = onRequestMicPermission,
                onVoiceTapped = viewModel::onVoiceTapped,
                onDismiss = viewModel::dismissVoiceResult,
                onSpeak = viewModel::speak
            )

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.answer(correct = false) },
                    modifier = Modifier.weight(1f)
                ) { Text("Не помню") }
                Button(
                    onClick = { viewModel.answer(correct = true) },
                    modifier = Modifier.weight(1f)
                ) { Text("Помню") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Голосовая тренировка слова: пользователь произносит своё предложение,
 * STT его распознаёт, LLM проверяет употребление.
 */
@Composable
private fun VoiceCheckBlock(
    word: String,
    voice: VoiceCheckState,
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onVoiceTapped: () -> Unit,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = { if (micPermissionGranted) onVoiceTapped() else onRequestMicPermission() },
            modifier = Modifier.fillMaxWidth(),
            enabled = voice !is VoiceCheckState.Checking
        ) {
            when (voice) {
                is VoiceCheckState.Recording -> {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Остановить и проверить")
                }
                is VoiceCheckState.Checking -> {
                    CircularProgressIndicator(Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Проверяю…")
                }
                else -> {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сказать предложение со словом")
                }
            }
        }

        AnimatedVisibility(visible = voice is VoiceCheckState.Result) {
            (voice as? VoiceCheckState.Result)?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.ok) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                ) {
                    Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (result.ok) "Хорошо употреблено" else "Можно точнее",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Скрыть разбор")
                            }
                        }
                        Text(
                            "Услышано: ${result.heard}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (result.better.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    result.better,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onSpeak(result.better) }) {
                                    Icon(Icons.Filled.VolumeUp, contentDescription = "Произнести")
                                }
                            }
                        }
                        if (result.note.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(result.note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        (voice as? VoiceCheckState.Error)?.let {
            Text(
                it.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Text(
            "Подсказка: составьте своё предложение со словом «$word».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun FinishedState(
    hadCards: Boolean,
    correct: Int,
    wrong: Int,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (hadCards) "Сессия завершена" else "На сегодня всё повторено",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hadCards) {
                "Помню: $correct · Не помню: $wrong"
            } else {
                "Новые слова появятся, когда откроете темы курса, " +
                    "а старые вернутся к повторению по расписанию."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRestart) { Text("Ещё сессия") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack) { Text("Готово") }
    }
}
