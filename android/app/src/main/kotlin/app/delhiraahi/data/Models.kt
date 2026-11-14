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

/** Stops inside the visible map area: the full count, and the ones nearest the centre. */
@Serializable
data class StopsInArea(val count: Int = 0, val results: List<NearbyStop> = emptyList())

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
data class WalkParts(
    val origin_min: Double = 0.0, val transfer_min: Double = 0.0, val destination_min: Double = 0.0,
    val origin_m: Double? = null, val destination_m: Double? = null,
    val origin_path: List<List<Double>>? = null, val destination_path: List<List<Double>>? = null,
    val basis: WalkBasis? = null,
)

/** "osm_road" (real walking route), "straight_line_estimate" (fallback) or "stop" (trip starts at a stop). */
@Serializable
data class WalkBasis(val origin: String = "stop", val destination: String = "stop")

/** An address or landmark from OpenStreetMap (via our backend). */
@Serializable
data class PlaceHit(val name: String, val area: String = "", val lat: Double, val lon: Double, val kind: String = "")

@Serializable
data class PlaceSearchResponse(val results: List<PlaceHit> = emptyList())

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
    val kind: String = "direct",
    val connections: List<Connection> = emptyList(),
    val summary: Map<String, String> = emptyMap(),
)

@Serializable
data class JourneyResult(
    val journeys: List<Journey> = emptyList(),
    val notes: List<String> = emptyList(),
    val candidates_considered: Int = 0,
    val walk_option: WalkOption? = null,
)

/** Walking the whole way (offered for trips up to 2 km). basis: "osm_road" or "straight_line_estimate". */
@Serializable
data class WalkOption(val distance_m: Double, val minutes: Double, val path: List<List<Double>>? = null, val basis: String = "osm_road")

/** One change of bus: where you get off, where you board, the walk between and the wait. */
@Serializable
data class Connection(
    val alight: String, val board: String, val walk_min: Double = 0.0, val wait_min: Double = 0.0,
    val from_route: String = "", val to_route: String = "",
)

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
    val route_id: String = "",
    val board_stop_id: String = "",
    val alight_stop_id: String = "",
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
    val holder: String? = null,
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
    val speed_kmh: Double? = null,
    val age_s: Int? = null,
    val scheduled_trip_id: String? = null,
)

/** A stop on a live bus's route; passed / eta filled for GET /live/vehicle. */
@Serializable
data class LiveStop(
    val stop_id: String, val name: String, val lat: Double, val lon: Double,
    val passed: Boolean = false, val eta: String? = null, val eta_min: Double? = null, val basis: String? = null,
)

/** GET /live/vehicle */
@Serializable
data class VehicleDetail(
    val vehicle: Vehicle, val route_id: String = "", val route: String = "", val route_long_name: String? = null,
    val towards: String? = null, val stops: List<LiveStop> = emptyList(), val matched: Boolean = false,
)

/** GET /live/track: phase is approaching / on_board / arrived / no_live / unknown_route. */
@Serializable
data class TrackResult(
    val phase: String, val route_id: String = "", val route: String = "", val board_stop: String = "", val alight_stop: String = "",
    val live_feed: Boolean = false, val vehicle: Vehicle? = null, val basis: String? = null, val towards: String? = null,
    val stops_away: Int = 0, val stops_left: Int = 0,
    val eta_board: String? = null, val eta_board_min: Double? = null, val eta_alight: String? = null, val eta_alight_min: Double? = null,
    val next_stop: String? = null, val stops: List<LiveStop> = emptyList(), val progress: Double? = null,
    val next_scheduled: String? = null, val next_scheduled_min: Double? = null, val message: String? = null,
)

@Serializable
data class VehiclesResponse(val live: Boolean = false, val vehicles: List<Vehicle> = emptyList())

@Serializable
data class StopDetail(val stop_id: String, val name: String, val lat: Double, val lon: Double)

// K4: insights
@Serializable
data class RouteHit(val route_id: String, val route: String, val description: String = "")

@Serializable
data class RouteSearchResponse(val results: List<RouteHit> = emptyList())

@Serializable
data class DayBucket(val time: String, val category: String = "UNKNOWN", val busiest_segment: String? = null)

@Serializable
data class RouteDay(
    val route_id: String,
    val route: String = "",
    val buckets: List<DayBucket> = emptyList(),
    val quietest: String? = null,
    val busiest: String? = null,
    val summary: Map<String, String> = emptyMap(),
)

@Serializable
data class Hotspot(
    val segment_id: String,
    val route: String? = null,
    val from_stop: String = "",
    val to_stop: String = "",
    val lat: Double,
    val lon: Double,
    val to_lat: Double,
    val to_lon: Double,
    val category: String = "UNKNOWN",
)

@Serializable
data class HotspotsResponse(val segments: List<Hotspot> = emptyList())

// K5: accounts, DEMO wallet, passes
@Serializable
data class User(
    val id: Int,
    val phone: String,
    val phone_masked: String = "",
    val name: String = "",
    val woman_traveller: Boolean = false,
)

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class MeResponse(val user: User, val wallet_balance: Double = 0.0)

@Serializable
data class UserResponse(val user: User)

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class OkResponse(val ok: Boolean = true, val deleted: Boolean = false)

@Serializable
data class WalletTx(val kind: String, val amount: Double, val balance_after: Double = 0.0, val ref: String? = null, val created_at: String = "")

@Serializable
data class Wallet(val balance: Double = 0.0, val transactions: List<WalletTx> = emptyList())

@Serializable
data class MyTickets(val tickets: List<DemoTicket> = emptyList())

@Serializable
data class DemoPass(
    val pass_id: String,
    val kind: String,
    val bus_type: String,
    val price: Double = 0.0,
    val price_verified: Boolean = false,
    val holder: String? = null,
    val issued_at: String,
    val valid_until: String,
    val qr_payload: String,
    val watermark: Map<String, String> = emptyMap(),
)

@Serializable
data class MyPasses(val passes: List<DemoPass> = emptyList())

@Serializable
data class PassType(
    val kind: String,
    val name: Map<String, String> = emptyMap(),
    val price: Map<String, Double> = emptyMap(),
    val validity: Map<String, String> = emptyMap(),
    val verified: Boolean = false,
    val sources: List<String> = emptyList(),
)

@Serializable
data class PassCatalog(
    val checked_on: String = "",
    val coverage: Map<String, Map<String, String>> = emptyMap(),
    val passes: List<PassType> = emptyList(),
)


/** One manoeuvre of a walking route (OSRM foot). bearing_after: degrees, 0 = north. */
@Serializable
data class WalkStep(
    val type: String = "continue", val modifier: String = "", val name: String = "",
    val distance_m: Double = 0.0, val lat: Double, val lon: Double, val bearing_after: Int = 0,
)

/** GET /walk/directions */
@Serializable
data class WalkDirections(
    val distance_m: Double = 0.0, val duration_s: Double = 0.0,
    val path: List<List<Double>> = emptyList(), val steps: List<WalkStep> = emptyList(),
)
