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

// ---- Journeys (POST /journeys/search) ----

@Serializable
data class SegCrowd(val category: String = "UNKNOWN", val crowd_index: Double? = null, val evidence: String? = null)

@Serializable
data class Segment(
    val segment_id: String,
    val from_stop: String,
    val to_stop: String,
    val depart: String? = null,
    val minutes: Double = 0.0,
    val lat: Double,
    val lon: Double,
    val to_lat: Double,
    val to_lon: Double,
    val crowd: SegCrowd? = null,
)

@Serializable
data class LegFare(
    val distance_km: Double = 0.0,
    val bus_type: String = "unknown",
    val fare: Double? = null,
    val fare_digital: Double? = null,
    val fare_non_ac: Double = 0.0,
    val fare_ac: Double = 0.0,
    val note: String? = null,
)

@Serializable
data class Leg(
    val route_id: String,
    val route: String,
    val route_desc: String = "",
    val board_stop_id: String,
    val board_stop: String,
    val alight_stop_id: String,
    val alight_stop: String,
    val departure: String,
    val arrival: String,
    val in_vehicle_minutes: Double = 0.0,
    val stops: Int = 0,
    val segments: List<Segment> = emptyList(),
    val fare: LegFare? = null,
)

@Serializable
data class WalkParts(val origin_min: Double = 0.0, val transfer_min: Double = 0.0, val destination_min: Double = 0.0)

@Serializable
data class JourneyFare(
    val total_min: Double = 0.0,
    val total_max: Double = 0.0,
    val total_digital_min: Double = 0.0,
    val total_digital_max: Double = 0.0,
    val tickets_needed: Int = 1,
)

@Serializable
data class Journey(
    val journey_id: String,
    val labels: List<String> = emptyList(),
    val departure: String,
    val arrival: String,
    val duration_minutes: Double = 0.0,
    val walk_minutes: Double = 0.0,
    val wait_minutes: Double = 0.0,
    val transfers: Int = 0,
    val crowd_level: String = "UNKNOWN",
    val mean_crowd_index: Double? = null,
    val evidence_quality: String = "",
    val explanation: List<String> = emptyList(),
    val legs: List<Leg> = emptyList(),
    val walk: WalkParts = WalkParts(),
    val fare: JourneyFare? = null,
    val summary: Map<String, String> = emptyMap(),
)

@Serializable
data class JourneyResult(val journeys: List<Journey> = emptyList(), val notes: List<String> = emptyList())

// ---- Fares, demo tickets ----

@Serializable
data class LegRef(val route_id: String, val board_stop_id: String, val alight_stop_id: String)

@Serializable
data class QuoteLeg(
    val route: String,
    val board_stop: String,
    val alight_stop: String,
    val distance_km: Double = 0.0,
    val bus_type: String = "non_ac",
    val leg_total: Double = 0.0,
    val note: String? = null,
)

@Serializable
data class Quote(
    val legs: List<QuoteLeg> = emptyList(),
    val passengers: Int = 1,
    val women_free: Int = 0,
    val total: Double = 0.0,
    val watermark: Map<String, String> = emptyMap(),
    val summary: Map<String, String> = emptyMap(),
)

@Serializable
data class TicketLeg(
    val route: String,
    val board_stop: String,
    val alight_stop: String,
    val bus_type: String = "",
    val leg_total: Double = 0.0,
)

/** Demo ticket as issued by the server (HMAC-signed QR payload). Never a real ticket. */
@Serializable
data class DemoTicket(
    val ticket_id: String,
    val issued_at: String,
    val valid_until: String,
    val passengers: Int = 1,
    val women_free: Int = 0,
    val total: Double = 0.0,
    val legs: List<TicketLeg> = emptyList(),
    val qr_payload: String,
    val watermark: Map<String, String> = emptyMap(),
)

@Serializable
data class VerifyResult(val valid_signature: Boolean = false, val status: String = "")

// ---- Live ----

@Serializable
data class Arrival(
    val route_id: String,
    val route: String,
    val towards: String = "",
    val eta: String,
    val eta_min: Double = 0.0,
    val basis: String = "scheduled",
    val vehicle_id: String? = null,
    val delay_min: Double? = null,
)

@Serializable
data class ArrivalsBoard(
    val stop_id: String,
    val stop: String,
    val live_feed: Boolean = false,
    val arrivals: List<Arrival> = emptyList(),
    val summary: Map<String, String> = emptyMap(),
)

@Serializable
data class Vehicle(
    val vehicle_id: String,
    val route_id: String = "",
    val route: String? = null,
    val lat: Double,
    val lon: Double,
    val stale: Boolean = false,
    val next_stop: String? = null,
    val delay_min: Double? = null,
)

@Serializable
data class VehiclesResponse(val live: Boolean = false, val vehicles: List<Vehicle> = emptyList())

@Serializable
data class StopDetail(val stop_id: String, val name: String, val lat: Double, val lon: Double)
