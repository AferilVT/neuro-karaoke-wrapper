package com.soul.neurokaraoke.ui.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Grows the composable when D-pad focus lands on it.
 * Reads the animated scale inside [graphicsLayer] so focus transitions never recompose.
 */
fun Modifier.tvFocusScale(focused: Boolean, scale: Float = 1.1f): Modifier = composed {
    val scaleState = animateFloatAsState(if (focused) scale else 1f, label = "tvFocusScale")
    this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            val s = scaleState.value
            scaleX = s
            scaleY = s
        }
}

/**
 * Premium cover-art focus treatment (tvOS / Netflix style): the poster is always
 * clipped to rounded corners; on focus it lifts with a soft drop shadow and gains
 * a crisp light ring so the selected item reads clearly on a 10-foot display.
 * Defers animated elevation & ring width to graphicsLayer/draw phases (zero recompositions).
 */
fun Modifier.tvCoverFocus(focused: Boolean, cornerRadius: Dp = 14.dp): Modifier = composed {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val ringColor = MaterialTheme.colorScheme.primary
    val elevationState = animateDpAsState(if (focused) 20.dp else 0.dp, label = "tvCoverElevation")
    val ringWidthState = animateDpAsState(if (focused) 3.dp else 0.dp, label = "tvCoverRing")
    this
        .graphicsLayer {
            shadowElevation = elevationState.value.toPx()
            this.shape = shape
            clip = true
        }
        .drawWithContent {
            drawContent()
            val strokePx = ringWidthState.value.toPx()
            if (strokePx > 0.5f) {
                val half = strokePx / 2f
                val cornerPx = (cornerRadius.toPx() - half).coerceAtLeast(0f)
                drawRoundRect(
                    color = ringColor,
                    topLeft = Offset(half, half),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = strokePx)
                )
            }
        }
}
