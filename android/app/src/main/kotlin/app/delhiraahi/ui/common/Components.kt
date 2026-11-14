package app.delhiraahi.ui.common

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import app.delhiraahi.i18n.LocalStrings
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.delhiraahi.ui.theme.LocalExtra
import app.delhiraahi.ui.theme.MaxWidth
import app.delhiraahi.ui.theme.Radius
import app.delhiraahi.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Landscape tablets: the sheet becomes a fixed side panel and the map sits to its right. */
val SidePanelWidth = 400.dp
/** Gap left above the side panel for the floating buttons (back, language, locate). */
private val SidePanelTop = 68.dp

@Composable
fun isWide(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp > c.screenHeightDp && c.screenWidthDp >= 720
}

/** List row: icon in a grey circle, title, subtitle, optional chevron. Min height 56 dp. */
@Composable
fun ListRow(
    icon: ImageVector?, title: String, subtitle: String? = null, chevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
    val t = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (icon != null) Box(Modifier.size(40.dp).clip(CircleShape).background(t.surface), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = t.text, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = t.muted, modifier = Modifier.size(20.dp))
    }
}

/** Pill chip: grey by default, black (white in dark) when selected. */
@Composable
fun Chip(label: String, icon: ImageVector? = null, selected: Boolean = false, onClick: () -> Unit) {
    val t = LocalExtra.current
    val fg = if (selected) t.bg else t.text
    Row(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(if (selected) t.text else t.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp)
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** iOS-style segmented control: grey track, selected item is a raised card. Announced as a radio group. */
@Composable
fun <K> Segmented(options: List<Pair<K, String>>, value: K, onChange: (K) -> Unit, label: String) {
    val t = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(t.surface).padding(4.dp)
            .selectableGroup().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (k, text) ->
            val sel = k == value
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp)
                    .then(if (sel) Modifier.shadow(2.dp, RoundedCornerShape(9.dp)) else Modifier)
                    .clip(RoundedCornerShape(9.dp)).background(if (sel) t.card else t.surface)
                    .selectable(selected = sel, role = Role.RadioButton, onClick = { onChange(k) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, color = t.text, style = MaterialTheme.typography.bodyLarge, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/** Round floating button over the map (46 dp, card colour, soft shadow). */
@Composable
fun FloatingIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = LocalExtra.current
    Surface(onClick = onClick, modifier = modifier.size(46.dp).semantics { contentDescription = label },
        shape = CircleShape, color = t.card, shadowElevation = 4.dp) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = t.text, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
fun Divider() = HorizontalDivider(Modifier.padding(vertical = Space.xs), thickness = 0.5.dp, color = LocalExtra.current.border)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = LocalExtra.current.text, modifier = modifier.padding(top = Space.lg, bottom = Space.sm))
}

/**
 * Draggable bottom sheet over a map, like the Expo app: two snap points (`peek`, `full`, as
 * fractions of the available height), drag the handle/header, the body scrolls normally.
 * Centred and capped at [MaxWidth] on tablets.
 */
@Composable
fun MapSheet(
    peek: Float = 0.46f, full: Float = 0.88f,
    header: @Composable ColumnScope.() -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalExtra.current
    val s = LocalStrings.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    if (isWide()) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(start = Space.lg, top = SidePanelTop, bottom = Space.lg)) {
            Surface(Modifier.width(SidePanelWidth).fillMaxHeight(), shape = RoundedCornerShape(Radius.xl), color = t.card, shadowElevation = 16.dp) {
                Column {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg).padding(top = Space.lg, bottom = Space.sm), content = header)
                    Column(Modifier.weight(1f).fillMaxWidth(), content = content)
                    if (footer != null) footer()
                }
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        val hFull = h * full
        val closed = hFull - h * peek
        var y by remember { mutableFloatStateOf(closed) }
        LaunchedEffect(closed) { y = if (y <= 1f) 0f else closed }
        val drag = rememberDraggableState { d -> y = (y + d).coerceIn(0f, closed) }

        Surface(
            Modifier.align(Alignment.BottomCenter).widthIn(max = MaxWidth).fillMaxWidth()
                .height(with(density) { hFull.toDp() })
                .offset { IntOffset(0, y.roundToInt()) },
            shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
            color = t.card, shadowElevation = 16.dp,
        ) {
            Column {
                Column(
                    Modifier.fillMaxWidth()
                        .draggable(drag, Orientation.Vertical, onDragStopped = { v ->
                            val target = if (v > 1200f || (v > -1200f && y > closed / 2)) closed else 0f
                            scope.launch { animate(y, target, v, spring(dampingRatio = 0.9f, stiffness = 500f)) { value, _ -> y = value } }
                        })
                        .padding(horizontal = Space.lg).padding(bottom = Space.sm)
                        // TalkBack users cannot drag: offer expand/collapse as actions.
                        .semantics {
                            customActions = listOf(
                                if (y > 1f) CustomAccessibilityAction(s.expand) { scope.launch { animate(y, 0f) { v, _ -> y = v } }; true }
                                else CustomAccessibilityAction(s.collapse) { scope.launch { animate(y, closed) { v, _ -> y = v } }; true },
                            )
                        },
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = Space.sm).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(t.surface2))
                    header()
                }
                Column(Modifier.weight(1f).fillMaxWidth(), content = content)
                // Leave room so the last item can scroll above the pinned footer.
                if (footer != null) Box(Modifier.height(96.dp))
            }
        }
        if (footer != null) Surface(
            Modifier.align(Alignment.BottomCenter).widthIn(max = MaxWidth).fillMaxWidth(),
            color = t.card, shadowElevation = 8.dp,
        ) { footer() }
    }
}
