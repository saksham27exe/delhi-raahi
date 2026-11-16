package app.delhiraahi.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.delhiraahi.data.DeviceLocation
import app.delhiraahi.data.Lang
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import kotlinx.coroutines.launch

private val SOS_RED = Color(0xFFC62828)

private class SosText(val hi: Boolean) {
    val button = "SOS"
    val label = if (hi) "आपातकालीन सहायता" else "Emergency help"
    val title = if (hi) "आपातकालीन सहायता" else "Emergency help"
    val sub = if (hi) "नंबर दबाने पर फ़ोन का डायलर खुलेगा; कॉल बटन दबाकर बात करें।" else "Tapping a number opens your phone's dialer; press call to connect."
    val emergency = if (hi) "112 · आपातकाल (पुलिस, फ़ायर, एम्बुलेंस)" else "112 · Emergency (police, fire, ambulance)"
    val women = if (hi) "1091 · महिला हेल्पलाइन" else "1091 · Women's helpline"
    val ambulance = if (hi) "102 · एम्बुलेंस" else "102 · Ambulance"
    val share = if (hi) "मेरी लोकेशन भेजें" else "Share my location"
    val shareSub = if (hi) "किसी भरोसेमंद व्यक्ति को मैसेज या WhatsApp से" else "To someone you trust, by SMS or WhatsApp"
    val finding = if (hi) "लोकेशन ढूँढी जा रही है…" else "Finding your location…"
    val noLoc = if (hi) "लोकेशन नहीं मिली। लोकेशन चालू करें, या सीधे 112 पर कॉल करें।" else "Couldn't get your location. Turn location on, or call 112 directly."
    fun message(lat: Double, lon: Double, acc: Int?) =
        (if (hi) "मुझे मदद चाहिए। मेरी लोकेशन: " else "I need help. My location: ") +
            String.format(java.util.Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon) +
            (acc?.let { if (hi) " (लगभग $it मी.)" else " (within about $it m)" } ?: "") + " — DelhiRaahi"
    val close = if (hi) "बंद करें" else "Close"
}

/** Red SOS pill for the Home top bar. Opens the help panel; nothing is dialled or sent without a second tap. */
@Composable
fun SosButton(lang: Lang) {
    val tx = remember(lang) { SosText(lang == Lang.HI) }
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).background(SOS_RED)
            .clickable(role = Role.Button) { open = true }.semantics { contentDescription = tx.label }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(tx.button, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
    }
    if (open) SosPanel(tx) { open = false }
}

@Composable
private fun SosPanel(tx: SosText, onClose: () -> Unit) {
    val t = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(t.card).padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tx.title, style = MaterialTheme.typography.titleLarge, color = SOS_RED, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() })
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = tx.close }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = t.muted)
                }
            }
            Text(tx.sub, style = MaterialTheme.typography.bodyMedium, color = t.muted)
            SosRow(Icons.Outlined.Call, tx.emergency, null, strong = true) { dial(ctx, "112") }
            SosRow(Icons.Outlined.Shield, tx.women, null) { dial(ctx, "1091") }
            SosRow(Icons.Outlined.LocalHospital, tx.ambulance, null) { dial(ctx, "102") }
            SosRow(Icons.Outlined.MyLocation, tx.share, status ?: tx.shareSub, busy = busy) {
                if (busy) return@SosRow
                busy = true; status = tx.finding
                scope.launch {
                    val loc = runCatching { DeviceLocation.current(ctx) }.getOrNull()
                    busy = false
                    if (loc == null) { status = tx.noLoc; return@launch }
                    status = null
                    val msg = tx.message(loc.latitude, loc.longitude, loc.accuracy.toInt().takeIf { loc.hasAccuracy() && it > 0 })
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg)
                    runCatching { ctx.startActivity(Intent.createChooser(send, tx.share).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
    }
}

@Composable
private fun SosRow(icon: ImageVector, title: String, sub: String?, strong: Boolean = false, busy: Boolean = false, onClick: () -> Unit) {
    val t = LocalExtra.current
    val bg = if (strong) SOS_RED else t.surface
    val fg = if (strong) Color.White else t.text
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(Radius.md)).background(bg)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = fg)
        else Icon(icon, contentDescription = null, tint = if (strong) Color.White else SOS_RED, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = fg)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = if (strong) Color.White else t.muted)
        }
    }
}

/** Opens the dialer with the number filled in (no call permission needed; the person presses call). */
private fun dial(ctx: Context, number: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
