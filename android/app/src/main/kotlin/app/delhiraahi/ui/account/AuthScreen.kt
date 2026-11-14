package app.delhiraahi.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import app.delhiraahi.data.SessionViewModel
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.Segmented
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.trip.PrimaryButton
import kotlinx.coroutines.launch

/** Phone + password log in / sign up. `reason` explains why we asked (e.g. booking needs an account). */
@Composable
fun AuthScreen(session: SessionViewModel, reason: String?, onBack: () -> Unit, onDone: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val scope = rememberCoroutineScope()
    var signUp by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canSubmit = !busy && phone.filter(Char::isDigit).length >= 10 && pw.length >= (if (signUp) 8 else 1) && (!signUp || name.isNotBlank())

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        ScreenHeader(if (signUp) s.createAccount else s.logIn, onBack)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                if (reason != null) Text(reason, style = MaterialTheme.typography.titleMedium, color = t.text)
                Segmented(listOf(false to s.logIn, true to s.signUp), signUp, { signUp = it; error = null }, s.loginOrSignUp)
                if (signUp) FormField(s.yourName, name, { name = it.take(60) })
                FormField(s.mobile, phone, { v -> phone = v.filter { it.isDigit() || it == ' ' }.take(14) }, keyboard = KeyboardType.Phone, prefix = "+91")
                FormField(s.password, pw, { pw = it.take(128) }, keyboard = KeyboardType.Password, secret = true)
                if (signUp) Text(s.passwordRule, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                if (error != null) Text(error!!, color = t.veryHigh.fg, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                PrimaryButton(if (signUp) s.createAccount else s.logIn, enabled = canSubmit) {
                    busy = true; error = null
                    scope.launch {
                        runCatching { if (signUp) session.register(phone, pw, name.trim()) else session.login(phone, pw) }
                            .onSuccess { onDone() }
                            .onFailure { error = s.errorText(it); busy = false }
                    }
                }
                Text(s.noSmsNote, style = MaterialTheme.typography.labelSmall, color = t.muted)
            }
        }
    }
}
