package app.delhiraahi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Journey
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.theme.CrowdColor
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Tokens
import kotlin.math.roundToInt

fun Tokens.crowd(cat: String?): CrowdColor = when (cat) {
    "LOW" -> low; "MODERATE" -> moderate; "HIGH" -> high; "VERY_HIGH" -> veryHigh; else -> unknown
}

/** "210DOWN" → "210". */
fun routeName(route: String) = route.replace(Regex("(UP|DOWN)$"), "")

private fun money(x: Double) = if (x % 1.0 == 0.0) "₹${x.toInt()}" else "₹${"%.1f".format(x)}"

/** Digital fare (what an e-ticket costs), a range when the bus type is unknown. */
fun fareText(j: Journey): String? {
    val f = j.fare ?: return null
    if (f.total_digital_max <= 0.0) return null
    return if (f.total_digital_min == f.total_digital_max) money(f.total_digital_min)
    else "${money(f.total_digital_min)}–${money(f.total_digital_max)}"
}

fun Double.mins() = this.roundToInt()

/** Coloured pill with a dot; the level is always written out, never colour-only. */
@Composable
fun CrowdBadge(cat: String, short: Boolean = false) {
    val s = LocalStrings.current
    val c = LocalExtra.current.crowd(cat)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c.fg))
        Text(if (short) s.crowdShort(cat) else s.crowd(cat), color = c.fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** Ink route-number chip (paper-coloured text). */
@Composable
fun RoutePill(route: String, small: Boolean = false) {
    val t = LocalExtra.current
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(t.text).padding(horizontal = if (small) 6.dp else 8.dp, vertical = if (small) 1.dp else 2.dp)) {
        Text(routeName(route), color = t.bg, style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

/** Walk › 210 › 423: what you ride, in order. */
@Composable
fun LegChain(j: Journey) {
    val t = LocalExtra.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (j.walk.origin_min >= 1) {
            Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = t.muted, modifier = Modifier.size(18.dp))
            Text("${j.walk.origin_min.mins()}", style = MaterialTheme.typography.labelMedium, color = t.muted)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = t.muted, modifier = Modifier.size(16.dp))
        }
        j.legs.forEachIndexed { i, l ->
            if (i > 0) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = t.muted, modifier = Modifier.size(16.dp))
            RoutePill(l.route, small = true)
        }
    }
}

/** Horizontal bar: one block per stretch, width ∝ minutes, colour = expected crowding. */
@Composable
fun CrowdStrip(j: Journey, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val segs = j.legs.flatMap { it.segments }
    if (segs.isEmpty()) return
    Row(
        modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).semantics { contentDescription = "${s.crowdAlong}: ${s.crowd(j.crowd_level)}" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segs.forEach { sg ->
            Box(Modifier.weight(sg.minutes.coerceAtLeast(0.3).toFloat()).fillMaxHeight().background(t.crowd(sg.crowd?.category).line))
        }
    }
}

/** Map lines/pins for one journey: walks grey, each stretch in its crowd colour. */
fun journeyMap(j: Journey, t: Tokens, from: Pair<Double, Double>?, to: Pair<Double, Double>?): Pair<List<MapLine>, List<MapPin>> {
    val lines = mutableListOf<MapLine>()
    val pins = mutableListOf<MapPin>()
    val walkColor = t.muted.toArgb()
    val first = j.legs.firstOrNull()?.segments?.firstOrNull()
    val last = j.legs.lastOrNull()?.segments?.lastOrNull()
    fun path(p: List<List<Double>>?) = p?.filter { it.size >= 2 }?.map { it[0] to it[1] }?.takeIf { it.size >= 2 }
    if (from != null && first != null) lines += MapLine(
        path(j.walk.origin_path)?.let { listOf(from) + it + listOf(first.lat to first.lon) } ?: listOf(from, first.lat to first.lon), walkColor, 4f, dashed = true)
    j.legs.forEachIndexed { i, leg ->
        leg.segments.forEach { sg -> lines += MapLine(listOf(sg.lat to sg.lon, sg.to_lat to sg.to_lon), t.crowd(sg.crowd?.category).line.toArgb(), 6f) }
        val b = leg.segments.firstOrNull()
        val a = leg.segments.lastOrNull()
        if (b != null) pins += MapPin("b$i", b.lat, b.lon, leg.board_stop, if (i == 0) MapPin.Kind.FROM else MapPin.Kind.STOP)
        if (a != null) pins += MapPin("a$i", a.to_lat, a.to_lon, leg.alight_stop, if (i == j.legs.lastIndex && to == null) MapPin.Kind.TO else MapPin.Kind.STOP)
        val next = j.legs.getOrNull(i + 1)?.segments?.firstOrNull()
        if (a != null && next != null) lines += MapLine(listOf(a.to_lat to a.to_lon, next.lat to next.lon), walkColor, 4f, dashed = true)
    }
    if (to != null && last != null) pins += MapPin("dest", to.first, to.second, "", MapPin.Kind.TO)
    if (to != null && last != null) lines += MapLine(
        path(j.walk.destination_path)?.let { listOf(last.to_lat to last.to_lon) + it + listOf(to) } ?: listOf(last.to_lat to last.to_lon, to), walkColor, 4f, dashed = true)
    return lines to pins
}
