package com.warden.blocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.warden.blocker.data.Schedule

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/** Create/edit dialog for a [Schedule]: name, days, and start/end times. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorDialog(initial: Schedule?, onSave: (Schedule) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var daysMask by remember { mutableIntStateOf(initial?.daysMask ?: 0b0011111) }
    var start by remember { mutableIntStateOf(initial?.startMinute ?: 9 * 60) }
    var end by remember { mutableIntStateOf(initial?.endMinute ?: 17 * 60) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end" | null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && daysMask != 0,
                onClick = {
                    onSave(
                        (initial ?: Schedule(name = "", daysMask = 0, startMinute = 0, endMinute = 0)).copy(
                            name = name.trim(), daysMask = daysMask, startMinute = start, endMinute = end,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (initial == null) "New schedule" else "Edit schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Days", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LETTERS.forEachIndexed { i, letter ->
                        FilterChip(
                            selected = (daysMask shr i) and 1 == 1,
                            onClick = { daysMask = daysMask xor (1 shl i) },
                            label = { Text(letter) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.weight(1f)) {
                        Text("Start ${fmtTime(start)}")
                    }
                    OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.weight(1f)) {
                        Text("End ${fmtTime(end)}")
                    }
                }
                if (end <= start) {
                    Text("Ends next day (overnight window)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )

    picking?.let { which ->
        val minutes = if (which == "start") start else end
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    val v = state.hour * 60 + state.minute
                    if (which == "start") start = v else end = v
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

private fun fmtTime(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
