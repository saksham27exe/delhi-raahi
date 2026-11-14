package app.delhiraahi.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Journey
import app.delhiraahi.data.WalkOption
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.i18n.Strings
import app.delhiraahi.ui.common.CrowdBadge
import app.delhiraahi.ui.common.crowd
import app.delhiraahi.ui.common.fareText
import app.delhiraahi.ui.common.mins
import app.delhiraahi.ui.common.routeName
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.theme.TabularNums
import app.delhiraahi.ui.theme.Tokens
import java.time.LocalTime
import kotlin.math.roundToInt
import app.delhiraahi.i18n.OptionText
import app.delhiraahi.data.TrackResult
import app.delhiraahi.data.Api
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Time ribbons: every trip drawn on one shared clock, so you compare when you leave and when you
 * arrive at a glance. Dotted = walk, hollow = wait, solid = on the bus, coloured by expected crowding.
 */

/** Minutes since midnight for "HH:MM". */
internal fun clockMin(hhmm: String): Int? {
    val p = hhmm.split(":")
    if (p.size < 2) return null
    val h = p[0].trim().toIntOrNull() ?: return null
    val m = p[1].take(2).toIntOrNull() ?: return null
    return h * 60 + m
}

internal fun clockText(min: Float): String {
    val m = ((floor(min + 0.5f).toInt() % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

internal sealed interface Piece { val a: Float; val b: Float }
internal data class WalkPiece(override val a: Float, override val b: Float) : Piece
internal data class WaitPiece(override val a: Float, override val b: Float) : Piece
/** [parts]: relative weight and colour of each stretch of the ride. */
internal data class RidePiece(override val a: Float, override val b: Float, val route: String, val parts: List<Pair<Float, Color>>) : Piece

internal class Ribbon(val start: Float, val end: Float, val pieces: List<Piece>)

/** Times after midnight that belong to the same evening stay after it, relative to [ref]. */
private fun unwrap(m: Int, ref: Int): Float = (if (m < ref - 360) m + 1440 else m).toFloat()

internal fun ribbonOf(j: Journey, t: Tokens, ref: Int): Ribbon? {
    val dep = clockMin(j.departure)?.let { unwrap(it, ref) } ?: return null
    var arr = clockMin(j.arrival)?.let { unwrap(it, ref) } ?: return null
    if (arr < dep) arr += 1440
    val wo = j.walk.origin_min.toFloat()
    val start = dep - j.wait_minutes.toFloat() - wo
    val out = mutableListOf<Piece>()
    if (wo >= 0.5f) out += WalkPiece(start, start + wo)
    if (dep - (start + wo) >= 0.5f) out += WaitPiece(start + wo, dep)
    var prevEnd = dep
    j.legs.forEachIndexed { i, leg ->
        var ld = clockMin(leg.departure)?.let { unwrap(it, ref) } ?: prevEnd
        if (ld < prevEnd - 1) ld += 1440
        var la = clockMin(leg.arrival)?.let { unwrap(it, ref) } ?: (ld + leg.in_vehicle_minutes.toFloat())
        if (la < ld) la += 1440
        if (i > 0) {
            val walk = (j.connections.getOrNull(i - 1)?.walk_min ?: j.walk.transfer_min).toFloat()
            val w = walk.coerceAtMost(ld - prevEnd).coerceAtLeast(0f)
            if (w >= 0.5f) out += WalkPiece(prevEnd, prevEnd + w)
            if (ld - (prevEnd + w) >= 0.5f) out += WaitPiece(prevEnd + w, ld)
        }
        val parts = leg.segments.map { it.minutes.coerceAtLeast(0.3).toFloat() to t.crowd(it.crowd?.category).line }
            .ifEmpty { listOf(1f to t.crowd(j.crowd_level).line) }
        out += RidePiece(ld, la, leg.route, parts)
        prevEnd = la
    }
    if (arr - prevEnd >= 0.5f) out += WalkPiece(prevEnd, arr)
    return Ribbon(start, maxOf(arr, prevEnd), out)
}

internal class ClockAxis(val lo: Float, val hi: Float, val ticks: List<Float>, val now: Float?) {
    fun x(m: Float, w: Float) = ((m - lo) / (hi - lo)).coerceIn(0f, 1f) * w
}

internal fun axisFor(ribbons: List<Ribbon>, now: Float?): ClockAxis? {
    if (ribbons.isEmpty()) return null
    val lo0 = ribbons.minOf { it.start }
    val hi0 = ribbons.maxOf { it.end }
    val span = (hi0 - lo0).coerceAtLeast(10f)
    val step = when { span <= 50 -> 10f; span <= 120 -> 15f; span <= 240 -> 30f; else -> 60f }
    val lo = floor(lo0 / 5f) * 5f
    val hi = ceil((lo0 + span) / 5f) * 5f
    val ticks = generateSequence(ceil(lo / step) * step) { it + step }.takeWhile { it <= hi }.toList()
    return ClockAxis(lo, hi, ticks, now?.takeIf { it in lo..hi })
}

/** The current time on the same unwrapped scale as the trips. */
internal fun nowOnScale(ref: Int): Float {
    val n = LocalTime.now()
    return unwrap(n.hour * 60 + n.minute, ref) + n.second / 60f
}

/** Clock labels above the ribbons. */
@Composable
internal fun AxisLabels(axis: ClockAxis, modifier: Modifier = Modifier) {
    val t = LocalExtra.current
    val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums)
    BoxWithConstraints(modifier.fillMaxWidth().height(18.dp).clearAndSetSemantics { }) {
        val w = maxWidth
        axis.ticks.forEach { m ->
            val x = w * ((m - axis.lo) / (axis.hi - axis.lo))
            Text(clockText(m), style = style, color = t.muted,
                modifier = Modifier.offset(x = (x - 18.dp).coerceIn(0.dp, w - 36.dp)).width(36.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** One trip's ribbon on the shared clock. */
@Composable
internal fun RibbonBar(r: Ribbon, axis: ClockAxis, modifier: Modifier = Modifier) {
    val t = LocalExtra.current
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = t.bg)
    Canvas(modifier.fillMaxWidth().height(30.dp)) {
        val w = size.width
        val cy = size.height / 2
        axis.ticks.forEach { m ->
            val x = axis.x(m, w)
            drawLine(t.border, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
        r.pieces.forEach { p ->
            val x0 = axis.x(p.a, w)
            val x1 = axis.x(p.b, w).coerceAtLeast(x0 + 2.dp.toPx())
            when (p) {
                is WalkPiece -> dots(x0, x1, cy, t.muted)
                is WaitPiece -> {
                    val h = 12.dp.toPx()
                    drawRoundRect(t.muted.copy(alpha = 0.75f), Offset(x0 + 1.dp.toPx(), cy - h / 2), Size((x1 - x0 - 2.dp.toPx()).coerceAtLeast(1f), h),
                        CornerRadius(h / 2), style = Stroke(1.5.dp.toPx()))
                }
                is RidePiece -> {
                    val h = 22.dp.toPx()
                    val rr = RoundRect(x0, cy - h / 2, x1, cy + h / 2, CornerRadius(6.dp.toPx()))
                    clipPath(Path().apply { addRoundRect(rr) }) {
                        val total = p.parts.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.01f)
                        var x = x0
                        p.parts.forEach { (wt, c) ->
                            val dx = (x1 - x0) * wt / total
                            drawRect(c, Offset(x, cy - h / 2), Size(dx + 0.5f, h))
                            x += dx
                        }
                    }
                    // route number in a small ink tag at the start of the ride
                    val tl = measurer.measure(routeName(p.route), label)
                    val pad = 4.dp.toPx()
                    val tagW = tl.size.width + 2 * pad
                    val tagH = h - 6.dp.toPx()
                    val tx = x0 + 3.dp.toPx()
                    drawRoundRect(t.text, Offset(tx, cy - tagH / 2), Size(tagW, tagH), CornerRadius(4.dp.toPx()))
                    drawText(tl, topLeft = Offset(tx + pad, cy - tl.size.height / 2f))
                }
            }
        }
        axis.now?.let { n ->
            val x = axis.x(n, w)
            drawLine(t.primary, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

private fun DrawScope.dots(x0: Float, x1: Float, cy: Float, c: Color) {
    val gap = 6.dp.toPx()
    val r = 2.2.dp.toPx()
    var x = x0 + r
    if (x1 - x0 < 2 * r) { drawCircle(c, r, Offset((x0 + x1) / 2, cy)); return }
    while (x <= x1 - r) { drawCircle(c, r, Offset(x, cy)); x += gap }
}

/** Walk · Wait · Bus, by crowding — the key to the ribbons. */
@Composable
internal fun RibbonLegend() {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val style = MaterialTheme.typography.labelSmall
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "${s.legendWalk}, ${s.legendWait}, ${s.legendRide}" }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(Modifier.size(22.dp, 10.dp)) { dots(0f, size.width, size.height / 2, t.muted) }
            Text(s.legendWalk, style = style, color = t.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(Modifier.size(22.dp, 10.dp)) {
                drawRoundRect(t.muted, Offset(1f, 1f), Size(size.width - 2, size.height - 2), CornerRadius(size.height / 2), style = Stroke(1.5.dp.toPx()))
            }
            Text(s.legendWait, style = style, color = t.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.clip(RoundedCornerShape(3.dp))) {
                listOf(t.low, t.moderate, t.high, t.veryHigh).forEach { Box(Modifier.size(8.dp, 12.dp).background(it.line)) }
            }
            Text(s.legendRide, style = style, color = t.muted)
        }
    }
}

private fun a11y(s: Strings, j: Journey, r: Ribbon): String = listOfNotNull(
    "${clockText(r.start)} – ${j.arrival}",
    s.minutes(j.duration_minutes.mins()),
    if (j.transfers == 0) s.direct else s.changes(j.transfers),
    j.legs.joinToString(", ") { routeName(it.route) },
    s.crowd(j.crowd_level),
    fareText(j),
).joinToString(", ")

/** One row: leave → arrive, duration and fare, the ribbon, then what kind of trip it is. */
/** Minutes from now until a "HH:MM" time today (wrapping round midnight), or null if unreadable. */
internal fun minsFromNow(hhmm: String): Int? {
    val m = clockMin(hhmm) ?: return null
    val n = LocalTime.now().let { it.hour * 60 + it.minute }
    var d = m - n
    if (d < -720) d += 1440 else if (d > 720) d -= 1440
    return d
}

/** One small icon + one plain line. */
@Composable
private fun OptLine(icon: ImageVector, text: String, tag: String? = null, strong: Boolean = false, tint: Color? = null, busType: String? = null) {
    val t = LocalExtra.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Icon(icon, contentDescription = null, tint = tint ?: t.muted, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Text(
            buildAnnotatedString {
                append(text)
                if (tag != null) withStyle(SpanStyle(color = t.muted, fontWeight = FontWeight.Normal)) { append("  · $tag") }
            },
            style = MaterialTheme.typography.bodyMedium, color = tint ?: t.text,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f, fill = busType == null),
        )
        if (busType != null) app.delhiraahi.ui.common.BusTypeTag(busType, Modifier.weight(1f, fill = false).padding(top = 1.dp))
    }
}

/**
 * One trip: when to leave and when you arrive, its ribbon, then plain lines —
 * how far to walk, which bus and when (timetable, plus the live bus if one is near), changes, and arrival.
 */
@Composable
internal fun RibbonRow(j: Journey, r: Ribbon, axis: ClockAxis, selected: Boolean, toName: String?, live: TrackResult?, onClick: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val o = OptionText.of(s === app.delhiraahi.i18n.Hi)
    val num = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularNums)
    val first = j.legs.firstOrNull()
    val last = j.legs.lastOrNull()
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(if (selected) t.card else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected; contentDescription = a11y(s, j, r) },
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (selected) t.primary else Color.Transparent))
        Column(Modifier.weight(1f).padding(horizontal = Space.md, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Leave … Reach …, then length and fare
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.leave(clockText(r.start)), style = num, color = t.text, modifier = Modifier.weight(1f))
                Text(o.reach(j.arrival), style = num, color = t.text, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    listOfNotNull(
                        s.minutes(j.duration_minutes.mins()),
                        if (j.transfers == 0) s.direct else s.changes(j.transfers),
                        fareText(j),
                        j.labels.takeIf { it.isNotEmpty() }?.joinToString(" · ") { s.label(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = t.muted, modifier = Modifier.weight(1f), maxLines = 1,
                )
                CrowdBadge(j.crowd_level, short = true)
            }
            RibbonBar(r, axis)
            if (first != null) {
                // 1. Walk to the first stop
                val walkMin = j.walk.origin_min.mins()
                if (walkMin >= 1) OptLine(Icons.AutoMirrored.Outlined.DirectionsWalk, o.walkTo(walkMin, j.walk.origin_m?.roundToInt(), first.board_stop))
                else OptLine(Icons.AutoMirrored.Outlined.DirectionsWalk, o.atStop(first.board_stop))
                // 2. Each bus, with the change between them
                j.legs.forEachIndexed { k, leg ->
                    if (k > 0) j.connections.getOrNull(k - 1)?.let { c ->
                        OptLine(Icons.Outlined.SyncAlt, o.change(c.board, c.walk_min.mins(), c.wait_min.mins()))
                    }
                    val inMin = if (k == 0) minsFromNow(leg.departure)?.takeIf { it in -1..120 } else null
                    OptLine(Icons.Outlined.DirectionsBus, o.busAt(routeName(leg.route), leg.departure, inMin), tag = o.tagTimetable, strong = true,
                        busType = leg.fare?.bus_type)
                    // Live: the real bus of this route nearest to the first stop
                    if (k == 0 && live?.phase == "approaching" && live.eta_board_min != null) {
                        val m = live.eta_board_min.roundToInt()
                        OptLine(Icons.Outlined.Sensors, o.liveNext(routeName(leg.route), live.stops_away, m) + if (m < walkMin) o.liveTooSoon else "",
                            tag = o.tagLive, tint = t.low.fg)
                    }
                }
                // 3. Arrival
                OptLine(Icons.Outlined.Place, o.reachAt(toName ?: last!!.alight_stop, j.arrival, j.walk.destination_min.mins(), last!!.alight_stop))
            }
        }
    }
}

/** Walking the whole way, as a ribbon on the same clock. */
@Composable
internal fun WalkRibbonRow(w: WalkOption, axis: ClockAxis, start: Float, selected: Boolean, onClick: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val r = Ribbon(start, start + w.minutes.toFloat(), listOf(WalkPiece(start, start + w.minutes.toFloat())))
    val detail = s.walkDetail(w.minutes.mins(), w.distance_m.toInt(), w.basis != "osm_road")
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(if (selected) t.card else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected; contentDescription = "${s.walkWhole}, $detail" },
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (selected) t.primary else Color.Transparent))
        Column(Modifier.weight(1f).padding(horizontal = Space.md, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${clockText(r.start)} → ${clockText(r.end)}", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularNums),
                    color = t.text, modifier = Modifier.weight(1f))
                Text(s.walkWhole, style = MaterialTheme.typography.bodyMedium, color = t.muted)
            }
            RibbonBar(r, axis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = t.muted)
        }
    }
}

/** All trips on one clock: the axis, a legend, then one ribbon per trip. */
@Composable
fun TimeRibbons(
    journeys: List<Journey>, shown: List<Int>, selected: Int, onSelect: (Int) -> Unit,
    walk: WalkOption?, walkSelected: Boolean, showWalk: Boolean, onWalk: () -> Unit, toName: String? = null,
) {
    val t = LocalExtra.current
    // Live check for the first bus of each option, only when it leaves within the hour (not for later trips).
    val live = remember(journeys) { mutableStateMapOf<String, TrackResult>() }
    LaunchedEffect(journeys) {
        journeys.filter { j -> j.legs.firstOrNull()?.let { minsFromNow(it.departure) }?.let { it in -5..60 } == true }.forEach { j ->
            val l = j.legs.first()
            runCatching { Api.track(l.route_id, l.board_stop_id, l.alight_stop_id, null, emptySet()) }.onSuccess { live[j.journey_id] = it }
        }
    }
    val ref = journeys.firstNotNullOfOrNull { clockMin(it.departure) } ?: LocalTime.now().let { it.hour * 60 + it.minute }
    val ribbons = remember(journeys, t) { journeys.map { ribbonOf(it, t, ref) } }
    val now = remember(ref) { nowOnScale(ref) }
    val visible = shown.mapNotNull { i -> ribbons[i]?.let { i to it } }
    val walkStart = visible.minOfOrNull { it.second.start } ?: ribbons.filterNotNull().minOfOrNull { it.start } ?: now
    val walkRibbon = walk?.takeIf { showWalk }?.let { Ribbon(walkStart, walkStart + it.minutes.toFloat(), emptyList()) }
    val axis = axisFor(visible.map { it.second } + listOfNotNull(walkRibbon), now) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        RibbonLegend()
        Spacer(Modifier.height(Space.xs))
        Row { Spacer(Modifier.width(4.dp + Space.md)); Box(Modifier.weight(1f).padding(end = Space.md)) { AxisLabels(axis) } }
        visible.forEach { (i, r) -> RibbonRow(journeys[i], r, axis, selected = !walkSelected && i == selected, toName = toName, live = live[journeys[i].journey_id]) { onSelect(i) } }
        if (walk != null && showWalk) WalkRibbonRow(walk, axis, walkStart, selected = walkSelected, onClick = onWalk)
    }
}
