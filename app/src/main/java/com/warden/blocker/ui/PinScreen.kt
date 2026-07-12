package com.warden.blocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val PIN_LEN = 4

/** Enter-and-confirm dialog for setting a new PIN. */
@Composable
fun SetPinDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun onComplete(pin: String) {
        if (first == null) {
            first = pin; entry = ""; error = null
        } else if (first == pin) {
            onDone(pin); onDismiss()
        } else {
            first = null; entry = ""; error = "PINs didn't match — start again"
        }
    }

    PinDialog(
        title = if (first == null) "Set a PIN" else "Confirm your PIN",
        subtitle = error,
        entry = entry,
        onDigit = { if (entry.length < PIN_LEN) { entry += it; if (entry.length == PIN_LEN) onComplete(entry) } },
        onBackspace = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
        onDismiss = onDismiss,
    )
}

/** Verify dialog; [verify] runs off the DB and returns true on match. */
@Composable
fun VerifyPinDialog(
    title: String,
    verify: suspend (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    PinDialog(
        title = title,
        subtitle = error,
        entry = entry,
        onDigit = {
            if (entry.length < PIN_LEN) {
                entry += it
                if (entry.length == PIN_LEN) {
                    val attempt = entry
                    scope.launch {
                        if (verify(attempt)) onSuccess()
                        else { entry = ""; error = "Wrong PIN" }
                    }
                }
            }
        },
        onBackspace = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun PinDialog(
    title: String,
    subtitle: String?,
    entry: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.size(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(PIN_LEN) { i ->
                        Surface(
                            shape = CircleShape,
                            color = if (i < entry.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(16.dp),
                        ) {}
                    }
                }
                Spacer(Modifier.size(20.dp))
                PinPad(onDigit = onDigit, onBackspace = onBackspace)
            }
        },
    )
}

@Composable
private fun PinPad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row {
                row.forEach { key ->
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        when (key) {
                            "" -> Spacer(Modifier.width(64.dp))
                            "⌫" -> TextButton(onClick = onBackspace) { Text("⌫", style = MaterialTheme.typography.headlineSmall) }
                            else -> TextButton(onClick = { onDigit(key) }) {
                                Text(key, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
