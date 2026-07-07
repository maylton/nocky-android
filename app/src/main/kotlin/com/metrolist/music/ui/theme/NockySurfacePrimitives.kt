package com.metrolist.music.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
internal data class NockySurfaceSpec(
    val shape: Shape,
    val containerColor: Color,
    val contentColor: Color,
    val border: BorderStroke,
    val tonalElevation: Dp,
    val shadowElevation: Dp,
)

internal object NockySurfaceDefaults {
    val IslandShape: Shape
        @Composable get() = MaterialTheme.shapes.extraLarge

    val CardShape: Shape
        @Composable get() = MaterialTheme.shapes.large

    val CompactShape: Shape
        @Composable get() = MaterialTheme.shapes.medium

    val ControlShape: Shape = RoundedCornerShape(999.dp)

    val ArtworkShape: Shape
        @Composable get() = MaterialTheme.shapes.large

    val Island: NockySurfaceSpec
        @Composable get() = MaterialTheme.colorScheme.surfaceSpec(
            shape = IslandShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            outlineAlpha = 0.34f,
            tonalElevation = 2.dp,
            shadowElevation = 0.dp,
        )

    val Card: NockySurfaceSpec
        @Composable get() = MaterialTheme.colorScheme.surfaceSpec(
            shape = CardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            outlineAlpha = 0.40f,
            tonalElevation = 3.dp,
            shadowElevation = 1.dp,
        )

    val RaisedCard: NockySurfaceSpec
        @Composable get() = MaterialTheme.colorScheme.surfaceSpec(
            shape = CardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            outlineAlpha = 0.46f,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
        )

    val Menu: NockySurfaceSpec
        @Composable get() = MaterialTheme.colorScheme.surfaceSpec(
            shape = CardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            outlineAlpha = 0.42f,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        )

    val Control: NockySurfaceSpec
        @Composable get() = MaterialTheme.colorScheme.surfaceSpec(
            shape = ControlShape,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            outlineAlpha = 0.32f,
            tonalElevation = 2.dp,
            shadowElevation = 0.dp,
        )

    @Composable
    fun appBackground(pureBlack: Boolean): Brush =
        if (pureBlack) {
            Brush.verticalGradient(
                listOf(
                    Color.Black,
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        }

    @Composable
    fun artworkBorder(): BorderStroke =
        BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
        )

    @Composable
    fun accentGlow(): Brush =
        Brush.radialGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                Color.Transparent,
            ),
        )
}

internal fun Modifier.nockySurface(spec: NockySurfaceSpec): Modifier =
    this
        .shadow(
            elevation = spec.shadowElevation,
            shape = spec.shape,
            clip = false,
        )
        .background(
            color = spec.containerColor,
            shape = spec.shape,
        )
        .border(
            border = spec.border,
            shape = spec.shape,
        )

internal fun Modifier.nockyArtworkFrame(
    shape: Shape,
    border: BorderStroke,
    padding: Dp = 0.dp,
): Modifier =
    this
        .border(border, shape)
        .padding(padding)

internal fun Modifier.nockySoftGlow(
    brush: Brush,
    shape: Shape,
): Modifier =
    this.background(brush = brush, shape = shape)

@Composable
private fun ColorScheme.surfaceSpec(
    shape: Shape,
    containerColor: Color,
    outlineAlpha: Float,
    tonalElevation: Dp,
    shadowElevation: Dp,
): NockySurfaceSpec =
    NockySurfaceSpec(
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColorForContainer(containerColor),
        border = BorderStroke(1.dp, outlineVariant.copy(alpha = outlineAlpha)),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    )

@Composable
private fun contentColorForContainer(containerColor: Color): Color =
    CardDefaults.cardColors(containerColor = containerColor).contentColor
