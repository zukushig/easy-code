package com.codetree.feature.learn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.codetree.designsystem.CodeTreeTheme

/**
 * 设计令牌适配层（学习模块）。
 *
 * 页面只依赖本文件的 `learnPalette()/learnType()/learnSpacing()/learnShapes()/learnMotion()`
 * 与 [LearnPalette] 等内部类型；真实令牌全部来自 `:core:designsystem` 的 `CodeTreeTheme`，
 * 页面代码不出现任何硬编码颜色/字号/圆角/动效时长（design-system.md §1 约束）。
 *
 * 说明：`CodeTreeTheme` 由 `:app` 壳统一包裹；feature 只读取令牌，不再包主题。
 */
@Immutable
data class LearnPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val separator: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val isDark: Boolean,
)

@Immutable
data class LearnType(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val mono: TextStyle,
)

@Immutable
data class LearnSpacing(
    val xxs: Dp,
    val xs: Dp,
    val s: Dp,
    val m: Dp,
    val l: Dp,
    val xl: Dp,
    val xxl: Dp,
    val pageHorizontal: Dp,
    val cardPadding: Dp,
    val minTouchTarget: Dp,
)

@Immutable
data class LearnShapes(
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val xlarge: Dp,
    val pill: Dp,
)

@Immutable
data class LearnMotion(
    val durationFast: Int,
    val durationNormal: Int,
    val durationSlow: Int,
    val reduceMotion: Boolean,
)

@Composable
@ReadOnlyComposable
fun learnPalette(): LearnPalette {
    val c = CodeTreeTheme.colors
    return LearnPalette(
        background = c.background,
        surface = c.surface,
        surfaceElevated = c.surfaceElevated,
        textPrimary = c.textPrimary,
        textSecondary = c.textSecondary,
        textTertiary = c.textTertiary,
        separator = c.separator,
        accent = c.accent,
        accentSoft = c.accentSoft,
        success = c.success,
        warning = c.warning,
        error = c.error,
        isDark = c.isDark,
    )
}

@Composable
@ReadOnlyComposable
fun learnType(): LearnType {
    val t = CodeTreeTheme.typography
    return LearnType(
        largeTitle = t.largeTitle,
        title1 = t.title1,
        title2 = t.title2,
        headline = t.headline,
        body = t.body,
        callout = t.callout,
        subhead = t.subhead,
        footnote = t.footnote,
        caption = t.caption,
        mono = t.mono,
    )
}

@Composable
@ReadOnlyComposable
fun learnSpacing(): LearnSpacing {
    val s = CodeTreeTheme.spacing
    return LearnSpacing(
        xxs = s.xxs,
        xs = s.xs,
        s = s.sm,
        m = s.md,
        l = s.lg,
        xl = s.xl,
        xxl = s.xxl,
        pageHorizontal = s.pageHorizontal,
        cardPadding = s.cardPadding,
        minTouchTarget = s.minTouchTarget,
    )
}

@Composable
@ReadOnlyComposable
fun learnShapes(): LearnShapes {
    val s = CodeTreeTheme.shapes
    return LearnShapes(
        small = s.small,
        medium = s.medium,
        large = s.large,
        xlarge = s.xlarge,
        pill = s.pill,
    )
}

@Composable
@ReadOnlyComposable
fun learnMotion(): LearnMotion {
    val m = CodeTreeTheme.motion
    return LearnMotion(
        durationFast = m.fastMs,
        durationNormal = m.normalMs,
        durationSlow = m.slowMs,
        reduceMotion = m.reduceMotion,
    )
}

/** 难度语义色：入门=成功、进阶=警告、高级=错误（同时配文字标签，不只靠颜色）。 */
fun LearnPalette.difficultyColor(difficulty: com.codetree.domain.model.Difficulty): Color = when (difficulty) {
    com.codetree.domain.model.Difficulty.BEGINNER -> success
    com.codetree.domain.model.Difficulty.INTERMEDIATE -> warning
    com.codetree.domain.model.Difficulty.ADVANCED -> error
}

fun LearnPalette.statusColor(status: com.codetree.domain.model.LearningStatus): Color = when (status) {
    com.codetree.domain.model.LearningStatus.NOT_STARTED -> textTertiary
    com.codetree.domain.model.LearningStatus.LEARNING -> warning
    com.codetree.domain.model.LearningStatus.MASTERED -> success
}
