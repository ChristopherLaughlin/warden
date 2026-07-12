package com.warden.blocker

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.warden.blocker.ui.AppPickerScreen
import com.warden.blocker.ui.BlocklistScreen
import com.warden.blocker.ui.FeaturesScreen
import com.warden.blocker.ui.HomeScreen
import com.warden.blocker.ui.SchedulesScreen
import com.warden.blocker.ui.SettingsScreen
import com.warden.blocker.ui.StatsScreen
import com.warden.blocker.ui.WardenViewModel
import com.warden.blocker.ui.theme.WardenTheme
import com.warden.blocker.vpn.VpnController

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WardenTheme {
                val vm: WardenViewModel = viewModel()

                val notifPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* result ignored; the app still works without it */ }
                LaunchedEffect(Unit) {
                    vm.onAppOpened()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                var pendingAfterConsent by remember { mutableStateOf<(() -> Unit)?>(null) }
                val consentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) pendingAfterConsent?.invoke()
                    pendingAfterConsent = null
                }

                fun withVpn(action: () -> Unit) {
                    val consent = VpnController.consentIntent(this@MainActivity)
                    if (consent == null) action() else { pendingAfterConsent = action; consentLauncher.launch(consent) }
                }

                fun toggleBlocking(enabled: Boolean) {
                    if (enabled) withVpn { VpnController.start(this@MainActivity); vm.setMasterEnabled(true) }
                    else { VpnController.stop(this@MainActivity); vm.setMasterEnabled(false) }
                }

                fun startFocus(minutes: Int, strict: Boolean) {
                    vm.startFocus(minutes, strict)
                    withVpn { VpnController.start(this@MainActivity) }
                }

                fun cancelFocus() {
                    vm.cancelFocus()
                    if (!vm.masterEnabled.value) VpnController.stop(this@MainActivity)
                }

                WardenScaffold(vm, ::toggleBlocking, ::startFocus, ::cancelFocus)
            }
        }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    BLOCKLIST("blocklist", "Blocklist", Icons.Filled.Block),
    SCHEDULES("schedules", "Schedules", Icons.Filled.Schedule),
    STATS("stats", "Usage", Icons.Filled.Timeline),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
private fun WardenScaffold(
    vm: WardenViewModel,
    onToggleBlocking: (Boolean) -> Unit,
    onStartFocus: (Int, Boolean) -> Unit,
    onCancelFocus: () -> Unit,
) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val current by navController.currentBackStackEntryAsState()
                val dest = current?.destination
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = dest?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) { HomeScreen(vm, onToggleBlocking, onStartFocus, onCancelFocus) }
            composable(Tab.BLOCKLIST.route) {
                BlocklistScreen(
                    vm,
                    onOpenAppPicker = { navController.navigate("apppicker") },
                    onOpenFeatures = { navController.navigate("features") },
                )
            }
            composable("apppicker") { AppPickerScreen(vm) }
            composable("features") { FeaturesScreen(vm) }
            composable(Tab.SCHEDULES.route) { SchedulesScreen(vm) }
            composable(Tab.STATS.route) { StatsScreen(vm) }
            composable(Tab.SETTINGS.route) { SettingsScreen(vm) }
        }
    }
}
