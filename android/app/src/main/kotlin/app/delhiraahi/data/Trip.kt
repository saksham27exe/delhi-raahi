package app.delhiraahi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val DELHI_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

/** A place picked in search: a named stop (the backend resolves nearby platforms) or a coordinate. */
data class PickedPlace(val label: String, val stopName: String? = null, val lat: Double? = null, val lon: Double? = null) {
    val isCoord get() = stopName == null

    fun toJson(): JsonObject = if (stopName != null) buildJsonObject { put("type", "stop_name"); put("name", stopName) }
    else buildJsonObject { put("type", "coord"); put("lat", lat ?: 0.0); put("lon", lon ?: 0.0); put("label", label) }

    companion object {
        fun stop(h: StopHit) = PickedPlace(h.name, stopName = h.name, lat = h.lat, lon = h.lon)
        fun here(label: String, lat: Double, lon: Double) = PickedPlace(label, lat = lat, lon = lon)
    }
}

enum class Preference(val wire: String) { FASTEST("fastest"), BALANCED("balanced"), LEAST_CROWDED("least_crowded") }

/** When to leave: now, an offset from now, or a fixed time tomorrow. */
sealed interface LeaveAt {
    data object Now : LeaveAt
    data class InMinutes(val min: Int) : LeaveAt
    data class Tomorrow(val hour: Int, val minute: Int) : LeaveAt

    fun resolve(now: ZonedDateTime = ZonedDateTime.now(DELHI_ZONE)): ZonedDateTime = when (this) {
        Now -> now
        is InMinutes -> now.plusMinutes(min.toLong())
        is Tomorrow -> ZonedDateTime.of(LocalDate.now(DELHI_ZONE).plusDays(1), LocalTime.of(hour, minute), DELHI_ZONE)
    }

    companion object {
        val options = listOf(Now, InMinutes(15), InMinutes(30), InMinutes(60), InMinutes(120), Tomorrow(8, 30), Tomorrow(18, 0))
    }
}

val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Failed(val error: Throwable) : Load<Nothing>
}

/** Activity-scoped trip state shared by Search → Results → Journey. */
class TripViewModel : ViewModel() {
    var from by mutableStateOf<PickedPlace?>(null)
    var to by mutableStateOf<PickedPlace?>(null)
    var leaveAt by mutableStateOf<LeaveAt>(LeaveAt.Now)
    var preference by mutableStateOf(Preference.BALANCED)
    var result by mutableStateOf<Load<JourneyResult>>(Load.Idle)
        private set
    var selected by mutableIntStateOf(0)
    private var job: Job? = null

    fun search() {
        val f = from ?: return
        val t = to ?: return
        job?.cancel()
        result = Load.Loading
        selected = 0
        val departure = leaveAt.resolve().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val body = buildJsonObject {
            put("origin", f.toJson())
            put("destination", t.toJson())
            put("departure_datetime", departure)
            put("preference", preference.wire)
        }
        job = viewModelScope.launch {
            result = runCatching { Api.journeys(body) }.fold({ Load.Ok(it) }, { Load.Failed(it) })
        }
    }

    val journeys: List<Journey> get() = (result as? Load.Ok)?.value?.journeys.orEmpty()
}
