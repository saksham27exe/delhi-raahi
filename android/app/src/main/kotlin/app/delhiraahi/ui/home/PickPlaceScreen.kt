package app.delhiraahi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.delhiraahi.data.Api
import app.delhiraahi.data.PickedPlace
import app.delhiraahi.data.PlacesStore
import app.delhiraahi.data.SavedPlace
import app.delhiraahi.data.Slot
import app.delhiraahi.data.StopHit
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.ui.common.ListRow
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pick a stop to save as Home or Work (stored on this device only). */
@Composable
fun PickPlaceScreen(slot: Slot, places: PlacesStore, onDone: () -> Unit) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val scope = rememberCoroutineScope()
    val slotName = if (slot == Slot.HOME) s.home else s.work
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<StopHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(220)
        hits = runCatching { Api.searchStops(q.trim()).results }.getOrDefault(emptyList())
        searching = false
    }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.back, tint = t.text) }
            Text(s.choosePlace(slotName), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = t.text,
                modifier = Modifier.semantics { heading() })
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MaxWidth).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg)) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(Radius.md)).background(t.surface).padding(horizontal = Space.md),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = t.text, modifier = Modifier.size(20.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text(s.searchStop, color = t.muted, style = MaterialTheme.typography.bodyLarge)
                        BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = t.text, fontSize = 16.sp),
                            cursorBrush = SolidColor(t.text),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = s.choosePlace(slotName) })
                    }
                    if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.text)
                }
                if (q.trim().length >= 2 && !searching && hits.isEmpty()) Text(s.noMatch, color = t.muted, modifier = Modifier.padding(vertical = Space.md))
                hits.forEach { h ->
                    ListRow(Icons.Outlined.Place, h.name, subtitle = s.routesServed(h.routes_served)) {
                        scope.launch { places.set(slot, SavedPlace.of(PickedPlace.stop(h))); onDone() }
                    }
                }
            }
        }
    }
}
