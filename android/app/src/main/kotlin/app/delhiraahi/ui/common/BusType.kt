package app.delhiraahi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.delhiraahi.i18n.LocalStrings
import app.delhiraahi.i18n.Strings
import app.delhiraahi.ui.theme.LocalExtra

/**
 * AC / non-AC is not in the bus timetable (GTFS). The server estimates it per route from the share of
 * AC e-tickets sold on that route: "ac", "non_ac", "mixed" (both kinds run), or "unknown".
 */
fun busTypeName(s: Strings, type: String?): String? = when (type) {
    "ac" -> s.busAc
    "non_ac" -> s.busNonAc
    "mixed" -> s.busMixed
    else -> null
}

private val AC_BLUE = Color(0xFF1F6FB2)
private val AC_BLUE_DARK = Color(0xFF8CC4F2)

/** Small tag after a bus number: ❄ AC, Non-AC, or AC / non-AC. Nothing when unknown. */
@Composable
fun BusTypeTag(type: String?, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val t = LocalExtra.current
    val name = busTypeName(s, type) ?: return
    val ac = type == "ac"
    val fg = if (ac) (if (t.dark) AC_BLUE_DARK else AC_BLUE) else t.muted
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .background(fg.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (ac) Icon(Icons.Outlined.AcUnit, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
