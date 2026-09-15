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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.englishvoicetutor.domain.model.CefrLevel

/**
 * Вкладка «Правила» — справочник по всей грамматике курса.
 * Правила те же, что внутри тем; здесь они собраны в одном месте с поиском,
 * чтобы можно было быстро освежить конкретную конструкцию.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarScreen(
    onOpenRule: (String) -> Unit,
    viewModel: GrammarViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsState()
    val query by viewModel.query.collectAsState()
    val grouped = rules.groupBy { it.level }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Правила") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Поиск по правилам") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            if (rules.isEmpty()) {
                EmptyLearningState(
                    title = "Ничего не найдено",
                    subtitle = "Попробуйте другой запрос."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CefrLevel.entries.forEach { level ->
                        val levelRules = grouped[level].orEmpty()
                        if (levelRules.isEmpty()) return@forEach
                        item(key = "header-${level.name}") {
                            Text(
                                level.label,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                            )
                        }
                        items(levelRules, key = { it.rule.id }) { item ->
                            GrammarRuleCard(item = item, onClick = { onOpenRule(item.rule.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrammarRuleCard(item: RuleListItem, onClick: () -> Unit) {
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
                Text(item.rule.titleRu, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    item.rule.summaryRu,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.topicTitleRu,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
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
