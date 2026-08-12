package com.medcards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.material3.Card as M3Card

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: Store) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var defaultSubject by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            message = if (text == null) {
                "Could not read that file."
            } else {
                val r = CsvIo.import(text, store, defaultSubject)
                r.error ?: "Imported ${r.notes} notes → ${r.cards} cards (${r.skipped} rows skipped)."
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(CsvIo.export(store.cards.toList()).toByteArray())
                }
            }.isSuccess
            message = if (ok) "Exported ${store.cards.size} cards." else "Export failed."
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item { SectionHeader("Scheduling (FSRS-5)") }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Desired retention")
                    Text(
                        String.format(Locale.US, "%.0f%%", store.settings.desiredRetention * 100),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = store.settings.desiredRetention.toFloat(),
                    onValueChange = {
                        store.settings = store.settings.copy(desiredRetention = it.toDouble())
                    },
                    onValueChangeFinished = { store.save() },
                    valueRange = 0.80f..0.97f
                )
                Text(
                    "Higher retention = shorter intervals and more reviews. 90% is a good default.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Stepper("Session size", store.settings.sessionSize, 5, 5, 200) {
                    store.settings = store.settings.copy(sessionSize = it); store.save()
                }
                Stepper("Integrated session size", store.settings.topicSessionSize, 1, 4, 60) {
                    store.settings = store.settings.copy(topicSessionSize = it); store.save()
                }
                Stepper("New cards per session", store.settings.newCardsPerDay, 5, 0, 100) {
                    store.settings = store.settings.copy(newCardsPerDay = it); store.save()
                }
            }

            item { SectionHeader("Import / export") }

            item {
                OutlinedTextField(
                    value = defaultSubject,
                    onValueChange = { defaultSubject = it },
                    label = { Text("Default subject for rows without one") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("  Import CSV")
                    }
                    OutlinedButton(onClick = { exportLauncher.launch("medcards-export.csv") }) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Text("  Export")
                    }
                }
                Text(
                    "CSV columns: front, back, subject, topic, tags (tags separated by ;). " +
                            "Cloze markup in 'front' is expanded automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (message.isNotBlank()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2E9E5B),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            item { SectionHeader("Topics") }

            item {
                Text(
                    "Tap a topic to rename it. Renaming onto an existing name merges the two — " +
                            "that is how you link cards from different subjects into one session.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(store.topics) { t ->
                M3Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { renaming = t.name; newName = t.name }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                t.subjects.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${t.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionHeader("Library") }

            item {
                LabeledRow("Cards", "${store.cards.size}")
                LabeledRow("Subjects", "${store.subjects.size}")
                LabeledRow("Topics", "${store.topics.size}")
                LabeledRow("Reviews logged", "${store.logs.size}")
                Box(modifier = Modifier.height(40.dp))
            }
        }
    }

    val target = renaming
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename topic") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) store.renameTopic(target, trimmed)
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    step: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onChange((value - step).coerceAtLeast(min)) },
                enabled = value > min
            ) { Text("−") }
            OutlinedButton(
                onClick = { onChange((value + step).coerceAtMost(max)) },
                enabled = value < max
            ) { Text("+") }
        }
    }
}
