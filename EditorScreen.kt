package com.medcards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    store: Store,
    card: Card?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isNew = card == null

    var front by remember { mutableStateOf(card?.front ?: "") }
    var back by remember { mutableStateOf(card?.back ?: "") }
    var subject by remember { mutableStateOf(card?.subject ?: "") }
    var topic by remember { mutableStateOf(card?.topic ?: "") }
    var tagText by remember { mutableStateOf(card?.tags?.joinToString(", ") ?: "") }
    var suspended by remember { mutableStateOf(card?.suspended ?: false) }
    var keepAdding by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    val imageNames = remember { mutableStateListOf<String>().apply { addAll(card?.imageNames ?: emptyList()) } }

    val clozeCount = Cloze.indices(front).size

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    store.saveImage(input)?.let { imageNames.add(it) }
                }
            }
        }
    }

    fun parsedTags(): List<String> =
        tagText.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun save() {
        if (front.isBlank()) return
        if (card == null) {
            val made = store.addNote(front, back, subject, topic, parsedTags(), imageNames.toList())
            if (keepAdding) {
                status = "Saved $made card${if (made == 1) "" else "s"}"
                front = ""
                back = ""
                imageNames.clear()
            } else {
                onClose()
            }
        } else {
            val newIndices = Cloze.indices(front)
            val clozeIndex = card.clozeIndex?.let {
                if (newIndices.contains(it)) it else newIndices.firstOrNull()
            }
            store.upsert(
                card.copy(
                    front = front,
                    back = back,
                    subject = subject,
                    topic = topic,
                    tags = parsedTags(),
                    imageNames = imageNames.toList(),
                    suspended = suspended,
                    clozeIndex = clozeIndex
                )
            )
            onClose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New card" else "Edit card") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = front.isNotBlank()) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionHeader("Front / question")
            OutlinedTextField(
                value = front,
                onValueChange = { front = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder = { Text("Question, or text with {{c1::cloze}} blanks") }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { front += " {{c${clozeCount + 1}::}}" }) {
                    Icon(Icons.Filled.ContentCut, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Text("  Add cloze")
                }
                if (clozeCount > 0) {
                    Text(
                        "  $clozeCount cloze → $clozeCount cards",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8E24AA)
                    )
                }
            }

            SectionHeader("Back / answer")
            OutlinedTextField(
                value = back,
                onValueChange = { back = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            SectionHeader("Filing")
            SuggestField(
                label = "Subject",
                value = subject,
                onValueChange = { subject = it },
                options = (store.subjects + Mbbs.subjects).distinct()
            )
            SuggestField(
                label = "Topic",
                value = topic,
                onValueChange = { topic = it },
                options = store.topics.map { it.name }
            )
            Text(
                "Use the SAME topic name across subjects — that is what links cards into one " +
                        "integrated session.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedTextField(
                value = tagText,
                onValueChange = { tagText = it },
                label = { Text("Tags (comma separated)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            SectionHeader("Images")
            if (imageNames.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    imageNames.toList().forEach { name ->
                        val bmp = remember(name) { store.loadImage(name) }
                        Box {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(96.dp)
                                )
                            }
                            Icon(
                                Icons.Filled.Cancel,
                                contentDescription = "Remove",
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .clickable {
                                        imageNames.remove(name)
                                        store.deleteImage(name)
                                    }
                            )
                        }
                    }
                }
            }
            OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Text("  Add image")
            }

            if (card != null) {
                SectionHeader("Scheduling")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Suspended")
                    Switch(checked = suspended, onCheckedChange = { suspended = it })
                }
                LabeledRow("State", card.state.name.lowercase())
                LabeledRow("Due", formatDate(card.due))
                LabeledRow("Stability", String.format(Locale.US, "%.1f d", card.stability))
                LabeledRow("Difficulty", String.format(Locale.US, "%.1f / 10", card.difficulty))
                LabeledRow("Reviews", "${card.reps} · ${card.lapses} lapses")

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { store.resetProgress(card); onClose() }) {
                        Text("Reset progress")
                    }
                    OutlinedButton(onClick = { store.delete(card); onClose() }) {
                        Text("Delete", color = Color(0xFFD32F2F))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keep adding after save")
                    Switch(checked = keepAdding, onCheckedChange = { keepAdding = it })
                }
                Button(
                    onClick = { save() },
                    enabled = front.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text("Save card")
                }
                if (status.isNotBlank()) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2E9E5B),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Box(modifier = Modifier.height(60.dp))
        }
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

/** Text field with a dropdown of existing values, so names stay consistent. */
@Composable
private fun SuggestField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            trailingIcon = {
                if (options.isNotEmpty()) {
                    IconButton(onClick = { open = true }) {
                        Icon(Icons.Filled.ExpandMore, contentDescription = "Suggestions")
                    }
                }
            }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o) },
                    onClick = { onValueChange(o); open = false }
                )
            }
        }
    }
}
