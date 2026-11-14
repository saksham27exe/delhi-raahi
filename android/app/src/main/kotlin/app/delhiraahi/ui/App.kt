package app.delhiraahi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.delhiraahi.data.Lang
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import android.net.Uri
import app.delhiraahi.data.TicketStore
import app.delhiraahi.data.PlacesStore
import app.delhiraahi.data.Places
import app.delhiraahi.data.Slot
import app.delhiraahi.data.Load
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import app.delhiraahi.ui.home.PickPlaceScreen
import app.delhiraahi.ui.insights.InsightsScreen
import app.delhiraahi.ui.live.StopScreen
import app.delhiraahi.ui.tickets.BookScreen
import app.delhiraahi.ui.tickets.TicketScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import app.delhiraahi.data.TripViewModel
import app.delhiraahi.ui.trip.JourneyScreen
import app.delhiraahi.ui.trip.ResultsScreen
import app.delhiraahi.ui.trip.SearchScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.delhiraahi.data.Settings
import app.delhiraahi.data.SettingsStore
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.i18n.Strings
import app.delhiraahi.ui.account.AccountScreen
import app.delhiraahi.ui.home.HomeScreen
import app.delhiraahi.ui.live.LiveScreen
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.tickets.TicketsScreen

private data class Tab(val route: String, val icon: ImageVector, val iconOn: ImageVector, val label: (Strings) -> String)

private val tabs = listOf(
    Tab("home", Icons.Outlined.Home, Icons.Filled.Home) { it.tabHome },
    Tab("live", Icons.Outlined.Sensors, Icons.Filled.Sensors) { it.tabLive },
    Tab("tickets", Icons.Outlined.ConfirmationNumber, Icons.Filled.ConfirmationNumber) { it.tabTickets },
    Tab("account", Icons.Outlined.Person, Icons.Filled.Person) { it.tabAccount },
)

/** Bottom tab bar on every screen size, like the Expo app: no indicator pill, active = text colour. */
@Composable
fun App(settings: Settings, store: SettingsStore) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val trip: TripViewModel = viewModel()
    val ctx = LocalContext.current
    val tickets = remember { TicketStore(ctx.applicationContext) }
    val placesStore = remember { PlacesStore(ctx.applicationContext) }
    val places by placesStore.places.collectAsState(initial = Places())
    // Remember each successful search (on this device only).
    LaunchedEffect(trip.result) {
        val to = trip.to
        if (trip.result is Load.Ok && to != null) placesStore.addRecent(trip.from, to)
    }
    val openStop: (String, String?) -> Unit = { id, route ->
        nav.navigate("stop/${Uri.encode(id)}" + (route?.let { "?route=${Uri.encode(it)}" } ?: ""))
    }
    val showTabs = current == null || tabs.any { it.route == current }
    val go: (String) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Column(Modifier.fillMaxSize().background(t.bg)) {
        NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
            composable("home") {
                HomeScreen(
                    lang = settings.lang, places = places,
                    onToggleLang = { scope.launch { store.setLang(if (settings.lang == Lang.EN) Lang.HI else Lang.EN) } },
                    onSetPlace = { nav.navigate("pickplace/${it.name}") },
                    onWhereTo = { trip.to = null; nav.navigate("search") },
                    onGo = { from, to ->
                        trip.from = from; trip.to = to
                        if (from != null) { trip.search(); nav.navigate("results") } else nav.navigate("search")
                    },
                    onClearRecent = { scope.launch { placesStore.clearRecent() } },
                    onInsights = { nav.navigate("insights") },
                )
            }
            composable("live") { LiveScreen(onOpenStop = { openStop(it, null) }) }
            composable("tickets") {
                TicketsScreen(tickets, settings.lang, onOpen = { nav.navigate("ticket/${Uri.encode(it)}") }, onPlan = { nav.navigate("search") })
            }
            composable("ticket/{id}") { e ->
                TicketScreen(tickets, e.arguments?.getString("id").orEmpty(), settings.lang, onBack = { nav.popBackStack() })
            }
            composable("stop/{id}?route={route}", arguments = listOf(navArgument("route") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
                StopScreen(e.arguments?.getString("id").orEmpty(), e.arguments?.getString("route"), settings.lang, onBack = { nav.popBackStack() })
            }
            dialog("book/{idx}", arguments = listOf(navArgument("idx") { type = NavType.IntType }),
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { e ->
                BookScreen(trip, e.arguments?.getInt("idx") ?: 0, tickets, onClose = { nav.popBackStack() }, onBooked = { tk ->
                    nav.navigate("ticket/${Uri.encode(tk.ticket_id)}") { popUpTo("home") }
                })
            }
            composable("account") {
                AccountScreen(settings = settings, store = store, places = places,
                    onSetPlace = { nav.navigate("pickplace/${it.name}") },
                    onRemovePlace = { scope.launch { placesStore.set(it, null) } },
                    onInsights = { nav.navigate("insights") })
            }
            composable("insights") { InsightsScreen(settings.lang, onBack = { nav.popBackStack() }) }
            composable("pickplace/{slot}") { e ->
                val slot = runCatching { Slot.valueOf(e.arguments?.getString("slot").orEmpty()) }.getOrDefault(Slot.HOME)
                PickPlaceScreen(slot, placesStore, onDone = { nav.popBackStack() })
            }
            composable("search") {
                SearchScreen(trip, onBack = { nav.popBackStack() }, onDone = {
                    nav.navigate("results") { popUpTo("search") { inclusive = true } }
                })
            }
            composable("results") {
                ResultsScreen(trip, onBack = { nav.popBackStack() }, onOpen = { i -> nav.navigate("journey/$i") })
            }
            composable("journey/{idx}", arguments = listOf(navArgument("idx") { type = NavType.IntType })) { e ->
                val idx = e.arguments?.getInt("idx") ?: 0
                JourneyScreen(trip, idx, settings.lang, onBack = { nav.popBackStack() },
                    onBook = { nav.navigate("book/$idx") }, onTrack = { stop, route -> openStop(stop, route) })
            }
        }
        if (showTabs) {
        HorizontalDivider(thickness = 0.5.dp, color = t.border)
        Row(Modifier.fillMaxWidth().background(t.card).windowInsetsPadding(WindowInsets.navigationBars).height(64.dp)) {
            tabs.forEach { tab ->
                val on = current == tab.route
                val color = if (on) t.text else t.muted
                Column(
                    Modifier.weight(1f).fillMaxSize().selectable(selected = on, role = Role.Tab, onClick = { go(tab.route) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (on) tab.iconOn else tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Text(tab.label(s), color = color, style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        }
    }
}
