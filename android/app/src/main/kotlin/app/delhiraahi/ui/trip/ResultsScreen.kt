package app.delhiraahi.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

private const val PEEK = 0.55f

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
    val screenH = LocalConfiguration.current.screenHeightDp
    val journeys = trip.journeys
    val sel = journeys.getOrNull(trip.selected)
    val (lines, pins) = remember(sel, t) {
        if (sel == null) emptyList<app.delhiraahi.ui.common.MapLine>() to emptyList()
        else journeyMap(sel, t, trip.from?.let { f -> f.lat?.let { it to f.lon!! } }, trip.to?.let { d -> d.lat?.let { it to d.lon!! } })
    }

    Box(Modifier.fillMaxSize()) {
        BusMap(
            modifier = Modifier.fillMaxSize(), controller = map, dark = t.dark, pins = pins, lines = lines,
            bottomPadding = (screenH * PEEK).dp, showMyLocation = false, fitKey = sel?.journey_id,
        )
        Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(Space.lg)) {
            FloatingIconButton(Icons.AutoMirrored.Outlined.ArrowBack, s.back, onClick = onBack)
        }

        MapSheet(
            peek = PEEK, full = 0.9f,
            footer = sel?.let { j ->
                {
                    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Space.lg)) {
                        PrimaryButton(s.choose(j.legs.joinToString(" + ") { routeName(it.route) })) { onOpen(trip.selected) }
                    }
                }
            },
            header = {
                Text(
                    "${trip.from?.label ?: ""} → ${trip.to?.label ?: ""}", style = MaterialTheme.typography.titleMedium, color = t.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Space.sm))
                Segmented(
                    listOf(Preference.FASTEST to s.fastest, Preference.BALANCED to s.balanced, Preference.LEAST_CROWDED to s.leastCrowded),
                    trip.preference, { trip.preference = it; trip.search() }, s.preference,
                )
                Row(Modifier.padding(top = Space.sm).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    LeaveAt.options.forEach { o ->
                        Chip(leaveLabel(s, o), if (o == LeaveAt.Now) Icons.Outlined.Schedule else null, selected = trip.leaveAt == o) {
                            trip.leaveAt = o; trip.search()
                        }
                    }
                }
            },
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                when (val r = trip.result) {
                    Load.Idle, Load.Loading -> Row(Modifier.padding(vertical = Space.xl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = t.text)
                        Text(s.searching, color = t.muted)
                    }
                    is Load.Failed -> Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(t.veryHigh.bg).padding(Space.lg),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        val e = r.error
                        Text(if (e is ApiException && e.status == 0) s.errNetwork else (e.message ?: s.errNetwork), color = t.veryHigh.fg)
                        Text(s.retry, color = t.veryHigh.fg, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(role = Role.Button) { trip.search() })
                    }
                    is Load.Ok -> if (r.value.journeys.isEmpty()) Column(Modifier.padding(vertical = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text(s.noTrips, style = MaterialTheme.typography.titleMedium, color = t.text)
                        Text(s.noTripsHint, color = t.muted)
                    } else r.value.journeys.forEachIndexed { i, j ->
                        JourneyCard(j, selected = i == trip.selected) { trip.selected = i }
                    }
                }
                Text(s.crowdNote, style = MaterialTheme.typography.labelSmall, color = t.muted, modifier = Modifier.padding(top = Space.sm))
            }
        }
    }
}

@Composable
private fun JourneyCard(j: Journey, selected: Boolean, onClick: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg))
            .border(if (selected) 2.dp else 1.dp, if (selected) t.text else t.border, RoundedCornerShape(Radius.lg))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (j.labels.isNotEmpty()) Text(
            j.labels.joinToString(" · ") { s.label(it) }, style = MaterialTheme.typography.labelMedium, color = t.accent, fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${j.departure} – ${j.arrival}", style = MaterialTheme.typography.titleMedium, color = t.text)
                Text(
                    listOfNotNull(
                        s.minutes(j.duration_minutes.mins()),
                        if (j.transfers == 0) s.direct else s.changes(j.transfers),
                        j.walk_minutes.mins().takeIf { it >= 1 }?.let { s.walkMin(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = t.muted,
                )
            }
            fareText(j)?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = t.text) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { LegChain(j) }
            CrowdBadge(j.crowd_level, short = true)
        }
        CrowdStrip(j)
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

