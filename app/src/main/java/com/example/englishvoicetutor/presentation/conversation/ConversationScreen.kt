package com.example.englishvoicetutor.presentation.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.englishvoicetutor.domain.model.CefrLevel
import com.example.englishvoicetutor.domain.model.Message
import com.example.englishvoicetutor.domain.model.MessageRole
import com.example.englishvoicetutor.domain.model.ModelDownloadState
import com.example.englishvoicetutor.domain.model.VoiceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val conversationId by viewModel.conversationId.collectAsState()
    val meta by viewModel.conversationMeta.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val modelState by viewModel.modelDownloadState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meta?.scenario ?: "New conversation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (conversationId == null) {
            NewConversationForm(
                modifier = Modifier.padding(padding),
                scenario = viewModel.scenarioInput,
                level = viewModel.levelInput,
                onScenarioChange = viewModel::updateScenario,
                onLevelChange = viewModel::updateLevel,
                onStart = viewModel::startNewConversation
            )
        } else {
            ActiveConversation(
                modifier = Modifier.padding(padding),
                messages = messages,
                voiceState = voiceState,
                micPermissionGranted = micPermissionGranted,
                onRequestMicPermission = onRequestMicPermission,
                onMicTapped = viewModel::onMicTapped,
                modelState = modelState
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewConversationForm(
    modifier: Modifier = Modifier,
    scenario: String,
    level: CefrLevel,
    onScenarioChange: (String) -> Unit,
    onLevelChange: (CefrLevel) -> Unit,
    onStart: () -> Unit
) {
    var levelMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("О чём хотите поговорить?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = scenario,
            onValueChange = onScenarioChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Сценарий, например: ordering coffee") }
        )
        Spacer(Modifier.height(16.dp))
        ExposedDropdownMenuBox(
            expanded = levelMenuExpanded,
            onExpandedChange = { levelMenuExpanded = it }
        ) {
            OutlinedTextField(
                value = level.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Ваш уровень") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelMenuExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
            )
            DropdownMenu(
                expanded = levelMenuExpanded,
                onDismissRequest = { levelMenuExpanded = false }
            ) {
                CefrLevel.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.label) },
                        onClick = { onLevelChange(entry); levelMenuExpanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Начать разговор")
        }
    }
}

@Composable
private fun ActiveConversation(
    modifier: Modifier = Modifier,
    messages: List<Message>,
    voiceState: VoiceUiState,
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onMicTapped: () -> Unit,
    modelState: ModelDownloadState,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
            StatusLine(voiceState)
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val busy = voiceState !is VoiceUiState.Idle && voiceState !is VoiceUiState.Error
                FloatingActionButton(
                    onClick = { if (!micPermissionGranted) onRequestMicPermission() else onMicTapped() },
                    containerColor = if (busy) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primaryContainer
                ) {
                    if (voiceState is VoiceUiState.Thinking) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    } else {
                        Icon(
                            imageVector = if (voiceState is VoiceUiState.Recording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (voiceState is VoiceUiState.Recording) "Остановить" else "Говорить"
                        )
                    }
                }
            }
        }
        if (modelState !is ModelDownloadState.Ready && modelState !is ModelDownloadState.Idle) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (modelState) {
                        is ModelDownloadState.Downloading -> {
                            Text(
                                "Загрузка модели распознавания речи",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { modelState.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("${modelState.progressPercent}%")
                            Spacer(Modifier.height(16.dp))
                            // Лог последних 5 строк
                            modelState.logs.takeLast(5).forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is ModelDownloadState.Error -> {
                            Text(
                                "Ошибка: ${modelState.message}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(voiceState: VoiceUiState) {
    val text = when (voiceState) {
        is VoiceUiState.Idle -> "Нажмите на микрофон и говорите по-английски"
        is VoiceUiState.Listening -> "Слушаю…"
        is VoiceUiState.Transcribing -> "Распознаю речь…"
        is VoiceUiState.Thinking -> "Репетитор думает…"
        is VoiceUiState.Recording -> "Говорите… Нажмите снова чтобы остановить"
        is VoiceUiState.Speaking -> "Репетитор отвечает…"
        is VoiceUiState.Error -> "Ошибка: ${voiceState.message}"
    }
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun MessageBubble(message: Message) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.role == MessageRole.USER) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.85f)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (message.role == MessageRole.USER) "Вы" else "Репетитор",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(text = message.text)
            }
        }
    }
}