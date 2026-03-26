package com.soul.neurokaraoke.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Simulated glow via drawBehind with 3 layered blurred rounded rects.
 * Works on all API levels (no Modifier.blur needed).
 */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 8.dp,
    cornerRadius: Dp = 12.dp,
    alpha: Float = 0.15f
): Modifier = this.drawBehind {
    val radiusPx = radius.toPx()
    val cornerPx = cornerRadius.toPx()
    // Outer glow layer
    drawRoundRect(
        color = color.copy(alpha = alpha * 0.25f),
        topLeft = Offset(-radiusPx, -radiusPx),
        size = Size(size.width + radiusPx * 2, size.height + radiusPx * 2),
        cornerRadius = CornerRadius(cornerPx + radiusPx * 0.5f)
    )
    // Inner glow layer
    drawRoundRect(
        color = color.copy(alpha = alpha * 0.5f),
        topLeft = Offset(-radiusPx * 0.3f, -radiusPx * 0.3f),
        size = Size(size.width + radiusPx * 0.6f, size.height + radiusPx * 0.6f),
        cornerRadius = CornerRadius(cornerPx + radiusPx * 0.15f)
    )
}

/**
 * Gradient border + outer glow layer.
 */
fun Modifier.neonBorder(
    colors: List<Color>,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 12.dp,
    glowRadius: Dp = 4.dp
): Modifier = this
    .drawBehind {
        val glowPx = glowRadius.toPx()
        val cornerPx = cornerRadius.toPx()
        val glowColor = colors.first()
        // Subtle outer glow
        drawRoundRect(
            color = glowColor.copy(alpha = 0.08f),
            topLeft = Offset(-glowPx, -glowPx),
            size = Size(size.width + glowPx * 2, size.height + glowPx * 2),
            cornerRadius = CornerRadius(cornerPx + glowPx * 0.5f)
        )
    }
    .border(
        width = borderWidth,
        brush = Brush.horizontalGradient(colors),
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Pulsing glow using infiniteTransition — for play button, active elements.
 */
@Composable
fun Modifier.animatedNeonGlow(
    color: Color,
    baseRadius: Dp = 16.dp,
    cornerRadius: Dp = 50.dp,
    minAlpha: Float = 0.08f,
    maxAlpha: Float = 0.2f,
    durationMs: Int = 1500
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "neonPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    return this.neonGlow(
        color = color,
        radius = baseRadius,
        cornerRadius = cornerRadius,
        alpha = pulseAlpha
    )
}

/**
 * Semi-transparent bg + neon gradient border + optional glow.
 * Replaces Card everywhere for glassmorphic look.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    borderColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ),
    backgroundAlpha: Float = 0.5f,
    cornerRadius: Dp = 12.dp,
    showGlow: Boolean = false,
    glowRadius: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .then(
                if (showGlow) Modifier.neonGlow(
                    color = glowColor,
                    radius = glowRadius,
                    cornerRadius = cornerRadius,
                    alpha = 0.1f
                ) else Modifier
            )
            .neonBorder(
                colors = borderColors,
                borderWidth = 1.dp,
                cornerRadius = cornerRadius,
                glowRadius = if (showGlow) glowRadius else 4.dp
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha)),
        content = content
    )
}

/**
 * Text with horizontal gradient brush — for section headers and titles.
 */
@Composable
fun GradientText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = NeonTheme.colors.gradientColors,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null
) {
    Text(
        text = text,
        style = style.copy(
            brush = Brush.horizontalGradient(gradientColors),
            fontWeight = fontWeight ?: style.fontWeight
        ),
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * Gradient-filled progress bar with glow on filled portion.
 */
@Composable
fun NeonProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = NeonTheme.colors.gradientColors,
    height: Dp = 4.dp,
    cornerRadius: Dp = 2.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(trackColor)
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(height)
                    .clip(RoundedCornerShape(cornerRadius))
                    .drawBehind {
                        // Subtle glow on filled portion
                        val glowColor = gradientColors.first()
                        drawRoundRect(
                            color = glowColor.copy(alpha = 0.15f),
                            topLeft = Offset(0f, -size.height * 0.3f),
                            size = Size(size.width, size.height * 1.6f),
                            cornerRadius = CornerRadius(cornerRadius.toPx())
                        )
                    }
                    .background(Brush.horizontalGradient(gradientColors))
            )
        }
    }
}

/**
 * Background with subtle radial gradient mesh (drawBehind circles at low alpha).
 */
@Composable
fun CyberpunkBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                // Subtle radial gradient circles at low alpha for ambient glow
                drawCircle(
                    color = primaryColor.copy(alpha = 0.02f),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.2f, size.height * 0.15f)
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.015f),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.8f, size.height * 0.5f)
                )
            },
        content = content
    )
}

/**
 * Gradient fade line replacing HorizontalDivider (transparent -> primary -> transparent).
 */
@Composable
fun NeonDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = 0.3f),
                        color.copy(alpha = 0.4f),
                        color.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )
    )
}
