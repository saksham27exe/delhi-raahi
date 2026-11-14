package app.delhiraahi.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Api
import app.delhiraahi.data.ApiException
import app.delhiraahi.data.Arrival
import app.delhiraahi.data.ArrivalsBoard
import app.delhiraahi.data.Lang
import app.delhiraahi.data.StopDetail
import app.delhiraahi.data.Vehicle
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.BusMap
import app.delhiraahi.ui.common.Chip
import app.delhiraahi.ui.common.FloatingIconButton
import app.delhiraahi.ui.common.MapController
import app.delhiraahi.ui.common.MapPin
import app.delhiraahi.ui.common.MapSheet
import app.delhiraahi.ui.common.RoutePill
import app.delhiraahi.ui.common.routeName
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Space
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val PEEK = 0.56f

/** Next buses at one stop: live estimate or timetable, refreshed every 30 s; optional route filter. */
@Composable
fun StopScreen(stopId: String, routeFilter: String?, lang: Lang, onBack: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val screenH = LocalConfiguration.current.screenHeightDp
    val map = remember { MapController() }
    var stop by remember { mutableStateOf<StopDetail?>(null) }
    var board by remember { mutableStateOf<ArrivalsBoard?>(null) }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var filter by remember { mutableStateOf(routeFilter) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(stopId) {
        stop = runCatching { Api.stop(stopId) }.getOrNull()
        stop?.let { map.moveTo(it.lat, it.lon, 15.0) }
    }

    LaunchedEffect(stopId, filter, stop, tick) {
        while (true) {
            runCatching { Api.arrivals(stopId) }
                .onSuccess { board = it; error = null }
                .onFailure { error = if (it is ApiException && it.status == 0) s.errNetwork else it.message }
            stop?.let { st ->
                val d = 0.04
                vehicles = runCatching {
                    Api.vehicles(routeId = filter, bbox = if (filter == null) "${st.lat - d},${st.lon - d},${st.lat + d},${st.lon + d}" else null).vehicles.filter { !it.stale }
                }.getOrDefault(emptyList())
            }
            delay(30_000)
        }
    }

    val arrivals = board?.arrivals.orEmpty()
    val shown = arrivals.filter { filter == null || it.route_id == filter }
    val routes = arrivals.map { it.route_id to it.route }.distinctBy { it.first }
    val pins = remember(stop, vehicles) {
        listOfNotNull(stop?.let { MapPin("stop", it.lat, it.lon, it.name, MapPin.Kind.TO) }) +
            vehicles.map { MapPin(it.vehicle_id, it.lat, it.lon, routeName(it.route ?: ""), MapPin.Kind.BUS) }
    }

    Box(Modifier.fillMaxSize()) {
        BusMap(Modifier.fillMaxSize(), map, pins, t.dark, (screenH * PEEK).dp, showMyLocation = true)
        Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(Space.lg)) {
            FloatingIconButton(Icons.AutoMirrored.Outlined.ArrowBack, s.back, onClick = onBack)
        }
        MapSheet(
            peek = PEEK, full = 0.9f,
            header = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stop?.name ?: board?.stop ?: "…", style = MaterialTheme.typography.headlineSmall, color = t.text, maxLines = 2,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = { tick++ }) { Icon(Icons.Outlined.Refresh, contentDescription = s.refresh, tint = t.text) }
                }
                board?.let { b ->
                    val c = if (b.live_feed) t.low else t.moderate
                    Text(if (b.live_feed) s.liveOn else s.liveOff, color = c.fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = Space.xs).clip(RoundedCornerShape(6.dp)).background(c.bg).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                if (routes.size > 1) Row(Modifier.padding(top = Space.sm).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Chip(s.allBuses, selected = filter == null) { filter = null }
                    routes.forEach { (rid, r) -> Chip(routeName(r), selected = filter == rid) { filter = rid } }
                }
            },
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.xl)) {
                // Hidden summary for screen readers (bilingual, from the server).
                board?.summary?.get(if (lang == Lang.HI) "hi" else "en")?.let { sm -> Box(Modifier.size(1.dp).semantics { contentDescription = sm }) }
                if (error != null) Text(error!!, color = t.veryHigh.fg, modifier = Modifier.padding(vertical = Space.md))
                if (board == null && error == null) CircularProgressIndicator(Modifier.padding(Space.lg).size(22.dp), strokeWidth = 2.dp, color = t.text)
                if (board != null && shown.isEmpty()) Text(s.noArrivals, color = t.muted, modifier = Modifier.padding(vertical = Space.lg))
                shown.forEach { a -> ArrivalRow(a) }
            }
        }
    }
}

@Composable
private fun ArrivalRow(a: Arrival) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val m = a.eta_min.coerceAtLeast(0.0).roundToInt()
    val d = a.delay_min?.roundToInt()
    val live = a.basis != "scheduled"
    val whenText = if (m == 0) s.nowArriving else s.inMin(m)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Space.md)
            .semantics(mergeDescendants = true) { contentDescription = "${routeName(a.route)}, ${s.towards(a.towards)}, $whenText, ${s.basis(a.basis)}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        RoutePill(a.route)
        Column(Modifier.weight(1f)) {
            Text(s.towards(a.towards), style = MaterialTheme.typography.bodyMedium, color = t.text, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (live) Box(Modifier.size(8.dp).clip(CircleShape).background(t.low.line))
                Text(s.basis(a.basis), style = MaterialTheme.typography.labelSmall, color = if (live) t.low.fg else t.muted, fontWeight = if (live) FontWeight.Bold else FontWeight.Medium)
                if (d != null) Text(
                    "· " + when { d > 1 -> s.late(d); d < -1 -> s.early(-d); else -> s.onTime },
                    style = MaterialTheme.typography.labelSmall, color = t.muted,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(whenText, style = MaterialTheme.typography.titleMedium, color = t.text)
            Text(a.eta, style = MaterialTheme.typography.labelSmall, color = t.muted)
        }
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 0.5.dp, max = 0.5.dp).background(t.border))
}
