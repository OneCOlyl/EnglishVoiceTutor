package com.example.englishvoicetutor.presentation.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.englishvoicetutor.domain.model.LlmInstallState

@Composable
fun ModelSetupScreen(
    onModelReady: () -> Unit,
    viewModel: ModelSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is LlmInstallState.Ready) onModelReady()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("English Voice Tutor", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        when (val s = state) {
            is LlmInstallState.Idle,
            is LlmInstallState.Ready,
            is LlmInstallState.Error -> {
                if (s is LlmInstallState.Error) {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    "Выберите модель. Для «Своей ссылки» токен не нужен — подойдёт " +
                            "любой прямой URL на файл .litertlm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                ModelPickerForm(
                    models = viewModel.models,
                    installButtonText = "Скачать и загрузить модель",
                    onInstall = viewModel::install,
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
