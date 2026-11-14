package app.delhiraahi.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Lang
import app.delhiraahi.data.SessionViewModel
import app.delhiraahi.data.formatDelhi
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.Chip
import app.delhiraahi.ui.common.Divider
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.trip.PrimaryButton
import kotlinx.coroutines.launch

private val AMOUNTS = listOf(100, 200, 500, 1000)

/** DEMO wallet: balance, simulated top-up, history. */
@Composable
fun WalletScreen(session: SessionViewModel, lang: Lang, onBack: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableIntStateOf(200) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { session.refreshWallet() }
    val w = session.wallet

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenHeader(s.wallet, onBack)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                DemoBanner(s.demoWallet)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.xl)).background(t.card).border(0.5.dp, t.border, RoundedCornerShape(Radius.xl)).padding(Space.xl)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(s.wallet, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                    Text(w?.let { money(it.balance) } ?: "…", style = MaterialTheme.typography.headlineLarge, color = t.text)
                }
                Text(s.addMoney, style = MaterialTheme.typography.titleMedium, color = t.text, modifier = Modifier.semantics { heading() })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    AMOUNTS.forEach { a -> Chip("₹$a", selected = amount == a) { amount = a } }
                }
                if (error != null) Text(error!!, color = t.veryHigh.fg)
                PrimaryButton(s.add(amount), enabled = !busy) {
                    busy = true; error = null
                    scope.launch {
                        runCatching { session.topup(amount) }.onFailure { error = s.errorText(it) }
                        busy = false
                    }
                }
                Text(s.topupNote, style = MaterialTheme.typography.labelSmall, color = t.muted)

                Text(s.walletHistory, style = MaterialTheme.typography.titleMedium, color = t.text,
                    modifier = Modifier.padding(top = Space.md).semantics { heading() })
                val tx = w?.transactions.orEmpty()
                if (w != null && tx.isEmpty()) Text(s.noHistory, color = t.muted)
                tx.forEach { x ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.sm).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.txKind(x.kind), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = t.text)
                            Text(listOfNotNull(formatDelhi(x.created_at, lang), x.ref?.takeIf { x.kind != "topup" }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = t.muted)
                        }
                        Text((if (x.amount > 0) "+" else "−") + money(kotlin.math.abs(x.amount)),
                            style = MaterialTheme.typography.titleMedium, color = if (x.amount > 0) t.low.fg else t.text)
                    }
                    Divider()
                }
            }
        }
    }
}

