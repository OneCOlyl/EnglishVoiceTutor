package com.example.englishvoicetutor.presentation.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.englishvoicetutor.presentation.components.TtsStatusBanner
import com.example.englishvoicetutor.data.engine.TtsStatus
import com.example.englishvoicetutor.domain.TextLayout
import com.example.englishvoicetutor.domain.model.GrammarRule

/**
 * Экран правила: объяснение по-русски, примеры «как надо / как не надо»
 * и типичная ошибка русскоязычных. Кнопка внизу просит модель дать ещё один
 * пример по той же конструкции — статика остаётся основой, LLM дополняет.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleScreen(
    onBack: () -> Unit,
    viewModel: RuleViewModel = hiltViewModel()
) {
    val rule by viewModel.rule.collectAsState()
    val extra by viewModel.extra.collectAsState()
    val ttsStatus by viewModel.ttsStatus.collectAsState()
    val data = rule

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(data?.titleRu ?: "Правило") },
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
        if (data == null) {
            EmptyLearningState(
                title = "Правило не найдено",
                subtitle = "Вернитесь к списку правил.",
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        RuleContent(
            rule = data,
            extra = extra,
            ttsStatus = ttsStatus,
            onSpeak = viewModel::speak,
            onGenerate = viewModel::generateExample,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

@Composable
private fun RuleContent(
    rule: GrammarRule,
    extra: ExtraExampleState,
    ttsStatus: TtsStatus,
    onSpeak: (String) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Суть правила — отдельной карточкой: это то, что стоит запомнить,
        // и она не должна сливаться с объяснением.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                rule.summaryRu,
                style = MaterialTheme.typography.titleSmall,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(14.dp)
            )
        }

        RuleSectionTitle("Объяснение")
        // Сплошной текст на телефоне не читается — разбиваем на абзацы
        // и даём межстрочный интервал крупнее стандартного.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextLayout.paragraphs(rule.explanationRu).forEach { paragraph ->
                Text(
                    paragraph,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        TtsStatusBanner(ttsStatus)

        RuleSectionTitle("Примеры")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rule.examples.forEach { example ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    ExampleRow(
                        example = example,
                        onSpeak = onSpeak,
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
                    )
                }
            }
        }

        rule.pitfallRu?.let { pitfall ->
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Типичная ошибка",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            pitfall,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        RuleSectionTitle("Ещё примеры от репетитора")
        (extra as? ExtraExampleState.Ready)?.let { ready ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ready.examples.forEach { example ->
                    ExampleRow(example = example, onSpeak = onSpeak)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        (extra as? ExtraExampleState.Error)?.let {
            Text(
                it.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
        }

        FilledTonalButton(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            enabled = extra !is ExtraExampleState.Loading
        ) {
            if (extra is ExtraExampleState.Loading) {
                CircularProgressIndicator(Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Придумываю пример…")
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ещё пример")
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Заголовок секции правила: тонкая линия сверху задаёт видимое деление страницы —
 * без неё объяснение, примеры и разбор сливаются в одну ленту текста.
 */
@Composable
private fun RuleSectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
    )
}
