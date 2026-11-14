package app.delhiraahi.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Journey
import app.delhiraahi.data.Lang
import app.delhiraahi.data.Leg
import app.delhiraahi.data.TripViewModel
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.i18n.Strings
import app.delhiraahi.ui.common.BusMap
import app.delhiraahi.ui.common.CrowdBadge
import app.delhiraahi.ui.common.MapController
import app.delhiraahi.ui.common.RoutePill
import app.delhiraahi.ui.common.crowd
import app.delhiraahi.ui.common.fareText
import app.delhiraahi.ui.common.journeyMap
import app.delhiraahi.ui.common.mins
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.theme.TabularNums
import kotlin.math.roundToInt

/** One stop on the vertical ribbon: a time, what happens there, and the stretch that follows it. */
private class Moment(
    val time: Float, val title: String, val detail: String?, val after: Piece?,
    val leg: Leg? = null, val end: Boolean = false,
    /** Where a walking stretch leads (lat, lon, name), for spoken directions. */
    val guide: Triple<Double, Double, String>? = null,
)

/** Turn the trip's ribbon pieces into moments: start, board, get off, change, arrive. */
private fun moments(j: Journey, r: Ribbon, s: Strings, fromLabel: String, toLabel: String, to: Pair<Double, Double>?): List<Moment> {
    fun boardOf(l: Leg?) = l?.segments?.firstOrNull()?.let { Triple(it.lat, it.lon, l.board_stop) }
    fun walkText(min: Float, m: Double?, basis: String?) =
        (if (m != null) s.walkToM(min.roundToInt(), m.toInt()) else s.walkTo(min.roundToInt())) + if (basis == "straight_line_estimate") " (${s.approx})" else ""
    val out = mutableListOf<Moment>()
    var legIdx = 0
    r.pieces.forEachIndexed { k, p ->
        val prev = r.pieces.getOrNull(k - 1)
        val len = p.b - p.a
        val prevLeg = j.legs.getOrNull(legIdx - 1)
        val nextLeg = j.legs.getOrNull(legIdx)
        out += when (p) {
            is RidePiece -> {
                val leg = j.legs[legIdx]; legIdx++
                Moment(p.a, s.board(leg.board_stop), null, p, leg = leg)
            }
            is WalkPiece -> when {
                k == 0 -> Moment(p.a, fromLabel, walkText(len, j.walk.origin_m, j.walk.basis?.origin), p, guide = boardOf(nextLeg))
                legIdx == j.legs.size -> Moment(p.a, s.alight(prevLeg?.alight_stop.orEmpty()), walkText(len, j.walk.destination_m, j.walk.basis?.destination), p,
                    guide = to?.let { Triple(it.first, it.second, toLabel) })
                else -> Moment(p.a, s.alight(prevLeg?.alight_stop.orEmpty()), s.walkTo(len.roundToInt()) + (nextLeg?.let { " → ${it.board_stop}" } ?: ""), p,
                    guide = boardOf(nextLeg))
            }
            is WaitPiece -> when {
                k == 0 -> Moment(p.a, nextLeg?.board_stop ?: fromLabel, s.waitFor(len.roundToInt()), p)
                prev is RidePiece -> Moment(p.a, s.alight(prevLeg?.alight_stop.orEmpty()), s.waitFor(len.roundToInt()), p)
                else -> Moment(p.a, nextLeg?.board_stop.orEmpty(), s.waitFor(len.roundToInt()), p)
            }
        }
    }
    val last = r.pieces.lastOrNull()
    val endTitle = if (last is RidePiece) s.alight(j.legs.lastOrNull()?.alight_stop.orEmpty()) else toLabel
    out += Moment(r.end, endTitle, if (last is RidePiece && toLabel.isNotEmpty() && toLabel != j.legs.lastOrNull()?.alight_stop) toLabel else null, null, end = true)
    return out
}

@Composable
fun JourneyScreen(
    trip: TripViewModel, index: Int, lang: Lang, onBack: () -> Unit, onBook: () -> Unit, onTrack: (String, String) -> Unit,
    onGuide: (Double, Double, String) -> Unit = { _, _, _ -> },
) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val j = trip.journeys.getOrNull(index) ?: run { onBack(); return }
    val map = remember { MapController() }
    val (lines, pins) = remember(j, t) {
        journeyMap(j, t, trip.from?.let { f -> f.lat?.let { it to f.lon!! } }, trip.to?.let { d -> d.lat?.let { it to d.lon!! } })
    }
    val ribbon = remember(j, t) { clockMin(j.departure)?.let { ribbonOf(j, t, it) } }
    val num = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = TabularNums)

    Column(Modifier.fillMaxSize().background(t.bg)) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = Space.sm, top = Space.sm)) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = s.back },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = t.text) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.xl).padding(bottom = Space.lg)) {
            // Leave → arrive, big
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text("${ribbon?.let { clockText(it.start) } ?: j.departure} → ${j.arrival}", style = num, color = t.text)
                Text(
                    listOfNotNull(s.minutes(j.duration_minutes.mins()), if (j.transfers == 0) s.direct else s.changes(j.transfers),
                        fareText(j)?.let { "$it ${s.eTicketFare}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge, color = t.muted,
                )
            }
            Row(Modifier.padding(top = Space.sm)) { CrowdBadge(j.crowd_level) }

            // Small route sketch
            Box(Modifier.padding(top = Space.lg).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(Radius.lg)).border(1.dp, t.border, RoundedCornerShape(Radius.lg))) {
                BusMap(Modifier.fillMaxSize(), map, pins, t.dark, 0.dp, showMyLocation = false, lines = lines, fitKey = j.journey_id)
            }

            // The ribbon, turned vertical: one moment per row, the stretch after it drawn on the rail
            Spacer(Modifier.height(Space.xl))
            if (ribbon != null) {
                val toPt = trip.to?.let { d -> d.lat?.let { it to d.lon!! } }
                val ms = remember(j, ribbon, s) { moments(j, ribbon, s, trip.from?.label ?: s.yourLocation, trip.to?.label.orEmpty(), toPt) }
                ms.forEach { m -> MomentRow(m, guideLabel = app.delhiraahi.voice.GuideText.of(lang == Lang.HI).title, onGuide = onGuide) }
            }

            Text(s.why, style = MaterialTheme.typography.titleMedium, color = t.text, modifier = Modifier.padding(top = Space.xl, bottom = Space.xs).semantics { heading() })
            val summary = j.summary[if (lang == Lang.HI) "hi" else "en"] ?: j.summary["en"]
            if (!summary.isNullOrBlank()) Text(summary, style = MaterialTheme.typography.bodyLarge, color = t.text)
            // Backend explanations are English-only; show them only in English mode.
            if (lang == Lang.EN) j.explanation.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = t.muted, modifier = Modifier.padding(top = 2.dp)) }
            Text("${s.timetable} ${s.crowdNote}", style = MaterialTheme.typography.labelSmall, color = t.muted, modifier = Modifier.padding(top = Space.md))
        }
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Space.lg, vertical = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            SecondaryButton(s.track, Modifier.weight(1f)) { j.legs.firstOrNull()?.let { onTrack(it.board_stop_id, it.route_id) } }
            PrimaryButton(fareText(j)?.let { s.book(it) } ?: s.bookNoFare, Modifier.weight(1.6f), onClick = onBook)
        }
    }
}

@Composable
private fun MomentRow(m: Moment, guideLabel: String, onGuide: (Double, Double, String) -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val minutes = m.after?.let { it.b - it.a } ?: 0f
    // Rail length grows with the minutes it stands for, within readable bounds.
    val railMin = if (m.end) 0.dp else (minutes * 6f).coerceIn(36f, 240f).dp
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(clockText(m.time), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TabularNums), color = t.text,
            modifier = Modifier.width(56.dp).padding(top = 1.dp))
        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.padding(top = 4.dp).size(16.dp).clip(CircleShape).background(if (m.end) t.primary else t.bg)
                    .border(3.dp, if (m.end) t.primary else t.text, CircleShape),
            )
            m.after?.let { p -> Rail(p, Modifier.weight(1f).fillMaxWidth()) }
        }
        Column(Modifier.weight(1f).padding(start = Space.sm, bottom = Space.lg).heightIn(min = railMin).semantics(mergeDescendants = true) {}) {
            Text(m.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = t.text)
            m.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = t.muted) }
            m.leg?.let { leg -> LegDetail(leg, s) }
            m.guide?.let { (la, lo, nm) ->
                Row(
                    Modifier.padding(top = Space.xs).clip(RoundedCornerShape(50)).border(1.dp, t.primary, RoundedCornerShape(50))
                        .clickable(role = Role.Button) { onGuide(la, lo, nm) }.heightIn(min = 40.dp).padding(horizontal = Space.md),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = t.primary, modifier = Modifier.size(18.dp))
                    Text(guideLabel, style = MaterialTheme.typography.labelLarge, color = t.primary)
                }
            }
        }
    }
}

/** The stretch between two moments, drawn the same way as on the results ribbons. */
@Composable
private fun Rail(p: Piece, modifier: Modifier) {
    val t = LocalExtra.current
    Canvas(modifier.padding(vertical = 4.dp)) {
        val cx = size.width / 2
        when (p) {
            is WalkPiece -> {
                val r = 2.4.dp.toPx(); val gap = 7.dp.toPx()
                var y = r
                while (y <= size.height - r) { drawCircle(t.muted, r, Offset(cx, y)); y += gap }
            }
            is WaitPiece -> {
                val w = 12.dp.toPx()
                drawRoundRect(t.muted, Offset(cx - w / 2, 0f), Size(w, size.height), CornerRadius(w / 2), style = Stroke(1.5.dp.toPx()))
            }
            is RidePiece -> {
                val w = 14.dp.toPx()
                val rr = RoundRect(cx - w / 2, 0f, cx + w / 2, size.height, CornerRadius(5.dp.toPx()))
                clipPath(Path().apply { addRoundRect(rr) }) {
                    val total = p.parts.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.01f)
                    var y = 0f
                    p.parts.forEach { (wt, c) ->
                        val dy = size.height * wt / total
                        drawRect(c, Offset(cx - w / 2, y), Size(w, dy + 0.5f))
                        y += dy
                    }
                }
            }
        }
    }
}

@Composable
private fun LegDetail(leg: Leg, s: Strings) {
    val t = LocalExtra.current
    var open by remember { mutableStateOf(false) }
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
