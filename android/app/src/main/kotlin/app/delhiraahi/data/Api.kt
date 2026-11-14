package app.delhiraahi.data

import app.delhiraahi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.TimeUnit

/** [reason] is a machine-readable code such as "no_stop_nearby"; [side] says which end ("origin" / "destination"). */
class ApiException(val status: Int, message: String, val reason: String? = null, val side: String? = null) : Exception(message)

/**
 * Thin client for the backend. The base URL defaults to `api.base` from local.properties
 * and can be overridden at runtime from the Account screen.
 */
object Api {
    val defaultBase: String = BuildConfig.API_BASE.trimEnd('/')

    @Volatile
    var base: String = defaultBase
        private set

    fun setBase(custom: String?) {
        base = custom?.trim()?.trimEnd('/')?.takeIf { it.toHttpUrlOrNull() != null } ?: defaultBase
    }

    /** Login token for the signed-in user; null for guests. Never logged. */
    @Volatile
    var token: String? = null

    private fun Request.Builder.auth(): Request.Builder = apply { token?.let { header("Authorization", "Bearer $it") } }

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** FastAPI errors look like {"detail": "..."}; show just the message. */
    fun detail(body: String): String = runCatching {
        val d = json.parseToJsonElement(body).let { (it as kotlinx.serialization.json.JsonObject)["detail"] }
        (d as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: ((d as? kotlinx.serialization.json.JsonObject)?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content
    }.getOrNull() ?: body.take(200)

    /** Machine-readable error code from {"detail": {"code": ...}}, e.g. "no_stop_nearby". */
    fun reason(body: String): String? = runCatching {
        val d = json.parseToJsonElement(body).let { (it as kotlinx.serialization.json.JsonObject)["detail"] }
        ((d as? kotlinx.serialization.json.JsonObject)?.get("code") as? kotlinx.serialization.json.JsonPrimitive)?.content
    }.getOrNull()

    private fun detailField(body: String, key: String): String? = runCatching {
        val d = json.parseToJsonElement(body).let { (it as kotlinx.serialization.json.JsonObject)["detail"] }
        ((d as? kotlinx.serialization.json.JsonObject)?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content
    }.getOrNull()

    private suspend inline fun <reified T> get(path: String, query: Map<String, Any?> = emptyMap()): T =
        withContext(Dispatchers.IO) {
            val url = "$base/api/v1$path".toHttpUrlOrNull()?.newBuilder()?.apply {
                query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) }
            }?.build() ?: throw ApiException(0, "bad server address")
            val response = try {
                client.newCall(Request.Builder().url(url).auth().get().build()).execute()
            } catch (e: IOException) {
                throw ApiException(0, "network")
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw ApiException(it.code, detail(body))
                json.decodeFromString<T>(body)
            }
        }

    private suspend inline fun <reified T> post(path: String, body: JsonObject, timeoutMs: Long = 45_000, method: String = "POST"): T =
        withContext(Dispatchers.IO) {
            val url = "$base/api/v1$path".toHttpUrlOrNull() ?: throw ApiException(0, "bad server address")
            val req = Request.Builder().url(url).auth().method(method, body.toString().toRequestBody("application/json".toMediaType())).build()
            val call = client.newBuilder().readTimeout(timeoutMs, TimeUnit.MILLISECONDS).build().newCall(req)
            val response = try { call.execute() } catch (e: IOException) { throw ApiException(0, "network") }
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw ApiException(it.code, detail(text), reason(text), detailField(text, "side"))
                json.decodeFromString<T>(text)
            }
        }

    suspend fun journeys(body: JsonObject): JourneyResult = post("/journeys/search", body)

    private fun ticketBody(legs: List<LegRef>, passengers: Int, womenFree: Int, busTypes: List<String?>?) = buildJsonObject {
        put("legs", json.encodeToJsonElement(legs))
        put("passengers", passengers)
        put("women_free", womenFree)
        if (busTypes != null) put("bus_types", json.encodeToJsonElement(busTypes))
    }

    suspend fun quote(legs: List<LegRef>, passengers: Int, womenFree: Int, busTypes: List<String?>?): Quote =
        post("/fares/quote", ticketBody(legs, passengers, womenFree, busTypes))
    suspend fun book(legs: List<LegRef>, passengers: Int, womenFree: Int, busTypes: List<String?>?): DemoTicket =
        post("/tickets/book", ticketBody(legs, passengers, womenFree, busTypes))
    suspend fun verify(qr: String): VerifyResult = post("/tickets/verify", buildJsonObject { put("qr_payload", qr) })

    suspend fun stop(id: String): StopDetail = get("/stops/${java.net.URLEncoder.encode(id, "UTF-8")}")
    suspend fun vehicle(id: String): VehicleDetail = get("/live/vehicle", mapOf("id" to id))
    suspend fun track(routeId: String, board: String, alight: String, vehicleId: String?, skip: Set<String>): TrackResult =
        get("/live/track", buildMap {
            put("route_id", routeId); put("board_stop_id", board); put("alight_stop_id", alight)
            vehicleId?.let { put("vehicle_id", it) }
            if (skip.isNotEmpty()) put("skip", skip.joinToString(","))
        })
    suspend fun arrivals(stopId: String): ArrivalsBoard = get("/live/arrivals/${java.net.URLEncoder.encode(stopId, "UTF-8")}")
    suspend fun vehicles(routeId: String? = null, bbox: String? = null): VehiclesResponse =
        get("/live/vehicles", mapOf("route_id" to routeId, "bbox" to bbox))

    suspend fun health(): Health = get("/health")

    // ---- Accounts, DEMO wallet, passes ----
    suspend fun register(phone: String, password: String, name: String): AuthResponse =
        post("/auth/register", buildJsonObject { put("phone", phone); put("password", password); put("name", name) })
    suspend fun login(phone: String, password: String): AuthResponse =
        post("/auth/login", buildJsonObject { put("phone", phone); put("password", password) })
    suspend fun logout(): OkResponse = post("/auth/logout", buildJsonObject { })
    suspend fun me(): MeResponse = get("/me")
    suspend fun updateMe(name: String? = null, womanTraveller: Boolean? = null): UserResponse =
        post("/me", buildJsonObject { name?.let { put("name", it) }; womanTraveller?.let { put("woman_traveller", it) } }, method = "PATCH")
    suspend fun changePassword(old: String, new: String): TokenResponse =
        post("/me/password", buildJsonObject { put("old_password", old); put("new_password", new) })
    suspend fun deleteAccount(password: String): OkResponse = post("/me/delete", buildJsonObject { put("password", password) })
    suspend fun wallet(): Wallet = get("/wallet")
    suspend fun topup(amount: Int): Wallet = post("/wallet/topup", buildJsonObject { put("amount", amount) })
    suspend fun myTickets(): MyTickets = get("/tickets/mine")
    suspend fun passCatalog(): PassCatalog = get("/passes/catalog")
    suspend fun myPasses(): MyPasses = get("/passes/mine")
    suspend fun buyPass(kind: String, busType: String): DemoPass =
        post("/passes/buy", buildJsonObject { put("kind", kind); put("bus_type", busType) })
    suspend fun feedback(text: String): OkResponse = post("/feedback", buildJsonObject { put("text", text) })
    suspend fun searchRoutes(q: String): RouteSearchResponse = get("/routes/search", mapOf("q" to q))
    suspend fun routeDay(routeId: String, date: String): RouteDay =
        get("/crowd/route/${java.net.URLEncoder.encode(routeId, "UTF-8")}", mapOf("date" to date))
    suspend fun hotspots(date: String, time: String): HotspotsResponse = get("/crowd/hotspots", mapOf("date" to date, "time" to time, "limit" to 120))
    suspend fun modelCard(): kotlinx.serialization.json.JsonObject = get("/insights/model")
    suspend fun nearbyStops(lat: Double, lon: Double, radiusM: Int = 800): NearbyResponse =
        get("/stops/nearby", mapOf("lat" to lat, "lon" to lon, "radius_m" to radiusM))
    suspend fun searchStops(q: String): StopSearchResponse = get("/stops/search", mapOf("q" to q, "limit" to 8))
    suspend fun searchPlaces(q: String): PlaceSearchResponse = get("/places/search", mapOf("q" to q, "limit" to 6))
    suspend fun liveStatus(): LiveStatus = get("/live/status")
    suspend fun walkDirections(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): WalkDirections =
        get("/walk/directions", mapOf("from_lat" to fromLat, "from_lon" to fromLon, "to_lat" to toLat, "to_lon" to toLon))
}
