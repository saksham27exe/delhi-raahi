package app.delhiraahi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class SavedPlace(val label: String, val stopName: String? = null, val lat: Double? = null, val lon: Double? = null) {
    fun picked() = PickedPlace(label, stopName, lat, lon)

    companion object {
        fun of(p: PickedPlace) = SavedPlace(p.label, p.stopName, p.lat, p.lon)
    }
}

/** A past search. from == null means "from my location at the time". */
@Serializable
data class RecentTrip(val from: SavedPlace? = null, val to: SavedPlace)

data class Places(val home: SavedPlace? = null, val work: SavedPlace? = null, val recent: List<RecentTrip> = emptyList())

enum class Slot { HOME, WORK }

private val Context.placesStore by preferencesDataStore(name = "places")

/** Saved Home/Work and the last few searches, on this device only. */
class PlacesStore(private val context: Context) {
    private val kHome = stringPreferencesKey("home")
    private val kWork = stringPreferencesKey("work")
    private val kRecent = stringPreferencesKey("recent")

    private inline fun <reified T> decode(s: String?): T? = s?.let { runCatching { Api.json.decodeFromString<T>(it) }.getOrNull() }

    val places: Flow<Places> = context.placesStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p -> Places(decode(p[kHome]), decode(p[kWork]), decode<List<RecentTrip>>(p[kRecent]).orEmpty()) }

    suspend fun set(slot: Slot, place: SavedPlace?) = context.placesStore.edit {
        val k = if (slot == Slot.HOME) kHome else kWork
        if (place == null) it.remove(k) else it[k] = Api.json.encodeToString(place)
    }

    suspend fun addRecent(from: PickedPlace?, to: PickedPlace) = context.placesStore.edit {
        val entry = RecentTrip(from?.takeIf { f -> !f.isCoord }?.let(SavedPlace::of), SavedPlace.of(to))
        val old = decode<List<RecentTrip>>(it[kRecent]).orEmpty()
        it[kRecent] = Api.json.encodeToString((listOf(entry) + old.filter { r -> r != entry }).take(MAX_RECENT))
    }

    suspend fun clearRecent() = context.placesStore.edit { it.remove(kRecent) }

    companion object { const val MAX_RECENT = 5 }
}
