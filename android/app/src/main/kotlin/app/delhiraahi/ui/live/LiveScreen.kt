package app.delhiraahi.ui.live

import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.delhiraahi.data.Api
import app.delhiraahi.data.DeviceLocation
import app.delhiraahi.data.NearbyStop
import app.delhiraahi.data.StopHit
import app.delhiraahi.data.Vehicle
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.BusMap
import app.delhiraahi.ui.common.FloatingIconButton
import app.delhiraahi.ui.common.ListRow
import app.delhiraahi.ui.common.MapController
import app.delhiraahi.ui.common.MapPin
import app.delhiraahi.ui.common.MapSheet
import app.delhiraahi.ui.common.routeName
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay

private const val MIN_ZOOM = 11.5

/** Live buses for the visible map area (refreshed every 30 s), stop search and nearby stops. */
@Composable
fun LiveScreen(onOpenStop: (String) -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val ctx = LocalContext.current
    val screenH = LocalConfiguration.current.screenHeightDp
    val map = remember { MapController() }
    var bbox by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableDoubleStateOf(12.0) }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var live by remember { mutableStateOf<Boolean?>(null) }
    var failed by remember { mutableStateOf(false) }
    var me by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var nearby by remember { mutableStateOf<List<NearbyStop>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<StopHit>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }        // tapped bus
    var detail by remember { mutableStateOf<app.delhiraahi.data.VehicleDetail?>(null) }
    val lt = app.delhiraahi.voice.LiveText.of(LocalStrings.current === app.delhiraahi.i18n.Hi)

    LaunchedEffect(Unit) {
        val loc = DeviceLocation.current(ctx)
        if (loc == null) { map.moveTo(DELHI_LAT, DELHI_LON, 13.0); return@LaunchedEffect }
        me = loc.latitude to loc.longitude
        nearby = runCatching { Api.nearbyStops(loc.latitude, loc.longitude, 1000).results.take(6) }.getOrDefault(emptyList())
        // No DTC stop near you (outside the network): open on central Delhi, where the buses are.
        if (nearby.isEmpty()) map.moveTo(DELHI_LAT, DELHI_LON, 13.0) else map.moveTo(loc.latitude, loc.longitude, 13.5)
    }

    // Poll the visible area while this tab is shown.
    LaunchedEffect(bbox, zoom) {
        val b = bbox ?: return@LaunchedEffect
        if (zoom < MIN_ZOOM) { vehicles = emptyList(); return@LaunchedEffect }
        delay(300)
        while (true) {
            runCatching { Api.vehicles(bbox = b) }
                .onSuccess { r -> vehicles = r.vehicles.filter { !it.stale }; live = r.live; failed = false }
                .onFailure { failed = true }
            delay(30_000)
        }
    }

    // The tapped bus: its route and times, refreshed while selected.
    LaunchedEffect(selected) {
        detail = null
        val id = selected ?: return@LaunchedEffect
        while (true) {
            runCatching { Api.vehicle(id) }.onSuccess { detail = it }.onFailure { if (detail == null) selected = null }
            delay(15_000)
        }
    }

    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(220)
        hits = runCatching { Api.searchStops(q.trim()).results }.getOrDefault(emptyList())
    }

    val d = detail
    val pins = remember(vehicles, nearby, d) {
        val upcoming = d?.stops?.filter { !it.passed }?.take(12).orEmpty()
        // The followed bus stays on the map even when zoomed out past the live-bus limit.
        val shown = if (d != null && vehicles.none { it.vehicle_id == d.vehicle.vehicle_id }) vehicles + d.vehicle else vehicles
        shown.map { v -> MapPin("v:" + v.vehicle_id, v.lat, v.lon, routeName(v.route ?: "?") + (v.next_stop?.let { " → $it" } ?: ""), MapPin.Kind.BUS) } +
            nearby.map { st -> MapPin("s" + st.stop_id, st.lat, st.lon, st.name) } +
            upcoming.map { st -> MapPin("s" + st.stop_id, st.lat, st.lon, st.name + (st.eta?.let { " · $it" } ?: ""), MapPin.Kind.STOP) }
    }
    // Selected bus: the stretch already covered muted, the rest of its route in the accent colour.
    val routeLines = remember(d, t) {
        val st = d?.stops.orEmpty()
        if (st.size < 2) emptyList() else {
            val cut = st.indexOfLast { it.passed }.coerceAtLeast(0)
            val v = d!!.vehicle
            val done = st.take(cut + 1).map { it.lat to it.lon } + listOf(v.lat to v.lon)
            val ahead = listOf(v.lat to v.lon) + st.drop(cut + 1).map { it.lat to it.lon }
            listOfNotNull(
                done.takeIf { it.size >= 2 }?.let { app.delhiraahi.ui.common.MapLine(it, t.muted.toArgb(), 4f) },
                ahead.takeIf { it.size >= 2 }?.let { app.delhiraahi.ui.common.MapLine(it, t.primary.toArgb(), 6f) },
            )
        }
    }

    val mapH = (screenH * 0.40f).dp
    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.statusBars)) {
        // Title + live status
        Row(Modifier.fillMaxWidth().padding(start = Space.xl, end = Space.md, top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.tabLive, style = MaterialTheme.typography.headlineLarge, color = t.text, modifier = Modifier.semantics { heading() })
                Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (live == true && !failed) t.low.line else t.unknown.line))
                    Text(
                        when {
                            failed -> s.errNetwork.substringBefore('.')
                            selected != null && detail != null -> "${routeName(detail!!.route)} · ${detail!!.vehicle.next_stop ?: ""}"
                            zoom < MIN_ZOOM -> s.zoomIn
                            live == null -> s.loading
                            live == true -> "${s.liveOn} · ${s.busesShown(vehicles.size)}"
                            else -> s.liveOff
                        },
                        style = MaterialTheme.typography.bodyMedium, color = t.muted,
                    )
                }
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { me?.let { map.moveTo(it.first, it.second, 14.0) } }
                    .semantics { contentDescription = s.locate },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = t.text) }
        }

        // The map is a card on the page, not the page.
        Box(Modifier.padding(horizontal = Space.lg, vertical = Space.md).fillMaxWidth().height(mapH)
            .clip(RoundedCornerShape(Radius.lg)).border(1.dp, t.border, RoundedCornerShape(Radius.lg))) {
            BusMap(
                Modifier.fillMaxSize(), map, pins, t.dark, 0.dp, showMyLocation = me != null, lines = routeLines,
                fitKey = d?.vehicle?.vehicle_id?.takeIf { selected != null },
                onPinClick = { p ->
                    when {
                        p.id.startsWith("v:") -> selected = p.id.drop(2)
                        p.id.startsWith("s") -> onOpenStop(p.id.drop(1))
                    }
                },
                onCameraIdle = { b, z -> bbox = b; zoom = z },
            )
        }

        if (selected != null) {
            BusPanel(detail, lt, onClose = { selected = null }, onOpenStop = onOpenStop)
            return@Column
        }
        // Stop search, underlined like the trip sentence
        Row(Modifier.padding(horizontal = Space.xl).fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = t.muted, modifier = Modifier.size(22.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text(s.searchStop, color = t.muted, style = MaterialTheme.typography.titleMedium)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = MaterialTheme.typography.titleMedium.copy(color = t.text),
                    cursorBrush = SolidColor(t.primary), modifier = Modifier.fillMaxWidth().semantics { contentDescription = s.searchStop })
            }
        }
        Box(Modifier.padding(horizontal = Space.xl).fillMaxWidth().height(1.5.dp).background(if (q.isEmpty()) t.border else t.primary))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.xl).padding(bottom = Space.xl)) {
            if (hits.isNotEmpty()) hits.forEach { h ->
                ListRow(Icons.Outlined.Place, h.name, subtitle = s.routesServed(h.routes_served), chevron = true) { onOpenStop(h.stop_ids.first()) }
            } else {
                Text(s.nearbyStops.uppercase(), style = MaterialTheme.typography.labelMedium, color = t.muted,
                    modifier = Modifier.padding(top = Space.lg, bottom = Space.xs).semantics { heading() })
                if (me == null) Text(s.noLocation, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                else if (nearby.isEmpty()) Text(s.noStopsNearby, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                nearby.forEach { st ->
                    ListRow(Icons.Outlined.DirectionsBus, st.name, subtitle = s.metres(st.distance_m), chevron = true) { onOpenStop(st.stop_id) }
                }
            }
        }
    }
}

private const val DELHI_LAT = 28.6315
private const val DELHI_LON = 77.2167

/** Details of the tapped bus: route, direction, next stop, speed, delay, freshness, and its next stops with times. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BusPanel(
    d: app.delhiraahi.data.VehicleDetail?, lt: app.delhiraahi.voice.LiveText, onClose: () -> Unit, onOpenStop: (String) -> Unit,
) {
    val t = LocalExtra.current
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.xl).padding(bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (d == null) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = t.primary)
            else app.delhiraahi.ui.common.RoutePill(d.route)
            Text(d?.towards?.let { lt.towards(it) } ?: "", style = MaterialTheme.typography.titleMedium, color = t.text,
                modifier = Modifier.weight(1f), maxLines = 2)
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = lt.close },
                contentAlignment = Alignment.Center,
            ) { Icon(androidx.compose.material.icons.Icons.Outlined.Close, contentDescription = null, tint = t.text) }
        }
        if (d == null) return@Column
        val v = d.vehicle
        d.route_long_name?.takeIf { !it.startsWith(app.delhiraahi.ui.common.routeName(d.route)) }
            ?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = t.muted) }
        Text(listOfNotNull(v.speed_kmh?.let { lt.speed(it) }, lt.delay(v.delay_min), v.age_s?.let { lt.updated(it) }).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge, color = t.text)
        if (!d.matched) Text(lt.unmatched, style = MaterialTheme.typography.bodyMedium, color = t.muted)
        val ahead = d.stops.filter { !it.passed }
        if (ahead.isNotEmpty()) {
            Text(lt.upcoming.uppercase(), style = MaterialTheme.typography.labelMedium, color = t.muted,
                modifier = Modifier.padding(top = Space.md).semantics { heading() })
            ahead.take(15).forEachIndexed { i, st ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { onOpenStop(st.stop_id) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (i == 0) t.primary else t.bg)
                        .border(2.dp, if (i == 0) t.primary else t.text, CircleShape))
                    Text(st.name, style = MaterialTheme.typography.bodyLarge, color = t.text, modifier = Modifier.weight(1f), maxLines = 1)
                    st.eta?.let { Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = app.delhiraahi.ui.theme.TabularNums), color = t.text) }
                }
            }
            Text(lt.basis(ahead.firstOrNull()?.basis), style = MaterialTheme.typography.labelSmall, color = t.muted, modifier = Modifier.padding(top = Space.sm))
        }
    }
}
