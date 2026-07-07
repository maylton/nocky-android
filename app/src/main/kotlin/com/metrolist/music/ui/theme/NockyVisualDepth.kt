/**
 * Nocky Android visual depth tokens.
 *
 * This keeps the Android app on its existing Material 3 structure while moving
 * the shared color roles closer to the Nocky desktop surface language: subtle
 * tonal containers, clearer outlines, restrained accent tinting and visible
 * layer separation for cards, menus, sheets and controls.
 */
package com.metrolist.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

internal fun ColorScheme.withNockyVisualDepth(
    darkTheme: Boolean,
    pureBlack: Boolean,
): ColorScheme {
    val accent = primary
    val baseSurface = if (darkTheme && pureBlack) Color.Black else surface

    val depth = if (darkTheme) {
        SurfaceDepth(
            lowest = if (pureBlack) Color.Black else surfaceContainerLowest.depthTint(accent, 0.018f),
            low = if (pureBlack) Color(0xFF050508).depthTint(accent, 0.030f) else surfaceContainerLow.depthTint(accent, 0.034f),
            normal = if (pureBlack) Color(0xFF0C0C14).depthTint(accent, 0.052f) else surfaceContainer.depthTint(accent, 0.052f),
            high = if (pureBlack) Color(0xFF141422).depthTint(accent, 0.068f) else surfaceContainerHigh.depthTint(accent, 0.068f),
            highest = if (pureBlack) Color(0xFF1C1C2F).depthTint(accent, 0.088f) else surfaceContainerHighest.depthTint(accent, 0.088f),
        )
    } else {
        SurfaceDepth(
            lowest = surfaceContainerLowest.depthTint(accent, 0.010f),
            low = surfaceContainerLow.depthTint(accent, 0.016f),
            normal = surfaceContainer.depthTint(accent, 0.026f),
            high = surfaceContainerHigh.depthTint(accent, 0.036f),
            highest = surfaceContainerHighest.depthTint(accent, 0.046f),
        )
    }

    return copy(
        surface = baseSurface.depthTint(accent, if (darkTheme && !pureBlack) 0.012f else 0.0f),
        background = baseSurface.depthTint(accent, if (darkTheme && !pureBlack) 0.010f else 0.0f),
        surfaceContainerLowest = depth.lowest,
        surfaceContainerLow = depth.low,
        surfaceContainer = depth.normal,
        surfaceContainerHigh = depth.high,
        surfaceContainerHighest = depth.highest,
        surfaceVariant = surfaceVariant.depthTint(accent, if (darkTheme) 0.064f else 0.036f),
        outline = outline.towards(accent, if (darkTheme) 0.24f else 0.16f),
        outlineVariant = outlineVariant.towards(accent, if (darkTheme) 0.30f else 0.20f),
        primaryContainer = primaryContainer.depthTint(accent, if (darkTheme) 0.085f else 0.045f),
        secondaryContainer = secondaryContainer.depthTint(accent, if (darkTheme) 0.065f else 0.036f),
        tertiaryContainer = tertiaryContainer.depthTint(accent, if (darkTheme) 0.050f else 0.030f),
        scrim = Color.Black.copy(alpha = if (darkTheme) 0.76f else 0.42f),
    )
}

private data class SurfaceDepth(
    val lowest: Color,
    val low: Color,
    val normal: Color,
    val high: Color,
    val highest: Color,
)

private fun Color.depthTint(accent: Color, alpha: Float): Color =
    if (alpha <= 0f) this else accent.copy(alpha = alpha).compositeOver(this)

private fun Color.towards(target: Color, fraction: Float): Color =
    lerp(this, target, fraction)
