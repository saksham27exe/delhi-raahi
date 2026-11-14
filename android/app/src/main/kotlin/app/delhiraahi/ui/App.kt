package app.delhiraahi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.delhiraahi.data.Settings
import app.delhiraahi.data.SettingsStore
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.i18n.Strings
import app.delhiraahi.ui.account.AccountScreen
import app.delhiraahi.ui.home.HomeScreen
import app.delhiraahi.ui.live.LiveScreen
import app.delhiraahi.ui.tickets.TicketsScreen

private data class Tab(val route: String, val icon: ImageVector, val label: (Strings) -> String)

private val tabs = listOf(
    Tab("home", Icons.Outlined.Home) { it.tabHome },
    Tab("live", Icons.Outlined.DirectionsBus) { it.tabLive },
    Tab("tickets", Icons.Outlined.ConfirmationNumber) { it.tabTickets },
    Tab("account", Icons.Outlined.AccountCircle) { it.tabAccount },
)

/** Tablet (≥ 600 dp wide): navigation rail on the left. Phone: bottom bar. */
@Composable
fun App(settings: Settings, store: SettingsStore) {
    val s = LocalStrings.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val go: (String) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val host: @Composable (Modifier) -> Unit = { m ->
        NavHost(nav, startDestination = "home", modifier = m) {
            composable("home") { HomeScreen(wide = LocalConfiguration.current.screenWidthDp >= 720) }
            composable("live") { LiveScreen() }
            composable("tickets") { TicketsScreen() }
            composable("account") { AccountScreen(settings = settings, store = store) }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { t ->
                    NavigationRailItem(
                        selected = current == t.route, onClick = { go(t.route) },
                        icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label(s)) },
                        colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
            Box(Modifier.weight(1f)) { host(Modifier.fillMaxSize()) }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route, onClick = { go(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label(s)) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
            },
        ) { pad -> host(Modifier.padding(pad)) }
    }
}
