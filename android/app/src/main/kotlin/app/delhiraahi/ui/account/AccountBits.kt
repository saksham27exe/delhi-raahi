package app.delhiraahi.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.delhiraahi.data.ApiException
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.i18n.Strings
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space

fun money(x: Double) = if (x % 1.0 == 0.0) "₹${x.toInt()}" else "₹${"%.1f".format(x)}"

/** Server message, or the network message when the server could not be reached. */
fun Strings.errorText(e: Throwable): String = if (e is ApiException && e.status == 0) errNetwork else e.message ?: errNetwork

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Blocks screenshots, screen recording and the recent-apps preview while this screen is shown. */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current.activity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back, tint = t.text) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = t.text, modifier = Modifier.semantics { heading() })
    }
}

@Composable
fun DemoBanner(text: String) {
    val t = LocalExtra.current
    Text(
        text, color = t.veryHigh.fg, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(t.veryHigh.bg).padding(Space.md),
    )
}

/** Labelled text field; `secret` adds a show/hide toggle. */
@Composable
fun FormField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false, prefix: String? = null,
) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    var show by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = t.text)
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(Radius.md)).background(t.surface).padding(start = Space.md),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (prefix != null) Text(prefix, style = MaterialTheme.typography.bodyLarge, color = t.muted)
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value, onChange, singleLine = true, textStyle = TextStyle(color = t.text, fontSize = 17.sp),
                    cursorBrush = SolidColor(t.text), keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    visualTransformation = if (secret && !show) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
            }
            if (secret) IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (show) s.hidePassword else s.showPassword, tint = t.muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}
