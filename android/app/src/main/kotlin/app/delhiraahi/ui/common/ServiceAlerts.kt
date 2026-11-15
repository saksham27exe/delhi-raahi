package app.delhiraahi.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Api
import app.delhiraahi.data.ServiceAlert
import app.delhiraahi.i18n.Hi
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import kotlinx.coroutines.delay

/**
 * Service alerts posted by DelhiRaahi staff on the admin website (diversions, stop closures, strikes…).
 * Loads by itself and refreshes every 5 minutes; shows nothing when there are none or the server is unreachable.
 * `everyone` adds the alerts meant for all riders (Home); trip and bus screens pass only their routes / stops.
 */
@Composable
fun ServiceAlerts(routes: List<String> = emptyList(), stops: List<String> = emptyList(), everyone: Boolean = true, modifier: Modifier = Modifier) {
    var alerts by remember { mutableStateOf<List<ServiceAlert>>(emptyList()) }
    LaunchedEffect(routes, stops, everyone) {
        if (!everyone && routes.isEmpty() && stops.isEmpty()) { alerts = emptyList(); return@LaunchedEffect }
        while (true) {
            runCatching { Api.alerts(routes, stops, everyone) }.onSuccess { alerts = it.alerts }
            delay(5 * 60_000L)
        }
    }
    if (alerts.isEmpty()) return
    Column(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        alerts.take(3).forEach { AlertCard(it) }
    }
}

@Composable
private fun AlertCard(a: ServiceAlert) {
    val t = LocalExtra.current
    val hindi = LocalStrings.current == Hi
    var open by remember { mutableStateOf(false) }
    val c = when (a.severity) { "critical" -> t.veryHigh; "warning" -> t.high; else -> t.low }
    val title = (if (hindi) a.title_hi else null) ?: a.title_en
    val body = (if (hindi) a.body_hi else null) ?: a.body_en
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(Radius.md))
            .then(if (body != null) Modifier.clickable(role = Role.Button) { open = !open } else Modifier)
            .padding(Space.md).animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Top,
    ) {
        Icon(when (a.severity) { "critical" -> Icons.Outlined.Warning; "warning" -> Icons.Outlined.Campaign; else -> Icons.Outlined.Info },
            contentDescription = null, tint = c.fg, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = c.fg)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = c.fg, maxLines = if (open) 20 else 2)
            if (a.routes.isNotEmpty()) Text(a.routes.joinToString(" · ") { "🚌 $it" }, style = MaterialTheme.typography.labelMedium, color = c.fg,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}
