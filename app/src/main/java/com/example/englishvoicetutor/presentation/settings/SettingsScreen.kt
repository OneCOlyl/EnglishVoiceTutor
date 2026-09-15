package com.example.englishvoicetutor.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.englishvoicetutor.data.engine.TtsStatus
import com.example.englishvoicetutor.domain.model.LlmInstallState
import com.example.englishvoicetutor.domain.model.VoiceCatalog
import com.example.englishvoicetutor.domain.model.VoiceOption
import com.example.englishvoicetutor.presentation.components.TtsStatusBanner
import com.example.englishvoicetutor.presentation.setup.ModelInstallProgress
import com.example.englishvoicetutor.presentation.setup.ModelPickerForm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val speakerId by viewModel.speakerId.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val ttsStatus by viewModel.ttsStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            viewModel.installedModelLabel?.let { label ->
                Text(
                    "Сейчас установлена: $label",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            when (val s = state) {
                is LlmInstallState.Idle,
                is LlmInstallState.Ready,
                is LlmInstallState.Error -> {
                    if (s is LlmInstallState.Error) {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    if (s is LlmInstallState.Ready) {
                        Text(
                            "Модель обновлена и загружена.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    ModelPickerForm(
                        models = viewModel.models,
                        installButtonText = "Сменить модель",
                        onInstall = viewModel::install,
                        initialSelectedId = viewModel.installedModelId
                            ?: viewModel.models.first().id,
                        initialCustomUrl = viewModel.lastCustomUrl,
                    )
                }

                is LlmInstallState.Downloading,
                is LlmInstallState.Loading -> {
                    ModelInstallProgress(state = s)
                }
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            VoiceSection(
                voices = viewModel.voices,
                selectedId = speakerId,
                speed = speed,
                ttsStatus = ttsStatus,
                onSelect = viewModel::selectVoice,
                onSpeedChange = viewModel::setSpeed,
                onPreview = viewModel::previewVoice,
            )
        }
    }
}

/**
 * Выбор диктора и скорости речи.
 * Дикторы модели анонимные (номера говорящих из LibriTTS-R), поэтому выбор —
 * на слух: тап по варианту сразу применяет его и проговаривает пробную фразу.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSection(
    voices: List<VoiceOption>,
    selectedId: Int,
    speed: Float,
    ttsStatus: TtsStatus,
    onSelect: (VoiceOption) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPreview: () -> Unit,
) {
    Text("Голос озвучки", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Выбранный вариант применяется сразу и проговаривает пробную фразу. " +
            "Голоса различаются заметно — послушайте несколько.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))
    TtsStatusBanner(ttsStatus)

    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        voices.forEach { voice ->
            FilterChip(
                selected = voice.id == selectedId,
                onClick = { onSelect(voice) },
                label = { Text(voice.label) },
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    Text(
        "Скорость речи: ${"%.1f".format(speed)}×",
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = speed,
        onValueChange = onSpeedChange,
        onValueChangeFinished = onPreview,
        valueRange = VoiceCatalog.MIN_SPEED..VoiceCatalog.MAX_SPEED,
        // Шаг 0.1 на диапазоне 0.6–1.4 — восемь интервалов.
        steps = 7,
    )
    Text(
        "Медленнее — разборчивее для новичка, быстрее — ближе к живой речи.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = onPreview) {
        Icon(Icons.Filled.VolumeUp, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Прослушать")
    }
    Spacer(Modifier.height(24.dp))
}
