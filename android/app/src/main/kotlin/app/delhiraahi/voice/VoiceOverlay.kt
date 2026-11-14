package app.delhiraahi.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.delhiraahi.data.Lang
import app.delhiraahi.data.TripViewModel
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Space
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** The small round mic that opens the assistant from any tab. */
@Composable
fun VoiceButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalExtra.current
    Box(
        modifier.size(60.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(t.primary)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Mic, contentDescription = null, tint = t.onPrimary, modifier = Modifier.size(28.dp)) }
}

/** Listening: rings breathing out. Speaking: a soft wave of bars. Thinking: a turning arc. Idle: the mic. */
@Composable
private fun Orb(status: VoiceStatus, running: Boolean, onTap: () -> Unit, label: String) {
    val t = LocalExtra.current
    val anim = rememberInfiniteTransition(label = "orb")
    val phase by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(
        Modifier.size(72.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onTap).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(72.dp)) {
            val c = center
            val r = size.minDimension / 2
            when {
                status == VoiceStatus.LISTENING -> {
                    for (k in 0..1) {
                        val f = (phase + k * 0.5f) % 1f
                        drawCircle(t.primary.copy(alpha = 0.35f * (1 - f)), radius = r * (0.55f + 0.45f * f), center = c)
                    }
                    drawCircle(t.primary, radius = r * 0.55f, center = c)
                }
                status == VoiceStatus.SPEAKING -> {
                    drawCircle(t.primary, radius = r * 0.92f, center = c)
                    val n = 5; val w = r * 0.16f; val gap = r * 0.1f
                    val total = n * w + (n - 1) * gap
                    for (k in 0 until n) {
                        val h = r * (0.25f + 0.45f * ((sin((phase * 2 * PI + k * 0.9).toFloat()) + 1) / 2))
                        val x = c.x - total / 2 + k * (w + gap)
                        drawRoundRect(t.onPrimary, topLeft = Offset(x, c.y - h / 2), size = Size(w, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2))
                    }
                }
                status == VoiceStatus.THINKING || running && status == VoiceStatus.IDLE -> {
                    drawCircle(t.primary.copy(alpha = 0.18f), radius = r * 0.92f, center = c)
                    drawArc(t.primary, startAngle = phase * 360f, sweepAngle = 100f, useCenter = false,
                        topLeft = Offset(c.x - r * 0.8f, c.y - r * 0.8f), size = Size(r * 1.6f, r * 1.6f), style = Stroke(r * 0.12f))
                }
                else -> drawCircle(t.primary, radius = r * 0.8f, center = c)
            }
        }
        if (status == VoiceStatus.LISTENING || (!running && status == VoiceStatus.IDLE))
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = t.onPrimary, modifier = Modifier.size(28.dp))
    }
}

/**
 * The assistant as a small panel over whatever page is open (no page of its own).
 * When it finds buses it opens the results page underneath and keeps talking, so you see what it says.
 */
@Composable
fun VoiceOverlay(
    trip: TripViewModel, lang: Lang, modifier: Modifier = Modifier, onClose: () -> Unit, onResults: () -> Unit,
    onShow: (Int) -> Unit, onGuide: (Double, Double, String) -> Unit, onRide: (Int) -> Unit,
) {
    val vs = voiceStrings(lang)
    val t = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember(lang) { VoiceSession(ctx, trip, vs) }
    var job by remember { mutableStateOf<Job?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    val running = job?.isActive == true

    DisposableEffect(session) { onDispose { job?.cancel(); session.close() } }
    session.onShow = onShow
    session.onGuide = onGuide
    session.onRide = onRide
    session.onResults = onResults

    fun start(after: Long = 0) {
        job?.cancel()
        job = scope.launch { delay(after); session.run() }.also { j ->
            // When the conversation ends by itself, fold the panel away shortly after.
            j.invokeOnCompletion { scope.launch { if (job === j) { job = null; delay(1500); if (job == null && !micDenied) onClose() } } }
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> micDenied = !ok; if (ok) start(300) }
    val hasMic = { ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    LaunchedEffect(Unit) { if (hasMic()) start(250) else askMic.launch(Manifest.permission.RECORD_AUDIO) }

    val lastUser = session.log.lastOrNull { it.user && !it.step }?.text
    val lastReply = session.log.lastOrNull { !it.user && !it.step }?.text
    val lastStep = session.log.lastOrNull()?.takeIf { it.step }?.text
    val status = session.status
    val heading = when (status) {
        VoiceStatus.LISTENING -> vs.listening
        VoiceStatus.SPEAKING -> vs.speaking
        VoiceStatus.THINKING -> vs.thinking
        VoiceStatus.IDLE -> if (running) vs.thinking else vs.tapToTalk
    }
    val tapLabel = when {
        !running -> vs.tapToTalk
        status == VoiceStatus.SPEAKING -> vs.tapToInterrupt
        status == VoiceStatus.LISTENING -> vs.tapWhenDone
        else -> vs.thinking
    }

    Row(
        modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = Space.md)
            .shadow(16.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(t.card)
            .border(1.dp, t.border, RoundedCornerShape(28.dp)).padding(Space.md),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Orb(status, running, label = tapLabel, onTap = {
            if (running) session.tap() else if (hasMic()) start() else askMic.launch(Manifest.permission.RECORD_AUDIO)
        })
        Column(Modifier.weight(1f).heightIn(min = 72.dp), verticalArrangement = Arrangement.Center) {
            Text(heading, style = MaterialTheme.typography.labelMedium, color = t.primary, fontWeight = FontWeight.SemiBold)
            if (lastUser != null && status != VoiceStatus.LISTENING)
                Text("“$lastUser”", style = MaterialTheme.typography.bodyMedium, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val main = when {
                micDenied -> vs.micNeeded
                status == VoiceStatus.LISTENING -> session.partial.ifEmpty { "…" }
                status == VoiceStatus.THINKING && lastStep != null -> lastStep
                else -> lastReply ?: ""
            }
            Text(main, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal), color = t.text,
                maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { job?.cancel(); job = null; onClose() }
                .semantics { contentDescription = vs.end },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Close, contentDescription = null, tint = t.muted) }
    }
}

