package app.delhiraahi.ui.trip

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Lang
import app.delhiraahi.data.Leg
import app.delhiraahi.data.TripViewModel
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.BusMap
import app.delhiraahi.ui.common.CrowdBadge
import app.delhiraahi.ui.common.CrowdStrip
import app.delhiraahi.ui.common.FloatingIconButton
import app.delhiraahi.ui.common.MapController
import app.delhiraahi.ui.common.MapSheet
import app.delhiraahi.ui.common.RoutePill
import app.delhiraahi.ui.common.crowd
import app.delhiraahi.ui.common.fareText
import app.delhiraahi.ui.common.journeyMap
import app.delhiraahi.ui.common.mins
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space

private const val PEEK = 0.6f

@Composable
fun JourneyScreen(trip: TripViewModel, index: Int, lang: Lang, onBack: () -> Unit, onBook: () -> Unit, onTrack: (String, String) -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val ctx = LocalContext.current
    val j = trip.journeys.getOrNull(index) ?: run { onBack(); return }
    val map = remember { MapController() }
    val screenH = LocalConfiguration.current.screenHeightDp
    val (lines, pins) = remember(j, t) {
        journeyMap(j, t, trip.from?.let { f -> f.lat?.let { it to f.lon!! } }, trip.to?.let { d -> d.lat?.let { it to d.lon!! } })
    }

    Box(Modifier.fillMaxSize()) {
        BusMap(Modifier.fillMaxSize(), map, pins, t.dark, (screenH * PEEK).dp, showMyLocation = false, lines = lines, fitKey = j.journey_id)
        Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(Space.lg)) {
            FloatingIconButton(Icons.AutoMirrored.Outlined.ArrowBack, s.back, onClick = onBack)
        }

        MapSheet(
            peek = PEEK, full = 0.9f,
            footer = {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SecondaryButton(s.track, Modifier.weight(1f)) { j.legs.firstOrNull()?.let { onTrack(it.board_stop_id, it.route_id) } }
                    PrimaryButton(fareText(j)?.let { s.book(it) } ?: s.bookNoFare, Modifier.weight(1.6f), onClick = onBook)
                }
            },
            header = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${j.departure} – ${j.arrival}", style = MaterialTheme.typography.headlineSmall, color = t.text, modifier = Modifier.semantics { heading() })
                        Text(s.minutes(j.duration_minutes.mins()), style = MaterialTheme.typography.bodyMedium, color = t.muted)
                    }
                    fareText(j)?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(it, style = MaterialTheme.typography.headlineSmall, color = t.text)
                            Text(s.eTicketFare, style = MaterialTheme.typography.labelSmall, color = t.muted)
                        }
                    }
                }
                Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    CrowdBadge(j.crowd_level)
                    CrowdStrip(j, Modifier.weight(1f))
                }
            },
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.lg)) {
                // Step-by-step timeline
                if (j.walk.origin_min >= 1) Step(Icons.AutoMirrored.Outlined.DirectionsWalk, s.walkStart, s.walkTo(j.walk.origin_min.mins()), dotted = true)
                j.legs.forEachIndexed { i, leg ->
                    if (i > 0) Step(Icons.Outlined.SyncAlt, s.changeBus, if (j.walk.transfer_min >= 1) s.walkTo(j.walk.transfer_min.mins()) else null, dotted = true)
                    LegBlock(leg, last = i == j.legs.lastIndex && j.walk.destination_min < 1)
                }
                if (j.walk.destination_min >= 1) {
                    Step(Icons.AutoMirrored.Outlined.DirectionsWalk, s.walkEnd, s.walkTo(j.walk.destination_min.mins()), dotted = true)
                    Step(Icons.Outlined.Flag, trip.to?.label ?: "", j.arrival, last = true)
                }

                // Why this trip
                Text(s.why, style = MaterialTheme.typography.titleMedium, color = t.text, modifier = Modifier.padding(top = Space.lg, bottom = Space.xs).semantics { heading() })
                val summary = j.summary[if (lang == Lang.HI) "hi" else "en"] ?: j.summary["en"]
                if (!summary.isNullOrBlank()) Text(summary, style = MaterialTheme.typography.bodyLarge, color = t.text)
                // Backend explanations are English-only; show them only in English mode.
                if (lang == Lang.EN) j.explanation.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = t.muted, modifier = Modifier.padding(top = 2.dp)) }
                Text("${s.timetable} ${s.crowdNote}", style = MaterialTheme.typography.labelSmall, color = t.muted, modifier = Modifier.padding(top = Space.md))
            }
        }
    }
}

/** One row of the timeline: icon on a vertical rail, title, optional detail. */
@Composable
private fun Step(icon: ImageVector, title: String, detail: String?, dotted: Boolean = false, last: Boolean = false, content: (@Composable () -> Unit)? = null) {
    val t = LocalExtra.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(t.surface), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = t.text, modifier = Modifier.size(18.dp))
            }
            if (!last) Box(Modifier.width(if (dotted) 2.dp else 4.dp).weight(1f).background(if (dotted) t.surface2 else t.text))
        }
        Column(Modifier.weight(1f).padding(start = Space.md, bottom = Space.lg, top = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = t.text)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = t.muted)
            content?.invoke()
        }
    }
}

@Composable
private fun LegBlock(leg: Leg, last: Boolean) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    var open by remember { mutableStateOf(false) }
    Step(Icons.Outlined.DirectionsBus, s.board(leg.board_stop), s.departsAt(leg.departure)) {
        Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            RoutePill(leg.route)
            Text(leg.route_desc, style = MaterialTheme.typography.bodyMedium, color = t.muted, maxLines = 1)
        }
        Text(s.ride(leg.stops, leg.in_vehicle_minutes.mins()), style = MaterialTheme.typography.bodyMedium, color = t.text, modifier = Modifier.padding(top = Space.xs))
        Text(
            if (open) s.hideStretches else s.showStretches, style = MaterialTheme.typography.bodyMedium, color = t.accent, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Space.xs).clickable(role = Role.Button) { open = !open }.padding(vertical = Space.xs),
        )
        if (open) Column(Modifier.padding(top = Space.xs).clip(RoundedCornerShape(Radius.md)).background(t.surface).padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            leg.segments.forEach { sg ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(t.crowd(sg.crowd?.category).line))
                    Text("${sg.from_stop} → ${sg.to_stop}", style = MaterialTheme.typography.bodyMedium, color = t.text, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(s.crowdShort(sg.crowd?.category ?: "UNKNOWN"), style = MaterialTheme.typography.labelMedium, color = t.crowd(sg.crowd?.category).fg)
                }
            }
        }
    }
    Step(Icons.Outlined.Flag, s.alight(leg.alight_stop), leg.arrival, dotted = true, last = last)
}
