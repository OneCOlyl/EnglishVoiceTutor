package com.example.englishvoicetutor.presentation.learning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.englishvoicetutor.domain.model.GrammarExample
import com.example.englishvoicetutor.domain.model.VocabStatus
import com.example.englishvoicetutor.domain.model.VocabularyCard

/**
 * Карточка слова: тап раскрывает пример употребления, динамик озвучивает,
 * галочка помечает слово выученным. Используется и в теме, и в общем словаре.
 */
@Composable
fun WordCard(
    card: VocabularyCard,
    onSpeak: (String) -> Unit,
    onToggleKnown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(card.item.id) { mutableStateOf(false) }
    val isKnown = card.progress.status == VocabStatus.KNOWN

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(card.item.word, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        card.item.translationRu,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    card.item.transcription?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { onSpeak(card.item.word) }) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = "Произнести",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleKnown) {
                    Icon(
                        if (isKnown) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (isKnown) "Убрать отметку" else "Отметить выученным",
                        tint = if (isKnown) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp, end = 12.dp)) {
                    Text(
                        card.item.exampleEn,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        card.item.exampleRu,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Пример к правилу: верный вариант и, если он задан, зачёркнутый неверный.
 * Контраст «как надо / как не надо» — главный приём объяснения в этом приложении.
 */
@Composable
fun ExampleRow(
    example: GrammarExample,
    onSpeak: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(example.en, style = MaterialTheme.typography.bodyLarge)
            if (example.ru.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    example.ru,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            example.wrong?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textDecoration = TextDecoration.LineThrough,
                    fontStyle = FontStyle.Italic
                )
            }
        }
        if (onSpeak != null) {
            IconButton(onClick = { onSpeak(example.en) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "Произнести пример",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Заголовок секции внутри экрана — «Слова темы», «Правила» и т.д. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

/** Компактный вертикальный список с единым отступом между элементами. */
@Composable
fun StackedColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
internal fun LabelledValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
