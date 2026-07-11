package com.example.englishvoicetutor.presentation.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.englishvoicetutor.domain.model.LlmInstallState
import com.example.englishvoicetutor.domain.model.LlmModelOption

/**
 * Форма выбора и установки модели. Общая для экрана первичной настройки и
 * экрана настроек: список моделей + условные поля (токен HuggingFace / своя
 * ссылка) + кнопка установки.
 *
 * @param initialSelectedId какую модель выделить по умолчанию.
 * @param initialCustomUrl предзаполнить поле «своя ссылка» (последнее значение).
 */
@Composable
fun ModelPickerForm(
    models: List<LlmModelOption>,
    installButtonText: String,
    onInstall: (option: LlmModelOption, token: String, customUrl: String) -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedId: String = models.first().id,
    initialCustomUrl: String = "",
) {
    var selectedId by rememberSaveable { mutableStateOf(initialSelectedId) }
    var token by rememberSaveable { mutableStateOf("") }
    var customUrl by rememberSaveable { mutableStateOf(initialCustomUrl) }

    val selected = models.firstOrNull { it.id == selectedId } ?: models.first()

    val canInstall = when {
        selected.requiresToken && token.isBlank() -> false
        selected.isCustom && customUrl.isBlank() -> false
        else -> true
    }

    Column(modifier = modifier) {
        models.forEach { model ->
            ModelRow(
                model = model,
                selected = model.id == selected.id,
                onSelect = { selectedId = model.id }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (selected.isCustom) {
            OutlinedTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = { Text("Прямая ссылка на .litertlm") },
                placeholder = { Text("https://…/model.litertlm") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        if (selected.requiresToken) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("HuggingFace токен") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { onInstall(selected, token, customUrl.trim()) },
            enabled = canInstall,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(installButtonText)
        }
    }
}

@Composable
private fun ModelRow(
    model: LlmModelOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Индикатор процесса установки (скачивание / загрузка в память).
 * Возвращает true, если состояние «активное» и форму выбора показывать не нужно.
 */
@Composable
fun ModelInstallProgress(state: LlmInstallState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            is LlmInstallState.Downloading -> {
                Text("Скачиваем модель…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { state.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${state.downloadedMb} МБ / ${state.totalMb} МБ (${state.progressPercent}%)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Не закрывайте приложение во время загрузки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            is LlmInstallState.Loading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(state.message, textAlign = TextAlign.Center)
            }

            else -> Unit
        }
    }
}
