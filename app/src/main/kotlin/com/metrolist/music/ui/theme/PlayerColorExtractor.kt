/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Nocky artwork color extraction.
 *
 * This intentionally mirrors the desktop Nocky palette strategy more closely:
 * - sample artwork at 64x64;
 * - quantize pixels into 4096 RGB buckets;
 * - ignore nearly black/white colors;
 * - give saturated colors extra presence;
 * - choose one seed;
 * - generate controlled Material-like roles from that seed.
 */
object PlayerColorExtractor {
    private data class Bucket(
        var red: Long = 0,
        var green: Long = 0,
        var blue: Long = 0,
        var count: Long = 0,
    )

    private data class Rgb(
        val red: Int,
        val green: Int,
        val blue: Int,
    )

    suspend fun extractGradientColors(
        bitmap: Bitmap,
        fallbackColor: Int,
    ): List<Color> =
        withContext(Dispatchers.Default) {
            val seed = dominantSeed(bitmap) ?: intToRgb(fallbackColor)
            paletteRolesFromSeed(seed)
        }

    /**
     * Compatibility fallback for old call sites still using Android Palette.
     * Prefer the Bitmap overload above for desktop-like behavior.
     */
    suspend fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int,
    ): List<Color> =
        withContext(Dispatchers.Default) {
            val seed = seedFromPalette(palette) ?: intToRgb(fallbackColor)
            paletteRolesFromSeed(seed)
        }

    private fun dominantSeed(bitmap: Bitmap): Rgb? {
        val sampled =
            if (bitmap.width == 64 && bitmap.height == 64) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, 64, 64, true)
            }

        val width = sampled.width
        val height = sampled.height
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        sampled.getPixels(pixels, 0, width, 0, 0, width, height)

        val buckets = Array(4096) { Bucket() }

        for (argb in pixels) {
            val alpha = android.graphics.Color.alpha(argb)
            if (alpha < 96) continue

            val red = android.graphics.Color.red(argb)
            val green = android.graphics.Color.green(argb)
            val blue = android.graphics.Color.blue(argb)

            val (_, saturation, lightness) = rgbToHsl(Rgb(red, green, blue))
            if (lightness !in 0.025f..0.975f) continue

            val index =
                ((red shr 4) shl 8) or
                    ((green shr 4) shl 4) or
                    (blue shr 4)

            val bucket = buckets[index]
            bucket.red += red.toLong()
            bucket.green += green.toLong()
            bucket.blue += blue.toLong()
            bucket.count += 1

            if (saturation > 0.45f) {
                bucket.red += red.toLong()
                bucket.green += green.toLong()
                bucket.blue += blue.toLong()
                bucket.count += 1
            }
        }

        return buckets
            .asSequence()
            .filter { it.count > 0 }
            .map { bucket ->
                val rgb =
                    Rgb(
                        red = (bucket.red / bucket.count).toInt().coerceIn(0, 255),
                        green = (bucket.green / bucket.count).toInt().coerceIn(0, 255),
                        blue = (bucket.blue / bucket.count).toInt().coerceIn(0, 255),
                    )

                val (_, saturation, lightness) = rgbToHsl(rgb)
                val population = sqrt(bucket.count.toDouble())
                val chroma = 0.42 + saturation * 2.2
                val balance = (1.0 - abs(lightness - 0.52f) * 1.15).coerceIn(0.32, 1.0)

                population * chroma * balance to rgb
            }
            .maxByOrNull { it.first }
            ?.second
    }

    private fun seedFromPalette(palette: Palette): Rgb? =
        palette.swatches
            .asSequence()
            .filter { it.population > 0 }
            .map { swatch ->
                val rgb = intToRgb(swatch.rgb)
                val (_, saturation, lightness) = rgbToHsl(rgb)
                val population = sqrt(swatch.population.toDouble())
                val chroma = 0.42 + saturation * 2.2
                val balance = (1.0 - abs(lightness - 0.52f) * 1.15).coerceIn(0.32, 1.0)

                population * chroma * balance to rgb
            }
            .maxByOrNull { it.first }
            ?.second

    /**
     * Return order is adapted to current Android call sites:
     * [0] primaryContainer -> metadata card surface
     * [1] primary          -> accents/progress/buttons when used
     * [2] surface          -> dark background/gradient fallback
     */
    private fun paletteRolesFromSeed(seed: Rgb): List<Color> {
        var hue = rgbToHsl(seed).first
        val saturation = rgbToHsl(seed).second

        if (saturation < 0.10f) {
            hue = 232f
        }

        val expressiveSaturation = saturation.coerceIn(0.42f, 0.82f)

        val primary =
            hslToColor(
                hue = hue,
                saturation = expressiveSaturation,
                lightness = 0.78f,
            )

        val primaryContainer =
            hslToColor(
                hue = hue,
                saturation = (expressiveSaturation * 0.78f).coerceIn(0.34f, 0.66f),
                lightness = 0.29f,
            )

        val surfaceSaturation = (expressiveSaturation * 0.12f).coerceIn(0.045f, 0.10f)
        val surface =
            hslToColor(
                hue = hue,
                saturation = surfaceSaturation,
                lightness = 0.085f,
            )

        return listOf(
            primaryContainer,
            primary,
            surface,
        )
    }

    private fun intToRgb(argb: Int): Rgb =
        Rgb(
            red = android.graphics.Color.red(argb),
            green = android.graphics.Color.green(argb),
            blue = android.graphics.Color.blue(argb),
        )

    private fun rgbToHsl(rgb: Rgb): Triple<Float, Float, Float> {
        val red = rgb.red / 255f
        val green = rgb.green / 255f
        val blue = rgb.blue / 255f

        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        val delta = maximum - minimum
        val lightness = (maximum + minimum) / 2f

        if (delta <= Float.MIN_VALUE) {
            return Triple(0f, 0f, lightness)
        }

        val saturation = delta / (1f - abs(2f * lightness - 1f))
        val rawHue =
            when (maximum) {
                red -> 60f * (((green - blue) / delta) % 6f)
                green -> 60f * (((blue - red) / delta) + 2f)
                else -> 60f * (((red - green) / delta) + 4f)
            }

        return Triple(wrapHue(rawHue), saturation.coerceIn(0f, 1f), lightness)
    }

    private fun hslToColor(
        hue: Float,
        saturation: Float,
        lightness: Float,
    ): Color {
        val wrappedHue = wrapHue(hue)
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)

        val chroma = (1f - abs(2f * l - 1f)) * s
        val segment = wrappedHue / 60f
        val secondary = chroma * (1f - abs(segment % 2f - 1f))

        val (redPrime, greenPrime, bluePrime) =
            when (segment.toInt()) {
                0 -> Triple(chroma, secondary, 0f)
                1 -> Triple(secondary, chroma, 0f)
                2 -> Triple(0f, chroma, secondary)
                3 -> Triple(0f, secondary, chroma)
                4 -> Triple(secondary, 0f, chroma)
                else -> Triple(chroma, 0f, secondary)
            }

        val matchValue = l - chroma / 2f

        return Color(
            red = (redPrime + matchValue).coerceIn(0f, 1f),
            green = (greenPrime + matchValue).coerceIn(0f, 1f),
            blue = (bluePrime + matchValue).coerceIn(0f, 1f),
            alpha = 1f,
        )
    }

    private fun wrapHue(hue: Float): Float {
        val wrapped = hue % 360f
        return if (wrapped < 0f) wrapped + 360f else wrapped
    }

    object Config {
        const val MAX_COLOR_COUNT = 32
        const val BITMAP_AREA = 8000
        const val IMAGE_SIZE = 200
    }
}
