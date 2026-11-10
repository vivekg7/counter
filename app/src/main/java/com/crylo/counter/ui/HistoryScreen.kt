package com.crylo.counter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crylo.counter.R
import com.crylo.counter.core.DailyTotal
import com.crylo.counter.core.HistoryStats
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val CHART_DAYS = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val counter by remember(id) { vm.counter(id) }.collectAsStateWithLifecycle(null)
    val entries by remember(id) { vm.history(id) }.collectAsStateWithLifecycle(null)
    val today = remember { LocalDate.now().toEpochDay() }
    val accent = accentOf(counter?.color ?: 0xFF888888.toInt())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.history_title, counter?.name.orEmpty()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val list = entries ?: return@Scaffold
        val summary = remember(list, today) { HistoryStats.summarize(list, today) }
        val days = remember(list, today) { HistoryStats.lastDays(list, today, CHART_DAYS) }
        val log = remember(list) { list.filter { it.total > 0 || it.rounds > 0 } }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val daysText = pluralStringResource(R.plurals.days, summary.streak, summary.streak)
                val stats = listOf(
                    stringResource(R.string.today) to format(summary.today),
                    stringResource(R.string.last_7_days) to format(summary.last7Days),
                    stringResource(R.string.last_30_days) to format(summary.last30Days),
                    stringResource(R.string.all_time) to format(summary.allTime),
                    stringResource(R.string.best_day) to (summary.bestDay?.let { format(it.total) } ?: "–"),
                    stringResource(R.string.streak) to daysText,
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    stats.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { (label, value) -> StatCard(label, value, Modifier.weight(1f)) }
                        }
                    }
                    if (summary.allTimeRounds > 0) {
                        StatCard(stringResource(R.string.total_rounds), format(summary.allTimeRounds.toLong()), Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.last_30_days), style = MaterialTheme.typography.titleSmall)
                        Bars(days, accent, Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.daily_log),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (log.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_history),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(log, key = { it.epochDay }) { day -> LogRow(day, today) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** Daily totals as bars, oldest on the left; today is drawn in full colour. */
@Composable
private fun Bars(days: List<DailyTotal>, accent: Color, modifier: Modifier) {
    val max = days.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    val description = stringResource(R.string.chart_description, format(max))
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.semantics { contentDescription = description }) {
        val slot = size.width / days.size
        val barWidth = slot * 0.7f
        val radius = CornerRadius(barWidth / 3, barWidth / 3)
        days.forEachIndexed { i, day ->
            val h = size.height * day.total / max
            val color = if (i == days.lastIndex) accent else accent.copy(alpha = 0.55f)
            if (day.total > 0) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = radius,
                )
            }
        }
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun LogRow(day: DailyTotal, today: Long) {
    val date = if (day.epochDay == today) {
        stringResource(R.string.today)
    } else {
        LocalDate.ofEpochDay(day.epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(date, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (day.rounds > 0) {
                Text(
                    pluralStringResource(R.plurals.rounds, day.rounds, day.rounds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
            Text(format(day.total), style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
    }
}

private fun format(n: Long): String = NumberFormat.getIntegerInstance().format(n)
