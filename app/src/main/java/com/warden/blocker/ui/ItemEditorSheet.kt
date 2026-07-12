package com.warden.blocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warden.blocker.data.BlockType
import com.warden.blocker.data.BlockedItem
import com.warden.blocker.data.InterceptMode

/**
 * Bottom-sheet editor for a blocked item's enforcement: hard block vs mindful pause, and
 * (for pause) the wait, the prompt, and the daily open/time/cooldown limits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorSheet(item: BlockedItem, onSave: (BlockedItem) -> Unit, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(item.interceptMode) }
    var pauseSeconds by remember { mutableStateOf(item.pauseSeconds) }
    var prompt by remember { mutableStateOf(item.customPrompt ?: "") }
    var openLimit by remember { mutableStateOf(item.openLimitPerDay) }
    var timeLimit by remember { mutableStateOf(item.dailyLimitMinutes) }
    var cooldown by remember { mutableStateOf(item.cooldownMinutes) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(item.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Text("When opened", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == InterceptMode.PAUSE, onClick = { mode = InterceptMode.PAUSE }, label = { Text("Mindful pause") })
                FilterChip(selected = mode == InterceptMode.BLOCK, onClick = { mode = InterceptMode.BLOCK }, label = { Text("Hard block") })
            }

            if (mode == InterceptMode.PAUSE && item.type == BlockType.APP) {
                Stepper("Breathe for (seconds)", pauseSeconds, step = 5, min = 0, max = 120) { pauseSeconds = it }
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text("Pause prompt (optional)") },
                    placeholder = { Text("Why are you opening this?") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Stepper("Opens per day (0 = unlimited)", openLimit, step = 1, min = 0, max = 30) { openLimit = it }
                Stepper("Daily time limit, min (0 = off)", timeLimit, step = 5, min = 0, max = 240) { timeLimit = it }
                Stepper("Cooldown between opens, min", cooldown, step = 5, min = 0, max = 240) { cooldown = it }
            } else if (mode == InterceptMode.PAUSE) {
                Text(
                    "Mindful pause applies to apps. Websites are hard-blocked at the network layer.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onSave(
                        item.copy(
                            interceptMode = mode,
                            pauseSeconds = pauseSeconds,
                            customPrompt = prompt.ifBlank { null },
                            openLimitPerDay = openLimit,
                            dailyLimitMinutes = timeLimit,
                            cooldownMinutes = cooldown,
                        ),
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, step: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) { Text("−") }
        Spacer(Modifier.width(8.dp))
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(40.dp))
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = { onChange((value + step).coerceAtMost(max)) }) { Text("+") }
    }
}
