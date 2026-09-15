package com.example.englishvoicetutor.presentation.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.englishvoicetutor.presentation.components.TtsStatusBanner
import com.example.englishvoicetutor.domain.model.GrammarRule

/**
 * Экран темы курса. Сводит вместе всё, что относится к теме: цель, слова,
 * правила и запуск голосового диалога именно по этой теме.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    onBack: () -> Unit,
    onStartConversation: (String) -> Unit,
    onOpenRule: (String) -> Unit,
    viewModel: TopicViewModel = hiltViewModel()
) {
    val topic by viewModel.topic.collectAsState()
    val cards by viewModel.cards.collectAsState()
    val ttsStatus by viewModel.ttsStatus.collectAsState()
    val data = topic

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(data?.topic?.titleRu ?: "Тема") },
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
                title = "Тема не найдена",
                subtitle = "Похоже, курс обновился — вернитесь к списку тем.",
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column {
                    Text(
                        data.topic.goalRu,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${data.topic.level.label} · выучено ${data.wordsKnown} из ${data.wordsTotal} слов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onStartConversation(data.topic.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Говорить по теме", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Репетитор будет вести разговор по сценарию темы и подмешивать её слова.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { SectionTitle("Слова темы") }
            item { TtsStatusBanner(ttsStatus) }
            items(cards, key = { it.item.id }) { card ->
                WordCard(
                    card = card,
                    onSpeak = viewModel::speak,
                    onToggleKnown = { viewModel.toggleKnown(card) }
                )
            }

            item { SectionTitle("Правила") }
            items(data.topic.rules, key = { it.id }) { rule ->
                RuleRow(rule = rule, onClick = { onOpenRule(rule.id) })
            }
        }
    }
}

@Composable
private fun RuleRow(rule: GrammarRule, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.titleRu, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    rule.summaryRu,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
