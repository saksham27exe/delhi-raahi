package app.delhiraahi.ui.live

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
import kotlinx.coroutines.delay

private const val PEEK = 0.42f
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

    LaunchedEffect(Unit) {
        val loc = DeviceLocation.current(ctx) ?: return@LaunchedEffect
        me = loc.latitude to loc.longitude
        map.moveTo(loc.latitude, loc.longitude, 13.5)
        nearby = runCatching { Api.nearbyStops(loc.latitude, loc.longitude, 1000).results.take(6) }.getOrDefault(emptyList())
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

    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(220)
        hits = runCatching { Api.searchStops(q.trim()).results }.getOrDefault(emptyList())
    }

    val pins = remember(vehicles, nearby) {
        vehicles.map { v -> MapPin(v.vehicle_id, v.lat, v.lon, routeName(v.route ?: "?") + (v.next_stop?.let { " → $it" } ?: ""), MapPin.Kind.BUS) } +
            nearby.map { st -> MapPin("s" + st.stop_id, st.lat, st.lon, st.name) }
    }

    Box(Modifier.fillMaxSize()) {
        BusMap(
            Modifier.fillMaxSize(), map, pins, t.dark, (screenH * PEEK).dp, showMyLocation = me != null,
            onPinClick = { p -> if (p.id.startsWith("s")) onOpenStop(p.id.drop(1)) },
            onCameraIdle = { b, z -> bbox = b; zoom = z },
        )
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(50), color = t.card, shadowElevation = 4.dp, modifier = Modifier.height(44.dp)) {
                Row(Modifier.padding(horizontal = 14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (live == true && !failed) t.low.line else t.unknown.line))
                    Text(
                        when {
                            failed -> s.errNetwork.substringBefore('.')
                            zoom < MIN_ZOOM -> s.zoomIn
                            live == null -> s.loading
                            live == true -> "${s.liveOn} · ${s.busesShown(vehicles.size)}"
                            else -> s.liveOff
                        },
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = t.text,
                    )
                }
            }
            Box(Modifier.weight(1f))
            FloatingIconButton(Icons.Outlined.MyLocation, s.locate) { me?.let { map.moveTo(it.first, it.second, 14.0) } }
        }

        MapSheet(
            peek = PEEK, full = 0.88f,
            header = {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(Radius.md)).background(t.surface).padding(horizontal = Space.md),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = t.text, modifier = Modifier.size(20.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text(s.searchStop, color = t.muted, style = MaterialTheme.typography.bodyLarge)
                        BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = t.text, fontSize = 16.sp),
                            cursorBrush = SolidColor(t.text), modifier = Modifier.fillMaxWidth().semantics { contentDescription = s.searchStop })
                    }
                }
            },
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.xl)) {
                if (hits.isNotEmpty()) hits.forEach { h ->
                    ListRow(Icons.Outlined.Place, h.name, subtitle = s.routesServed(h.routes_served), chevron = true) { onOpenStop(h.stop_ids.first()) }
                } else {
                    Text(s.nearbyStops, style = MaterialTheme.typography.titleMedium, color = t.text,
                        modifier = Modifier.padding(top = Space.sm, bottom = Space.xs).semantics { heading() })
                    if (me == null) Text(s.noLocation, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                    else if (nearby.isEmpty()) Text(s.noStopsNearby, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                    nearby.forEach { st ->
                        ListRow(Icons.Outlined.DirectionsBus, st.name, subtitle = s.metres(st.distance_m), chevron = true) { onOpenStop(st.stop_id) }
                    }
                }
            }
        }
    }
}
