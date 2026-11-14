package app.delhiraahi.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Api
import app.delhiraahi.data.LiveStatus
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.theme.LocalExtra
import kotlinx.coroutines.delay

/** K1: real live-feed status from the backend, refreshed every 30 s. Arrivals board comes in K3. */
@Composable
fun LiveScreen() {
    val s = LocalStrings.current
    val x = LocalExtra.current
    var status by remember { mutableStateOf<LiveStatus?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { Api.liveStatus() }.onSuccess { status = it; failed = false }.onFailure { failed = true }
            delay(30_000)
        }
    }
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp).widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(s.liveTitle, style = MaterialTheme.typography.headlineLarge)
        val on = status?.live == true
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            androidx.compose.foundation.layout.Box(Modifier.size(12.dp).clip(CircleShape).background(if (on) x.low.line else x.unknown.line))
            Text(
                when {
                    failed -> s.errNetwork
                    status == null -> s.loading
                    on -> "${s.liveOn} · ${s.busesLive(status!!.vehicles)}"
                    else -> s.liveOff
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(s.comingSoon, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
