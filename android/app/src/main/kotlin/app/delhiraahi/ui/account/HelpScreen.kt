package app.delhiraahi.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.delhiraahi.data.Api
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.Divider
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.trip.PrimaryButton
import kotlinx.coroutines.launch

/** FAQ, feedback (guests may send too) and the privacy note. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<Int?>(null) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        ScreenHeader(s.help, onBack)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
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

                Text(s.feedback, style = MaterialTheme.typography.titleMedium, color = t.text,
                    modifier = Modifier.padding(top = Space.lg).semantics { heading() })
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(Radius.md)).background(t.surface).padding(Space.md)) {
                    if (text.isEmpty()) Text(s.feedbackHint, color = t.muted)
                    BasicTextField(text, { text = it.take(2000); status = null }, textStyle = TextStyle(color = t.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(t.text), modifier = Modifier.fillMaxWidth().semantics { contentDescription = s.feedback })
                }
                status?.let { Text(it, color = t.muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                PrimaryButton(s.send, enabled = !busy && text.trim().length >= 3) {
                    busy = true
                    scope.launch {
                        runCatching { Api.feedback(text.trim()) }
                            .onSuccess { text = ""; status = s.thanks }
                            .onFailure { status = s.errorText(it) }
                        busy = false
                    }
                }

                Text(s.privacyTitle, style = MaterialTheme.typography.titleMedium, color = t.text,
                    modifier = Modifier.padding(top = Space.lg).semantics { heading() })
                Text(s.privacyBody, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                Text(s.noScreenshots, style = MaterialTheme.typography.bodyMedium, color = t.muted)
            }
        }
    }
}
