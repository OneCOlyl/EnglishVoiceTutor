package com.example.englishvoicetutor.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.englishvoicetutor.data.engine.TtsStatus

/**
 * Состояние озвучки: загрузка голоса или сообщение о том, что она недоступна.
 * Нужно потому, что иначе кнопка «произнести» просто молчит, и это выглядит
 * как поломка приложения. В готовом состоянии ничего не рисует.
 *
 * Общий для всех разделов, где есть озвучка: курс, словарь, повторение,
 * правила и сам диалог.
 */
@Composable
fun TtsStatusBanner(status: TtsStatus, modifier: Modifier = Modifier) {
    when (status) {
        is TtsStatus.Downloading -> VoiceDownloadBanner(status, modifier)
        is TtsStatus.Unavailable -> VoiceUnavailableBanner(status, modifier)
        else -> Unit
    }
}

/**
 * Разовая загрузка голоса (~75 МБ). Показываем прогресс там же, где стоит кнопка
 * озвучки: пользователь нажал «произнести» и должен понимать, чего он ждёт.
 */
@Composable
private fun VoiceDownloadBanner(status: TtsStatus.Downloading, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Скачиваем голос для озвучки — это один раз",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { status.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                status.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun VoiceUnavailableBanner(status: TtsStatus.Unavailable, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.VolumeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    status.reason,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Голос скачивается один раз и дальше работает офлайн. " +
                        "Проверьте подключение к сети и попробуйте озвучить ещё раз — " +
                        "загрузка продолжится. Остальные разделы работают и без озвучки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
