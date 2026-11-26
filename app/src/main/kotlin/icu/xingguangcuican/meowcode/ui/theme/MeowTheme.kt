package icu.xingguangcuican.meowcode.ui.theme

import icu.xingguangcuican.meowcode.model.AppPreferences
import icu.xingguangcuican.meowcode.model.ThemeSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.xingguangcuican6666.abk.ui.theme.AbkColorSource
import io.github.xingguangcuican6666.abk.ui.theme.AbkDefaultSeed
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import icu.xingguangcuican.meowcode.ui.state.LocalMeow

/**
 * Host-owned theme preferences fed into the library. The library itself never
 * persists anything; these values live in [icu.xingguangcuican.meowcode.ui.state.MeowState].
 */
@Composable
fun MeowTheme(preferences: AppPreferences, content: @Composable () -> Unit) {
    val dark = preferences.darkTheme ?: isSystemInDarkTheme()
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * preferences.fontSizeScale),
    ) {
        AbkTheme(
            darkTheme = dark,
            colorSource = when (preferences.colorSource) {
                ThemeSource.SystemDynamic -> AbkColorSource.System
                ThemeSource.DatasetSeed -> AbkColorSource.Seed
            },
            seed = AbkDefaultSeed,
            reduceMotion = preferences.reduceMotion,
            content = content,
        )
    }
}

/** Convenience for screens that want the current preferences (e.g. the 我 tab). */
@Composable
fun currentPreferences(): AppPreferences = LocalMeow.current.preferences

/** The app's accent for non-library drawing, kept in sync with the ABK tokens. */
val MeowInkAccent: Color @Composable get() = AbkTheme.colors.accent.color
