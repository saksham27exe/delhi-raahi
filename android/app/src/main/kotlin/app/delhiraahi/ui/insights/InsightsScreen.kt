package app.delhiraahi.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.delhiraahi.data.Api
import app.delhiraahi.data.ApiException
import app.delhiraahi.data.DELHI_ZONE
import app.delhiraahi.data.DayBucket
import app.delhiraahi.data.Hotspot
import app.delhiraahi.data.Lang
import app.delhiraahi.data.RouteDay
import app.delhiraahi.data.RouteHit
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.BusMap
import app.delhiraahi.ui.common.Chip
import app.delhiraahi.ui.common.CrowdBadge
import app.delhiraahi.ui.common.Divider
import app.delhiraahi.ui.common.ListRow
import app.delhiraahi.ui.common.MapController
import app.delhiraahi.ui.common.MapLine
import app.delhiraahi.ui.common.RoutePill
import app.delhiraahi.ui.common.crowd
import app.delhiraahi.ui.common.routeName
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate

private val LEVEL = mapOf("LOW" to 1, "MODERATE" to 2, "HIGH" to 3, "VERY_HIGH" to 4)
private val TIMES = listOf("07:00", "08:30", "10:00", "13:00", "17:00", "18:30", "21:00")

private fun JsonElement?.at(vararg keys: String): JsonElement? =
    keys.fold(this) { e, k -> (e as? JsonObject)?.get(k) }

private fun JsonElement?.num(vararg keys: String): Double? = (at(*keys) as? JsonPrimitive)?.doubleOrNull

private fun fmt(x: Double?, d: Int = 3) = x?.let { "%.${d}f".format(it) } ?: "–"
private fun pct(x: Double?) = x?.let { "%.1f%%".format(it * 100) } ?: "–"

/** Route day pattern, busiest stretches and the model card. Everything shown comes from the server. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(lang: Lang, onBack: () -> Unit, initialRoute: RouteHit? = null) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val today = remember { LocalDate.now(DELHI_ZONE).toString() }
    val net: (Throwable) -> String = { if (it is ApiException && it.status == 0) s.errNetwork else it.message ?: s.errNetwork }

    var q by remember { mutableStateOf(initialRoute?.route?.let(::routeName).orEmpty()) }
    var hits by remember { mutableStateOf<List<RouteHit>>(emptyList()) }
    var route by remember { mutableStateOf(initialRoute) }
    var day by remember { mutableStateOf<RouteDay?>(null) }
    var dayErr by remember { mutableStateOf<String?>(null) }

    var time by remember { mutableStateOf("08:30") }
    var spots by remember { mutableStateOf<List<Hotspot>?>(null) }
    var spotErr by remember { mutableStateOf<String?>(null) }
    var card by remember { mutableStateOf<JsonObject?>(null) }
    var cardErr by remember { mutableStateOf(false) }
    val map = remember { MapController() }

    LaunchedEffect(q) {
        if (q.isBlank() || route?.route?.let(::routeName) == q.trim()) { hits = emptyList(); return@LaunchedEffect }
        delay(220)
        hits = runCatching { Api.searchRoutes(q.trim()).results.take(6) }.getOrDefault(emptyList())
    }
    LaunchedEffect(route) {
        val r = route ?: return@LaunchedEffect
        day = null; dayErr = null
        runCatching { Api.routeDay(r.route_id, today) }.onSuccess { day = it }.onFailure { dayErr = net(it) }
    }
    LaunchedEffect(time) {
        spots = null; spotErr = null
        runCatching { Api.hotspots(today, time).segments }.onSuccess { spots = it }.onFailure { spotErr = net(it) }
    }
    LaunchedEffect(Unit) { runCatching { Api.modelCard() }.onSuccess { card = it }.onFailure { cardErr = true } }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back, tint = t.text) }
            Text(s.insightsTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = t.text,
                modifier = Modifier.semantics { heading() })
        }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(horizontal = Space.lg).padding(bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                // 1. When is my bus quiet?
                Header(s.routeQuiet)
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(Radius.md)).background(t.surface).padding(horizontal = Space.md),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = t.text, modifier = Modifier.size(20.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text(s.routePh, color = t.muted, style = MaterialTheme.typography.bodyLarge)
                        BasicTextField(
                            q, { v -> q = v; if (route != null && v != route?.route?.let(::routeName)) route = null }, singleLine = true,
                            textStyle = TextStyle(color = t.text, fontSize = 16.sp), cursorBrush = SolidColor(t.text),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = s.routePh },
                        )
                    }
                }
                hits.forEach { h ->
                    ListRow(Icons.Outlined.DirectionsBus, routeName(h.route), subtitle = h.description, chevron = true) {
                        route = h; q = routeName(h.route); hits = emptyList()
                    }
                }
                val r = route
                if (r == null && hits.isEmpty()) Text(s.pickRoute, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                if (r != null) Card {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        RoutePill(r.route)
                        if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodyMedium, color = t.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    }
                    Text(s.dayPattern, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                    val d = day
                    when {
                        dayErr != null -> Text(dayErr!!, color = t.veryHigh.fg)
                        d == null -> Spinner()
                        d.buckets.all { it.category !in LEVEL } -> Text(s.noData, color = t.muted)
                        else -> {
                            DayChart(d.buckets)
                            if (d.quietest != null && d.busiest != null)
                                Text(s.quietest(d.quietest, d.busiest), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = t.text)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                LEVEL.keys.forEach { CrowdBadge(it, short = true) }
                            }
                        }
                    }
                }
                Text(s.crowdNote, style = MaterialTheme.typography.labelSmall, color = t.muted)

                Divider()

                // 2. Busiest stretches
                Header(s.hotspots)
                Text(s.hotspotsHint, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    TIMES.forEach { x -> Chip(x, Icons.Outlined.Schedule, selected = time == x) { time = x } }
                }
                val lines = remember(spots, t) {
                    spots.orEmpty().map { h -> MapLine(listOf(h.lat to h.lon, h.to_lat to h.to_lon), t.crowd(h.category).line.toArgb(), 6f) }
                }
                Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(Radius.lg)).border(0.5.dp, t.border, RoundedCornerShape(Radius.lg))) {
                    BusMap(Modifier.fillMaxSize(), map, emptyList(), t.dark, 0.dp, showMyLocation = false, lines = lines,
                        fitKey = if (spots.isNullOrEmpty()) null else time)
                }
                when {
                    spotErr != null -> Text(spotErr!!, color = t.veryHigh.fg)
                    spots == null -> Spinner()
                }
                spots.orEmpty().take(8).forEach { h ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Space.sm)
                            .semantics(mergeDescendants = true) { contentDescription = "${h.route?.let(::routeName) ?: ""} ${h.from_stop} → ${h.to_stop}, ${s.crowd(h.category)}" },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        h.route?.let { RoutePill(it, small = true) }
                        Text("${h.from_stop} → ${h.to_stop}", style = MaterialTheme.typography.bodyMedium, color = t.text, maxLines = 2, modifier = Modifier.weight(1f))
                        CrowdBadge(h.category, short = true)
                    }
                    Divider()
                }

                // 3. How good are the numbers?
                Header(s.modelTitle)
                val c = card
                when {
                    cardErr -> Text(s.errNetwork, color = t.veryHigh.fg)
                    c == null -> Spinner()
                    else -> {
                        Card {
                            Text(s.routeModel, style = MaterialTheme.typography.titleSmall, color = t.text)
                            Kv(s.ours, fmt(c.num("route_model", "test", "lightgbm", "macro_f1_demand_class")))
                            Kv(s.baseline, fmt(c.num("route_model", "test", "daytype_seasonal_avg", "macro_f1_demand_class")))
                            c.num("route_model", "interval_coverage_test", "all_buckets")?.let {
                                Text(s.coverage(Math.round(it * 100).toInt()), style = MaterialTheme.typography.labelSmall, color = t.muted)
                            }
                            Divider()
                            Text(s.segModel, style = MaterialTheme.typography.titleSmall, color = t.text)
                            Kv(s.ours, fmt(c.num("segment_model", "model", "mae")))
                            Kv(s.baseline, fmt(c.num("segment_model", "historical_average_baseline", "mae")))
                            Text(s.honest, style = MaterialTheme.typography.labelSmall, color = t.muted)
                            Divider()
                            Text(s.fares, style = MaterialTheme.typography.titleSmall, color = t.text)
                            Kv(s.exactFare, pct(c.num("fare_validation", "all_resolved_tickets", "exact_match")))
                            Kv(s.withinSlab, pct(c.num("fare_validation", "all_resolved_tickets", "within_one_slab")))
                        }
                        val lim = (c["limitations"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
                        if (lim.isNotEmpty()) {
                            Text(s.limitations + if (lang == Lang.HI) " " + s.limitationsEnOnly else "",
                                style = MaterialTheme.typography.titleMedium, color = t.text,
                                modifier = Modifier.padding(top = Space.md).semantics { heading() })
                            lim.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = t.muted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) = Text(
    text, style = MaterialTheme.typography.headlineSmall, color = LocalExtra.current.text,
    modifier = Modifier.padding(top = Space.md).semantics { heading() },
)

@Composable
private fun Spinner() = CircularProgressIndicator(Modifier.padding(Space.md).size(22.dp), strokeWidth = 2.dp, color = LocalExtra.current.text)

@Composable
private fun Card(content: @Composable () -> Unit) {
    val t = LocalExtra.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(t.card).border(0.5.dp, t.border, RoundedCornerShape(Radius.lg)).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) { content() }
}

@Composable
private fun Kv(k: String, v: String) {
    val t = LocalExtra.current
    Row(Modifier.fillMaxWidth()) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = t.muted, modifier = Modifier.weight(1f))
        Text(v, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = t.text)
    }
}

/** One bar per time bucket; height = crowd level, colour = category. Read aloud as a list for TalkBack. */
@Composable
private fun DayChart(buckets: List<DayBucket>) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = t.muted, fontSize = 11.sp)
    val spoken = buckets.filter { it.category in LEVEL }.joinToString(", ") { "${it.time} ${s.crowdShort(it.category)}" }
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = s.chartA11y(spoken) }) {
        val labelH = 18.dp.toPx()
        val h = size.height - labelH
        val bw = size.width / buckets.size.coerceAtLeast(1)
        buckets.forEachIndexed { i, b ->
            val lvl = LEVEL[b.category] ?: return@forEachIndexed
            val bh = lvl / 4f * (h - 4)
            drawRoundRect(
                t.crowd(b.category).line, topLeft = Offset(i * bw + 1, h - bh), size = Size((bw - 2).coerceAtLeast(1f), bh),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        buckets.forEachIndexed { i, b ->
            val hr = b.time.take(2).toIntOrNull() ?: return@forEachIndexed
            if (b.time.endsWith(":00") && hr % 4 == 0) {
                val layout = measurer.measure(b.time, labelStyle)
                val x = (i * bw).coerceAtMost(size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, h + 3.dp.toPx()))
            }
        }
    }
}
