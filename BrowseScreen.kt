package com.medcards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Card as M3Card

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    store: Store,
    onEdit: (Card) -> Unit,
    onNewCard: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var subjectFilter by remember { mutableStateOf<String?>(null) }
    var topicFilter by remember { mutableStateOf<String?>(null) }
    var topicMenu by remember { mutableStateOf(false) }

    val filtered = store.cards.filter { c ->
        if (subjectFilter != null && c.subject != subjectFilter) return@filter false
        if (topicFilter != null && c.topic != topicFilter) return@filter false
        if (search.isNotBlank()) {
            val hay = c.front + " " + c.back + " " + c.tags.joinToString(" ")
            if (!hay.contains(search, ignoreCase = true)) return@filter false
        }
        true
    }.sortedWith(
        compareBy<Card> { Mbbs.order(it.subject) }
            .thenBy { it.topic }
            .thenBy { it.createdAt }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse") },
                actions = {
                    IconButton(onClick = { topicMenu = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Filter by topic")
                    }
                    DropdownMenu(expanded = topicMenu, onDismissRequest = { topicMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All topics") },
                            onClick = { topicFilter = null; topicMenu = false }
                        )
                        store.topics.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name) },
                                onClick = { topicFilter = t.name; topicMenu = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewCard) {
                Icon(Icons.Filled.Add, contentDescription = "New card")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search cards") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectableChip("All", subjectFilter == null) { subjectFilter = null }
                store.subjects.forEach { s ->
                    SelectableChip(s, subjectFilter == s) {
                        subjectFilter = if (subjectFilter == s) null else s
                    }
                }
            }

            if (topicFilter != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    TagChip("Topic: $topicFilter", Color(0xFF00897B))
                    Text(
                        "  clear",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { topicFilter = null }
                    )
                }
            }

            Text(
                "${filtered.size} cards",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { card ->
                    M3Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onEdit(card) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                card.renderedFront,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TagChip(card.subject, Color(0xFF4A46C4))
                                TagChip(card.topic, Color(0xFF00897B))
                                if (card.clozeIndex != null) {
                                    TagChip("c${card.clozeIndex}", Color(0xFF8E24AA))
                                }
                                if (card.suspended) TagChip("suspended", Color(0xFF6B7280))
                            }
                            Text(
                                stateLabel(card),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                item { Column(modifier = Modifier.padding(bottom = 90.dp)) {} }
            }
        }
    }
}

private fun stateLabel(card: Card): String = when (card.state) {
    CardState.NEW -> "new"
    CardState.LEARNING -> "learning"
    CardState.REVIEW -> {
        val delta = card.due - System.currentTimeMillis()
        if (delta <= 0) "due now" else "in " + Fsrs.humanInterval(delta)
    }
}
