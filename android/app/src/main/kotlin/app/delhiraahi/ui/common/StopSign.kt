package app.delhiraahi.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Bus stop symbol for lists: the same signpost as the map marker (sign on a pole, shelter with a bench),
 * so a stop always looks like a stop and never like a bus. Outline style to sit with Material icons.
 */
val StopSignIcon: ImageVector by lazy {
    ImageVector.Builder("StopSign", 24.dp, 24.dp, 24f, 24f).apply {
        val ink = SolidColor(Color.Black)
        // sign
        path(stroke = ink, strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(7f, 2f); lineTo(17f, 2f)
            arcTo(3f, 3f, 0f, false, true, 20f, 5f); lineTo(20f, 13f)
            arcTo(3f, 3f, 0f, false, true, 17f, 16f); lineTo(7f, 16f)
            arcTo(3f, 3f, 0f, false, true, 4f, 13f); lineTo(4f, 5f)
            arcTo(3f, 3f, 0f, false, true, 7f, 2f); close()
        }
        // shelter: roof, two posts, bench
        path(stroke = ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
            moveTo(7.5f, 6f); lineTo(16.5f, 6f)
            moveTo(9f, 6f); lineTo(9f, 12.5f)
            moveTo(15f, 6f); lineTo(15f, 12.5f)
            moveTo(9f, 10f); lineTo(15f, 10f)
        }
        // pole
        path(stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 16f); lineTo(12f, 22f)
            moveTo(9.5f, 22f); lineTo(14.5f, 22f)
        }
    }.build()
}
