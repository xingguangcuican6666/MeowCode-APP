package io.github.xingguangcuican6666.abk.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

public val AbkDefaultSeed: Color = Color(0xFF496A78)

public enum class AbkColorSource { System, Seed }
public enum class AbkTone { Neutral, Accent, Success, Warning, Danger, Info }

@Immutable
public data class AbkStatusColors(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
)

@Immutable
public data class AbkColors(
    val material: ColorScheme,
    val background: Color,
    val raised: Color,
    val sunken: Color,
    val input: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: AbkStatusColors,
    val success: AbkStatusColors,
    val warning: AbkStatusColors,
    val danger: AbkStatusColors,
    val info: AbkStatusColors,
    val scrim: Color,
) {
    public fun status(tone: AbkTone): AbkStatusColors = when (tone) {
        AbkTone.Neutral -> AbkStatusColors(inkMuted, raised, sunken, inkSoft)
        AbkTone.Accent -> accent
        AbkTone.Success -> success
        AbkTone.Warning -> warning
        AbkTone.Danger -> danger
        AbkTone.Info -> info
    }
}

private fun status(color: Long, onColor: Long, container: Long, onContainer: Long) = AbkStatusColors(
    Color(color), Color(onColor), Color(container), Color(onContainer),
)

/** Fixed counterpart of rebuild_abk_design/assets/css/tokens.css (seed #496a78). */
@Suppress("UNUSED_PARAMETER")
public fun abkSeedColors(seed: Color = AbkDefaultSeed, dark: Boolean = false): AbkColors {
    val material = if (dark) darkColorScheme(
        primary = Color(0xFF89D0ED), onPrimary = Color(0xFF003545),
        primaryContainer = Color(0xFF004D62), onPrimaryContainer = Color(0xFFBAEAFF),
        secondary = Color(0xFFB3CAD5), onSecondary = Color(0xFF1E333C),
        secondaryContainer = Color(0xFF354A53), onSecondaryContainer = Color(0xFFCFE6F1),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0F1417), surface = Color(0xFF0F1417),
        onBackground = Color(0xFFDEE3E6), onSurface = Color(0xFFDEE3E6),
        surfaceVariant = Color(0xFF252B2D), onSurfaceVariant = Color(0xFFC0C8CC),
        outline = Color(0xFF8A9296), outlineVariant = Color(0xFF40484C),
    ) else lightColorScheme(
        primary = Color(0xFF0C6780), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBAEAFF), onPrimaryContainer = Color(0xFF004D62),
        secondary = Color(0xFF4C616B), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCFE6F1), onSecondaryContainer = Color(0xFF354A53),
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFF5FAFD), surface = Color(0xFFF5FAFD),
        onBackground = Color(0xFF171C1F), onSurface = Color(0xFF171C1F),
        surfaceVariant = Color(0xFFF0F4F7), onSurfaceVariant = Color(0xFF40484C),
        outline = Color(0xFF70787D), outlineVariant = Color(0xFFC0C8CC),
    )
    return abkColorsFromMaterial(material, dark)
}

internal fun abkColorsFromMaterial(material: ColorScheme, dark: Boolean): AbkColors {
    val background = if (dark) Color(0xFF0F1417) else Color(0xFFF5FAFD)
    val raised = if (dark) Color(0xFF1B2023) else Color(0xFFFFFFFF)
    val sunken = if (dark) Color(0xFF0A0F11) else Color(0xFFEAEFF2)
    val input = if (dark) Color(0xFF252B2D) else Color(0xFFF0F4F7)
    val inkMuted = if (dark) Color(0xFFBAC2C7) else Color(0xFF4E565A)
    val inkFaint = if (dark) Color(0xFFB2BABF) else Color(0xFF576064)
    val success = if (dark) status(0xFF96D5A1, 0xFF003918, 0xFF13512A, 0xFFB2F2BC)
        else status(0xFF2F6A40, 0xFFFFFFFF, 0xFFB2F2BC, 0xFF00210B)
    val warning = if (dark) status(0xFFFFB964, 0xFF472A00, 0xFF663E00, 0xFFFFDDB9)
        else status(0xFF875300, 0xFFFFFFFF, 0xFFFFDDB9, 0xFF2B1700)
    return AbkColors(
        material = material,
        background = if (material.primary == Color(0xFF0C6780) || material.primary == Color(0xFF89D0ED)) background else material.surface,
        raised = if (material.primary == Color(0xFF0C6780) || material.primary == Color(0xFF89D0ED)) raised else material.surface,
        sunken = if (material.primary == Color(0xFF0C6780) || material.primary == Color(0xFF89D0ED)) sunken else material.surfaceContainer,
        input = if (material.primary == Color(0xFF0C6780) || material.primary == Color(0xFF89D0ED)) input else material.surfaceContainerHigh,
        ink = material.onSurface,
        inkSoft = material.onSurfaceVariant,
        inkMuted = if (material.primary == Color(0xFF0C6780) || material.primary == Color(0xFF89D0ED)) inkMuted else material.onSurfaceVariant,
        inkFaint = if (material.primary == Color(0xFF0C6780) || material.primary == Color(0xFF89D0ED)) inkFaint else material.onSurfaceVariant,
        line = material.outlineVariant,
        lineStrong = material.outline,
        accent = AbkStatusColors(material.primary, material.onPrimary, material.primaryContainer, material.onPrimaryContainer),
        success = success,
        warning = warning,
        danger = AbkStatusColors(material.error, material.onError, material.errorContainer, material.onErrorContainer),
        info = AbkStatusColors(material.secondary, material.onSecondary, material.secondaryContainer, material.onSecondaryContainer),
        scrim = material.scrim.copy(alpha = if (dark) 0x94 / 255f else 0x6B / 255f),
    )
}
