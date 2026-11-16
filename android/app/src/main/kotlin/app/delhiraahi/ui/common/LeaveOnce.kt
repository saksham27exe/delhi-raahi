package app.delhiraahi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState

/**
 * Leave this screen (pop it) once, as an effect: never call navigation while the screen is being drawn,
 * where it can run twice during the exit animation and pop the screen underneath as well.
 */
@Composable
fun LeaveOnce(leave: () -> Unit) {
    val go = rememberUpdatedState(leave)
    LaunchedEffect(Unit) { go.value() }
}
