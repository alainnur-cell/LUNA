package com.gomobile.luna.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gomobile.luna.data.Broadcast
import com.gomobile.luna.ui.fmtDateTime

@Composable
fun BroadcastsScreen(
    broadcasts: List<Broadcast>,
    lastSeen: Long,
    isAdmin: Boolean,
    busy: Boolean,
    onSend: (String, String, () -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onSeen: () -> Unit,
) {
    var composing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    // Opening this tab marks everything as read; capture the old value so new items stay highlighted.
    val highlightAfter by rememberSaveable { mutableStateOf(lastSeen) }
    LaunchedEffect(broadcasts) { onSeen() }

    Box(Modifier.fillMaxSize()) {
        if (broadcasts.isEmpty()) {
            Text(
                if (isAdmin) "No broadcasts yet. Tap “New broadcast” to message the whole team."
                else "No messages from the admin yet.",
                Modifier.padding(24.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(broadcasts, key = { it.id }) { b ->
                val isNew = (b.createdAt?.time ?: 0L) > highlightAfter
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNew) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (isAdmin) {
                                IconButton(onClick = { deleting = b.id }) { Icon(Icons.Filled.DeleteOutline, "Delete") }
                            }
                        }
                        Text(
                            "${b.author} · ${b.createdAt.fmtDateTime()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(b.body, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (isAdmin) {
            ExtendedFloatingActionButton(
                onClick = { composing = true },
                icon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                text = { Text("New broadcast") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (composing) {
        ComposeDialog(busy = busy, onDismiss = { composing = false }) { title, body ->
            onSend(title, body) { composing = false }
        }
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this broadcast?") },
            text = { Text("It will disappear from everyone's app.") },
            confirmButton = { TextButton(onClick = { onDelete(id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ComposeDialog(busy: Boolean, onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message the team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSend(title, body) }, enabled = !busy) { Text("Send to all") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
