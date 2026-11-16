package app.delhiraahi.ui.tickets

import app.delhiraahi.ui.common.LeaveOnce
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.delhiraahi.data.Api
import app.delhiraahi.data.DemoTicket
import app.delhiraahi.data.Lang
import app.delhiraahi.data.TicketStore
import app.delhiraahi.data.formatDelhi
import app.delhiraahi.data.isExpired
import app.delhiraahi.data.SessionViewModel
import app.delhiraahi.ui.account.SecureScreen
import androidx.compose.runtime.LaunchedEffect
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.Divider
import app.delhiraahi.ui.common.RoutePill
import app.delhiraahi.ui.common.qrBitmap
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.trip.PrimaryButton
import app.delhiraahi.ui.trip.SecondaryButton
import kotlinx.coroutines.launch

private fun money(x: Double) = if (x % 1.0 == 0.0) "₹${x.toInt()}" else "₹${"%.1f".format(x)}"

/** The account's demo tickets (synced from the server, cached on the device). Guests are asked to log in. */
@Composable
fun TicketsScreen(session: SessionViewModel, lang: Lang, onOpen: (String) -> Unit, onPlan: () -> Unit, onLogin: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val store = session.tickets
    LaunchedEffect(session.user?.id) { session.syncTickets() }
    val tickets by store.tickets.collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        LazyColumn(
            Modifier.fillMaxSize().widthIn(max = MaxWidth).align(Alignment.CenterHorizontally),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item { Text(s.ticketsTitle, style = MaterialTheme.typography.headlineLarge, color = t.text, modifier = Modifier.semantics { heading() }) }
            if (!session.loggedIn) item {
                Column(Modifier.fillMaxWidth().padding(vertical = Space.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(t.surface), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ConfirmationNumber, contentDescription = null, tint = t.text, modifier = Modifier.size(28.dp))
                    }
                    Text(s.loginToSeeTickets, style = MaterialTheme.typography.titleMedium, color = t.text, textAlign = TextAlign.Center)
                    Box(Modifier.padding(top = Space.md).widthIn(max = 260.dp)) { PrimaryButton(s.loginOrSignUp, onClick = onLogin) }
                }
            } else if (tickets.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = Space.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(t.surface), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ConfirmationNumber, contentDescription = null, tint = t.text, modifier = Modifier.size(28.dp))
                    }
                    Text(s.ticketsEmpty, style = MaterialTheme.typography.titleMedium, color = t.text)
                    Text(s.emptyHint, color = t.muted, textAlign = TextAlign.Center)
                    Box(Modifier.padding(top = Space.md).widthIn(max = 260.dp)) { PrimaryButton(s.planTrip, onClick = onPlan) }
                }
            }
            if (session.loggedIn) items(tickets, key = { it.ticket_id }) { tk -> TicketCard(tk, lang) { onOpen(tk.ticket_id) } }
            if (session.loggedIn && tickets.isNotEmpty()) item {
                Text(s.noScreenshots, style = MaterialTheme.typography.labelSmall, color = t.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** A paper ticket stub: details on the left, a perforation, the fare on a torn-off stub on the right. */
@Composable
private fun TicketCard(tk: DemoTicket, lang: Lang, onClick: () -> Unit) {
    val t = LocalExtra.current
    val expired = tk.isExpired()
    val stubW = 92.dp
    val stubBg = if (expired) t.surface2 else t.primary
    val stubFg = if (expired) t.muted else t.onPrimary
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(Radius.md)).background(t.card)
            .clickable(role = Role.Button, onClick = onClick)
            .drawWithContent {
                drawContent()
                // notches + perforation where the stub tears off
                val x = size.width - stubW.toPx()
                val r = 9.dp.toPx()
                drawCircle(t.bg, r, Offset(x, 0f))
                drawCircle(t.bg, r, Offset(x, size.height))
                drawLine(t.bg, Offset(x, r + 4.dp.toPx()), Offset(x, size.height - r - 4.dp.toPx()), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            }
            .semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.weight(1f).padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatusPill(expired)
            tk.legs.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    RoutePill(l.route, small = true)
                    Text("${l.board_stop} → ${l.alight_stop}", style = MaterialTheme.typography.bodyMedium, color = t.text, maxLines = 2, modifier = Modifier.weight(1f))
                }
            }
            Text("${tk.ticket_id} · ${formatDelhi(tk.issued_at, lang)}", style = MaterialTheme.typography.labelSmall, color = t.muted)
        }
        Column(
            Modifier.width(stubW).fillMaxHeight().background(stubBg).padding(Space.sm),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(money(tk.total), style = MaterialTheme.typography.titleLarge, color = stubFg, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = stubFg, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StatusPill(expired: Boolean) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    Text(
        if (expired) s.ticketExpired else s.ticketActive,
        color = if (expired) t.muted else t.low.fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (expired) t.surface else t.low.bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** One ticket: DEMO banner, QR with a large rotated DEMO watermark, status, legs, verify / delete. */
@Composable
fun TicketScreen(store: TicketStore, id: String, lang: Lang, onBack: () -> Unit, onStartRide: (String) -> Unit = {}) {
    SecureScreen()
    val s = LocalStrings.current
    val t = LocalExtra.current
    val scope = rememberCoroutineScope()
    val tickets by store.tickets.collectAsState(initial = null)
    val tk = tickets?.firstOrNull { it.ticket_id == id }
    var check by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val qrPx = with(density) { 220.dp.roundToPx() }
    val qr = remember(tk?.qr_payload) { tk?.let { qrBitmap(it.qr_payload, qrPx) } }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back, tint = t.text) }
            Text(s.ticketTitle, style = MaterialTheme.typography.titleMedium, color = t.text)
        }
        if (tickets != null && tk == null) { LeaveOnce(onBack); return }
        if (tk == null) return
        val expired = tk.isExpired()
        val banner = tk.watermark[if (lang == Lang.HI) "hi" else "en"] ?: s.demoBanner
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max = MaxWidth).align(Alignment.CenterHorizontally).padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.xl)).border(1.dp, t.border, RoundedCornerShape(Radius.xl)).background(t.card).padding(Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.md), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(banner, color = t.veryHigh.fg, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(t.veryHigh.bg).padding(Space.md))
                if (tk.voided) Text(s.voidedTicket, color = t.onPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(t.danger).padding(Space.md))
                Box(contentAlignment = Alignment.Center) {
                    if (qr != null) Image(
                        qr, contentDescription = "QR · ${tk.ticket_id} · $banner",
                        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(Radius.md)).background(Color.White).padding(10.dp).alpha(if (expired) 0.35f else 1f),
                    )
                    Text("DEMO", fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = t.veryHigh.line,
                        modifier = Modifier.rotate(-20f).alpha(0.22f).semantics { contentDescription = "" })
                }
                Text(tk.ticket_id, style = MaterialTheme.typography.titleMedium, color = t.text)
                StatusPill(expired)
                Text(s.validUntil(formatDelhi(tk.valid_until, lang)), style = MaterialTheme.typography.bodyMedium, color = t.muted)
                tk.holder?.let { Text(s.holder(it), style = MaterialTheme.typography.bodyMedium, color = t.text) }
                Divider()
                tk.legs.forEach { l ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        RoutePill(l.route)
                        Text("${l.board_stop} → ${l.alight_stop}", style = MaterialTheme.typography.bodyMedium, color = t.text, modifier = Modifier.weight(1f))
                        Text(money(l.leg_total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = t.text)
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.passengersN(tk.passengers), color = t.text, modifier = Modifier.weight(1f))
                    Text(money(tk.total), style = MaterialTheme.typography.headlineSmall, color = t.text)
                }
            }
            // Follow this ride on live bus data (needs route/stop ids, which tickets issued since v0.9 carry).
            if (!expired && tk.legs.any { it.route_id.isNotBlank() && it.board_stop_id.isNotBlank() && it.alight_stop_id.isNotBlank() })
                app.delhiraahi.ui.trip.PrimaryButton(app.delhiraahi.voice.LiveText.of(lang == Lang.HI).startRide) { onStartRide(tk.ticket_id) }
            if (check != null) Text(check!!, color = t.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            SecondaryButton(s.verify) {
                check = "…"
                scope.launch {
                    check = runCatching { Api.verify(tk.qr_payload) }
                        .fold({ if (it.valid_signature) s.verified(it.status) else s.notVerified }, { s.errNetwork })
                }
            }
            Text(s.noScreenshots, style = MaterialTheme.typography.labelSmall, color = t.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
