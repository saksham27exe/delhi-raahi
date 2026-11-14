package app.delhiraahi.ui.home

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.delhiraahi.BuildConfig
import app.delhiraahi.data.Api
import app.delhiraahi.data.DeviceLocation
import app.delhiraahi.data.NearbyStop
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.ListRow
import app.delhiraahi.ui.theme.LocalExtra
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

/** Connaught Place: used until we know where the user is. */
private val DELHI = LatLng(28.6315, 77.2167)

private const val DARK_STYLE = """[
 {"elementType":"geometry","stylers":[{"color":"#1d1d1d"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#8a8a8a"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#1d1d1d"}]},
 {"featureType":"poi","stylers":[{"visibility":"off"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2c2c2c"}]},
 {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#3a3a3a"}]},
 {"featureType":"transit","stylers":[{"visibility":"off"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e1626"}]}
]"""
private const val LIGHT_STYLE = """[
 {"featureType":"poi","stylers":[{"visibility":"off"}]},
 {"featureType":"transit","stylers":[{"visibility":"off"}]}
]"""

private sealed interface NearbyState {
    data object NoPermission : NearbyState
    data object Locating : NearbyState
    data object NoFix : NearbyState
    data class Loaded(val stops: List<NearbyStop>) : NearbyState
    data object Error : NearbyState
}

@Composable
fun HomeScreen(wide: Boolean) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = LocalExtra.current.dark
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(DELHI, 12f) }
    var me by remember { mutableStateOf<LatLng?>(null) }
    var state by remember { mutableStateOf<NearbyState>(if (DeviceLocation.granted(ctx)) NearbyState.Locating else NearbyState.NoPermission) }
    var attempt by remember { mutableIntStateOf(0) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) { state = NearbyState.Locating; attempt++ }
    }

    LaunchedEffect(attempt) {
        if (!DeviceLocation.granted(ctx)) { state = NearbyState.NoPermission; return@LaunchedEffect }
        state = NearbyState.Locating
        val loc = DeviceLocation.current(ctx)
        if (loc == null) { state = NearbyState.NoFix; return@LaunchedEffect }
        val here = LatLng(loc.latitude, loc.longitude)
        me = here
        camera.animate(CameraUpdateFactory.newLatLngZoom(here, 15f))
        state = runCatching { NearbyState.Loaded(Api.nearbyStops(here.latitude, here.longitude, 1000).results) }
            .getOrElse { NearbyState.Error }
    }

    val stops = (state as? NearbyState.Loaded)?.stops.orEmpty()
    val focusStop: (NearbyStop) -> Unit = { st ->
        scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(st.lat, st.lon), 17f)) }
    }
    val whereTo = { Toast.makeText(ctx, s.comingSoon, Toast.LENGTH_SHORT).show() }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(
                isMyLocationEnabled = me != null && DeviceLocation.granted(ctx),
                mapStyleOptions = MapStyleOptions(if (dark) DARK_STYLE else LIGHT_STYLE),
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
            contentPadding = if (wide) androidx.compose.foundation.layout.PaddingValues(start = 420.dp)
            else androidx.compose.foundation.layout.PaddingValues(bottom = 360.dp),
        ) {
            stops.forEach { st -> Marker(state = rememberMarkerState(key = st.stop_id, position = LatLng(st.lat, st.lon)), title = st.name) }
        }

        if (!BuildConfig.HAS_MAPS_KEY) {
            Surface(
                Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
                shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp,
            ) { Text(s.mapNoKey, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium) }
        }

        SmallFloatingActionButton(
            onClick = { if (DeviceLocation.granted(ctx)) attempt++ else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            modifier = Modifier.align(if (wide) Alignment.BottomEnd else Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).padding(top = if (wide || BuildConfig.HAS_MAPS_KEY) 0.dp else 48.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) { Icon(Icons.Outlined.MyLocation, contentDescription = s.nearby) }

        val panel = Modifier.then(
            if (wide) Modifier.width(400.dp).fillMaxHeight().padding(16.dp).windowInsetsPadding(WindowInsets.safeDrawing)
            else Modifier.fillMaxWidth().height(380.dp).align(Alignment.BottomCenter)
        )
        Surface(
            panel,
            shape = if (wide) RoundedCornerShape(22.dp) else RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (!wide) Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
                Text(s.greeting, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp).semantics { heading() })
                Row(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(role = Role.Button, onClick = whereTo).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    Text(s.whereTo, style = MaterialTheme.typography.titleMedium)
                }
                Text(s.nearby, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 22.dp, bottom = 4.dp).semantics { heading() })
                when (val st = state) {
                    NearbyState.NoPermission -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(s.noLocation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text(s.allowLocation) }
                    }
                    NearbyState.Locating -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(s.locating, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    NearbyState.NoFix -> Text(s.noLocation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NearbyState.Error -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(s.errNetwork, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { attempt++ }) { Text(s.retry) }
                    }
                    is NearbyState.Loaded -> if (st.stops.isEmpty()) Text(s.noStopsNearby, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LazyColumn(Modifier.fillMaxWidth()) {
                        items(st.stops, key = { it.stop_id }) { stop ->
                            ListRow(Icons.Outlined.DirectionsBus, stop.name, trailing = s.metres(stop.distance_m), onClick = { focusStop(stop) })
                        }
                    }
                }
            }
        }
    }
}
