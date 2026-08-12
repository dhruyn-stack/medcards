package com.medcards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card as M3Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: Store,
    onStart: (String, List<Card>) -> Unit,
    onNewCard: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("MedCards") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewCard) {
                Icon(Icons.Filled.Add, contentDescription = "New card")
            }
        }
    ) { padding ->
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
                    StatPill("${store.dueCount()}", "Due", Color(0xFF1F6FEB), Modifier.weight(1f))
                    StatPill("${store.newCount()}", "New", Color(0xFF2E9E5B), Modifier.weight(1f))
                    StatPill("${store.reviewsToday}", "Today", Color(0xFFE07B00), Modifier.weight(1f))
                    StatPill("${store.currentStreak}", "Streak", Color(0xFFD9418C), Modifier.weight(1f))
                }
            }

            item {
                val queue = store.buildQueue()
                Button(
                    onClick = { onStart("Review", store.buildQueue()) },
                    enabled = queue.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("  Study everything due (${queue.size})")
                }
            }

            item { SectionHeader("By subject") }

            if (store.subjects.isEmpty()) {
                item {
                    Text(
                        "No cards yet. Tap + to add one, or import a CSV from Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(store.subjects) { subject ->
                val s = store.statsFor(subject)
                val enabled = s.due + s.new > 0
                M3Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(enabled = enabled) {
                            onStart(subject, store.buildQueue(subject))
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(subject, fontWeight = FontWeight.Medium)
                            Text(
                                "${s.total} cards · ${s.mature} mature",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CountBadge(s.due, Color(0xFF1F6FEB))
                            CountBadge(s.new, Color(0xFF2E9E5B))
                        }
                    }
                }
            }

            item { Column(modifier = Modifier.padding(bottom = 80.dp)) {} }
        }
    }
}
