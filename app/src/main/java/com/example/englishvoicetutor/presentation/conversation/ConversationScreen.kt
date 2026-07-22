package com.example.englishvoicetutor.presentation.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.input.ImeAction
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                onTextSubmit = viewModel::onSpeechResult,
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
    onTextSubmit: (String) -> Unit,
    modelState: ModelDownloadState,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
            StatusLine(voiceState)
            InputBar(
                voiceState = voiceState,
                micPermissionGranted = micPermissionGranted,
                onRequestMicPermission = onRequestMicPermission,
                onMicTapped = onMicTapped,
                onTextSubmit = onTextSubmit
            )
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
private fun InputBar(
    voiceState: VoiceUiState,
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onMicTapped: () -> Unit,
    onTextSubmit: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val busy = voiceState is VoiceUiState.Transcribing || voiceState is VoiceUiState.Thinking
    val recording = voiceState is VoiceUiState.Recording
    val hasText = text.isNotBlank()

    fun submit() {
        if (hasText) {
            onTextSubmit(text.trim())
            text = ""
        }
    }

    val fabContainer = when {
        busy -> MaterialTheme.colorScheme.surfaceContainerHighest
        recording -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val fabContent = when {
        busy -> MaterialTheme.colorScheme.onSurfaceVariant
        recording -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Введите сообщение…") },
                enabled = !busy && !recording,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
            FloatingActionButton(
                onClick = {
                    when {
                        busy -> {}
                        hasText -> submit()
                        !micPermissionGranted -> onRequestMicPermission()
                        else -> onMicTapped()
                    }
                },
                modifier = Modifier.size(52.dp),
                containerColor = fabContainer,
                contentColor = fabContent,
                elevation = FloatingActionButtonDefaults.elevation(2.dp, 4.dp, 2.dp, 2.dp)
            ) {
                when {
                    busy -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    hasText -> Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                    recording -> Icon(Icons.Filled.Stop, contentDescription = "Остановить")
                    else -> Icon(Icons.Filled.Mic, contentDescription = "Говорить")
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
    val isError = voiceState is VoiceUiState.Error
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.role == MessageRole.USER
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        val shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = if (isUser) 20.dp else 6.dp,
            bottomEnd = if (isUser) 6.dp else 20.dp
        )
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .shadow(
                    elevation = if (isUser) 3.dp else 1.dp,
                    shape = shape,
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = MaterialTheme.colorScheme.primary
                )
                .clip(shape)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}