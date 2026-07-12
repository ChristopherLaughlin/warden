package com.warden.blocker.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warden.blocker.data.BlockType
import com.warden.blocker.data.BlockedItem
import com.warden.blocker.data.Schedule
import com.warden.blocker.usage.UsageStatsHelper
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(vm: WardenViewModel, onToggleBlocking: (Boolean) -> Unit) {
    val enabled by vm.masterEnabled.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val activeCount = items.count { it.enabled }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Warden", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (enabled) "Blocking is ON" else "Blocking is OFF", style = MaterialTheme.typography.titleLarge)
                        Text("$activeCount item(s) protected", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = enabled, onCheckedChange = onToggleBlocking)
                }
                Text(
                    "Turning this on starts a local, private VPN that filters DNS on-device. " +
                        "No traffic leaves your phone through Warden.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text("Add sites and apps in Blocklist, or set times in Schedules.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun BlocklistScreen(vm: WardenViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Blocklist", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Website, e.g. instagram.com") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.addWebsite(input); input = "" }, modifier = Modifier.padding(start = 8.dp)) { Text("Add") }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.titleMedium)
                            Text(if (item.type == BlockType.WEBSITE) "Website" else "App", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = item.enabled, onCheckedChange = { vm.toggleItem(item) })
                        IconButton(onClick = { vm.removeItem(item) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                }
            }
        }
    }
}

@Composable
fun SchedulesScreen(vm: WardenViewModel) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Schedules", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("When enabled with 'always-on' off, the blocklist is only enforced during these windows.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        // v1: quick-add a weekday focus window (9:00–17:00). Full editor is a follow-up.
        OutlinedButton(onClick = {
            vm.addSchedule(
                Schedule(name = "Work focus", daysMask = 0b0011111, startMinute = 9 * 60, endMinute = 17 * 60),
            )
        }) { Text("+ Add weekday 9–5 focus window") }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(schedules, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            Text("${fmt(s.startMinute)}–${fmt(s.endMinute)}  •  ${daysLabel(s.daysMask)}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { vm.removeSchedule(s) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val hasPermission = remember { UsageStatsHelper.hasPermission(context) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Usage today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (!hasPermission) {
            Text("Grant usage access to see screen-time.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                Text("Grant usage access")
            }
        } else {
            val usage = remember { UsageStatsHelper.todayUsage(context).take(25) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(usage, key = { it.packageName }) { u ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(u.packageName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(fmtDuration(u.totalMillis), style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: WardenViewModel) {
    val context = LocalContext.current
    val strict by vm.strictMode.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strict mode", style = MaterialTheme.typography.titleMedium)
                    Text("Prevents turning blocking off or uninstalling while active.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = strict, onCheckedChange = { vm.setStrictMode(it) })
            }
        }

        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
            Text("App-blocking (Accessibility)")
        }
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
            Text("Usage access (screen-time)")
        }

        Spacer(Modifier.height(8.dp))
        Text("PIN protection and full schedule editing are on the roadmap.", style = MaterialTheme.typography.bodySmall)
    }
}

// --- small formatting helpers ---
private fun fmt(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

private fun daysLabel(mask: Int): String {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return names.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.joinToString(" ")
}

private fun fmtDuration(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
