package com.soul.neurokaraoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.soul.neurokaraoke.ui.theme.GlassCard
import com.soul.neurokaraoke.ui.theme.NeonTheme

@Composable
fun CyanBorderCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val gradientColors = NeonTheme.colors.gradientColors
    val glowColor = NeonTheme.colors.glowColor

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 12.dp
    ) {
        Row {
            // Neon gradient left border with glow
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .drawBehind {
                        // Glow effect on the strip
                        drawRoundRect(
                            color = glowColor.copy(alpha = 0.3f),
                            topLeft = Offset(-4f, 0f),
                            size = Size(size.width + 8f, size.height),
                            cornerRadius = CornerRadius(4f)
                        )
                    }
                    .background(Brush.verticalGradient(gradientColors))
            )
            // Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}
