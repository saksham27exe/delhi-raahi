package app.delhiraahi.ui.trip

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Api
import app.delhiraahi.data.DeviceLocation
import app.delhiraahi.data.Lang
import app.delhiraahi.data.TicketStore
import app.delhiraahi.data.TrackResult
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.BusMap
import app.delhiraahi.ui.common.MapController
import app.delhiraahi.ui.common.MapLine
import app.delhiraahi.ui.common.MapPin
import app.delhiraahi.ui.common.RoutePill
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.voice.LiveText
import app.delhiraahi.voice.RideCue
import app.delhiraahi.voice.RideText
import app.delhiraahi.voice.Speaker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private const val POLL_MS = 15_000L
private const val MISSED_BUS_M = 300f       // bus passed your stop while you were this far from it -> you didn't board

/**
 * Follows a booked ticket on live bus data: the bus coming to your stop, then the stops left until yours,
 * for each leg in turn. Speaks and vibrates at the moments that matter.
 */
@Composable
fun LiveRideScreen(store: TicketStore, ticketId: String, lang: Lang, onBack: () -> Unit, onGuide: (Double, Double, String) -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val tickets by store.tickets.collectAsState(initial = null)
    val tk = tickets?.firstOrNull { it.ticket_id == ticketId }
    if (tickets != null && tk == null) { onBack(); return }
    if (tk == null) return
    val legs = tk.legs.filter { it.route_id.isNotBlank() && it.board_stop_id.isNotBlank() && it.alight_stop_id.isNotBlank() }
    if (legs.isEmpty()) { onBack(); return }

    val lt = remember(lang) { LiveText.of(lang == Lang.HI) }
    val rt = remember(lang) { RideText.of(lang == Lang.HI) }
    val locale = remember(lang) { if (lang == Lang.HI) Locale("hi", "IN") else Locale("en", "IN") }
    val speaker = remember { Speaker(ctx) }
    val map = remember { MapController() }

    var legIdx by remember { mutableIntStateOf(0) }
    var vehicleId by remember { mutableStateOf<String?>(null) }
    var skip by remember { mutableStateOf(setOf<String>()) }
    var res by remember { mutableStateOf<TrackResult?>(null) }
    var failed by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf("") }
    var me by remember { mutableStateOf<Location?>(null) }
    var finished by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf(setOf<String>()) }   // keys of announcements already made this leg

    val queue = remember { Channel<String>(Channel.UNLIMITED) }
    fun speak(x: String) { line = x; queue.trySend(x) }
    fun once(key: String, block: () -> Unit) { if (key !in said) { said = said + key; block() } }
    LaunchedEffect(Unit) { for (x in queue) runCatching { speaker.say(x, locale) } }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; speaker.shutdown() }
    }
    // Phone position, only to notice a missed bus.
    LaunchedEffect(Unit) { DeviceLocation.updates(ctx).collect { if (it.accuracy <= 100f) me = it } }

    fun react(r: TrackResult) {
        val leg = legs[legIdx]
        val v = r.vehicle
        when (r.phase) {
            "approaching" -> {
                if (v != null && vehicleId != v.vehicle_id) { vehicleId = v.vehicle_id; said = said.filterNot { it.startsWith("a:") }.toSet() }
                val min = (r.eta_board_min ?: 0.0).roundToInt()
                once("a:first") { speak(lt.approaching(leg.route, r.stops_away, min)) }
                if (min in 2..5) once("a:5") { speak(lt.fiveMin(leg.route)) }
                if (r.stops_away == 0 || (r.eta_board_min ?: 99.0) <= 1.5) once("a:now") { speak(lt.arrivingNow(leg.route)); buzz(ctx, long = false) }
            }
            "on_board", "arrived" -> {
                // The followed bus has passed the boarding stop. If the phone was clearly elsewhere, the rider missed it.
                val m = me
                if (v != null && "board" !in said && m != null && System.currentTimeMillis() - m.time < 90_000) {
                    val d = FloatArray(1)
                    Location.distanceBetween(m.latitude, m.longitude, v.lat, v.lon, d)
                    if (d[0] > MISSED_BUS_M) {
                        speak(lt.missed); skip = skip + v.vehicle_id; vehicleId = null; said = emptySet(); return
                    }
                }
                once("board") { speak(lt.boarded(leg.route, r.stops_left, leg.alight_stop)) }
                if (r.phase == "on_board" && r.stops_left == 2) once("two") { speak(rt.say(RideCue.TwoLeft(r.next_stop ?: "", false))) }
                if (r.phase == "on_board" && r.stops_left == 1) once("one") { speak(rt.say(RideCue.NextIsYours(leg.alight_stop, false))); buzz(ctx, long = false) }
                if (r.phase == "arrived") once("arrive") {
                    speak(rt.say(RideCue.Arrive(leg.alight_stop, false))); buzz(ctx, long = true)
                    val next = legs.getOrNull(legIdx + 1)
                    if (next != null) {
                        speak(rt.changeHere(next.route, next.board_stop, 0))
                        legIdx++; vehicleId = null; skip = emptySet(); said = emptySet()
                    } else finished = true
                }
            }
            "no_live" -> once("nolive") { speak(lt.noLive(leg.route, r.next_scheduled)) }
        }
    }

    // Poll the live tracker.
    LaunchedEffect(legIdx, skip, finished) {
        while (!finished) {
            val leg = legs[legIdx]
            val r = runCatching { Api.track(leg.route_id, leg.board_stop_id, leg.alight_stop_id, vehicleId, skip) }.getOrNull()
            failed = r == null
            if (r != null) { res = r; react(r) }
            delay(POLL_MS)
        }
    }

    val r = res
    val leg = legs[legIdx]
    val (lines, pins) = remember(r, t) {
        val st = r?.stops.orEmpty()
        val ls = if (st.size >= 2) listOf(MapLine(st.map { it.lat to it.lon }, t.primary.toArgb(), 5f)) else emptyList()
        val ps = buildList {
            st.firstOrNull()?.let { add(MapPin("b", it.lat, it.lon, it.name, MapPin.Kind.FROM)) }
            st.lastOrNull()?.takeIf { st.size >= 2 }?.let { add(MapPin("a", it.lat, it.lon, it.name, MapPin.Kind.TO)) }
            r?.vehicle?.let { add(MapPin("v", it.lat, it.lon, it.route ?: leg.route, MapPin.Kind.BUS)) }
        }
        ls to ps
    }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = Space.sm, top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = s.back },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = t.text) }
            Text(lt.title, style = MaterialTheme.typography.titleMedium, color = t.text)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(Modifier.padding(top = Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                RoutePill(leg.route)
                Text("${leg.board_stop} → ${leg.alight_stop}", style = MaterialTheme.typography.bodyLarge, color = t.text)
            }
            // The one number that matters now
            val big = when (r?.phase) {
                null -> null
                "approaching" -> lt.stopsAway(r.stops_away)
                "on_board" -> rt.stopsLeft(r.stops_left)
                "arrived" -> rt.stopsLeft(0)
                else -> r.message
            }
            if (big == null) Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = t.primary)
                Text(s.loading, color = t.muted)
            } else Text(big, style = MaterialTheme.typography.displaySmall, color = if (r?.phase == "arrived") t.primary else t.text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            r?.let { x ->
                when (x.phase) {
                    "approaching" -> x.eta_board?.let { Text(lt.arrivesAt(it, (x.eta_board_min ?: 0.0).roundToInt()), style = MaterialTheme.typography.titleMedium, color = t.text) }
                    "on_board" -> x.eta_alight?.let { Text(lt.arrivesAt(it, (x.eta_alight_min ?: 0.0).roundToInt()), style = MaterialTheme.typography.titleMedium, color = t.text) }
                    "no_live" -> x.next_scheduled?.let { Text(lt.arrivesAt(it, (x.next_scheduled_min ?: 0.0).roundToInt()), style = MaterialTheme.typography.titleMedium, color = t.text) }
                }
                // Where the numbers come from, and how fresh they are
                val fresh = listOfNotNull(lt.basis(x.basis), x.vehicle?.age_s?.let { lt.updated(it) }, x.vehicle?.delay_min?.let { lt.delay(it) })
                Text(fresh.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = t.muted)
            }
            if (failed) Text(s.errNetwork, style = MaterialTheme.typography.bodyMedium, color = t.veryHigh.fg)
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyLarge, color = t.text)
            if (lines.isNotEmpty()) Box(Modifier.padding(top = Space.sm).fillMaxWidth().height(220.dp).clip(RoundedCornerShape(Radius.lg))
                .border(1.dp, t.border, RoundedCornerShape(Radius.lg))) {
                BusMap(Modifier.fillMaxSize(), map, pins, t.dark, 0.dp, showMyLocation = me != null, lines = lines, fitKey = "$legIdx:${r?.vehicle?.vehicle_id}")
            }
        }
        Row(Modifier.fillMaxWidth().padding(Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            val v = r?.vehicle
            val first = r?.stops?.firstOrNull()
            when {
                legIdx > 0 && r?.phase == "approaching" && first != null && "walked" !in said ->
                    SecondaryButton(rt.walkNext, Modifier.weight(1f)) { said = said + "walked"; onGuide(first.lat, first.lon, first.name) }
                v != null && r.phase in setOf("approaching", "on_board") ->
                    SecondaryButton(lt.notMyBus, Modifier.weight(1f)) { skip = skip + v.vehicle_id; vehicleId = null; said = emptySet() }
            }
            PrimaryButton(if (finished) rt.done else lt.endRide, Modifier.weight(1f), onClick = onBack)
        }
    }
}
