package app.delhiraahi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Context.ticketData by preferencesDataStore(name = "tickets")

/** Cache of the signed-in account's demo tickets (newest first, max 50); cleared on log out. */
class TicketStore(private val context: Context) {
    private val key = stringPreferencesKey("tickets_json")

    val tickets: Flow<List<DemoTicket>> = context.ticketData.data
        .catch { emit(emptyPreferences()) }
        .map { p -> p[key]?.let { runCatching { Api.json.decodeFromString<List<DemoTicket>>(it) }.getOrNull() }.orEmpty() }

    private suspend fun write(f: (List<DemoTicket>) -> List<DemoTicket>) {
        context.ticketData.edit { p ->
            val cur = p[key]?.let { runCatching { Api.json.decodeFromString<List<DemoTicket>>(it) }.getOrNull() }.orEmpty()
            p[key] = Api.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DemoTicket.serializer()), f(cur))
        }
    }

    suspend fun add(t: DemoTicket) = write { (listOf(t) + it.filter { x -> x.ticket_id != t.ticket_id }).take(50) }
    suspend fun remove(id: String) = write { it.filter { x -> x.ticket_id != id } }
    /** Replace the cache with the account's tickets (server is the source of truth when logged in). */
    suspend fun replaceAll(list: List<DemoTicket>) = write { list.take(50) }
    suspend fun clear() = write { emptyList() }
}

fun DemoTicket.isExpired(): Boolean = runCatching { OffsetDateTime.parse(valid_until).isBefore(OffsetDateTime.now()) }.getOrDefault(false)

/** "14 Nov, 13:13" in Delhi time; Hindi month names in Hindi mode. */
fun formatDelhi(iso: String, lang: Lang): String = runCatching {
    val t = OffsetDateTime.parse(iso).atZoneSameInstant(DELHI_ZONE)
    if (lang == Lang.HI) {
        val months = listOf("जन", "फ़र", "मार्च", "अप्रै", "मई", "जून", "जुला", "अग", "सित", "अक्टू", "नव", "दिस")
        "${t.dayOfMonth} ${months[t.monthValue - 1]}, ${t.format(HHMM)}"
    } else t.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH))
}.getOrDefault(iso)

fun isPast(iso: String): Boolean = runCatching { OffsetDateTime.parse(iso).isBefore(OffsetDateTime.now()) }.getOrDefault(false)
