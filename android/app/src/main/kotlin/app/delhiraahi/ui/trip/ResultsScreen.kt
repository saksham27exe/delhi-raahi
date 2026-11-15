package app.delhiraahi.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.ui.graphics.toArgb
import app.delhiraahi.data.TripKind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.ApiException
import app.delhiraahi.data.Journey
import app.delhiraahi.data.LeaveAt
import app.delhiraahi.data.Load
import app.delhiraahi.data.Preference
import app.delhiraahi.data.TripViewModel
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.i18n.Strings
import app.delhiraahi.ui.common.BusMap
import app.delhiraahi.ui.common.Chip
import app.delhiraahi.ui.common.CrowdBadge
import app.delhiraahi.ui.common.CrowdStrip
import app.delhiraahi.ui.common.FloatingIconButton
import app.delhiraahi.ui.common.LegChain
import app.delhiraahi.ui.common.MapController
import app.delhiraahi.ui.common.MapSheet
import app.delhiraahi.ui.common.Segmented
import app.delhiraahi.ui.common.fareText
import app.delhiraahi.ui.common.journeyMap
import app.delhiraahi.ui.common.mins
import app.delhiraahi.ui.common.routeName
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.delhiraahi.data.BusFilter

fun leaveLabel(s: Strings, l: LeaveAt): String = when (l) {
    LeaveAt.Now -> s.leaveNow
    is LeaveAt.InMinutes -> s.leaveIn(l.min)
    is LeaveAt.Tomorrow -> s.tomorrowAt("%02d:%02d".format(l.hour, l.minute))
}

@Composable
fun ResultsScreen(trip: TripViewModel, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val map = remember { MapController() }
    val journeys = trip.journeys
    val walking = trip.kind == TripKind.WALK
    val sel = if (walking) null else journeys.getOrNull(trip.selected)
    val fromPt = trip.from?.let { f -> f.lat?.let { it to f.lon!! } }
    val toPt = trip.to?.let { d -> d.lat?.let { it to d.lon!! } }
    val walkOpt = trip.walkOption
    val (lines, pins) = remember(sel, walking, walkOpt, t) {
        when {
            walking && walkOpt != null && fromPt != null && toPt != null -> {
                val path = walkOpt.path?.filter { it.size >= 2 }?.map { it[0] to it[1] }?.takeIf { it.size >= 2 } ?: listOf(fromPt, toPt)
                listOf(app.delhiraahi.ui.common.MapLine(listOf(fromPt) + path + listOf(toPt), t.text.toArgb(), 4f, dashed = true)) to
                    listOf(app.delhiraahi.ui.common.MapPin("from", fromPt.first, fromPt.second, trip.from?.label.orEmpty(), app.delhiraahi.ui.common.MapPin.Kind.FROM),
                        app.delhiraahi.ui.common.MapPin("to", toPt.first, toPt.second, trip.to?.label.orEmpty(), app.delhiraahi.ui.common.MapPin.Kind.TO))
            }
            sel == null -> emptyList<app.delhiraahi.ui.common.MapLine>() to emptyList()
            else -> journeyMap(sel, t, fromPt, toPt)
        }
    }

    Column(Modifier.fillMaxSize().background(t.bg)) {
        // The sentence you asked: From … to …, leaving …
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = Space.sm, end = Space.lg, top = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = s.back },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = t.text) }
            Column(Modifier.weight(1f).padding(start = Space.xs).semantics(mergeDescendants = true) { heading() }) {
                Text(trip.from?.label.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("→ ${trip.to?.label.orEmpty()}", style = MaterialTheme.typography.headlineSmall, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(top = Space.md, bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            // Filters: one row of labelled drop-downs (when · sort · bus type) …
            val pick: (TripKind) -> Unit = { k -> trip.kind = k; trip.shown(k).firstOrNull()?.let { trip.selected = it } }
            val prefs = listOf(Preference.FASTEST to s.fastest, Preference.BALANCED to s.balanced, Preference.LEAST_CROWDED to s.leastCrowded)
            val buses = listOf(BusFilter.ANY to s.busAny, BusFilter.AC to s.busAc, BusFilter.NON_AC to s.busNonAc)
            val ready = trip.result is Load.Ok
            val legs = journeys.flatMap { it.legs }
            app.delhiraahi.ui.common.ServiceAlerts(routes = legs.map { it.route }.distinct(), stops = legs.map { it.board_stop_id }.distinct(), everyone = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilterPill(Icons.Outlined.Schedule, s.filterWhen, leaveLabel(s, trip.leaveAt),
                    LeaveAt.options.map { it to leaveLabel(s, it) }, trip.leaveAt, Modifier.weight(1f)) { trip.leaveAt = it; trip.search() }
                FilterPill(Icons.AutoMirrored.Outlined.Sort, s.filterSort, prefs.first { it.first == trip.preference }.second,
                    prefs, trip.preference, Modifier.weight(1f)) { trip.preference = it; trip.search() }
                FilterPill(Icons.Outlined.AcUnit, s.filterBus, buses.first { it.first == trip.busFilter }.second,
                    buses.map { (k, n) -> k to (if (ready) "$n (${trip.shown(TripKind.ALL, k).size})" else n) }, trip.busFilter, Modifier.weight(1f)) {
                    trip.busFilter = it
                    if (trip.kind != TripKind.WALK) trip.shown().firstOrNull()?.let { i -> trip.selected = i }
                }
            }
            // … then tabs for the kind of trip, with how many of each.
            if (ready && (journeys.isNotEmpty() || walkOpt != null)) KindTabs(
                buildList {
                    add(Triple(TripKind.ALL, s.filterAll, "${trip.shown(TripKind.ALL).size}"))
                    add(Triple(TripKind.DIRECT, s.filterDirect, "${trip.shown(TripKind.DIRECT).size}"))
                    add(Triple(TripKind.CHANGE, s.filterChange, "${trip.shown(TripKind.CHANGE).size}"))
                    if (walkOpt != null) add(Triple(TripKind.WALK, s.filterWalk, s.minutes(walkOpt.minutes.mins())))
                },
                trip.kind, pick,
            )

            // A small sketch of the selected route, not a full-screen map.
            if (lines.isNotEmpty()) Box(
                Modifier.padding(top = Space.xs).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(Radius.lg)).border(1.dp, t.border, RoundedCornerShape(Radius.lg)),
            ) {
                BusMap(
                    modifier = Modifier.fillMaxSize(), controller = map, dark = t.dark, pins = pins, lines = lines,
                    bottomPadding = 0.dp, showMyLocation = false, fitKey = if (walking) "walk" else sel?.journey_id,
                )
            }

            when (val r = trip.result) {
                Load.Idle, Load.Loading -> Row(Modifier.padding(vertical = Space.xl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = t.primary)
                    Text(s.searching, color = t.muted)
                }
                is Load.Failed -> Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(t.veryHigh.bg).padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    val e = r.error
                    val noStop = e is ApiException && e.reason == "no_stop_nearby"
                    Text(when {
                        e is ApiException && e.status == 0 -> s.errNetwork
                        noStop -> s.noStopNear(trip.radiusM)
                        else -> e.message ?: s.errNetwork
                    }, color = t.veryHigh.fg)
                    if (noStop && trip.radiusM < 1500)
                        Text(s.widerSearch, color = t.veryHigh.fg, fontWeight = FontWeight.Bold,
                            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { trip.search(1500) }.padding(vertical = Space.sm))
                    else
                        Text(s.retry, color = t.veryHigh.fg, fontWeight = FontWeight.Bold,
                            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { trip.search(trip.radiusM) }.padding(vertical = Space.sm))
                }
                is Load.Ok -> if (r.value.journeys.isEmpty() && walkOpt == null) Column(Modifier.padding(vertical = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(s.noTrips, style = MaterialTheme.typography.titleMedium, color = t.text)
                    Text(s.noTripsHint, color = t.muted)
                } else {
                    val idx = if (walking) emptyList() else trip.shown()
                    if (!walking && idx.isEmpty()) Column(Modifier.padding(vertical = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text(s.noneOfKind, color = t.muted)
                        if (trip.busFilter != BusFilter.ANY) Text("${s.filterBus}: ${s.busAny}", color = t.primary, fontWeight = FontWeight.Bold,
                            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { trip.busFilter = BusFilter.ANY }.padding(vertical = Space.sm))
                    }
                    Spacer(Modifier.height(Space.xs))
                    TimeRibbons(
                        journeys = journeys, shown = idx, selected = trip.selected, onSelect = { trip.selected = it },
                        walk = walkOpt, walkSelected = walking, showWalk = trip.kind == TripKind.ALL || walking,
                        onWalk = { pick(TripKind.WALK) },
                        toName = trip.to?.label,
                    )
                }
            }
            Text(s.crowdNote, style = MaterialTheme.typography.labelSmall, color = t.muted, modifier = Modifier.padding(top = Space.sm))
            Text(s.busTypeNote, style = MaterialTheme.typography.labelSmall, color = t.muted)
        }
        sel?.let { j ->
            Box(Modifier.fillMaxWidth().background(t.bg).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Space.lg, vertical = Space.md)) {
                PrimaryButton(s.choose(j.legs.joinToString(" + ") { routeName(it.route) })) { onOpen(trip.selected) }
            }
        }
    }
}

/** A labelled drop-down: small caption ("Leave"), the current value ("In 30 min") and a ▾. */
@Composable
private fun <K> FilterPill(
    icon: ImageVector, label: String, value: String, options: List<Pair<K, String>>, current: K,
    modifier: Modifier = Modifier, onPick: (K) -> Unit,
) {
    val t = LocalExtra.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(Radius.md)).background(t.surface)
                .clickable(role = Role.Button) { open = true }.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = t.muted, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = t.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = t.muted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEach { (k, name) ->
                val on = k == current
                DropdownMenuItem(
                    text = { Text(name, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null, tint = if (on) t.primary else Color.Transparent) },
                    onClick = { open = false; if (!on) onPick(k) },
                )
            }
        }
    }
}

/** All · Direct · With change · Walk, each with its count, underlined when chosen. Empty tabs are greyed out. */
@Composable
private fun KindTabs(tabs: List<Triple<TripKind, String, String>>, current: TripKind, onPick: (TripKind) -> Unit) {
    val t = LocalExtra.current
    Row(Modifier.fillMaxWidth().selectableGroup()) {
        tabs.forEach { (k, name, count) ->
            val on = k == current
            val empty = count == "0"
            Column(
                Modifier.weight(1f).selectable(selected = on, enabled = !empty || on, role = Role.Tab) { onPick(k) }
                    .padding(top = Space.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    color = when { on -> t.primary; empty -> t.muted.copy(alpha = 0.5f); else -> t.text }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(count, style = MaterialTheme.typography.labelMedium, color = if (on) t.primary else t.muted.copy(alpha = if (empty) 0.5f else 1f))
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(if (on) 3.dp else 1.dp).background(if (on) t.primary else t.border))
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val t = LocalExtra.current
    Box(
        modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(Radius.md))
            .background(if (enabled) t.primary else t.surface2).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) t.onPrimary else t.muted, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalExtra.current
    Box(
        modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(Radius.md)).background(t.surface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = t.text, style = MaterialTheme.typography.titleMedium) }
}

