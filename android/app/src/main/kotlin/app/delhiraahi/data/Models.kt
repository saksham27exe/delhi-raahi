package app.delhiraahi.data

import kotlinx.serialization.Serializable

/** Wire types for the DelhiRaahi backend (/api/v1). Unknown fields are ignored. */

@Serializable
data class Health(val status: String, val components: Map<String, Boolean> = emptyMap())

@Serializable
data class NearbyStop(
    val stop_id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val distance_m: Int,
)

@Serializable
data class NearbyResponse(val results: List<NearbyStop> = emptyList())

@Serializable
data class StopHit(
    val name: String,
    val stop_ids: List<String>,
    val lat: Double,
    val lon: Double,
    val routes_served: Int = 0,
)

@Serializable
data class StopSearchResponse(val results: List<StopHit> = emptyList())

@Serializable
data class LiveStatus(val live: Boolean, val vehicles: Int = 0)
