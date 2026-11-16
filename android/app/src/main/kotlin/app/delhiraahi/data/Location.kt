package app.delhiraahi.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Foreground location via the platform LocationManager (no Play Services dependency). */
object DeviceLocation {
    fun granted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Last known fix if recent (< 2 min), otherwise one fresh fix; null when off or denied. */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        if (!granted(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 120_000 }
            .minByOrNull { it.accuracy }
        if (recent != null) return recent
        val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER } ?: providers.firstOrNull() ?: return null
        return suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(ctx)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } catch (_: SecurityException) {          // permission withdrawn, or only coarse for this provider
                if (cont.isActive) cont.resume(null)
            } catch (_: IllegalArgumentException) {   // provider disappeared
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /** Continuous fixes (about every 2 s) while collected: GPS outdoors, network as a fallback. */
    @SuppressLint("MissingPermission")
    fun updates(ctx: Context): Flow<Location> = callbackFlow {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!granted(ctx)) { close(); return@callbackFlow }
        val listener = LocationListenerCompat { trySend(it) }
        val req = LocationRequestCompat.Builder(2_000).setMinUpdateDistanceMeters(2f)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build()
        val exec = ContextCompat.getMainExecutor(ctx)
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            .forEach { runCatching { LocationManagerCompat.requestLocationUpdates(lm, it, req, exec, listener) } }
        awaitClose { LocationManagerCompat.removeUpdates(lm, listener) }
    }
}
