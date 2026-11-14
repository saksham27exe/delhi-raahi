package app.delhiraahi.data

import app.delhiraahi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, message: String) : Exception(message)

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

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend inline fun <reified T> get(path: String, query: Map<String, Any?> = emptyMap()): T =
        withContext(Dispatchers.IO) {
            val url = "$base/api/v1$path".toHttpUrlOrNull()?.newBuilder()?.apply {
                query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) }
            }?.build() ?: throw ApiException(0, "bad server address")
            val response = try {
                client.newCall(Request.Builder().url(url).get().build()).execute()
            } catch (e: IOException) {
                throw ApiException(0, "network")
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw ApiException(it.code, body.take(200))
                json.decodeFromString<T>(body)
            }
        }

    suspend fun health(): Health = get("/health")
    suspend fun nearbyStops(lat: Double, lon: Double, radiusM: Int = 800): NearbyResponse =
        get("/stops/nearby", mapOf("lat" to lat, "lon" to lon, "radius_m" to radiusM))
    suspend fun searchStops(q: String): StopSearchResponse = get("/stops/search", mapOf("q" to q, "limit" to 8))
    suspend fun liveStatus(): LiveStatus = get("/live/status")
}
