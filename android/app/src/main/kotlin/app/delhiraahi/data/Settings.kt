package app.delhiraahi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    /** Spoken + vibration alerts while waiting: "bus is 5 min away", "arriving now". */
    val alertArrive: Boolean = true,
    /** Spoken + vibration alerts on the bus: stops left, "next stop is yours", "get off here". */
    val alertGetOff: Boolean = true,
    /** Off (default): alerts are a soft chime + vibration. On: they are also spoken aloud. */
    val alertSpoken: Boolean = false,
    /** "Hey Raahi" opens the assistant. null = not chosen yet: on when TalkBack is on, else off. */
    val wakeWord: Boolean? = null,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** On-device settings only; no accounts. Read errors fall back to defaults. */
class SettingsStore(private val context: Context) {
    private val kLang = stringPreferencesKey("lang")
    private val kTheme = stringPreferencesKey("theme")
    private val kBase = stringPreferencesKey("api_base")
    private val kArrive = booleanPreferencesKey("alert_arrive")
    private val kGetOff = booleanPreferencesKey("alert_get_off")
    private val kSpoken = booleanPreferencesKey("alert_spoken")
    private val kWake = booleanPreferencesKey("wake_word")

    val settings: Flow<Settings> = context.dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p ->
            Settings(
                lang = p[kLang]?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN,
                theme = p[kTheme]?.let { runCatching { ThemePref.valueOf(it) }.getOrNull() } ?: ThemePref.SYSTEM,
                apiBase = p[kBase].orEmpty(),
                alertArrive = p[kArrive] ?: true,
                alertGetOff = p[kGetOff] ?: true,
                alertSpoken = p[kSpoken] ?: false,
                wakeWord = p[kWake],
            )
        }

    suspend fun setLang(l: Lang) = context.dataStore.edit { it[kLang] = l.name }
    suspend fun setTheme(t: ThemePref) = context.dataStore.edit { it[kTheme] = t.name }
    suspend fun setAlertArrive(on: Boolean) = context.dataStore.edit { it[kArrive] = on }
    suspend fun setAlertGetOff(on: Boolean) = context.dataStore.edit { it[kGetOff] = on }
    suspend fun setAlertSpoken(on: Boolean) = context.dataStore.edit { it[kSpoken] = on }
    suspend fun setWakeWord(on: Boolean) = context.dataStore.edit { it[kWake] = on }
    suspend fun setApiBase(b: String) = context.dataStore.edit { it[kBase] = b.trim() }
}
