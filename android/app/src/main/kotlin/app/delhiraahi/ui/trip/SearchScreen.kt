package app.delhiraahi.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import kotlinx.coroutines.async
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.delhiraahi.data.Api
import app.delhiraahi.data.DeviceLocation
import app.delhiraahi.data.PickedPlace
import app.delhiraahi.data.StopHit
import app.delhiraahi.data.TripViewModel
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.ListRow
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import kotlinx.coroutines.delay

private enum class Field { FROM, TO }

/** From/To on a dot-and-square rail, like ride apps. When both are set we go to trip options. */
@Composable
fun SearchScreen(trip: TripViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val ctx = LocalContext.current
    var field by remember { mutableStateOf(if (trip.editFrom || (trip.to != null && trip.from == null)) Field.FROM else Field.TO).also { trip.editFrom = false } }
    var fromText by remember { mutableStateOf(trip.from?.label.orEmpty()) }
    var toText by remember { mutableStateOf(trip.to?.label.orEmpty()) }
    var hits by remember { mutableStateOf<List<StopHit>>(emptyList()) }
    var places by remember { mutableStateOf<List<app.delhiraahi.data.PlaceHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var here by remember { mutableStateOf<PickedPlace?>(null) }
    val fromFocus = remember { FocusRequester() }
    val toFocus = remember { FocusRequester() }

    // Default origin = current location (if allowed).
    LaunchedEffect(Unit) {
        if (field == Field.TO) toFocus.requestFocus() else fromFocus.requestFocus()
        val loc = DeviceLocation.current(ctx) ?: return@LaunchedEffect
        here = PickedPlace.here(s.currentLocation, loc.latitude, loc.longitude)
        if (trip.from == null) { trip.from = here; fromText = s.currentLocation }
    }

    val query = (if (field == Field.FROM) fromText else toText).trim()
    val picked = if (field == Field.FROM) trip.from else trip.to
    LaunchedEffect(query, field) {
        if (query.length < 2 || picked?.label == query) { hits = emptyList(); places = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(260)
        kotlinx.coroutines.coroutineScope {
            val p = async { runCatching { Api.searchPlaces(query).results }.getOrDefault(emptyList()) }
            hits = runCatching { Api.searchStops(query).results }.getOrDefault(emptyList())
            places = p.await()
        }
        searching = false
    }

    fun choose(p: PickedPlace) {
        if (field == Field.FROM) { trip.from = p; fromText = p.label } else { trip.to = p; toText = p.label }
        hits = emptyList(); places = emptyList()
        when {
            trip.from != null && trip.to != null -> { trip.search(); onDone() }
            trip.from == null -> { field = Field.FROM; fromFocus.requestFocus() }
            else -> { field = Field.TO; toFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Column(Modifier.fillMaxWidth().widthIn(max = MaxWidth).align(Alignment.CenterHorizontally).padding(horizontal = Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back, tint = t.text) }
                Text(s.toPh, style = MaterialTheme.typography.titleMedium, color = t.text)
            }
            // The same sentence as Home: "From ____ to ____"
            Row(Modifier.padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.md), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    PlaceField(fromText, s.fromPh, s.from, fromFocus, field == Field.FROM, s.sFromPre, s.sFromPost,
                        onFocus = { field = Field.FROM },
                        onChange = { fromText = it; trip.from = null },
                        onClear = { fromText = ""; trip.from = null; fromFocus.requestFocus() })
                    PlaceField(toText, s.toPh, s.to, toFocus, field == Field.TO, s.sToPre, s.sToPost,
                        onFocus = { field = Field.TO },
                        onChange = { toText = it; trip.to = null },
                        onClear = { toText = ""; trip.to = null; toFocus.requestFocus() })
                }
                IconButton(onClick = {
                    val f = trip.from; trip.from = trip.to; trip.to = f
                    val ft = fromText; fromText = toText; toText = ft
                    if (trip.from != null && trip.to != null) { trip.search(); onDone() }
                }) { Icon(Icons.Outlined.SwapVert, contentDescription = s.swap, tint = t.text) }
            }

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = Space.sm)) {
                if (field == Field.FROM && here != null && query.length < 2) item {
                    ListRow(Icons.Outlined.MyLocation, s.currentLocation, onClick = { choose(here!!) })
                }
                if (query.length in 1..1) item { Text(s.typeMore, color = t.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.lg)) }
                if (searching && hits.isEmpty() && places.isEmpty()) item {
                    Row(Modifier.padding(Space.lg)) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.text) }
                }
                if (!searching && query.length >= 2 && hits.isEmpty() && places.isEmpty() && picked?.label != query) item {
                    Text(s.noMatch, color = t.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.lg))
                }
                // A stop whose name contains the typed text wins; otherwise addresses/landmarks come first.
                val stopsFirst = places.isEmpty() || hits.any { it.name.contains(query, ignoreCase = true) }
                val shownStops = if (stopsFirst) hits else hits.take(4)
                val stopSection: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                    if (shownStops.isNotEmpty() && places.isNotEmpty()) item(key = "h:stops") { SectionLabel(s.busStops) }
                    items(shownStops, key = { "s:" + it.name }) { h ->
                        ListRow(Icons.Outlined.Place, h.name, subtitle = s.routesServed(h.routes_served), onClick = { choose(PickedPlace.stop(h)) })
                    }
                }
                val placeSection: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                    if (places.isNotEmpty()) item(key = "h:places") { SectionLabel(s.placesHeader) }
                    items(places, key = { "p:${it.name}:${it.lat}:${it.lon}" }) { p ->
                        ListRow(Icons.Outlined.LocationCity, p.name, subtitle = p.area.ifBlank { null }, onClick = { choose(PickedPlace.place(p)) })
                    }
                    if (places.isNotEmpty()) item(key = "h:osm") {
                        Text(s.osmCredit, style = MaterialTheme.typography.labelSmall, color = t.muted, modifier = Modifier.padding(Space.md))
                    }
                }
                if (stopsFirst) { stopSection(); placeSection() } else { placeSection(); stopSection() }
                item { Spacer(Modifier.height(Space.xxl)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val t = LocalExtra.current
    Text(text, style = MaterialTheme.typography.labelLarge, color = t.muted,
        modifier = Modifier.padding(start = Space.md, top = Space.md, bottom = Space.xs).semantics { heading() })
}

@Composable
private fun PlaceField(
    value: String, placeholder: String, label: String, focus: FocusRequester, active: Boolean,
    pre: String, post: String,
    onFocus: () -> Unit, onChange: (String) -> Unit, onClear: () -> Unit,
) {
    val t = LocalExtra.current
    val big = MaterialTheme.typography.headlineSmall
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
    if (pre.isNotEmpty()) Text(pre, style = big.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = t.muted,
        modifier = Modifier.width(56.dp).padding(bottom = 8.dp))
    Column(Modifier.weight(1f)) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = if (active) t.primary else t.muted, style = big)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = big.copy(color = t.text),
                cursorBrush = SolidColor(t.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .onFocusChanged { if (it.isFocused) onFocus() }
                    .semantics { contentDescription = label },
            )
        }
        if (value.isNotEmpty() && active) IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = null, tint = t.muted, modifier = Modifier.size(20.dp))
        }
    }
    Box(Modifier.fillMaxWidth().height(if (active) 2.5.dp else 1.5.dp).background(if (active) t.primary else t.border))
    }
    if (post.isNotEmpty()) Text(post, style = big.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = t.muted,
        modifier = Modifier.padding(bottom = 8.dp))
    }
}
