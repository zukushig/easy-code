package com.codetree.designsystem

import android.animation.ValueAnimator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density

/**
 * CodeTree 主题入口（docs/specs/design-system.md §1）。
 *
 * - 关闭 Material You 动态取色，保持品牌一致；`MaterialTheme` 只作为底层实现（Material 组件配色）。
 * - 深浅模式：近白 `#F5F5F7` / 纯黑 `#000000` 两套令牌。
 * - 应用内字号：`fontScale` 叠加在系统 fontScale 之上（0.8～2.0 夹紧），所有 `sp` 自动缩放。
 * - 减少动效：设置项 `reduceMotion` 或系统 `ANIMATOR_DURATION_SCALE == 0` 任一成立即降级
 *   （动效时长归零、无位移 / 缩放 / 弹跳）。
 *
 * 业务代码读取令牌：
 * ```
 * val colors = CodeTreeTheme.colors
 * val typography = CodeTreeTheme.typography
 * val spacing = CodeTreeTheme.spacing
 * val shapes = CodeTreeTheme.shapes
 * val motion = CodeTreeTheme.motion
 * val terminal = CodeTreeTheme.terminal
 * ```
 */
@Composable
fun CodeTreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemReduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val effectiveReduceMotion = reduceMotion || systemReduceMotion

    val colors = if (darkTheme) DarkCodeTreeColors else LightCodeTreeColors
    val typography = DefaultCodeTreeTypography
    val spacing = DefaultCodeTreeSpacing
    val shapes = DefaultCodeTreeShapes
    val motion = remember(effectiveReduceMotion) { CodeTreeMotion(reduceMotion = effectiveReduceMotion) }
    val terminal = DefaultTerminalColors

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, fontScale) {
        val clamped = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        Density(baseDensity.density, baseDensity.fontScale * clamped)
    }

    CompositionLocalProvider(
        LocalCodeTreeColors provides colors,
        LocalCodeTreeTypography provides typography,
        LocalCodeTreeSpacing provides spacing,
        LocalCodeTreeShapes provides shapes,
        LocalCodeTreeMotion provides motion,
        LocalCodeTreeTerminal provides terminal,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            shapes = shapes.toMaterialShapes(),
            content = content,
        )
    }
}

/** 令牌访问器（与 `MaterialTheme.colorScheme` 同构）。 */
object CodeTreeTheme {
    val colors: CodeTreeColors
        @Composable @ReadOnlyComposable get() = LocalCodeTreeColors.current

    val typography: CodeTreeTypography
        @Composable @ReadOnlyComposable get() = LocalCodeTreeTypography.current

    val spacing: CodeTreeSpacing
        @Composable @ReadOnlyComposable get() = LocalCodeTreeSpacing.current

    val shapes: CodeTreeShapes
        @Composable @ReadOnlyComposable get() = LocalCodeTreeShapes.current

    val motion: CodeTreeMotion
        @Composable @ReadOnlyComposable get() = LocalCodeTreeMotion.current

    val terminal: TerminalColors
        @Composable @ReadOnlyComposable get() = LocalCodeTreeTerminal.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalCodeTreeColors.current.isDark

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalCodeTreeMotion.current.reduceMotion
}

private const val MIN_FONT_SCALE = 0.8f
private const val MAX_FONT_SCALE = 2.0f

private fun CodeTreeColors.toMaterialColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accentSoft,
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = Color.White,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        outline = separator,
        outlineVariant = separator,
        error = error,
        onError = Color.White,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accentSoft,
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = Color.White,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        outline = separator,
        outlineVariant = separator,
        error = error,
        onError = Color.White,
    )
}

private fun CodeTreeTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = largeTitle,
    displayMedium = largeTitle,
    displaySmall = title1,
    headlineLarge = title1,
    headlineMedium = title2,
    headlineSmall = title2,
    titleLarge = title2,
    titleMedium = headline,
    titleSmall = callout,
    bodyLarge = body,
    bodyMedium = callout,
    bodySmall = footnote,
    labelLarge = button,
    labelMedium = footnote,
    labelSmall = caption,
)

private fun CodeTreeShapes.toMaterialShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(small),
    small = RoundedCornerShape(small),
    medium = RoundedCornerShape(medium),
    large = RoundedCornerShape(large),
    extraLarge = RoundedCornerShape(xlarge),
)

/** Token 便捷别名：把 `TextStyle` 复制并覆盖颜色，供 `Text(style = ...)` 使用。 */
fun TextStyle.withColor(color: Color): TextStyle = copy(color = color)
