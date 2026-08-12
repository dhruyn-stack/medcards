package com.medcards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.material3.Card as M3Card

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(store: Store) {
    var range by remember { mutableStateOf(30) }

    Scaffold(topBar = { TopAppBar(title = { Text("Stats") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatPill("${store.currentStreak}", "Day streak", Color(0xFFD9418C), Modifier.weight(1f))
                    StatPill("${store.reviewsToday}", "Today", Color(0xFFE07B00), Modifier.weight(1f))
                    StatPill(
                        String.format(Locale.US, "%.0f%%", store.retentionRate * 100),
                        "Retention", Color(0xFF2E9E5B), Modifier.weight(1f)
                    )
                }
            }

            item { SectionHeader("Reviews") }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { r ->
                        SelectableChip("$r days", range == r) { range = r }
                    }
                }
            }

            item {
                val data = store.reviewsPerDay(range)
                BarChart(
                    values = data.map { it.second },
                    color = Color(0xFF1F6FEB),
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    "${data.sumOf { it.second }} reviews in the last $range days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { SectionHeader("Upcoming load") }

            item {
                val f = store.forecast()
                BarChart(values = f.map { it.second }, color = Color(0xFF00897B))
                Text(
                    "Cards falling due over the next 14 days.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { SectionHeader("By subject") }

            items(store.subjects) { s ->
                val st = store.statsFor(s)
                M3Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(s, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${st.mature} mature / ${st.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionHeader("Topic coverage") }

            items(store.topics.filter { it.isIntegrated }) { t ->
                M3Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(t.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${t.subjects.size} subjects · ${t.total} cards · ${t.subjects.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Box(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun BarChart(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        values.forEach { v ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(if (v == 0) 0.02f else v.toFloat() / max)
                    .background(
                        if (v == 0) color.copy(alpha = 0.15f) else color,
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
