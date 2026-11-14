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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Schedule
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
                SosButton(lang)
                Spacer(Modifier.width(Space.sm))
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

            // Search box: From / To in one card, with "leaving" as a chip under it
            SearchCard(
                from = s.currentLocation, fromLabel = "${s.from}: ${s.currentLocation}", onFrom = onWhereFrom,
                to = s.whereTo, onTo = onWhereTo,
                modifier = Modifier.padding(top = Space.xl),
            )
            Box(Modifier.padding(top = Space.sm)) {
                Row(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).border(1.dp, t.border, RoundedCornerShape(50))
                        .clickable(role = Role.Button, onClickLabel = leaveLabel(s, leaveAt)) { whenOpen = true }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = t.text, modifier = Modifier.size(18.dp))
                    Text(leaveLabel(s, leaveAt), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = t.text)
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = t.muted, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = whenOpen, onDismissRequest = { whenOpen = false }) {
                    LeaveAt.options.forEach { o ->
                        DropdownMenuItem(text = { Text(leaveLabel(s, o), style = MaterialTheme.typography.bodyLarge) },
                            onClick = { onLeave(o); whenOpen = false })
                    }
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
                // Own scroll: about four rows tall, so a long history doesn't push the rest of Home down.
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 232.dp).clip(RoundedCornerShape(Radius.lg)).background(t.surface)
                        .verticalScroll(rememberScrollState()).padding(horizontal = Space.lg),
                ) {
                    places.recent.forEachIndexed { i, r ->
                        RecentRow(r.from?.label ?: s.yourLocation, r.to.label, divider = i < places.recent.lastIndex) { go(r.from?.picked(), r.to.picked()) }
                    }
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

/** From / To as one search box: a dot and a pin joined by a dotted line, each field tappable. */
@Composable
private fun SearchCard(from: String, fromLabel: String, onFrom: () -> Unit, to: String, onTo: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalExtra.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(t.surface).border(1.dp, t.border, RoundedCornerShape(Radius.lg))
            .padding(start = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rail: hollow dot (start), dotted line, filled pin (end)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(12.dp).clip(CircleShape).border(2.5.dp, t.text, CircleShape))
            Canvas(Modifier.size(width = 2.dp, height = 30.dp)) {
                drawLine(t.muted, Offset(size.width / 2, 4f), Offset(size.width / 2, size.height - 4f), strokeWidth = size.width,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            Icon(Icons.Outlined.Place, contentDescription = null, tint = t.primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f).padding(start = Space.md)) {
            Field(from, t.text, fromLabel, onFrom)
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(t.border))
            Field(to, t.muted, to, onTo, bold = false)
        }
    }
}

@Composable
private fun Field(text: String, color: androidx.compose.ui.graphics.Color, label: String, onClick: () -> Unit, bold: Boolean = true) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = color,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }.padding(vertical = 17.dp, horizontal = Space.sm),
    )
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
private fun RecentRow(from: String, to: String, divider: Boolean, onClick: () -> Unit) {
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
    if (divider) Spacer(Modifier.fillMaxWidth().height(1.dp).background(t.border))
}
