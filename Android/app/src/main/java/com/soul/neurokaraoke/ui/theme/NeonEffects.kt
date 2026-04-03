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
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Subtle ambient glow — drawn as layered translucent rounded rects behind the content.
 * Used sparingly: player controls, active nav item, key CTAs.
 */
fun Modifier.ambientGlow(
    color: Color,
    radius: Dp = 12.dp,
    cornerRadius: Dp = 16.dp,
    alpha: Float = 0.10f
): Modifier = this.drawBehind {
    val radiusPx = radius.toPx()
    val cornerPx = cornerRadius.toPx()
    // Outer diffuse layer
    drawRoundRect(
        color = color.copy(alpha = alpha * 0.3f),
        topLeft = Offset(-radiusPx, -radiusPx),
        size = Size(size.width + radiusPx * 2, size.height + radiusPx * 2),
        cornerRadius = CornerRadius(cornerPx + radiusPx * 0.5f)
    )
    // Inner concentrated layer
    drawRoundRect(
        color = color.copy(alpha = alpha * 0.6f),
        topLeft = Offset(-radiusPx * 0.3f, -radiusPx * 0.3f),
        size = Size(size.width + radiusPx * 0.6f, size.height + radiusPx * 0.6f),
        cornerRadius = CornerRadius(cornerPx + radiusPx * 0.15f)
    )
}

/**
 * Hairline border with optional gradient — the primary card treatment.
 */
fun Modifier.subtleBorder(
    color: Color,
    alpha: Float = 0.08f,
    cornerRadius: Dp = 16.dp,
    width: Dp = 1.dp
): Modifier = this.border(
    width = width,
    color = color.copy(alpha = alpha),
    shape = RoundedCornerShape(cornerRadius)
)

/**
 * Gradient border for accent cards (player, featured items).
 */
fun Modifier.gradientBorder(
    colors: List<Color>,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 16.dp
): Modifier = this.border(
    width = borderWidth,
    brush = Brush.horizontalGradient(colors.map { it.copy(alpha = 0.3f) }),
    shape = RoundedCornerShape(cornerRadius)
)

/**
 * Pulsing glow for active/playing elements — play button, now-playing indicator.
 */
@Composable
fun Modifier.pulsingGlow(
    color: Color,
    baseRadius: Dp = 16.dp,
    cornerRadius: Dp = 50.dp,
    minAlpha: Float = 0.05f,
    maxAlpha: Float = 0.15f,
    durationMs: Int = 2000
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    return this.ambientGlow(color = color, radius = baseRadius, cornerRadius = cornerRadius, alpha = alpha)
}

/**
 * Glass card — the standard elevated container.
 * Semi-transparent surface + hairline border + optional accent glow.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    borderAlpha: Float = 0.08f,
    backgroundAlpha: Float = 0.6f,
    cornerRadius: Dp = 16.dp,
    showGlow: Boolean = false,
    glowRadius: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .then(
                if (showGlow) Modifier.ambientGlow(
                    color = glowColor,
                    radius = glowRadius,
                    cornerRadius = cornerRadius,
                    alpha = 0.08f
                ) else Modifier
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha))
            .subtleBorder(
                color = Color.White,
                alpha = borderAlpha,
                cornerRadius = cornerRadius
            ),
        content = content
    )
}

// Backward-compat overload for callers that pass borderColors
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    borderAlpha: Float = 0.08f,
    backgroundAlpha: Float = 0.6f,
    cornerRadius: Dp = 16.dp,
    showGlow: Boolean = false,
    glowRadius: Dp = 12.dp,
    borderColors: List<Color>,  // Legacy parameter — ignored, uses borderAlpha instead
    content: @Composable BoxScope.() -> Unit
) = GlassCard(modifier, glowColor, borderAlpha, backgroundAlpha, cornerRadius, showGlow, glowRadius, content)

/**
 * Gradient text — for section headers, titles, featured text.
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
 * Gradient progress bar — for player progress, download progress.
 */
@Composable
fun GradientProgressBar(
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
                    .background(Brush.horizontalGradient(gradientColors))
            )
        }
    }
}

/**
 * Cinematic background with subtle ambient color blobs.
 */
@Composable
fun CinematicBackground(
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
                // Top-left primary blob
                drawCircle(
                    color = primaryColor.copy(alpha = 0.018f),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.15f, size.height * 0.1f)
                )
                // Bottom-right secondary blob
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.012f),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.85f, size.height * 0.6f)
                )
            },
        content = content
    )
}

/**
 * Gradient fade divider (transparent → accent → transparent).
 */
@Composable
fun AccentDivider(
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
                        color.copy(alpha = 0.2f),
                        color.copy(alpha = 0.3f),
                        color.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                )
            )
    )
}

