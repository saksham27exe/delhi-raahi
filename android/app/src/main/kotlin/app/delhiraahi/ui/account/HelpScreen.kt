package app.delhiraahi.ui.account

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.delhiraahi.BuildConfig
import app.delhiraahi.data.Api
import app.delhiraahi.data.SupportInfo
import app.delhiraahi.i18n.Hi
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.i18n.Strings
import app.delhiraahi.ui.common.Chip
import app.delhiraahi.ui.common.Divider
import app.delhiraahi.ui.common.ListRow
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.trip.PrimaryButton
import kotlinx.coroutines.launch

private val KINDS = listOf("complaint", "problem", "ticket", "idea")
private val EMAIL = Regex("^[^@\\s]{1,64}@[^@\\s]+\\.[A-Za-z]{2,}$")

private fun Strings.kindName(k: String) = when (k) {
    "complaint" -> kindComplaint; "problem" -> kindProblem; "ticket" -> kindTicket; else -> kindIdea
}

/** FAQ, contact support (complaints, problems, ticket questions, suggestions), "Email us" and the privacy note. Guests may write too. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(email: String, onBack: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var open by remember { mutableStateOf<Int?>(null) }
    var kind by remember { mutableStateOf("complaint") }
    var text by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }
    var whenText by remember { mutableStateOf("") }
    var where by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf(email) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<SupportInfo?>(null) }
    var faqY by remember { mutableFloatStateOf(0f) }
    var supportY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { info = runCatching { Api.supportInfo() }.getOrNull() }

    val emailBad = replyTo.isNotBlank() && !EMAIL.matches(replyTo.trim())
    val goTo = { y: Float -> scope.launch { scroll.animateScrollTo(y.toInt()) }; Unit }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        ScreenHeader(s.help, onBack)
        Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                // Quick ways in
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    QuickTile(Icons.Outlined.QuestionAnswer, s.quickFaq, Modifier.weight(1f)) { goTo(faqY) }
                    QuickTile(Icons.Outlined.SupportAgent, s.quickSupport, Modifier.weight(1f)) { kind = "problem"; goTo(supportY) }
                    QuickTile(Icons.Outlined.ReportProblem, s.quickComplaint, Modifier.weight(1f)) { kind = "complaint"; goTo(supportY) }
                }
                info?.email?.let { addr ->
                    ListRow(Icons.Outlined.Email, s.emailUs, subtitle = addr, chevron = true) { emailUs(ctx, s, addr) }
                }
                Text(s.emergencyNote, style = MaterialTheme.typography.bodyMedium, color = t.muted)

                // FAQ
                Heading(s.faqTitle, Modifier.onGloballyPositioned { faqY = it.positionInParent().y })
                s.faq.forEachIndexed { i, (q, a) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { open = if (open == i) null else i }
                            .padding(vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(q, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = t.text, modifier = Modifier.weight(1f))
                        Icon(if (open == i) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = t.muted)
                    }
                    if (open == i) Text(a, style = MaterialTheme.typography.bodyMedium, color = t.muted, modifier = Modifier.padding(bottom = Space.sm))
                    Divider()
                }

                // Contact support
                Heading(s.supportTitle, Modifier.onGloballyPositioned { supportY = it.positionInParent().y })
                Text(s.supportSub, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    KINDS.forEach { k -> Chip(s.kindName(k), selected = kind == k) { kind = k; status = null } }
                }
                if (kind == "complaint") {
                    Text(s.complaintDetails, style = MaterialTheme.typography.labelLarge, color = t.muted, modifier = Modifier.padding(top = Space.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        FormField(s.complaintRoute, route, { route = it.take(20) }, Modifier.weight(1f))
                        FormField(s.complaintWhen, whenText, { whenText = it.take(40) }, Modifier.weight(1f))
                    }
                    FormField(s.complaintWhere, where, { where = it.take(80) })
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(Radius.md)).background(t.surface).padding(Space.md)) {
                    if (text.isEmpty()) Text(s.messageHint(kind), color = t.muted)
                    BasicTextField(text, { text = it.take(2000); status = null }, textStyle = TextStyle(color = t.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(t.text), modifier = Modifier.fillMaxWidth().semantics { contentDescription = s.kindName(kind) })
                }
                FormField(s.yourEmail, replyTo, { replyTo = it.take(254).trim(); status = null }, keyboard = KeyboardType.Email)
                Text(
                    when { emailBad -> s.badEmail; replyTo.isBlank() -> s.noEmailNote; else -> s.emailCopyNote },
                    style = MaterialTheme.typography.bodySmall, color = if (emailBad) t.danger else t.muted,
                )
                status?.let { Text(it, color = t.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                PrimaryButton(s.send, enabled = !busy && !emailBad && text.isNotBlank()) {
                    if (text.trim().length < 10) { status = s.tooShort; return@PrimaryButton }
                    busy = true
                    scope.launch {
                        runCatching {
                            Api.support(kind, text.trim(), replyTo.ifBlank { null }, route.takeIf { kind == "complaint" },
                                whenText.takeIf { kind == "complaint" }, where.takeIf { kind == "complaint" }, if (s == Hi) "hi" else "en")
                        }.onSuccess { r ->
                            status = if (r.emailed) s.supportSent(r.ref, replyTo.trim().takeIf { r.copy_sent }) else s.supportSaved(r.ref)
                            text = ""; route = ""; whenText = ""; where = ""
                        }.onFailure { status = s.errorText(it) }
                        busy = false
                    }
                }

                Heading(s.privacyTitle)
                Text(s.privacyBody, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                Text(s.noScreenshots, style = MaterialTheme.typography.bodyMedium, color = t.muted)
            }
        }
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = LocalExtra.current.text,
        modifier = modifier.padding(top = Space.lg).semantics { heading() })
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalExtra.current
    Column(
        modifier.heightIn(min = 84.dp).clip(RoundedCornerShape(Radius.md)).background(t.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = t.accent, modifier = Modifier.size(26.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = t.text, textAlign = TextAlign.Center)
    }
}

/** Opens the email app on a new message to support; copies the address when there is no email app. */
private fun emailUs(ctx: Context, s: Strings, addr: String) {
    val body = "\n\n—\nDelhiRaahi ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE}"
    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(addr))
        putExtra(Intent.EXTRA_SUBJECT, "DelhiRaahi: ${s.quickSupport}")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        ctx.startActivity(i)
    } catch (_: ActivityNotFoundException) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("email", addr))
        Toast.makeText(ctx, s.noMailApp, Toast.LENGTH_LONG).show()
    }
}
