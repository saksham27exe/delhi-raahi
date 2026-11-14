package app.delhiraahi.ui.trip

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.DeviceLocation
import app.delhiraahi.data.Lang
import app.delhiraahi.data.TripViewModel
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.RoutePill
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.voice.RideCue
import app.delhiraahi.voice.RideText
import app.delhiraahi.voice.RideTracker
import app.delhiraahi.voice.Speaker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

private const val GOOD_FIX_M = 100f          // on a bus, accept rougher fixes than when walking
private const val CLOCK_AFTER_MS = 25_000L   // no good fix for this long -> follow the timetable

/** What comes after getting off: walk to the next bus stop, or to the destination. */
private data class NextStep(val say: String, val lat: Double, val lon: Double, val name: String)

private fun buzz(ctx: Context, long: Boolean) {
    val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    val pattern = if (long) longArrayOf(0, 500, 200, 500, 200, 500) else longArrayOf(0, 300, 150, 300)
    runCatching { v?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
}

/**
 * On the bus: says how many stops are left, warns one stop before yours, and tells you to get off.
 * Uses GPS while the screen is open; when GPS is weak it follows the timetable and says so.
 */
@Composable
fun RideScreen(trip: TripViewModel, index: Int, legIdx: Int, lang: Lang, onBack: () -> Unit, onGuide: (Double, Double, String) -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val j = trip.journeys.getOrNull(index)
    val leg = j?.legs?.getOrNull(legIdx) ?: run { onBack(); return }
    val rt = remember(lang) { RideText.of(lang == Lang.HI) }
    val locale = remember(lang) { if (lang == Lang.HI) Locale("hi", "IN") else Locale("en", "IN") }
    val speaker = remember { Speaker(ctx) }
    val tracker = remember(leg) { RideTracker.of(leg) }

    var line by remember { mutableStateOf("") }
    var passed by remember { mutableIntStateOf(0) }
    var clockMode by remember { mutableStateOf(false) }
    var next by remember { mutableStateOf<NextStep?>(null) }
    var arrived by remember { mutableStateOf(false) }

    val queue = remember { Channel<String>(Channel.UNLIMITED) }
    fun speak(x: String) { line = x; queue.trySend(x) }
    LaunchedEffect(Unit) { for (x in queue) runCatching { speaker.say(x, locale) } }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; speaker.shutdown() }
    }

    // After getting off: the change (walk to the next bus) or the walk to the destination.
    val after = remember(j, legIdx) {
        val nextLeg = j.legs.getOrNull(legIdx + 1)
        val seg = nextLeg?.segments?.firstOrNull()
        when {
            nextLeg != null && seg != null -> NextStep(
                rt.changeHere(nextLeg.route, nextLeg.board_stop, j.connections.getOrNull(legIdx)?.walk_min?.roundToInt() ?: 0),
                seg.lat, seg.lon, nextLeg.board_stop)
            nextLeg == null && j.walk.destination_min >= 1 -> trip.to?.let { d ->
                d.lat?.let { NextStep(rt.destinationNext, it, d.lon!!, d.label) }
            }
            else -> null
        }
    }

    fun handle(cues: List<RideCue>) {
        for (c in cues) {
            speak(rt.say(c))
            when (c) {
                is RideCue.NextIsYours -> buzz(ctx, long = false)
                is RideCue.Arrive -> {
                    buzz(ctx, long = true); arrived = true
                    after?.let { speak(it.say); next = it }
                }
                else -> {}
            }
        }
        passed = tracker.passed
        clockMode = tracker.lastWasClock
    }

    var lastGood by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        handle(tracker.start())
        DeviceLocation.updates(ctx).collect { loc ->
            if (loc.accuracy > GOOD_FIX_M) return@collect
            lastGood = System.currentTimeMillis()
            handle(tracker.update(loc.latitude, loc.longitude))
        }
    }
    // Timetable fallback when GPS is weak (inside a bus, GPS often is).
    LaunchedEffect(Unit) {
        val ref = tracker.stops.firstOrNull()?.time ?: return@LaunchedEffect
        while (!arrived) {
            delay(15_000)
            if (System.currentTimeMillis() - lastGood > CLOCK_AFTER_MS) {
                val n = LocalTime.now()
                var m = n.hour * 60 + n.minute + n.second / 60f
                if (m < ref - 360) m += 1440
                handle(tracker.byClock(m))
                if (tracker.passed == 0) clockMode = true
            }
        }
    }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = Space.sm, top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = s.back },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = t.text) }
            Text(rt.title, style = MaterialTheme.typography.titleMedium, color = t.text)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(Modifier.padding(top = Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                RoutePill(leg.route)
                Text("→ ${leg.alight_stop}", style = MaterialTheme.typography.titleMedium, color = t.text)
            }
            // Big count, announced to screen readers as it changes
            Text(rt.stopsLeft(tracker.last - passed), style = MaterialTheme.typography.displaySmall,
                color = if (tracker.last - passed <= 1) t.primary else t.text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            tracker.stops.getOrNull(passed + 1)?.takeIf { !arrived }?.let {
                Text(rt.nextStop(it.name), style = MaterialTheme.typography.titleMedium, color = t.muted)
            }
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyLarge, color = t.text)
            if (clockMode) Text(rt.byTimetable, style = MaterialTheme.typography.bodyMedium, color = t.moderate.fg)

            // The stops of this ride, ticked off as they pass
            Column(Modifier.padding(top = Space.md)) {
                tracker.stops.forEachIndexed { k, st ->
                    val isEnd = k == tracker.last
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(
                            when { isEnd -> t.primary; k <= passed -> t.muted; else -> t.bg })
                            .border(2.dp, if (isEnd) t.primary else if (k <= passed) t.muted else t.text, CircleShape))
                        Text(st.name, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (k == passed + 1 || isEnd) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (k < passed) t.muted else t.text, modifier = Modifier.weight(1f))
                        st.time?.let { Text(clockText(it), style = MaterialTheme.typography.bodyMedium, color = t.muted) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            val n = next
            if (arrived && n != null) {
                SecondaryButton(rt.done, Modifier.weight(1f), onClick = onBack)
                PrimaryButton(rt.walkNext, Modifier.weight(1.4f)) { onGuide(n.lat, n.lon, n.name) }
            } else PrimaryButton(if (arrived) rt.done else s.back, onClick = onBack)
        }
    }
}
