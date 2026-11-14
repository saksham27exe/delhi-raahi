package app.delhiraahi.ui.trip

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Api
import app.delhiraahi.data.DeviceLocation
import app.delhiraahi.data.Lang
import app.delhiraahi.data.WalkDirections
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.BusMap
import app.delhiraahi.ui.common.MapController
import app.delhiraahi.ui.common.MapLine
import app.delhiraahi.ui.common.MapPin
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.voice.Cue
import app.delhiraahi.voice.GuideText
import app.delhiraahi.voice.Speaker
import app.delhiraahi.voice.WalkGuide
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_ACCURACY_M = 60f      // ignore rough network fixes once guiding
private const val REROUTE_GAP_MS = 20_000L

/**
 * Spoken turn-by-turn walking directions from where you are to [name] (a bus stop or the destination).
 * Uses the phone's GPS while this screen is open; the screen stays on while guiding.
 */
@Composable
fun WalkGuideScreen(lat: Double, lon: Double, name: String, lang: Lang, onBack: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val g = remember(lang) { GuideText.of(lang == Lang.HI) }
    val locale = remember(lang) { if (lang == Lang.HI) Locale("hi", "IN") else Locale("en", "IN") }
    val speaker = remember { Speaker(ctx) }
    val scope = rememberCoroutineScope()
    val map = remember { MapController() }

    var dir by remember { mutableStateOf<WalkDirections?>(null) }
    var guide by remember { mutableStateOf<WalkGuide?>(null) }
    var line by remember { mutableStateOf(g.lookingUp) }
    var left by remember { mutableStateOf<Int?>(null) }
    var me by remember { mutableStateOf<Location?>(null) }
    var routeKey by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }

    // One sentence at a time; a newer instruction replaces one not yet spoken.
    val queue = remember { Channel<String>(Channel.CONFLATED) }
    fun speak(text: String) { line = text; queue.trySend(text) }
    LaunchedEffect(Unit) { for (x in queue) runCatching { speaker.say(x, locale) } }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; speaker.shutdown() }
    }

    var lastRoute = 0L
    suspend fun route(from: Location) {
        lastRoute = System.currentTimeMillis()
        val r = runCatching { Api.walkDirections(from.latitude, from.longitude, lat, lon) }
        val d = r.getOrNull()
        if ((r.exceptionOrNull() as? app.delhiraahi.data.ApiException)?.status == 422) { speak(g.tooFar); return }
        if (d == null || d.steps.isEmpty()) { speak(g.noRoute); return }
        dir = d
        guide = WalkGuide(d)
        routeKey++
    }

    // Show the target until a route is drawn.
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(600); if (dir == null) map.moveTo(lat, lon, 15.5) }

    // First fix -> route -> follow fixes.
    LaunchedEffect(Unit) {
        speak(g.lookingUp)
        val first = DeviceLocation.current(ctx)
        if (first == null) { speak(g.noLocation); return@LaunchedEffect }
        me = first
        route(first)
        guide?.update(first.latitude, first.longitude, first.accuracy)?.forEach { speak(g.say(it, name)) }
        DeviceLocation.updates(ctx).collect { loc ->
            me = loc
            if (done || loc.accuracy > MAX_ACCURACY_M) return@collect
            val gd = guide ?: return@collect
            for (c in gd.update(loc.latitude, loc.longitude, loc.accuracy)) {
                speak(g.say(c, name))
                if (c == Cue.Arrive) done = true
                if (c == Cue.OffRoute && System.currentTimeMillis() - lastRoute > REROUTE_GAP_MS) {
                    route(loc)
                    guide?.update(loc.latitude, loc.longitude, loc.accuracy)?.forEach { speak(g.say(it, name)) }
                }
            }
            left = guide?.let { (it.length - it.progress).roundToInt().coerceAtLeast(0) }
        }
    }

    val lines = remember(dir, t) {
        dir?.path?.filter { it.size >= 2 }?.map { it[0] to it[1] }?.takeIf { it.size >= 2 }
            ?.let { listOf(MapLine(it, t.primary.toArgb(), 5f, dashed = true)) }.orEmpty()
    }
    val pins = remember(name) { listOf(MapPin("to", lat, lon, name, MapPin.Kind.TO)) }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = Space.sm, top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = s.back },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = t.text) }
            Text(g.title, style = MaterialTheme.typography.titleMedium, color = t.text)
        }
        Column(Modifier.weight(1f).padding(horizontal = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(g.toward(name), style = MaterialTheme.typography.bodyLarge, color = t.muted, modifier = Modifier.padding(top = Space.md))
            // The instruction, large; screen readers announce it as it changes.
            Text(line, style = MaterialTheme.typography.headlineLarge, color = t.text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            left?.let { Text(g.metresLeft(WalkGuide.roundM(it.toDouble())), style = MaterialTheme.typography.titleMedium, color = t.primary) }
            Spacer(Modifier.height(Space.xs))
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(Radius.lg)).border(1.dp, t.border, RoundedCornerShape(Radius.lg))) {
                BusMap(Modifier.fillMaxSize(), map, pins, t.dark, 0.dp, showMyLocation = me != null, lines = lines, fitKey = routeKey.takeIf { it > 0 })
            }
        }
        Row(Modifier.fillMaxWidth().padding(Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            SecondaryButton(g.repeat, Modifier.weight(1f)) { scope.launch { queue.trySend(line) } }
            PrimaryButton(g.stop, Modifier.weight(1f), onClick = onBack)
        }
    }
}
