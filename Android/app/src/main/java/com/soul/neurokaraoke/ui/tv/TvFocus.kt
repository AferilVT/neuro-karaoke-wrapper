package com.soul.neurokaraoke.ui.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Grows the composable when D-pad focus lands on it. */
fun Modifier.tvFocusScale(focused: Boolean, scale: Float = 1.1f): Modifier = composed {
    val s = animateFloatAsState(if (focused) scale else 1f, label = "tvFocusScale").value
    this.zIndex(if (focused) 1f else 0f).scale(s)
}

/**
 * Premium cover-art focus treatment (tvOS / Netflix style): the poster is always
 * clipped to rounded corners; on focus it lifts with a soft drop shadow and gains
 * a crisp light ring so the selected item reads clearly on a 10-foot display.
 * Animated so focus transitions feel deliberate rather than snapping.
 */
fun Modifier.tvCoverFocus(focused: Boolean, cornerRadius: Dp = 14.dp): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    val elevation = animateDpAsState(if (focused) 20.dp else 0.dp, label = "tvCoverElevation").value
    val ringWidth = animateDpAsState(if (focused) 3.dp else 0.dp, label = "tvCoverRing").value
    this
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)
        .border(width = ringWidth, color = MaterialTheme.colorScheme.primary, shape = shape)
}
