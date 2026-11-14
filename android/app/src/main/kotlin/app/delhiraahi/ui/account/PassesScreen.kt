package app.delhiraahi.ui.account

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.delhiraahi.data.ApiException
import app.delhiraahi.data.DemoPass
import app.delhiraahi.data.Lang
import app.delhiraahi.data.PassCatalog
import app.delhiraahi.data.PassType
import app.delhiraahi.data.SessionViewModel
import app.delhiraahi.data.formatDelhi
import app.delhiraahi.data.isPast
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.Divider
import app.delhiraahi.ui.common.Segmented
import app.delhiraahi.ui.common.qrBitmap
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.trip.PrimaryButton
import app.delhiraahi.ui.trip.SecondaryButton
import kotlinx.coroutines.launch

private fun Lang.k() = if (this == Lang.HI) "hi" else "en"

private fun PassCatalog?.name(kind: String, lang: Lang) =
    this?.passes?.firstOrNull { it.kind == kind }?.name?.get(lang.k()) ?: kind

/** My passes + buy a DEMO pass (price from the server catalogue, with its source). */
@Composable
fun PassesScreen(session: SessionViewModel, lang: Lang, onBack: () -> Unit, onOpen: (String) -> Unit, onWallet: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    LaunchedEffect(Unit) { session.loadCatalog(); session.refreshPasses(); session.refreshWallet() }
    val cat = session.catalog
    val mine = session.passes

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenHeader(s.passes, onBack)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(s.myPasses, style = MaterialTheme.typography.titleMedium, color = t.text, modifier = Modifier.semantics { heading() })
                when {
                    mine == null -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = t.text)
                    mine.isEmpty() -> Text(s.noPasses, color = t.muted)
                    else -> mine.forEach { p -> PassCard(p, cat, lang) { onOpen(p.pass_id) } }
                }
                Text(s.buyPass, style = MaterialTheme.typography.titleMedium, color = t.text,
                    modifier = Modifier.padding(top = Space.md).semantics { heading() })
                DemoBanner(s.demoPass)
                session.wallet?.let { Text(s.balance(money(it.balance)), style = MaterialTheme.typography.bodyMedium, color = t.muted) }
                if (cat == null) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = t.text)
                cat?.passes?.forEach { pt -> BuyCard(pt, cat, lang, session, onOpen, onWallet) }
                cat?.let { Text(s.checkedOn(it.checked_on), style = MaterialTheme.typography.labelSmall, color = t.muted) }
            }
        }
    }
}

@Composable
private fun PassCard(p: DemoPass, cat: PassCatalog?, lang: Lang, onClick: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val expired = isPast(p.valid_until)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).border(1.dp, t.border, RoundedCornerShape(Radius.lg)).background(t.card)
            .clickable(role = Role.Button, onClick = onClick).padding(Space.lg).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(cat.name(p.kind, lang), style = MaterialTheme.typography.titleMedium, color = t.text)
            Text(cat?.coverage?.get(p.bus_type)?.get(lang.k()) ?: p.bus_type, style = MaterialTheme.typography.bodyMedium, color = t.muted)
            Text((if (expired) s.ticketExpired else s.ticketActive) + " · " + s.validUntil(formatDelhi(p.valid_until, lang)),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (expired) t.muted else t.low.fg)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = t.muted)
    }
}

@Composable
private fun BuyCard(pt: PassType, cat: PassCatalog, lang: Lang, session: SessionViewModel, onOpen: (String) -> Unit, onWallet: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val scope = rememberCoroutineScope()
    var bus by remember { mutableStateOf("non_ac") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val price = pt.price[bus] ?: 0.0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).border(1.dp, t.border, RoundedCornerShape(Radius.lg)).background(t.card).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pt.name[lang.k()] ?: pt.kind, style = MaterialTheme.typography.titleMedium, color = t.text)
                Text(pt.validity[lang.k()].orEmpty(), style = MaterialTheme.typography.bodyMedium, color = t.muted)
            }
            Text(money(price), style = MaterialTheme.typography.headlineSmall, color = t.text)
        }
        Segmented(listOf("non_ac" to s.nonAc, "ac" to s.ac), bus, { bus = it }, s.busType)
        Text(cat.coverage[bus]?.get(lang.k()).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        val c = if (pt.verified) t.low else t.moderate
        Text(if (pt.verified) s.officialPrice else s.unverifiedPrice, color = c.fg, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.bg).padding(horizontal = 8.dp, vertical = 3.dp))
        pt.sources.firstOrNull()?.let { src ->
            Text("${s.source}: ${src.substringAfter("//").substringBefore("/")}", style = MaterialTheme.typography.labelSmall, color = t.muted)
        }
        error?.let { e ->
            Text(s.errorText(e), color = t.veryHigh.fg, style = MaterialTheme.typography.bodyMedium)
            if (e is ApiException && e.status == 402) SecondaryButton(s.addMoney, onClick = onWallet)
        }
        PrimaryButton(if (busy) s.buying else s.buyFor(money(price)), enabled = !busy) {
            busy = true; error = null
            scope.launch {
                runCatching { session.buyPass(pt.kind, bus) }
                    .onSuccess { onOpen(it.pass_id) }
                    .onFailure { error = it }
                busy = false
            }
        }
    }
}

/** One pass: DEMO banner, QR with DEMO watermark, holder, validity. Screenshots are blocked. */
@Composable
fun PassScreen(session: SessionViewModel, id: String, lang: Lang, onBack: () -> Unit) {
    SecureScreen()
    val s = LocalStrings.current
    val t = LocalExtra.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { session.loadCatalog(); if (session.passes == null) session.refreshPasses() }
    val p = session.passes?.firstOrNull { it.pass_id == id }
    var check by remember { mutableStateOf<String?>(null) }
    val qrPx = with(LocalDensity.current) { 220.dp.roundToPx() }
    val qr = remember(p?.qr_payload) { p?.let { qrBitmap(it.qr_payload, qrPx) } }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenHeader(s.passTitle, onBack)
        if (p == null) return
        val expired = isPast(p.valid_until)
        val banner = p.watermark[lang.k()]?.takeIf { "PASS" in it || "पास" in it } ?: s.demoPass
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.xl)).border(1.dp, t.border, RoundedCornerShape(Radius.xl)).background(t.card).padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.md), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DemoBanner(banner)
                    Box(contentAlignment = Alignment.Center) {
                        if (qr != null) Image(qr, contentDescription = "QR · ${p.pass_id} · $banner",
                            modifier = Modifier.size(240.dp).clip(RoundedCornerShape(Radius.md)).background(Color.White).padding(10.dp).alpha(if (expired) 0.35f else 1f))
                        Text("DEMO", fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = t.veryHigh.line,
                            modifier = Modifier.rotate(-20f).alpha(0.22f).semantics { contentDescription = "" })
                    }
                    Text(session.catalog.name(p.kind, lang), style = MaterialTheme.typography.headlineSmall, color = t.text, textAlign = TextAlign.Center)
                    Text(session.catalog?.coverage?.get(p.bus_type)?.get(lang.k()) ?: p.bus_type, color = t.muted)
                    Text(p.pass_id, style = MaterialTheme.typography.titleMedium, color = t.text)
                    p.holder?.let { Text(s.holder(it), color = t.text) }
                    Text((if (expired) s.ticketExpired else s.ticketActive) + " · " + s.validUntil(formatDelhi(p.valid_until, lang)),
                        fontWeight = FontWeight.Bold, color = if (expired) t.muted else t.low.fg, textAlign = TextAlign.Center)
                    Divider()
                    Row(Modifier.fillMaxWidth()) {
                        Text(if (p.price_verified) s.officialPrice else s.unverifiedPrice, color = t.muted, modifier = Modifier.weight(1f))
                        Text(money(p.price), style = MaterialTheme.typography.titleMedium, color = t.text)
                    }
                }
                Text(s.noScreenshots, style = MaterialTheme.typography.labelSmall, color = t.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (check != null) Text(check!!, color = t.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                SecondaryButton(s.verifyPass) {
                    check = "…"
                    scope.launch {
                        check = runCatching { Api.verify(p.qr_payload) }
                            .fold({ if (it.valid_signature) s.verified(it.status) else s.notVerified }, { s.errNetwork })
                    }
                }
            }
        }
    }
}
