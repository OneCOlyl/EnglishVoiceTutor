package com.example.englishvoicetutor.presentation.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.englishvoicetutor.domain.model.ReplyReview

/**
 * Итоговый разбор диалога: что учащийся сказал, как сказать лучше и в чём была ошибка.
 * Открывается после завершения разговора («goodbye») или из шапки экрана диалога.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionReviewScreen(
    onBack: () -> Unit,
    viewModel: SessionReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scenario by viewModel.scenario.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Разбор диалога", style = MaterialTheme.typography.titleMedium)
                        scenario?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
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
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)

        when (val current = state) {
            is SessionReviewState.Running -> ReviewProgress(current, contentModifier)
            is SessionReviewState.Empty -> CenteredMessage(
                title = "Разбирать нечего",
                subtitle = "В этом диалоге вы ещё ничего не сказали.",
                modifier = contentModifier
            )
            is SessionReviewState.Error -> CenteredMessage(
                title = "Не получилось",
                subtitle = current.message,
                modifier = contentModifier,
                action = { Button(onClick = viewModel::retry) { Text("Попробовать снова") } }
            )
            is SessionReviewState.Ready -> ReviewList(current.reviews, contentModifier)
        }
    }
}

@Composable
private fun ReviewProgress(state: SessionReviewState.Running, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Проверяю ваши реплики…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${state.done} из ${state.total}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            CircularProgressIndicator()
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Модель работает на устройстве, поэтому разбор занимает время.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReviewList(reviews: List<ReplyReview>, modifier: Modifier = Modifier) {
    val mistakes = reviews.count { it.hasMistake }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SummaryCard(total = reviews.size, mistakes = mistakes) }
        items(reviews) { review -> ReviewCard(review) }
    }
}

@Composable
private fun SummaryCard(total: Int, mistakes: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (mistakes == 0) "Ошибок не нашлось" else "Есть что поправить",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Проверено реплик: $total · с замечаниями: $mistakes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ReviewCard(review: ReplyReview) {
    val accent = if (review.hasMistake) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (review.hasMistake) Icons.Filled.EditNote else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (review.hasMistake) "Вы сказали" else "Всё верно",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(review.original, style = MaterialTheme.typography.bodyLarge)

            if (review.hasMistake && review.better.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Как лучше",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(review.better, style = MaterialTheme.typography.bodyLarge)
            }

            if (review.note.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    review.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
