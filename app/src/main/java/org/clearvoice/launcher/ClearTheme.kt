package org.clearvoice.launcher

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.min

data class ClearColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val error: Color,
    val success: Color,
    val divider: Color,
    val isDark: Boolean
)

// ── Base Themes ───────────────────────────────────────────────────────────────

val DarkColors = ClearColors(
    background = Color(0xFF2C2416),
    surface = Color(0xFF3A3020),
    surfaceVariant = Color(0xFF4A3D28),
    primary = Color(0xFFC4674A),
    primaryContainer = Color(0xFFC4674A).copy(alpha = 0.2f),
    onPrimary = Color(0xFFF7F4EF),
    text = Color(0xFFF7F4EF),
    textMuted = Color(0xFFBDB0A0),
    accent = Color(0xFF7A9E7E),
    error = Color(0xFFE57373),
    success = Color(0xFF7A9E7E),
    divider = Color(0xFFF7F4EF).copy(alpha = 0.1f),
    isDark = true
)

val LightColors = ClearColors(
    background = Color(0xFFF7F4EF),
    surface = Color(0xFFEDE8DF),
    surfaceVariant = Color(0xFFE5DED4),
    primary = Color(0xFFC4674A),
    primaryContainer = Color(0xFFC4674A).copy(alpha = 0.15f),
    onPrimary = Color(0xFFF7F4EF),
    text = Color(0xFF2C2C2C),
    textMuted = Color(0xFF6B5F52),
    accent = Color(0xFF7A9E7E),
    error = Color(0xFFB71C1C),
    success = Color(0xFF7A9E7E),
    divider = Color(0xFF2C2C2C).copy(alpha = 0.1f),
    isDark = false
)


// ── Wallpaper Options ─────────────────────────────────────────────────────────

data class WallpaperOption(val colorLong: Long, val name: String, val isDark: Boolean)

val DarkWallpaperOptions = listOf(
    WallpaperOption(0xFF2C2416L, "Espresso", true),
    WallpaperOption(0xFF1A1A0EL, "Midnight", true),
    WallpaperOption(0xFF1F2E1FL, "Forest Night", true),
    WallpaperOption(0xFF2A1F1AL, "Walnut", true),
    WallpaperOption(0xFF1C1C1CL, "Charcoal", true),
    WallpaperOption(0xFF2A2318L, "Dark Sand", true),
    WallpaperOption(0xFF1E2820L, "Dark Sage", true),
    WallpaperOption(0xFF2E1F1AL, "Mahogany", true),
    WallpaperOption(0xFF1A1F2AL, "Slate Night", true),
    WallpaperOption(0xFF251A14L, "Clay Dark", true),
)

val LightWallpaperOptions = listOf(
    WallpaperOption(0xFFF7F4EFL, "Cream", false),
    WallpaperOption(0xFFEDE8DFL, "Linen", false),
    WallpaperOption(0xFFF0EBE3L, "Sand", false),
    WallpaperOption(0xFFE8F0E8L, "Mint", false),
    WallpaperOption(0xFFF5EDE8L, "Blush", false),
    WallpaperOption(0xFFEAE4DCL, "Parchment", false),
    WallpaperOption(0xFFE8EDE8L, "Sage", false),
    WallpaperOption(0xFFF2EDE8L, "Warm White", false),
    WallpaperOption(0xFFE8E0D8L, "Antique", false),
    WallpaperOption(0xFFEDE8E0L, "Ivory", false),
)
// ── Color Helpers ─────────────────────────────────────────────────────────────

private fun Color.lighten(amount: Float): Color {
    val r = min(1f, red + amount)
    val g = min(1f, green + amount)
    val b = min(1f, blue + amount)
    return Color(r, g, b, alpha)
}

private fun Color.darken(amount: Float): Color {
    val r = max(0f, red - amount)
    val g = max(0f, green - amount)
    val b = max(0f, blue - amount)
    return Color(r, g, b, alpha)
}

private fun Color.muted(factor: Float = 0.4f): Color {
    // Blend toward grey to desaturate — keeps it calm
    val grey = (red * 0.299f + green * 0.587f + blue * 0.114f)
    val r = red + (grey - red) * factor
    val g = green + (grey - green) * factor
    val b = blue + (grey - blue) * factor
    return Color(r, g, b, alpha)
}

private fun androidx.compose.ui.graphics.Color.toAndroidColor(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

// ── Palette Extraction ────────────────────────────────────────────────────────

data class ExtractedPalette(
    val mutedColor: Color,
    val vibrantColor: Color,
    val isDark: Boolean
)

fun extractPaletteFromBitmap(bitmap: Bitmap): ExtractedPalette {
    val palette = Palette.from(bitmap).generate()

    // Prefer muted swatch for calm base — fallback chain
    val mutedSwatch = palette.mutedSwatch
        ?: palette.darkMutedSwatch
        ?: palette.lightMutedSwatch
        ?: palette.dominantSwatch

    // Prefer vibrant swatch for accent — fallback chain
    val vibrantSwatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.dominantSwatch

    val mutedColor = mutedSwatch?.let {
        Color(it.rgb)
    } ?: Color(0xFF2C3E50)

    val vibrantColor = vibrantSwatch?.let {
        Color(it.rgb)
    } ?: Color(0xFFC9A96E)

    val isDark = mutedColor.luminance() < 0.4f

    return ExtractedPalette(
        mutedColor = mutedColor.muted(0.3f), // extra calm
        vibrantColor = vibrantColor.muted(0.2f), // slightly calm accent
        isDark = isDark
    )
}

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val options = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = 4 // Sample down for performance
        }
        android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
    } catch (e: Exception) {
        null
    }
}

// ── Theme Derivation ──────────────────────────────────────────────────────────

fun deriveThemeFromWallpaper(wallpaperColorLong: Long, isWallpaperDark: Boolean): ClearColors {
    val base = Color(wallpaperColorLong)
    return if (isWallpaperDark) {
        ClearColors(
            background = base,
            surface = base.lighten(0.06f),
            surfaceVariant = base.lighten(0.12f),
            primary = Color(0xFFC9A96E),
            primaryContainer = Color(0xFFC9A96E).copy(alpha = 0.2f),
            onPrimary = base,
            text = Color(0xFFF5F0E8),
            textMuted = Color(0xFFF5F0E8).copy(alpha = 0.55f),
            accent = Color(0xFF7D9B76),
            error = Color(0xFFE57373),
            success = Color(0xFF7D9B76),
            divider = Color.White.copy(alpha = 0.1f),
            isDark = true
        )
    } else {
        ClearColors(
            background = base,
            surface = base.darken(0.04f),
            surfaceVariant = base.darken(0.08f),
            primary = Color(0xFFC9A96E),
            primaryContainer = Color(0xFFC9A96E).copy(alpha = 0.2f),
            onPrimary = Color(0xFFF5F0E8),
            text = Color(0xFF2C3E50),
            textMuted = Color(0xFF2C3E50).copy(alpha = 0.55f),
            accent = Color(0xFF5A7A5A),
            error = Color(0xFFB71C1C),
            success = Color(0xFF2E7D32),
            divider = Color(0xFF2C3E50).copy(alpha = 0.12f),
            isDark = false
        )
    }
}

fun deriveThemeFromPalette(
    extracted: ExtractedPalette,
    baseIsDark: Boolean
): ClearColors {
    val muted = extracted.mutedColor
    val vibrant = extracted.vibrantColor

    return if (baseIsDark || extracted.isDark) {
        // Dark base — muted color tints the surfaces
        val bg = if (extracted.isDark) muted.darken(0.15f) else Color(0xFF2C3E50)
        ClearColors(
            background = bg,
            surface = bg.lighten(0.06f),
            surfaceVariant = bg.lighten(0.12f),
            primary = vibrant.lighten(0.1f),
            primaryContainer = vibrant.copy(alpha = 0.2f),
            onPrimary = if (vibrant.luminance() > 0.4f) Color(0xFF1A1A1A)
            else Color(0xFFF5F0E8),
            text = Color(0xFFF5F0E8),
            textMuted = Color(0xFFF5F0E8).copy(alpha = 0.6f),
            accent = muted.lighten(0.2f),
            error = Color(0xFFE57373),
            success = Color(0xFF7D9B76),
            divider = Color.White.copy(alpha = 0.1f),
            isDark = true
        )
    } else {
        // Light base — muted color tints surfaces softly
        val bg = if (!extracted.isDark) muted.lighten(0.25f) else Color(0xFFF5F0E8)
        ClearColors(
            background = bg,
            surface = bg.darken(0.04f),
            surfaceVariant = bg.darken(0.08f),
            primary = vibrant.darken(0.1f),
            primaryContainer = vibrant.copy(alpha = 0.15f),
            onPrimary = Color(0xFFF5F0E8),
            text = Color(0xFF2C3E50),
            textMuted = Color(0xFF2C3E50).copy(alpha = 0.6f),
            accent = muted.darken(0.1f),
            error = Color(0xFFB71C1C),
            success = Color(0xFF2E7D32),
            divider = Color(0xFF2C3E50).copy(alpha = 0.12f),
            isDark = false
        )
    }
}

fun getHomeTextColorForBackground(
    wallpaperType: String,
    wallpaperColor: Long,
    themeColors: ClearColors
): Pair<Color, Color> {
    return when (wallpaperType) {
        "solid" -> {
            val bgColor = Color(wallpaperColor)
            if (bgColor.luminance() > 0.4f)
                Pair(Color(0xFF2C3E50), Color(0xFF6B5F52))
            else
                Pair(Color(0xFFF5F0E8), Color(0xFF8C8070))
        }
        "gallery" -> Pair(themeColors.text, themeColors.textMuted)
        else -> Pair(themeColors.text, themeColors.textMuted)
    }
}

// ── Theme State ───────────────────────────────────────────────────────────────

object ClearTheme {
    var colors by mutableStateOf(DarkColors)
        private set

    var extractedPalette by mutableStateOf<ExtractedPalette?>(null)
        private set

    fun applyTheme(isDark: Boolean) {
        extractedPalette = null
        colors = if (isDark) DarkColors else LightColors
    }

    fun applyWallpaperTheme(wallpaperColorLong: Long, isDark: Boolean) {
        extractedPalette = null
        colors = deriveThemeFromWallpaper(wallpaperColorLong, isDark)
    }

    fun applyGalleryTheme(extracted: ExtractedPalette, baseIsDark: Boolean) {
        extractedPalette = extracted
        colors = deriveThemeFromPalette(extracted, baseIsDark)
    }

    fun loadFromStorage(context: Context) {
        val wallpaperType = PinStorage.getWallpaperType(context)
        val baseIsDark = PinStorage.getTheme(context) == "dark"
        when (wallpaperType) {
            "solid" -> {
                val colorLong = PinStorage.getWallpaperColor(context)
                val isDark = DarkWallpaperOptions.any { it.colorLong == colorLong }
                val isLight = LightWallpaperOptions.any { it.colorLong == colorLong }
                if (!isDark && !isLight) {
                    // Unknown color — fall back to base theme
                    applyTheme(baseIsDark)
                } else {
                    applyWallpaperTheme(colorLong, isDark)
                }
            }
            "gallery" -> {
                val uriString = PinStorage.getWallpaperUri(context)
                if (uriString.isNotEmpty()) {
                    val bitmap = loadBitmapFromUri(context, Uri.parse(uriString))
                    if (bitmap != null) {
                        val extracted = extractPaletteFromBitmap(bitmap)
                        applyGalleryTheme(extracted, baseIsDark)
                    } else {
                        applyTheme(baseIsDark)
                    }
                } else {
                    applyTheme(baseIsDark)
                }
            }
            else -> applyTheme(baseIsDark)
        }
    }
}

val LocalClearColors = compositionLocalOf { DarkColors }