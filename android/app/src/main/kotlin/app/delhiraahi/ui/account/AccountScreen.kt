package app.delhiraahi.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import app.delhiraahi.ui.common.Segmented
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import app.delhiraahi.ui.trip.PrimaryButton
import kotlinx.coroutines.launch

/** Sub-pages of Account, so the main list stays short. */
private enum class Page { MAIN, ALERTS, LOOK, ADVANCED, ABOUT }

@Composable
fun AccountScreen(
    settings: Settings, store: SettingsStore, session: SessionViewModel, places: Places,
    onLogin: () -> Unit, onWallet: () -> Unit, onPasses: () -> Unit, onHelp: () -> Unit,
    onSetPlace: (Slot) -> Unit, onRemovePlace: (Slot) -> Unit, onInsights: () -> Unit,
) {
    val s = LocalStrings.current
    val user = session.user
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    val mainScroll = rememberScrollState()       // kept, so coming back from a sub-page returns to the same spot
    var showPw by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(user?.id) { if (user != null) { session.refreshWallet(); session.refreshPasses() } }
    BackHandler(enabled = page != Page.MAIN) { page = Page.MAIN }

    when (page) {
        Page.MAIN -> Screen(s.accountTitle, scroll = mainScroll) { MainPage(settings, session, places, onLogin, onWallet, onPasses, onHelp, onSetPlace, onRemovePlace, onInsights,
            open = { page = it }, onPassword = { showPw = true }, onDelete = { showDelete = true }) }
        Page.ALERTS -> Screen(s.alertsVoice, onBack = { page = Page.MAIN }) { AlertsPage(settings, store) }
        Page.LOOK -> Screen(s.langLook, onBack = { page = Page.MAIN }) { LookPage(settings, store) }
        Page.ADVANCED -> Screen(s.advanced, onBack = { page = Page.MAIN }) { AdvancedPage(settings, store) }
        Page.ABOUT -> Screen(s.aboutPrivacy, onBack = { page = Page.MAIN }) { AboutPage() }
    }

    if (showPw) PasswordDialog(session) { showPw = false }
    if (showDelete) DeleteDialog(session) { showDelete = false }
}

/** Scrolling page with a big title, and a back arrow on sub-pages. */
@Composable
private fun Screen(title: String, onBack: (() -> Unit)? = null, scroll: androidx.compose.foundation.ScrollState = rememberScrollState(),
                   content: @Composable ColumnScope.() -> Unit) {
    val s = LocalStrings.current
    val x = LocalExtra.current
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(Space.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.padding(end = Space.xs)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.accountTitle, tint = x.text)
                }
                Text(title, style = if (onBack == null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                    color = x.text, modifier = Modifier.semantics { heading() })
            }
            content()
            Box(Modifier.padding(bottom = Space.xl))
        }
    }
}

/** A titled card holding related rows, with thin lines between them. */
@Composable
private fun Group(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val x = LocalExtra.current
    if (title != null) Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = x.muted,
        modifier = Modifier.padding(top = Space.lg, bottom = Space.xs, start = Space.xs).semantics { heading() })
    else Box(Modifier.padding(top = Space.lg))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(x.card)
            .border(0.5.dp, x.border, RoundedCornerShape(Radius.lg)).padding(horizontal = Space.md, vertical = Space.xs),
        content = content,
    )
}

@Composable
private fun MainPage(
    settings: Settings, session: SessionViewModel, places: Places,
    onLogin: () -> Unit, onWallet: () -> Unit, onPasses: () -> Unit, onHelp: () -> Unit,
    onSetPlace: (Slot) -> Unit, onRemovePlace: (Slot) -> Unit, onInsights: () -> Unit,
    open: (Page) -> Unit, onPassword: () -> Unit, onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    val x = LocalExtra.current
    val user = session.user
    val ctx = LocalContext.current

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
                    Text(if (user.email.isNotBlank()) user.email else "+91 ${user.phone_masked}", style = MaterialTheme.typography.bodyMedium, color = x.muted)
                }
            }
        }
    }
    if (user != null && !user.email_verified) VerifyEmailCard(session, user.email)

    if (user != null) Group(s.groupMoney) {
        ListRow(Icons.Outlined.AccountBalanceWallet, s.wallet,
            subtitle = session.wallet?.let { s.balance(money(it.balance)) } ?: "…", chevron = true, onClick = onWallet)
        Divider()
        val active = session.passes?.count { !isPast(it.valid_until) }
        ListRow(Icons.Outlined.CardMembership, s.passes,
            subtitle = if (active == null) "…" else if (active == 0) s.noPasses else "$active ${s.ticketActive.lowercase()}",
            chevron = true, onClick = onPasses)
    }

    Group(s.savedPlaces) {
        listOf(Triple(Slot.HOME, s.home, Icons.Outlined.Home), Triple(Slot.WORK, s.work, Icons.Outlined.Work)).forEachIndexed { i, (slot, name, icon) ->
            if (i > 0) Divider()
            val p = if (slot == Slot.HOME) places.home else places.work
            ListRow(icon, name, subtitle = p?.label ?: s.notSet,
                trailing = if (p != null) ({ TextButton(onClick = { onRemovePlace(slot) }) { Text(s.remove, color = x.veryHigh.fg) } }) else null,
                chevron = p == null, onClick = { onSetPlace(slot) })
        }
    }

    Group(s.groupMore) {
        ListRow(Icons.Outlined.BarChart, s.insightsTitle, subtitle = s.insightsSub, chevron = true, onClick = onInsights)
        Divider()
        ListRow(Icons.AutoMirrored.Outlined.HelpOutline, s.help, subtitle = s.helpSub, chevron = true, onClick = onHelp)
    }

    Group(s.settings) {
        val wakeOn = settings.wakeWord ?: app.delhiraahi.voice.screenReaderOn(ctx)
        val on = listOf(settings.alertArrive, settings.alertGetOff, settings.alertSpoken, wakeOn).count { it }
        ListRow(Icons.Outlined.NotificationsActive, s.alertsVoice, subtitle = s.onOf(on, 4), chevron = true) { open(Page.ALERTS) }
        Divider()
        val theme = when (settings.theme) { ThemePref.LIGHT -> s.light; ThemePref.DARK -> s.dark; else -> s.system }
        ListRow(Icons.Outlined.Palette, s.langLook, subtitle = "${if (settings.lang == Lang.HI) "हिन्दी" else "English"} · $theme", chevron = true) { open(Page.LOOK) }
        Divider()
        ListRow(Icons.Outlined.Dns, s.advanced, subtitle = settings.apiBase.ifBlank { s.serverDefault }, chevron = true) { open(Page.ADVANCED) }
    }

    if (user != null) Group(s.security) {
        ListRow(Icons.Outlined.Lock, s.changePassword, chevron = true, onClick = onPassword)
        Divider()
        ListRow(Icons.AutoMirrored.Outlined.Logout, s.logout, subtitle = s.logoutSub) { session.logout() }
    }

    Group(null) {
        ListRow(Icons.Outlined.Info, s.aboutPrivacy, chevron = true) { open(Page.ABOUT) }
    }

    // Kept apart from everything else so nobody taps it by accident.
    if (user != null) TextButton(onClick = onDelete, modifier = Modifier.padding(top = Space.lg).fillMaxWidth()) {
        Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = x.veryHigh.fg, modifier = Modifier.padding(end = Space.xs))
        Text(s.deleteAccount, color = x.veryHigh.fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val x = LocalExtra.current
    Row(Modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Space.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = x.text)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = x.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = x.primary, checkedThumbColor = x.onPrimary),
            modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun AlertsPage(settings: Settings, store: SettingsStore) {
    val s = LocalStrings.current
    val x = LocalExtra.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Group(s.alertsTitle) {
        SwitchRow(s.alertArrive, s.alertArriveSub, settings.alertArrive) { scope.launch { store.setAlertArrive(it) } }
        Divider()
        SwitchRow(s.alertGetOff, s.alertGetOffSub, settings.alertGetOff) { scope.launch { store.setAlertGetOff(it) } }
        Divider()
        SwitchRow(s.alertSpoken, s.alertSpokenSub, settings.alertSpoken) { scope.launch { store.setAlertSpoken(it) } }
        Divider()
        TextButton(onClick = { app.delhiraahi.voice.Chime.play(app.delhiraahi.voice.Chime.Kind.SOFT) }) { Text("▶ " + s.hearChime, color = x.primary) }
    }
    // Wake word: hands-free start, mainly for people who can't see the screen.
    val wakeOn = settings.wakeWord ?: app.delhiraahi.voice.screenReaderOn(ctx)
    val askMic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> if (!ok) scope.launch { store.setWakeWord(false) } }
    Group(s.voiceTitle) {
        SwitchRow(s.wakeTitle, s.wakeSub, wakeOn) { on ->
            scope.launch { store.setWakeWord(on) }
            if (on && !app.delhiraahi.voice.hasMicPermission(ctx)) askMic.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
private fun LookPage(settings: Settings, store: SettingsStore) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    Group(s.language) {
        Box(Modifier.padding(vertical = Space.sm)) {
            Segmented(listOf(Lang.EN to "English", Lang.HI to "हिन्दी"), settings.lang, { scope.launch { store.setLang(it) } }, s.language)
        }
    }
    Group(s.appearance) {
        Box(Modifier.padding(vertical = Space.sm)) {
            Segmented(listOf(ThemePref.SYSTEM to s.system, ThemePref.LIGHT to s.light, ThemePref.DARK to s.dark),
                settings.theme, { scope.launch { store.setTheme(it) } }, s.appearance)
        }
    }
}

@Composable
private fun AdvancedPage(settings: Settings, store: SettingsStore) {
    val s = LocalStrings.current
    val x = LocalExtra.current
    val scope = rememberCoroutineScope()
    var server by remember(settings.apiBase) { mutableStateOf(settings.apiBase) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    Group(s.server) {
        Column(Modifier.padding(vertical = Space.sm)) {
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
        }
    }
}

@Composable
private fun AboutPage() {
    val s = LocalStrings.current
    val x = LocalExtra.current
    Group(s.about) {
        Text(s.aboutText, style = MaterialTheme.typography.bodyMedium, color = x.text, modifier = Modifier.padding(vertical = Space.sm))
    }
    Group(s.privacyTitle) {
        Text(s.privacy, style = MaterialTheme.typography.bodyMedium, color = x.text, modifier = Modifier.padding(top = Space.sm))
        Text(s.privacyBody, style = MaterialTheme.typography.bodyMedium, color = x.muted, modifier = Modifier.padding(vertical = Space.sm))
    }
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
                    runCatching { session.changePassword(old, new, s === app.delhiraahi.i18n.Hi) }
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
                    runCatching { session.deleteAccount(pw, s === app.delhiraahi.i18n.Hi) }.onSuccess { onClose() }.onFailure { msg = s.errorText(it) }
                }
            }) { Text(s.deleteAccount, color = x.veryHigh.fg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(s.cancel, color = x.muted) } },
    )
}

/** Shown until the email link is opened: adding money, booking and buying stay locked until then. */
@Composable
private fun VerifyEmailCard(session: app.delhiraahi.data.SessionViewModel, email: String) {
    val s = LocalStrings.current
    val x = LocalExtra.current
    val hi = s === app.delhiraahi.i18n.Hi
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(top = Space.md).fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(x.moderate.bg)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(if (hi) "अपना ईमेल सत्यापित करें" else "Verify your email", style = MaterialTheme.typography.titleMedium, color = x.moderate.fg)
        Text(if (hi) "$email पर एक लिंक भेजा गया है। उसे खोलें, फिर \"सत्यापित कर दिया\" दबाएँ। तब तक आप यात्रा देख सकते हैं, पर पैसे जोड़ना, टिकट या पास लेना बंद रहेगा।"
             else "We sent a link to $email. Open it, then tap \"I've verified\". Until then you can plan trips, but adding money, booking and buying passes stay locked.",
            style = MaterialTheme.typography.bodyMedium, color = x.text)
        msg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = x.text) }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            app.delhiraahi.ui.trip.PrimaryButton(if (hi) "सत्यापित कर दिया" else "I've verified", Modifier.weight(1f), enabled = !busy) {
                busy = true; msg = null
                scope.launch {
                    runCatching { session.checkVerified(hi) }
                        .onSuccess { ok -> if (!ok) msg = if (hi) "अभी सत्यापित नहीं हुआ। ईमेल का लिंक खोलें (स्पैम फ़ोल्डर भी देखें)।" else "Not verified yet. Open the link in the email (check spam too)." }
                        .onFailure { msg = s.errorText(it) }
                    busy = false
                }
            }
            app.delhiraahi.ui.trip.SecondaryButton(if (hi) "ईमेल फिर भेजें" else "Resend email", Modifier.weight(1f)) {
                scope.launch {
                    runCatching { session.resendVerification(hi) }
                        .onSuccess { msg = if (hi) "लिंक फिर से भेजा गया।" else "Link sent again." }
                        .onFailure { msg = s.errorText(it) }
                }
            }
        }
    }
}
