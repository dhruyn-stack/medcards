package com.medcards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun ratingColor(r: Rating): Color = when (r) {
    Rating.AGAIN -> Color(0xFFD32F2F)
    Rating.HARD -> Color(0xFFE07B00)
    Rating.GOOD -> Color(0xFF1F6FEB)
    Rating.EASY -> Color(0xFF2E9E5B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    store: Store,
    session: Session,
    onEditCard: (Card) -> Unit,
    onClose: () -> Unit
) {
    var position by remember { mutableStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var answered by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    // Always read the freshest copy of the card from the store.
    val current: Card? = session.cards.getOrNull(position)?.let { queued ->
        store.cards.firstOrNull { it.id == queued.id } ?: queued
    }

    fun advance() {
        revealed = false
        position += 1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit card") },
                                onClick = { menuOpen = false; onEditCard(current) }
                            )
                            DropdownMenuItem(
                                text = { Text("Suspend card") },
                                onClick = {
                                    menuOpen = false
                                    store.upsert(current.copy(suspended = true))
                                    advance()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete card") },
                                onClick = {
                                    menuOpen = false
                                    store.delete(current)
                                    advance()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (current == null) {
                FinishedPanel(answered, onClose)
            } else {
                Column(modifier = Modifier.fillMaxSize()) {

                    LinearProgressIndicator(
                        progress = position.toFloat() / session.cards.size.coerceAtLeast(1),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TagChip(current.subject, Color(0xFF4A46C4))
                            TagChip(current.topic, Color(0xFF00897B))
                            Box(modifier = Modifier.weight(1f))
                            Text(
                                "${position + 1}/${session.cards.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            current.renderedFront,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        CardImages(store, current.imageNames)

                        if (revealed) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                            Text(current.renderedBack, style = MaterialTheme.typography.bodyLarge)
                            if (current.tags.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.padding(top = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    current.tags.forEach { TagChip(it, Color(0xFF6B7280)) }
                                }
                            }
                        }
                    }

                    // Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp)
                    ) {
                        if (!revealed) {
                            Button(
                                onClick = { revealed = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Show answer")
                            }
                        } else {
                            val previews = store.previews(current)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Rating.values().forEach { r ->
                                    val color = ratingColor(r)
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                store.answer(current, r)
                                                answered += 1
                                                advance()
                                            }
                                            .padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(r.label, color = color, fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge)
                                        Text(previews[r] ?: "", color = color,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinishedPanel(answered: Int, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Session complete", style = MaterialTheme.typography.headlineSmall)
        Text(
            "$answered card${if (answered == 1) "" else "s"} reviewed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        Button(onClick = onClose) { Text("Done") }
    }
}

@Composable
fun CardImages(store: Store, names: List<String>) {
    if (names.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        names.forEach { name ->
            val bitmap = remember(name) { store.loadImage(name) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.heightIn(max = 240.dp)
                )
            }
        }
    }
}
