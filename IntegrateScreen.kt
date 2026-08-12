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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Card as M3Card

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrateScreen(
    store: Store,
    onStart: (String, List<Card>) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectMode by remember { mutableStateOf(false) }
    val selection = remember { mutableStateListOf<String>() }

    val all = store.topics
    val filtered = if (search.isBlank()) all
    else all.filter { it.name.contains(search, ignoreCase = true) }

    val integrated = filtered.filter { it.isIntegrated }
    val single = filtered.filter { !it.isIntegrated }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Integrate") },
                actions = {
                    TextButton(onClick = {
                        selectMode = !selectMode
                        if (!selectMode) selection.clear()
                    }) {
                        Text(if (selectMode) "Done" else "Select")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search topics") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            if (selection.isNotEmpty()) {
                item {
                    Button(
                        onClick = {
                            val cards = store.buildMultiTopicQueue(selection.toList())
                            onStart("${selection.size} topics", cards)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text("Review ${selection.size} topics together")
                    }
                }
            }

            item { SectionHeader("Cross-subject topics") }

            if (integrated.isEmpty()) {
                item {
                    Text(
                        "No integrated topics yet. Give cards from two or more subjects the " +
                                "same Topic name — for example tag your Physiology, Pathology and " +
                                "Pharmacology cards all as \"Diabetes Mellitus\" — and they show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(integrated) { t ->
                TopicRow(t, selectMode, selection.contains(t.name)) {
                    if (selectMode) {
                        if (selection.contains(t.name)) selection.remove(t.name)
                        else selection.add(t.name)
                    } else {
                        onStart(t.name, store.buildTopicQueue(t.name))
                    }
                }
            }

            if (integrated.isNotEmpty()) {
                item {
                    Text(
                        "Sessions interleave subjects so you meet the same concept from every " +
                                "angle, capped at ${store.settings.topicSessionSize} cards.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (single.isNotEmpty()) {
                item { SectionHeader("Single-subject topics") }
                items(single) { t ->
                    TopicRow(t, selectMode, selection.contains(t.name)) {
                        if (selectMode) {
                            if (selection.contains(t.name)) selection.remove(t.name)
                            else selection.add(t.name)
                        } else {
                            onStart(t.name, store.buildTopicQueue(t.name))
                        }
                    }
                }
            }

            item { Column(modifier = Modifier.padding(bottom = 40.dp)) {} }
        }
    }
}

@Composable
private fun TopicRow(
    topic: Store.TopicInfo,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    M3Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle
                    else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(topic.name, fontWeight = FontWeight.Medium)
                Text(
                    topic.subjects.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    CountBadge(topic.due, Color(0xFF1F6FEB))
                    CountBadge(topic.new, Color(0xFF2E9E5B))
                }
                Text(
                    "${topic.total} cards",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
