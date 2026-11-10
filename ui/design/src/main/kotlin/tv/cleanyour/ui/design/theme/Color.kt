package tv.cleanyour.ui.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// DESIGN-SYSTEM 2 and 11. Dark is the default and the brand; light mirrors the roles one to one.
@Immutable
data class CytColors(
    val bg: Color, val surface1: Color, val surface2: Color, val surface3: Color,
    val rim: Color, val outline: Color, val outlineStrong: Color,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    val primary: Color, val onPrimary: Color, val primaryPressed: Color, val accent: Color,
    val glowBrand: Color, val glowTv: Color,
    val success: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val warningContainer: Color, val onWarningContainer: Color,
    val danger: Color, val dangerFill: Color, val onDanger: Color,
    val dangerContainer: Color, val onDangerContainer: Color,
    val scrim: Color,
    // Glass recipe (DESIGN-SYSTEM 5.1): fill top and bottom, specular top, rim.
    val glassFillTop: Color, val glassFillBottom: Color, val glassSpecular: Color,
    // The springy highlight: brighter fill, an 80% rim and three outer rings.
    val highlightFillTop: Color, val highlightFillBottom: Color, val highlightSpecular: Color,
    val highlightRim: Color, val highlightRing: Color,
    // Ambient glow core alpha (24% dark, 14% light) and its mid stop.
    val glowCoreAlpha: Float, val glowMidAlpha: Float,
    val isDark: Boolean,
)

// Upgrade Your TV: a deep midnight blue with frosted glass surfaces, a sky blue primary and a violet
// accent, in place of the original's black and white. Every text colour keeps at least 4.5:1 on the
// surface it sits on.
val DarkCytColors = CytColors(
    bg = Color(0xFF050816), surface1 = Color(0xFF0D1428), surface2 = Color(0xFF141C36),
    surface3 = Color(0xFF1F294A), rim = Color(0x4DA9C4FF), outline = Color(0x29A9C4FF),
    outlineStrong = Color(0x6BA9C4FF), textPrimary = Color(0xFFF4F7FF),
    textSecondary = Color(0xFFAEB8D6), textTertiary = Color(0xFFA0ABCB),
    primary = Color(0xFF5CC8FF), onPrimary = Color(0xFF00131F), primaryPressed = Color(0xFF3FB1EC),
    accent = Color(0xFFB79CFF),
    glowBrand = Color(0xFF2F7BFF), glowTv = Color(0xFF8A5CFF),
    success = Color(0xFF3DDC84), successContainer = Color(0xFF0B2A1C),
    onSuccessContainer = Color(0xFF6EE7A4), warning = Color(0xFFFFB340),
    warningContainer = Color(0xFF33240A), onWarningContainer = Color(0xFFFFC46B),
    danger = Color(0xFFFF7A72), dangerFill = Color(0xFFFF5247), onDanger = Color(0xFF1A0503),
    dangerContainer = Color(0xFF3A1518), onDangerContainer = Color(0xFFFF9C94),
    scrim = Color(0xB3020510),
    glassFillTop = Color(0xFFBCD3FF).copy(alpha = 0.16f), glassFillBottom = Color(0xFFBCD3FF).copy(alpha = 0.06f),
    glassSpecular = Color.White.copy(alpha = 0.14f),
    highlightFillTop = Color(0xFFBCD3FF).copy(alpha = 0.26f), highlightFillBottom = Color(0xFFBCD3FF).copy(alpha = 0.10f),
    highlightSpecular = Color.White.copy(alpha = 0.20f), highlightRim = Color(0xFFD6E4FF).copy(alpha = 0.85f),
    highlightRing = Color(0xFF9CC8FF),
    glowCoreAlpha = 0.42f, glowMidAlpha = 0.18f,
    isDark = true,
)

val LightCytColors = CytColors(
    bg = Color(0xFFEDF2FB), surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFF5F8FE),
    surface3 = Color(0xFFDFE7F6), rim = Color(0x260B2A66), outline = Color(0x1A0B2A66),
    outlineStrong = Color(0x5C0B2A66), textPrimary = Color(0xFF0A1020),
    textSecondary = Color(0xFF3F4962), textTertiary = Color(0xFF505B76),
    primary = Color(0xFF0057D9), onPrimary = Color(0xFFFFFFFF), primaryPressed = Color(0xFF0046B0),
    accent = Color(0xFF6236D6),
    glowBrand = Color(0xFF2F7BFF), glowTv = Color(0xFF8A5CFF),
    success = Color(0xFF157030), successContainer = Color(0xFFE3F4E8),
    onSuccessContainer = Color(0xFF0F5A26), warning = Color(0xFF8F5300),
    warningContainer = Color(0xFFFFF1DB), onWarningContainer = Color(0xFF7A4600),
    danger = Color(0xFFC01B2C), dangerFill = Color(0xFFC01B2C), onDanger = Color(0xFFFFFFFF),
    dangerContainer = Color(0xFFFDE7E9), onDangerContainer = Color(0xFFA3121F),
    scrim = Color(0x660A1020),
    glassFillTop = Color.White.copy(alpha = 0.78f), glassFillBottom = Color.White.copy(alpha = 0.58f),
    glassSpecular = Color.White.copy(alpha = 0.60f),
    highlightFillTop = Color.White.copy(alpha = 0.96f), highlightFillBottom = Color.White.copy(alpha = 0.82f),
    highlightSpecular = Color.White.copy(alpha = 0.70f), highlightRim = Color(0xFF0057D9).copy(alpha = 0.60f),
    highlightRing = Color(0xFF0057D9),
    glowCoreAlpha = 0.22f, glowMidAlpha = 0.10f,
    isDark = false,
)

// Stock Material 3 components inherit the brand without wrappers (DESIGN-SYSTEM 11).
fun CytColors.toM3(): ColorScheme = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = primary, onPrimary = onPrimary, background = bg, onBackground = textPrimary,
    surface = bg, onSurface = textPrimary, onSurfaceVariant = textSecondary,
    surfaceContainerLowest = bg, surfaceContainerLow = surface1, surfaceContainer = surface2,
    surfaceContainerHigh = surface3, surfaceContainerHighest = surface3,
    surfaceVariant = surface3, inverseSurface = surface3, inverseOnSurface = textPrimary,
    outline = outlineStrong, outlineVariant = outline, error = danger,
    errorContainer = dangerContainer, onErrorContainer = onDangerContainer,
    secondary = accent, onSecondary = onPrimary, tertiary = accent, scrim = scrim,
    primaryContainer = surface3, onPrimaryContainer = textPrimary,
    secondaryContainer = surface3, onSecondaryContainer = textPrimary,
)
