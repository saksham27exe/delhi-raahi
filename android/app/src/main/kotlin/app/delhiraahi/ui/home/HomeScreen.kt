package app.delhiraahi.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Api
import app.delhiraahi.data.DeviceLocation
import app.delhiraahi.data.Lang
import app.delhiraahi.data.LeaveAt
import app.delhiraahi.data.NearbyStop
import app.delhiraahi.data.PickedPlace
import app.delhiraahi.data.Places
import app.delhiraahi.data.Slot
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.i18n.Strings
import app.delhiraahi.ui.common.Chip
import app.delhiraahi.ui.common.ListRow
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.trip.leaveLabel

private sealed interface Nearby {
    data object NoPermission : Nearby
    data object Locating : Nearby
    data object NoFix : Nearby
    data class Loaded(val stops: List<NearbyStop>) : Nearby
    data object Error : Nearby
}

/** "now", "in 15 min", "tomorrow 08:30": the leave time as it reads inside the sentence. */
private fun whenSlot(s: Strings, l: LeaveAt): String =
    if (l == LeaveAt.Now) s.now.lowercase() else leaveLabel(s, l).replaceFirstChar { it.lowercase() }

/**
 * Home is a sentence with blanks: "From [where I am] to [where?] leaving [now]".
 * It is the same sentence the voice assistant understands, so tapping and speaking feel alike.
 */
@Composable
fun HomeScreen(
    lang: Lang, places: Places, onToggleLang: () -> Unit, onSetPlace: (Slot) -> Unit, onWhereTo: () -> Unit,
    onGo: (PickedPlace?, PickedPlace) -> Unit, onClearRecent: () -> Unit, onInsights: () -> Unit, onVoice: () -> Unit,
    onWhereFrom: () -> Unit = onWhereTo, leaveAt: LeaveAt = LeaveAt.Now, onLeave: (LeaveAt) -> Unit = {},
    onPickFrom: (PickedPlace) -> Unit = {},
) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val ctx = LocalContext.current
    var me by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var nearby by remember { mutableStateOf<Nearby>(if (DeviceLocation.granted(ctx)) Nearby.Locating else Nearby.NoPermission) }
    var attempt by remember { mutableIntStateOf(0) }
    var whenOpen by remember { mutableStateOf(false) }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) attempt++ else nearby = Nearby.NoPermission
    }
    val requestLocation = { askPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    LaunchedEffect(Unit) { if (!DeviceLocation.granted(ctx)) requestLocation() }

    LaunchedEffect(attempt) {
        if (!DeviceLocation.granted(ctx)) return@LaunchedEffect
        nearby = Nearby.Locating
        val loc = DeviceLocation.current(ctx)
        if (loc == null) { nearby = Nearby.NoFix; return@LaunchedEffect }
        me = loc.latitude to loc.longitude
        nearby = runCatching { Nearby.Loaded(Api.nearbyStops(loc.latitude, loc.longitude, 1000).results.take(5)) }.getOrElse { Nearby.Error }
    }

    // A missing origin means "from where I am"; without a fix the search screen asks for one.
    val go: (PickedPlace?, PickedPlace) -> Unit = { from, to ->
        onGo(from ?: me?.let { PickedPlace.here(s.currentLocation, it.first, it.second) }, to)
    }

    Box(Modifier.fillMaxSize().background(t.bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = MaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = Space.xl).padding(bottom = Space.xxl),
        ) {
            // Top line: app name + language
            Row(Modifier.fillMaxWidth().padding(top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                Text("DelhiRaahi", style = MaterialTheme.typography.titleMedium, color = t.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Row(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).border(1.dp, t.border, RoundedCornerShape(50))
                        .clickable(role = Role.Button, onClick = onToggleLang).semantics { contentDescription = s.switchLang }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.Language, contentDescription = null, tint = t.text, modifier = Modifier.size(18.dp))
                    Text(s.otherLang, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = t.text)
                }
            }

            // The sentence
            Column(Modifier.padding(top = Space.xxl).semantics { heading() }, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                SentenceLine(s.sFromPre, s.yourLocation, s.sFromPost, filled = true, label = "${s.from}: ${s.yourLocation}", onClick = onWhereFrom)
                SentenceLine(s.sToPre, s.whereQ, s.sToPost, filled = false, label = s.whereTo, onClick = onWhereTo, accent = true)
                Box {
                    SentenceLine(s.sWhenPre, whenSlot(s, leaveAt), s.sWhenPost, filled = true,
                        label = leaveLabel(s, leaveAt), onClick = { whenOpen = true })
                    DropdownMenu(expanded = whenOpen, onDismissRequest = { whenOpen = false }) {
                        LeaveAt.options.forEach { o ->
                            DropdownMenuItem(text = { Text(leaveLabel(s, o), style = MaterialTheme.typography.bodyLarge) },
                                onClick = { onLeave(o); whenOpen = false })
                        }
                    }
                }
            }

            // …or say it
            Row(
                Modifier.padding(top = Space.xl).fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).border(1.5.dp, t.primary, RoundedCornerShape(Radius.lg))
                    .clickable(role = Role.Button, onClick = onVoice).padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(t.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, contentDescription = null, tint = t.onPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(s.sayIt, style = MaterialTheme.typography.titleMedium, color = t.text)
                    Text(s.sayItSub, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                }
            }

            // Saved places fill the "to" blank in one tap
            Row(Modifier.padding(top = Space.lg).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                val home = places.home
                val work = places.work
                if (home != null) Chip(s.home, Icons.Outlined.Home) { go(null, home.picked()) }
                else Chip(s.addHome, Icons.Outlined.Home) { onSetPlace(Slot.HOME) }
                if (work != null) Chip(s.work, Icons.Outlined.Work) { go(null, work.picked()) }
                else Chip(s.addWork, Icons.Outlined.Work) { onSetPlace(Slot.WORK) }
            }

            if (places.recent.isNotEmpty()) {
                SectionHead(s.recentTrips, action = s.clearRecent, onAction = onClearRecent)
                places.recent.forEach { r ->
                    RecentRow(r.from?.label ?: s.yourLocation, r.to.label) { go(r.from?.picked(), r.to.picked()) }
                }
            }

            SectionHead(s.nearby)
            when (val n = nearby) {
                Nearby.Locating -> Text(s.locating, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                Nearby.NoPermission, Nearby.NoFix -> Text(s.noLocation, style = MaterialTheme.typography.bodyMedium, color = t.muted,
                    modifier = Modifier.clickable(role = Role.Button, onClick = requestLocation).padding(vertical = Space.xs))
                Nearby.Error -> Text(s.errNetwork, style = MaterialTheme.typography.bodyMedium, color = t.veryHigh.fg,
                    modifier = Modifier.clickable(role = Role.Button) { attempt++ }.padding(vertical = Space.xs))
                is Nearby.Loaded -> if (n.stops.isEmpty()) Text(s.noStopsNearby, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                else n.stops.forEach { st ->
                    ListRow(Icons.Outlined.DirectionsBus, st.name, subtitle = s.metres(st.distance_m), chevron = true) {
                        onPickFrom(PickedPlace(st.name, stopName = st.name, lat = st.lat, lon = st.lon))
                    }
                }
            }

            // Insights
            Row(
                Modifier.padding(top = Space.xl).fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(t.surface)
                    .clickable(role = Role.Button, onClick = onInsights).padding(Space.lg),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.insights, style = MaterialTheme.typography.titleMedium, color = t.text)
                    Text(s.insightsSub, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(t.text), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.BarChart, contentDescription = null, tint = t.bg)
                }
            }
        }
    }
}

/** One line of the sentence: small word, big tappable blank (underlined), small word. */
@Composable
private fun SentenceLine(pre: String, slot: String, post: String, filled: Boolean, label: String, onClick: () -> Unit, accent: Boolean = false) {
    val t = LocalExtra.current
    val big = MaterialTheme.typography.displaySmall
    val lineColor = if (accent) t.primary else t.text
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (pre.isNotEmpty()) Text(pre, style = big.copy(fontWeight = FontWeight.Normal), color = t.muted, modifier = Modifier.padding(bottom = 3.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(slot, style = big, color = if (filled) t.text else t.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                drawLine(lineColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
                    pathEffect = if (filled) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)
            }
        }
        if (post.isNotEmpty()) Text(post, style = big.copy(fontWeight = FontWeight.Normal), color = t.muted, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun SectionHead(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val t = LocalExtra.current
    Row(Modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = t.muted, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null) Text(action, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = t.muted,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onAction).padding(horizontal = Space.md, vertical = Space.sm))
    }
}

/** A past trip, written as the sentence it came from: "Kalkaji Depot → Kalkaji Mandir". */
@Composable
private fun RecentRow(from: String, to: String, onClick: () -> Unit) {
    val t = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(Radius.md)).clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(to, style = MaterialTheme.typography.titleMedium, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(from, style = MaterialTheme.typography.bodyMedium, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = t.muted)
    }
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(t.border))
}
