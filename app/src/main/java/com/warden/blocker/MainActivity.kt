package com.warden.blocker

import android.app.Activity
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.warden.blocker.ui.BlocklistScreen
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

                val consentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        VpnController.start(this)
                        vm.setMasterEnabled(true)
                    }
                }

                fun toggleBlocking(enabled: Boolean) {
                    if (enabled) {
                        val consent = VpnController.consentIntent(this)
                        if (consent != null) consentLauncher.launch(consent)
                        else { VpnController.start(this); vm.setMasterEnabled(true) }
                    } else {
                        VpnController.stop(this)
                        vm.setMasterEnabled(false)
                    }
                }

                WardenScaffold(vm, ::toggleBlocking)
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
private fun WardenScaffold(vm: WardenViewModel, onToggleBlocking: (Boolean) -> Unit) {
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
            composable(Tab.HOME.route) { HomeScreen(vm, onToggleBlocking) }
            composable(Tab.BLOCKLIST.route) { BlocklistScreen(vm) }
            composable(Tab.SCHEDULES.route) { SchedulesScreen(vm) }
            composable(Tab.STATS.route) { StatsScreen() }
            composable(Tab.SETTINGS.route) { SettingsScreen(vm) }
        }
    }
}
