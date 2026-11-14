package app.delhiraahi.ui.account

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.delhiraahi.data.Api
import app.delhiraahi.data.Lang
import app.delhiraahi.data.Settings
import app.delhiraahi.data.SettingsStore
import app.delhiraahi.data.ThemePref
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.SectionTitle
import app.delhiraahi.ui.common.Segmented
import app.delhiraahi.ui.theme.LocalExtra
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(settings: Settings, store: SettingsStore) {
    val s = LocalStrings.current
    val x = LocalExtra.current
    val scope = rememberCoroutineScope()
    var server by remember(settings.apiBase) { mutableStateOf(settings.apiBase) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
            .padding(24.dp).widthIn(max = 640.dp),
    ) {
        Text(s.accountTitle, style = MaterialTheme.typography.headlineLarge)

        SectionTitle(s.language)
        Segmented(listOf(Lang.EN to "English", Lang.HI to "हिन्दी"), settings.lang, { scope.launch { store.setLang(it) } }, s.language)

        SectionTitle(s.appearance)
        Segmented(
            listOf(ThemePref.SYSTEM to s.system, ThemePref.LIGHT to s.light, ThemePref.DARK to s.dark),
            settings.theme, { scope.launch { store.setTheme(it) } }, s.appearance,
        )

        SectionTitle(s.server)
        Text(s.serverHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            }) { Text(s.test) }
        }
        when (result) {
            true -> Text("✓ ${s.serverOk} · ${Api.base}", color = x.low.fg, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            false -> Text("${s.serverFail} · ${Api.base}", color = x.veryHigh.fg, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            null -> {}
        }

        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.outline)
        Text(s.about, style = MaterialTheme.typography.titleMedium)
        Text(s.aboutText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Text(s.privacy, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}
