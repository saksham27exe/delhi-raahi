package app.delhiraahi.ui.tickets

import app.delhiraahi.ui.common.LeaveOnce
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Api
import app.delhiraahi.data.ApiException
import app.delhiraahi.data.DemoTicket
import app.delhiraahi.data.LegRef
import app.delhiraahi.data.Quote
import app.delhiraahi.data.SessionViewModel
import app.delhiraahi.ui.account.money
import app.delhiraahi.ui.trip.SecondaryButton
import app.delhiraahi.data.TripViewModel
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.RoutePill
import app.delhiraahi.ui.common.Segmented
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.trip.PrimaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Demo booking card over a dimmed background. Total always comes from the server quote. */
@Composable
fun BookScreen(trip: TripViewModel, index: Int, session: SessionViewModel, onClose: () -> Unit, onBooked: (DemoTicket) -> Unit, onWallet: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val scope = rememberCoroutineScope()
    val j = remember(index) { trip.journeys.getOrNull(index) } ?: run { LeaveOnce(onClose); return }
    val legs = remember(j) { j.legs.map { LegRef(it.route_id, it.board_stop_id, it.alight_stop_id) } }
    // Legs whose bus type the data can't tell (mixed fleet) get a choice; default AC (the common case).
    val needsType = remember(j) { j.legs.map { (it.fare?.bus_type ?: "unknown") !in listOf("ac", "non_ac") } }
    var chosenType by remember { mutableStateOf("ac") }
    var passengers by remember { mutableIntStateOf(1) }
    // Women travelling free (Delhi scheme): chosen per booking.
    var women by remember { mutableIntStateOf(0) }
    var needMoney by remember { mutableStateOf(false) }
    var quote by remember { mutableStateOf<Quote?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var booking by remember { mutableStateOf(false) }
    val busTypes = if (needsType.any { it }) needsType.map { if (it) chosenType else null } else null

    LaunchedEffect(Unit) { session.refreshWallet() }
    LaunchedEffect(passengers, women, chosenType) {
        quote = null          // never show (or confirm) the total for the previous choices
        delay(150)
        runCatching { Api.quote(legs, passengers, women, busTypes) }
            .onSuccess { quote = it; error = null }
            .onFailure { quote = null; error = if (it is ApiException && it.status == 0) s.errNetwork else it.message }
    }
    val close = { if (!booking) onClose() }     // not while the wallet is being charged: the ticket would be lost

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(Space.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.xl)).background(t.card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState()).padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.bookTitle, style = MaterialTheme.typography.headlineSmall, color = t.text, modifier = Modifier.weight(1f))
                IconButton(onClick = close, enabled = !booking) { Icon(Icons.Outlined.Close, contentDescription = s.close, tint = t.text) }
            }
            // The demo status is always visible as text.
            Text(
                s.demoBanner, color = t.veryHigh.fg, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(t.veryHigh.bg).padding(Space.md),
            )
            j.legs.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    RoutePill(l.route, small = true)
                    Text("${l.board_stop} → ${l.alight_stop}", style = MaterialTheme.typography.bodyMedium, color = t.text, maxLines = 2)
                }
            }
            Stepper(s.passengers, passengers, 1, 6) { passengers = it; if (women > it) women = it }
            Stepper(s.womenFree, women, 0, passengers) { women = it }
            Text(s.womenNote, style = MaterialTheme.typography.labelSmall, color = t.muted)
            if (busTypes != null) {
                Text(s.busType, style = MaterialTheme.typography.titleMedium, color = t.text)
                Segmented(listOf("ac" to s.ac, "non_ac" to s.nonAc), chosenType, { chosenType = it }, s.busType)
            }
            Row(Modifier.padding(top = Space.sm).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                Text(s.totalDigital, style = MaterialTheme.typography.bodyLarge, color = t.muted, modifier = Modifier.weight(1f))
                val q = quote
                if (q == null && error == null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.text)
                else if (q != null) Text("₹${if (q.total % 1.0 == 0.0) q.total.toInt().toString() else "%.1f".format(q.total)}", style = MaterialTheme.typography.headlineSmall, color = t.text)
            }
            session.wallet?.let { Text(s.payFromWallet(money(it.balance)), style = MaterialTheme.typography.bodyMedium, color = t.muted) }
            if (error != null) Text(error!!, color = t.veryHigh.fg, style = MaterialTheme.typography.bodyMedium)
            if (needMoney) SecondaryButton(s.addMoney, onClick = onWallet)
            PrimaryButton(if (booking) s.confirming else s.confirmDemo, enabled = quote != null && !booking) {
                booking = true; needMoney = false
                scope.launch {
                    runCatching { Api.book(legs, passengers, women, busTypes) }
                        .onSuccess { tk -> session.onBooked(tk); onBooked(tk) }
                        .onFailure {
                            error = if (it is ApiException && it.status == 0) s.errNetwork else it.message
                            needMoney = it is ApiException && it.status == 402
                            booking = false
                        }
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val t = LocalExtra.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = t.text, modifier = Modifier.weight(1f))
        StepButton(Icons.Outlined.Remove, "$label −", value > min) { onChange(value - 1) }
        Text("$value", style = MaterialTheme.typography.titleMedium, color = t.text, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp).semantics { liveRegion = LiveRegionMode.Polite })
        StepButton(Icons.Outlined.Add, "$label +", value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val t = LocalExtra.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(t.surface).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (enabled) t.text else t.surface2) }
}
