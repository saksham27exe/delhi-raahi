package app.delhiraahi.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.delhiraahi.data.DeviceLocation
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Map styles: OpenStreetMap data, no API key.
 * Light = OpenFreeMap "Liberty" (detailed: buildings, road names, transit, landmarks).
 * Dark = CARTO "Dark Matter".
 */
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

/** Connaught Place: shown until we know where the user is. */
val DELHI_CENTER = LatLng(28.6315, 77.2167)

/** A route line: bus legs in crowd colours, walks as thin grey. */
data class MapLine(val points: List<Pair<Double, Double>>, val color: Int, val widthDp: Float = 5f)

data class MapPin(val id: String, val lat: Double, val lon: Double, val title: String, val kind: Kind = Kind.STOP) {
    enum class Kind { STOP, BUS, FROM, TO }
}

/** Imperative handle for camera moves from screens. */
class MapController {
    private var pending: Triple<Double, Double, Double>? = null
    internal var map: MapLibreMap? = null
        set(value) {
            field = value
            // A move requested before the map was ready is applied as soon as it is.
            pending?.let { (lat, lon, z) -> value?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), z)); pending = null }
        }

    fun moveTo(lat: Double, lon: Double, zoom: Double = 15.5) {
        val m = map
        if (m == null) pending = Triple(lat, lon, zoom)
        else m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom), 600)
    }
}

/** Small round marker: black dot with white ring for stops, coloured for other kinds. */
private fun dotIcon(ctx: Context, fill: Int, sizeDp: Int): Icon {
    val px = (sizeDp * ctx.resources.displayMetrics.density).toInt()
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0x55000000; c.drawCircle(px / 2f, px / 2f + 1, px / 2f - 1, p)
    p.color = 0xFFFFFFFF.toInt(); c.drawCircle(px / 2f, px / 2f, px / 2f - 2, p)
    p.color = fill; c.drawCircle(px / 2f, px / 2f, px / 2f - px / 5f, p)
    return IconFactory.getInstance(ctx).fromBitmap(bmp)
}

@SuppressLint("MissingPermission")
@Composable
fun BusMap(
    modifier: Modifier = Modifier,
    controller: MapController,
    pins: List<MapPin>,
    dark: Boolean,
    bottomPadding: Dp,
    showMyLocation: Boolean,
    lines: List<MapLine> = emptyList(),
    fitKey: Any? = null,
    onPinClick: ((MapPin) -> Unit)? = null,
    /** Called when the camera stops moving: bbox "latS,lonW,latN,lonE" and zoom. */
    onCameraIdle: ((String, Double) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(Bundle()) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val idleCb by rememberUpdatedState(onCameraIdle)
    val pinCb by rememberUpdatedState(onPinClick)
    var style by remember { mutableStateOf<Style?>(null) }
    val icons = remember {
        mapOf(
            MapPin.Kind.STOP to dotIcon(ctx, 0xFF000000.toInt(), 22),
            MapPin.Kind.BUS to dotIcon(ctx, 0xFF0F766E.toInt(), 22),
            MapPin.Kind.FROM to dotIcon(ctx, 0xFF16A34A.toInt(), 26),
            MapPin.Kind.TO to dotIcon(ctx, 0xFF000000.toInt(), 28),
        )
    }

    // Forward the screen lifecycle to the MapView.
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            controller.map = null
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder().target(DELHI_CENTER).zoom(12.0).build()
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            map = m
            controller.map = m
            m.addOnCameraIdleListener {
                val b = m.projection.visibleRegion.latLngBounds
                idleCb?.invoke("${b.latitudeSouth},${b.longitudeWest},${b.latitudeNorth},${b.longitudeEast}", m.cameraPosition.zoom)
            }
        }
    }

    // (Re)load the style when the theme changes.
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { style = it }
    }

    // Keep the map centre visible above the bottom sheet.
    LaunchedEffect(map, bottomPadding) {
        map?.setPadding(0, 0, 0, with(density) { bottomPadding.roundToPx() })
    }

    // Lines + pins (annotations are cleared and redrawn together)
    LaunchedEffect(map, style, pins, lines) {
        val m = map ?: return@LaunchedEffect
        if (style == null) return@LaunchedEffect
        m.clear()
        lines.forEach { l ->
            if (l.points.size >= 2) m.addPolyline(PolylineOptions().addAll(l.points.map { LatLng(it.first, it.second) }).color(l.color).width(l.widthDp))
        }
        val byMarker = pins.associateBy { pin ->
            m.addMarker(MarkerOptions().position(LatLng(pin.lat, pin.lon)).title(pin.title).icon(icons.getValue(pin.kind))).id
        }
        m.setOnMarkerClickListener { mk -> byMarker[mk.id]?.let { pinCb?.invoke(it) }; false }
    }

    // Fit the camera to everything drawn whenever fitKey changes (e.g. another trip selected).
    LaunchedEffect(map, fitKey) {
        val m = map ?: return@LaunchedEffect
        if (fitKey == null) return@LaunchedEffect
        val pts = lines.flatMap { it.points } + pins.map { it.lat to it.lon }
        if (pts.size < 2) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().includes(pts.map { LatLng(it.first, it.second) }).build()
        val pad = with(density) { 48.dp.roundToPx() }
        val sheet = with(density) { bottomPadding.roundToPx() }
        runCatching { m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad, pad * 2, pad, sheet + pad), 600) }
    }

    // Blue "you are here" dot (MapLibre location component), only with permission.
    LaunchedEffect(map, style, showMyLocation) {
        val m = map ?: return@LaunchedEffect
        val st = style ?: return@LaunchedEffect
        if (!showMyLocation || !DeviceLocation.granted(ctx)) return@LaunchedEffect
        runCatching {
            m.locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(ctx, st).build())
            m.locationComponent.isLocationComponentEnabled = true
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
