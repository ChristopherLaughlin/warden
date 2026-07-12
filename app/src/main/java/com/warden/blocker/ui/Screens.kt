package com.warden.blocker.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warden.blocker.data.BlockType
import com.warden.blocker.data.BlockedItem
import com.warden.blocker.data.InterceptMode
import com.warden.blocker.data.Schedule
import com.warden.blocker.system.AdminManager
import com.warden.blocker.usage.UsageStatsHelper
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    vm: WardenViewModel,
    onToggleBlocking: (Boolean) -> Unit,
    onStartFocus: (Int, Boolean) -> Unit,
    onCancelFocus: () -> Unit,
) {
    val enabled by vm.masterEnabled.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val streak by vm.currentStreak.collectAsStateWithLifecycle()
    val focusEndsAt by vm.focusEndsAt.collectAsStateWithLifecycle()
    val focusStrict by vm.focusStrict.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val activeCount = items.count { it.enabled }
    var showVerify by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Warden", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (enabled) "Blocking is ON" else "Blocking is OFF", style = MaterialTheme.typography.titleLarge)
                        Text("$activeCount item(s) protected", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { turnOn ->
                            // Disabling while a PIN is set requires the PIN (strict-mode friction).
                            if (!turnOn && hasPin) showVerify = true else onToggleBlocking(turnOn)
                        },
                    )
                }
                Text(
                    "Turning this on starts a local, private VPN that filters DNS on-device. " +
                        "No traffic leaves your phone through Warden.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        FocusCard(focusEndsAt = focusEndsAt, strict = focusStrict, onStart = onStartFocus, onCancel = onCancelFocus)
        if (streak > 0) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(0.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("$streak-day focus streak", style = MaterialTheme.typography.titleLarge)
                        Text("Keep Warden on each day to grow it.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text("Add sites and apps in Blocklist, or set times in Schedules.", style = MaterialTheme.typography.bodyMedium)
    }

    if (showVerify) {
        VerifyPinDialog(
            title = "Enter PIN to stop blocking",
            verify = vm::verifyPin,
            onSuccess = { showVerify = false; onToggleBlocking(false) },
            onDismiss = { showVerify = false },
        )
    }
}

@Composable
fun BlocklistScreen(vm: WardenViewModel, onOpenAppPicker: () -> Unit, onOpenFeatures: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<BlockedItem?>(null) }

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
            Button(onClick = { vm.addWebsite(input); input = "" }, modifier = Modifier.padding(start = 8.dp)) { Text("Add") }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onOpenAppPicker, modifier = Modifier.fillMaxWidth()) { Text("+ Add apps to block") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenFeatures, modifier = Modifier.fillMaxWidth()) { Text("Block in-app feeds (Reels, Shorts…)") }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable { editing = item }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.titleMedium)
                            Text(itemSummary(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(checked = item.enabled, onCheckedChange = { vm.toggleItem(item) })
                        IconButton(onClick = { vm.removeItem(item) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                }
            }
        }
    }

    editing?.let { current ->
        ItemEditorSheet(
            item = current,
            onSave = { vm.saveItem(it) },
            onDismiss = { editing = null },
        )
    }
}

private fun itemSummary(item: BlockedItem): String {
    if (item.type == BlockType.WEBSITE) return "Website · hard block"
    return when (item.interceptMode) {
        InterceptMode.BLOCK -> "App · hard block"
        InterceptMode.PAUSE -> buildString {
            append("App · pause ${item.pauseSeconds}s")
            if (item.openLimitPerDay > 0) append(" · ${item.openLimitPerDay}/day")
            if (item.dailyLimitMinutes > 0) append(" · ${item.dailyLimitMinutes}m limit")
        }
    }
}

@Composable
fun SchedulesScreen(vm: WardenViewModel) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Schedules", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("With 'always-on' off, the blocklist is only enforced during these windows.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("+ New schedule") }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(schedules, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth().clickable { editing = s }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            Text("${fmt(s.startMinute)}–${fmt(s.endMinute)}  •  ${daysLabel(s.daysMask)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = s.enabled, onCheckedChange = { vm.addSchedule(s.copy(enabled = it)) })
                        IconButton(onClick = { vm.removeSchedule(s) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                }
            }
        }
    }

    if (creating) {
        ScheduleEditorDialog(initial = null, onSave = { vm.addSchedule(it) }, onDismiss = { creating = false })
    }
    editing?.let { current ->
        ScheduleEditorDialog(initial = current, onSave = { vm.addSchedule(it) }, onDismiss = { editing = null })
    }
}

@Composable
fun StatsScreen(vm: WardenViewModel) {
    val context = LocalContext.current
    val hasPermission = remember { UsageStatsHelper.hasPermission(context) }
    val streak by vm.currentStreak.collectAsStateWithLifecycle()
    val longest by vm.longestStreak.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Your wins", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Current streak", "$streak d", Modifier.weight(1f))
            StatCard("Longest streak", "$longest d", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Text("Screen time today", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
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
    val alwaysOn by vm.alwaysOn.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()

    var unlocked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var showSetPin by remember { mutableStateOf(false) }
    var showRemovePin by remember { mutableStateOf(false) }
    var adminActive by remember { mutableStateOf(AdminManager.isActive(context)) }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        adminActive = AdminManager.isActive(context)
    }

    // PIN gate: settings are locked until the PIN is entered.
    if (hasPin && !unlocked) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Settings are locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { showUnlock = true }) { Text("Unlock with PIN") }
        }
        if (showUnlock) {
            VerifyPinDialog("Enter PIN", vm::verifyPin, onSuccess = { unlocked = true; showUnlock = false }, onDismiss = { showUnlock = false })
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // Blocking mode: always-on vs schedule-driven
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Always on", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (alwaysOn) "Blocking is enforced whenever it's switched on." else "Blocking is enforced only during your schedules.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = alwaysOn, onCheckedChange = { vm.setAlwaysOn(it) })
            }
        }

        // Strict mode
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strict mode", style = MaterialTheme.typography.titleMedium)
                    Text("Locks the master switch while a block is active. Pair with a PIN + uninstall protection.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = strict, onCheckedChange = { on ->
                    vm.setStrictMode(on)
                    if (on && !adminActive) adminLauncher.launch(AdminManager.enableIntent(context))
                })
            }
        }

        // Uninstall protection (device admin)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Uninstall protection", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (adminActive) "On — Warden can't be uninstalled until you turn this off." else "Off — Warden can be uninstalled anytime.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                if (adminActive) {
                    OutlinedButton(
                        onClick = { if (!strict) { AdminManager.disable(context); adminActive = AdminManager.isActive(context) } },
                        enabled = !strict,
                    ) { Text(if (strict) "Turn off strict mode first" else "Turn off protection") }
                } else {
                    Button(onClick = { adminLauncher.launch(AdminManager.enableIntent(context)) }) { Text("Turn on protection") }
                }
            }
        }

        // PIN
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("PIN lock", style = MaterialTheme.typography.titleMedium)
                Text(if (hasPin) "A PIN is required to change settings or stop blocking." else "Protect settings and disabling with a 4-digit PIN.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showSetPin = true }) { Text(if (hasPin) "Change PIN" else "Set PIN") }
                    if (hasPin) OutlinedButton(onClick = { showRemovePin = true }) { Text("Remove PIN") }
                }
            }
        }

        HorizontalDivider()
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
            Text("App-blocking (Accessibility)")
        }
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
            Text("Usage access (screen-time)")
        }
    }

    if (showSetPin) {
        SetPinDialog(onDone = { vm.setPin(it) }, onDismiss = { showSetPin = false })
    }
    if (showRemovePin) {
        VerifyPinDialog("Enter PIN to remove", vm::verifyPin, onSuccess = { vm.clearPin(); showRemovePin = false }, onDismiss = { showRemovePin = false })
    }
}

@Composable
private fun FocusCard(focusEndsAt: Long, strict: Boolean, onStart: (Int, Boolean) -> Unit, onCancel: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(focusEndsAt) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val active = focusEndsAt > now

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (active) {
                Text("Focus session", style = MaterialTheme.typography.titleMedium)
                Text(formatCountdown(focusEndsAt - now), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    if (strict) "Strict — this can't be ended early. Deep work, locked in." else "Blocking stays on until the timer ends.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!strict) OutlinedButton(onClick = onCancel) { Text("End session") }
            } else {
                Text("Start a focus session", style = MaterialTheme.typography.titleMedium)
                Text("Force blocking on for a set time — great for deep work.", style = MaterialTheme.typography.bodySmall)
                var strictChoice by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = strictChoice, onCheckedChange = { strictChoice = it })
                    Text("Strict (no early exit)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50).forEach { m ->
                        Button(onClick = { onStart(m, strictChoice) }) { Text("$m min") }
                    }
                }
            }
        }
    }
}

private fun formatCountdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
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
