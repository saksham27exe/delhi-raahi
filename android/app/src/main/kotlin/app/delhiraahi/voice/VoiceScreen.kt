package app.delhiraahi.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.delhiraahi.data.Lang
import app.delhiraahi.data.TripViewModel
import app.delhiraahi.ui.account.ScreenHeader
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hands-free trip planning. Everything the assistant says is also shown as text; the screen itself is not a
 * live region, so TalkBack does not read over the assistant's voice.
 */
@Composable
fun VoiceScreen(trip: TripViewModel, lang: Lang, onBack: () -> Unit, onShow: (Int) -> Unit) {
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

    fun start(greetingDelay: Long = 0) {
        job?.cancel()
        job = scope.launch {
            delay(greetingDelay)
            session.run()
        }.also { j -> j.invokeOnCompletion { scope.launch { if (job === j) job = null } } }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        micDenied = !ok
        if (ok) start(400)
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start(900)
        else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    val list = rememberLazyListState()
    LaunchedEffect(session.log.size) { if (session.log.isNotEmpty()) list.animateScrollToItem(session.log.lastIndex) }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenHeader(vs.title, onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().widthIn(max = MaxWidth).align(Alignment.CenterHorizontally).padding(horizontal = Space.lg),
        ) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                itemsIndexed(session.log) { _, line ->
                    if (line.step) {
                        Row(Modifier.fillMaxWidth().padding(start = Space.sm), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            if (line.done) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = t.low.fg, modifier = Modifier.size(18.dp))
                            else CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = t.muted)
                            Text(line.text, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                        }
                        return@itemsIndexed
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (line.user) Alignment.CenterEnd else Alignment.CenterStart) {
                        Text(
                            line.text,
                            style = MaterialTheme.typography.titleMedium,
                            color = t.text,
                            modifier = Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(Radius.lg))
                                .background(if (line.user) t.surface2 else t.card).padding(Space.md)
                                .semantics { contentDescription = (if (line.user) vs.you else vs.assistant) + ": " + line.text },
                        )
                    }
                }
            }
            if (session.partial.isNotEmpty()) Text(session.partial, style = MaterialTheme.typography.titleMedium, color = t.muted,
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm))
            Text(vs.dataNote, style = MaterialTheme.typography.labelSmall, color = t.muted, modifier = Modifier.padding(top = Space.sm))
            if (micDenied) Text(vs.micNeeded, color = t.veryHigh.fg, modifier = Modifier.padding(vertical = Space.sm))
        }

        // Status + one very large button: start / stop.
        Column(Modifier.fillMaxWidth().padding(Space.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(
                when (session.status) {
                    VoiceStatus.LISTENING -> vs.listening
                    VoiceStatus.SPEAKING -> vs.speaking
                    VoiceStatus.THINKING -> vs.thinking
                    VoiceStatus.IDLE -> if (running) vs.thinking else vs.tapToTalk
                },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.text,
            )
            val listening = session.status == VoiceStatus.LISTENING
            val label = when {
                !running -> vs.tapToTalk
                session.status == VoiceStatus.SPEAKING -> vs.tapToInterrupt
                listening -> vs.tapWhenDone
                else -> vs.thinking
            }
            Surface(
                onClick = {
                    if (running) session.tap()
                    else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                    else askMic.launch(Manifest.permission.RECORD_AUDIO)
                },
                shape = CircleShape,
                color = if (listening) t.low.line else t.text,
                modifier = Modifier.size(120.dp).border(if (listening) 6.dp else 0.dp, t.low.bg, CircleShape)
                    .semantics { contentDescription = label },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, contentDescription = null, tint = t.bg, modifier = Modifier.size(56.dp))
                }
            }
            if (running) Surface(
                onClick = { job?.cancel(); job = null }, shape = RoundedCornerShape(50), color = t.card,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Row(Modifier.padding(horizontal = Space.lg, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, tint = t.text)
                    Text(vs.end, style = MaterialTheme.typography.titleMedium, color = t.text)
                }
            }
        }
    }
}
