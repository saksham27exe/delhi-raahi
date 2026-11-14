package app.delhiraahi.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Api
import app.delhiraahi.data.Lang
import app.delhiraahi.data.Places
import app.delhiraahi.data.SessionViewModel
import app.delhiraahi.data.Settings
import app.delhiraahi.data.SettingsStore
import app.delhiraahi.data.Slot
import app.delhiraahi.data.ThemePref
import app.delhiraahi.data.isPast
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.Divider
import app.delhiraahi.ui.common.ListRow
import app.delhiraahi.ui.common.SectionTitle
import app.delhiraahi.ui.common.Segmented
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.trip.PrimaryButton
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    settings: Settings, store: SettingsStore, session: SessionViewModel, places: Places,
    onLogin: () -> Unit, onWallet: () -> Unit, onPasses: () -> Unit, onHelp: () -> Unit,
    onSetPlace: (Slot) -> Unit, onRemovePlace: (Slot) -> Unit, onInsights: () -> Unit,
) {
    val s = LocalStrings.current
    val x = LocalExtra.current
    val scope = rememberCoroutineScope()
    val user = session.user
    var server by remember(settings.apiBase) { mutableStateOf(settings.apiBase) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showPw by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(user?.id) { if (user != null) { session.refreshWallet(); session.refreshPasses() } }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(Space.xl)) {
            Text(s.accountTitle, style = MaterialTheme.typography.headlineLarge, color = x.text, modifier = Modifier.semantics { heading() })

            // Profile or guest card
            Column(
                Modifier.padding(top = Space.lg).fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(x.card)
                    .border(0.5.dp, x.border, RoundedCornerShape(Radius.lg)).padding(Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (user == null) {
                    Text(s.guestTitle, style = MaterialTheme.typography.titleMedium, color = x.text)
                    Text(s.guestSub, style = MaterialTheme.typography.bodyMedium, color = x.muted)
                    PrimaryButton(s.loginOrSignUp, onClick = onLogin)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md),
                        modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(x.primary), contentAlignment = Alignment.Center) {
                            Text(user.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = x.onPrimary)
                        }
                        Column {
                            Text(user.name, style = MaterialTheme.typography.titleMedium, color = x.text)
                            Text("+91 ${user.phone_masked}", style = MaterialTheme.typography.bodyMedium, color = x.muted)
                        }
                    }
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.womanTraveller, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = x.text)
                            Text(s.womanTravellerSub, style = MaterialTheme.typography.bodyMedium, color = x.muted)
                        }
                        Switch(
                            checked = user.woman_traveller,
                            onCheckedChange = { on -> scope.launch { runCatching { session.setWomanTraveller(on) } } },
                            colors = SwitchDefaults.colors(checkedTrackColor = x.primary, checkedThumbColor = x.onPrimary),
                            modifier = Modifier.semantics { contentDescription = s.womanTraveller },
                        )
                    }
                }
            }

            if (user != null) {
                SectionTitle(s.wallet)
                ListRow(Icons.Outlined.AccountBalanceWallet, s.wallet,
                    subtitle = session.wallet?.let { s.balance(money(it.balance)) } ?: "…", chevron = true, onClick = onWallet)
                val active = session.passes?.count { !isPast(it.valid_until) }
                ListRow(Icons.Outlined.CardMembership, s.passes,
                    subtitle = if (active == null) "…" else if (active == 0) s.noPasses else "$active ${s.ticketActive.lowercase()}",
                    chevron = true, onClick = onPasses)
            }

            SectionTitle(s.savedPlaces)
            listOf(Triple(Slot.HOME, s.home, Icons.Outlined.Home), Triple(Slot.WORK, s.work, Icons.Outlined.Work)).forEach { (slot, name, icon) ->
                val p = if (slot == Slot.HOME) places.home else places.work
                ListRow(icon, name, subtitle = p?.label ?: s.notSet,
                    trailing = if (p != null) ({ TextButton(onClick = { onRemovePlace(slot) }) { Text(s.remove, color = x.veryHigh.fg) } }) else null,
                    chevron = p == null, onClick = { onSetPlace(slot) })
            }
            ListRow(Icons.Outlined.BarChart, s.insightsTitle, subtitle = s.insightsSub, chevron = true, onClick = onInsights)
            ListRow(Icons.AutoMirrored.Outlined.HelpOutline, s.help, subtitle = s.privacyTitle, chevron = true, onClick = onHelp)

            SectionTitle(s.settings)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.alertsTitle, style = MaterialTheme.typography.labelLarge, color = x.text, modifier = Modifier.weight(1f))
                TextButton(onClick = { app.delhiraahi.voice.Chime.play(app.delhiraahi.voice.Chime.Kind.SOFT) }) { Text("▶ " + s.hearChime, color = x.primary) }
            }
            listOf(
                Triple(s.alertArrive, s.alertArriveSub, settings.alertArrive) to { on: Boolean -> scope.launch { store.setAlertArrive(on) }; Unit },
                Triple(s.alertGetOff, s.alertGetOffSub, settings.alertGetOff) to { on: Boolean -> scope.launch { store.setAlertGetOff(on) }; Unit },
                Triple(s.alertSpoken, s.alertSpokenSub, settings.alertSpoken) to { on: Boolean -> scope.launch { store.setAlertSpoken(on) }; Unit },
            ).forEach { (txt, set) ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = Space.md)) {
                        Text(txt.first, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = x.text)
                        Text(txt.second, style = MaterialTheme.typography.bodyMedium, color = x.muted)
                    }
                    Switch(
                        checked = txt.third, onCheckedChange = set,
                        colors = SwitchDefaults.colors(checkedTrackColor = x.primary, checkedThumbColor = x.onPrimary),
                        modifier = Modifier.semantics { contentDescription = txt.first },
                    )
                }
            }
            Text(s.language, style = MaterialTheme.typography.labelLarge, color = x.text, modifier = Modifier.padding(top = Space.md, bottom = Space.xs))
            Segmented(listOf(Lang.EN to "English", Lang.HI to "हिन्दी"), settings.lang, { scope.launch { store.setLang(it) } }, s.language)
            Text(s.appearance, style = MaterialTheme.typography.labelLarge, color = x.text, modifier = Modifier.padding(top = Space.md, bottom = Space.xs))
            Segmented(
                listOf(ThemePref.SYSTEM to s.system, ThemePref.LIGHT to s.light, ThemePref.DARK to s.dark),
                settings.theme, { scope.launch { store.setTheme(it) } }, s.appearance,
            )
            Text(s.server, style = MaterialTheme.typography.labelLarge, color = x.text, modifier = Modifier.padding(top = Space.md))
            Text(s.serverHint, style = MaterialTheme.typography.bodyMedium, color = x.muted)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = server, onValueChange = { server = it; result = null }, singleLine = true,
                    placeholder = { Text(Api.defaultBase) }, label = { Text(s.server) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(1f),
                )
                OutlinedButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        store.setApiBase(server)
                        Api.setBase(server)
                        result = runCatching { Api.health() }.isSuccess
                        busy = false
                    }
                }) { Text(s.test, color = x.text) }
            }
            when (result) {
                true -> Text("✓ ${s.serverOk} · ${Api.base}", color = x.low.fg, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                false -> Text("${s.serverFail} · ${Api.base}", color = x.veryHigh.fg, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                null -> {}
            }

            if (user != null) {
                SectionTitle(s.profile)
                ListRow(Icons.Outlined.Lock, s.changePassword, chevron = true) { showPw = true }
                ListRow(Icons.AutoMirrored.Outlined.Logout, s.logout, subtitle = s.logoutSub) { session.logout() }
                ListRow(Icons.Outlined.DeleteForever, s.deleteAccount, trailing = null) { showDelete = true }
            }

            Divider()
            Text(s.about, style = MaterialTheme.typography.titleMedium, color = x.text, modifier = Modifier.padding(top = Space.lg))
            Text(s.aboutText, style = MaterialTheme.typography.bodyMedium, color = x.muted, modifier = Modifier.padding(top = 6.dp))
            Text(s.privacy, style = MaterialTheme.typography.bodyMedium, color = x.muted, modifier = Modifier.padding(top = 6.dp, bottom = Space.xl))
        }
    }

    if (showPw) PasswordDialog(session) { showPw = false }
    if (showDelete) DeleteDialog(session) { showDelete = false }
}

@Composable
private fun PasswordDialog(session: SessionViewModel, onClose: () -> Unit) {
    val s = LocalStrings.current
    val x = LocalExtra.current
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose, containerColor = x.card,
        title = { Text(s.changePassword, color = x.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                FormField(s.currentPassword, old, { old = it }, keyboard = KeyboardType.Password, secret = true)
                FormField(s.newPassword, new, { new = it }, keyboard = KeyboardType.Password, secret = true)
                Text(s.passwordRule, style = MaterialTheme.typography.bodyMedium, color = x.muted)
                msg?.let { Text(it, color = if (done) x.low.fg else x.veryHigh.fg) }
            }
        },
        confirmButton = {
            TextButton(enabled = !done && old.isNotEmpty() && new.length >= 8, onClick = {
                scope.launch {
                    runCatching { session.changePassword(old, new) }
                        .onSuccess { done = true; msg = s.saved }
                        .onFailure { msg = s.errorText(it) }
                }
            }) { Text(s.save, color = x.text) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(if (done) s.close else s.cancel, color = x.muted) } },
    )
}

@Composable
private fun DeleteDialog(session: SessionViewModel, onClose: () -> Unit) {
    val s = LocalStrings.current
    val x = LocalExtra.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, containerColor = x.card,
        title = { Text(s.deleteAccount, color = x.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(s.deleteWarn, color = x.text)
                FormField(s.password, pw, { pw = it }, keyboard = KeyboardType.Password, secret = true)
                msg?.let { Text(it, color = x.veryHigh.fg) }
            }
        },
        confirmButton = {
            TextButton(enabled = pw.isNotEmpty(), onClick = {
                scope.launch {
                    runCatching { session.deleteAccount(pw) }.onSuccess { onClose() }.onFailure { msg = s.errorText(it) }
                }
            }) { Text(s.deleteAccount, color = x.veryHigh.fg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(s.cancel, color = x.muted) } },
    )
}
