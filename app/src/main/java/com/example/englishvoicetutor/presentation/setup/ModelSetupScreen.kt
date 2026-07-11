package com.example.englishvoicetutor.presentation.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ModelSetupScreen(
    onModelReady: () -> Unit,
    viewModel: ModelSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var token by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("English Voice Tutor", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        when (val s = state) {
            is ModelSetupState.Idle -> {
                Text(
                    "Нужна модель Gemma 4 E2B (~2.4 ГБ).\n\n" +
                            "Для скачивания нужен бесплатный токен HuggingFace:\n" +
                            "1. Зарегистрируйтесь на huggingface.co\n" +
                            "2. Примите лицензию модели на\n" +
                            "   litert-community/gemma-4-E2B-it-litert-lm\n" +
                            "3. Создайте токен: Settings → Access Tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("HuggingFace токен") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.downloadAndLoad(token, onModelReady) },
                    enabled = token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Скачать и загрузить модель")
                }
            }

            is ModelSetupState.Downloading -> {
                Text("Скачиваем модель…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { s.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${s.downloadedMb} МБ / ${s.totalMb} МБ (${s.progressPercent}%)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Не закрывайте приложение во время загрузки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is ModelSetupState.Loading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(s.message, textAlign = TextAlign.Center)
            }

            is ModelSetupState.Error -> {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("HuggingFace токен") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.downloadAndLoad(token, onModelReady) },
                    enabled = token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Попробовать снова")
                }
            }
        }
    }
}