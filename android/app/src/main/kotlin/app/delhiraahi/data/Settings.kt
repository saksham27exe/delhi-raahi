package app.delhiraahi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class Lang { EN, HI }
enum class ThemePref { SYSTEM, LIGHT, DARK }

data class Settings(
    val lang: Lang = Lang.EN,
    val theme: ThemePref = ThemePref.SYSTEM,
    val apiBase: String = "",
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** On-device settings only; no accounts. Read errors fall back to defaults. */
class SettingsStore(private val context: Context) {
    private val kLang = stringPreferencesKey("lang")
    private val kTheme = stringPreferencesKey("theme")
    private val kBase = stringPreferencesKey("api_base")

    val settings: Flow<Settings> = context.dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p ->
            Settings(
                lang = p[kLang]?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN,
                theme = p[kTheme]?.let { runCatching { ThemePref.valueOf(it) }.getOrNull() } ?: ThemePref.SYSTEM,
                apiBase = p[kBase].orEmpty(),
            )
        }

    suspend fun setLang(l: Lang) = context.dataStore.edit { it[kLang] = l.name }
    suspend fun setTheme(t: ThemePref) = context.dataStore.edit { it[kTheme] = t.name }
    suspend fun setApiBase(b: String) = context.dataStore.edit { it[kBase] = b.trim() }
}
