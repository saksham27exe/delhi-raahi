package app.delhiraahi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.delhiraahi.data.Api
import app.delhiraahi.data.Settings
import app.delhiraahi.data.SettingsStore
import app.delhiraahi.data.ThemePref
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.i18n.stringsFor
import app.delhiraahi.ui.App
import app.delhiraahi.ui.theme.DelhiRaahiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore(applicationContext)
        setContent {
            val settings by store.settings.collectAsState(initial = Settings())
            LaunchedEffect(settings.apiBase) { Api.setBase(settings.apiBase) }
            val dark = when (settings.theme) {
                ThemePref.SYSTEM -> isSystemInDarkTheme()
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
            }
            val strings = remember(settings.lang) { stringsFor(settings.lang) }
            CompositionLocalProvider(LocalStrings provides strings) {
                DelhiRaahiTheme(dark = dark) { App(settings = settings, store = store) }
            }
        }
    }
}
