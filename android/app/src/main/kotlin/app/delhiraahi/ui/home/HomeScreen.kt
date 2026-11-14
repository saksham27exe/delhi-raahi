package app.delhiraahi.ui.home

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Api
import app.delhiraahi.data.DeviceLocation
import app.delhiraahi.data.Lang
import app.delhiraahi.data.PickedPlace
import app.delhiraahi.data.Places
import app.delhiraahi.data.Slot
import androidx.compose.material.icons.outlined.History
import app.delhiraahi.data.NearbyStop
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.BusMap
import app.delhiraahi.ui.common.Chip
import app.delhiraahi.ui.common.MapController
import app.delhiraahi.ui.common.MapPin
import app.delhiraahi.ui.common.FloatingIconButton
import app.delhiraahi.ui.common.ListRow
import app.delhiraahi.ui.common.MapSheet
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import kotlinx.coroutines.launch

private const val PEEK = 0.46f


private sealed interface Nearby {
    data object NoPermission : Nearby
    data object Locating : Nearby
    data object NoFix : Nearby
    data class Loaded(val stops: List<NearbyStop>) : Nearby
    data object Error : Nearby
}

/** Same structure as the Expo Home: full map, floating language pill + locate, draggable sheet. */
@Composable
fun HomeScreen(
    lang: Lang, places: Places, onToggleLang: () -> Unit, onSetPlace: (Slot) -> Unit, onWhereTo: () -> Unit,
    onGo: (PickedPlace?, PickedPlace) -> Unit, onClearRecent: () -> Unit, onInsights: () -> Unit, onVoice: () -> Unit,
) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val map = remember { MapController() }
    var me by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var nearby by remember { mutableStateOf<Nearby>(if (DeviceLocation.granted(ctx)) Nearby.Locating else Nearby.NoPermission) }
    var attempt by remember { mutableIntStateOf(0) }
    val screenH = LocalConfiguration.current.screenHeightDp

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) attempt++ else nearby = Nearby.NoPermission
    }
    val requestLocation = { askPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    // Ask once on first open, like the Expo app.
    LaunchedEffect(Unit) { if (!DeviceLocation.granted(ctx)) requestLocation() }

    LaunchedEffect(attempt) {
        if (!DeviceLocation.granted(ctx)) return@LaunchedEffect
        nearby = Nearby.Locating
        val loc = DeviceLocation.current(ctx)
        if (loc == null) { nearby = Nearby.NoFix; return@LaunchedEffect }
        me = loc.latitude to loc.longitude
        map.moveTo(loc.latitude, loc.longitude)
        nearby = runCatching { Nearby.Loaded(Api.nearbyStops(loc.latitude, loc.longitude, 1000).results.take(5)) }.getOrElse { Nearby.Error }
    }

    // A missing origin means "from where I am"; without a fix the search screen asks for one.
    val go: (PickedPlace?, PickedPlace) -> Unit = { from, to ->
        onGo(from ?: me?.let { PickedPlace.here(s.currentLocation, it.first, it.second) }, to)
    }
    val stops = (nearby as? Nearby.Loaded)?.stops.orEmpty()

    Box(Modifier.fillMaxSize()) {
        BusMap(
            modifier = Modifier.fillMaxSize(), controller = map, dark = t.dark,
            pins = stops.map { MapPin(it.stop_id, it.lat, it.lon, it.name) },
            bottomPadding = (screenH * PEEK).dp, showMyLocation = me != null,
        )

        // Top bar over the map: language pill (left), locate (right).
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onToggleLang, shape = RoundedCornerShape(50), color = t.card, shadowElevation = 4.dp,
                modifier = Modifier.height(44.dp).semantics { contentDescription = s.switchLang },
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Language, contentDescription = null, tint = t.text, modifier = Modifier.size(18.dp))
                    Text(s.otherLang, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = t.text)
                }
            }
            Box(Modifier.weight(1f))
            FloatingIconButton(Icons.Outlined.MyLocation, s.locate) {
                val here = me
                if (here != null) map.moveTo(here.first, here.second)
                else if (DeviceLocation.granted(ctx)) attempt++ else requestLocation()
            }
        }

        MapSheet(
            peek = PEEK, full = 0.88f,
            header = {
                Text(s.greeting, style = MaterialTheme.typography.headlineSmall, color = t.text, modifier = Modifier.semantics { heading() })
                Row(Modifier.padding(top = Space.md).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Row(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(Radius.md)).background(t.surface)
                        .clickable(role = Role.Button, onClickLabel = s.whereTo, onClick = onWhereTo).padding(start = Space.lg, end = Space.sm),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = t.text, modifier = Modifier.size(22.dp))
                    Text(s.whereTo, style = MaterialTheme.typography.titleMedium, color = t.text, modifier = Modifier.weight(1f))
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(t.card).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = t.text, modifier = Modifier.size(16.dp))
                        Text(s.now, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = t.text)
                    }
                }
                // Voice assistant: hands-free planning (for blind and low-vision travellers).
                val voiceLabel = app.delhiraahi.voice.voiceStrings(lang).open
                Surface(onClick = onVoice, shape = RoundedCornerShape(Radius.md), color = t.text,
                    modifier = Modifier.size(56.dp).semantics { contentDescription = voiceLabel }) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Mic, contentDescription = null, tint = t.bg, modifier = Modifier.size(26.dp)) }
                }
                }
            },
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.xxl)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    val home = places.home
                    val work = places.work
                    if (home != null) Chip(s.home, Icons.Outlined.Home) { go(null, home.picked()) }
                    else Chip(s.addHome, Icons.Outlined.Home) { onSetPlace(Slot.HOME) }
                    if (work != null) Chip(s.work, Icons.Outlined.Work) { go(null, work.picked()) }
                    else Chip(s.addWork, Icons.Outlined.Work) { onSetPlace(Slot.WORK) }
                }

                if (places.recent.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.recent, style = MaterialTheme.typography.titleMedium, color = t.text,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        Text(s.clearRecent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = t.muted,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClearRecent)
                                .padding(horizontal = Space.md, vertical = Space.sm))
                    }
                    places.recent.forEach { r ->
                        ListRow(Icons.Outlined.History, r.to.label, subtitle = r.from?.let { s.fromStop(it.label) } ?: s.fromHere, chevron = true) {
                            go(r.from?.picked(), r.to.picked())
                        }
                    }
                }

                Text(s.nearby, style = MaterialTheme.typography.titleMedium, color = t.text,
                    modifier = Modifier.padding(top = Space.md, bottom = Space.xs).semantics { heading() })
                when (val n = nearby) {
                    Nearby.Locating -> Text(s.locating, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                    Nearby.NoPermission, Nearby.NoFix -> Text(s.noLocation, style = MaterialTheme.typography.bodyMedium, color = t.muted,
                        modifier = Modifier.clickable(role = Role.Button, onClick = requestLocation).padding(vertical = Space.xs))
                    Nearby.Error -> Text(s.errNetwork, style = MaterialTheme.typography.bodyMedium, color = t.veryHigh.fg,
                        modifier = Modifier.clickable(role = Role.Button) { attempt++ }.padding(vertical = Space.xs))
                    is Nearby.Loaded -> if (n.stops.isEmpty()) Text(s.noStopsNearby, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                    else n.stops.forEach { st ->
                        ListRow(Icons.Outlined.DirectionsBus, st.name, subtitle = s.metres(st.distance_m), chevron = true) {
                            map.moveTo(st.lat, st.lon, 17.0)
                        }
                    }
                }

                // Insights promo card
                Row(
                    Modifier.padding(top = Space.lg).fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(t.surface)
                        .clickable(role = Role.Button, onClick = onInsights).padding(Space.lg),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.insights, style = MaterialTheme.typography.titleMedium, color = t.text)
                        Text(s.insightsSub, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(t.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.BarChart, contentDescription = null, tint = t.onPrimary)
                    }
                }
            }
        }
    }
}
