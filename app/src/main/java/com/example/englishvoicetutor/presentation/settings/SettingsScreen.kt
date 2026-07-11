package com.example.englishvoicetutor.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.englishvoicetutor.domain.model.LlmInstallState
import com.example.englishvoicetutor.presentation.setup.ModelInstallProgress
import com.example.englishvoicetutor.presentation.setup.ModelPickerForm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
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
        }
    }
}
