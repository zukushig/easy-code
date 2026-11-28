package com.codetree.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌（docs/specs/design-system.md §1～§5）。
 *
 * 所有颜色 / 字号 / 间距 / 圆角 / 动效都集中在本文件；业务代码只能通过
 * `CodeTreeTheme.colors` 等访问器读取，禁止硬编码 `Color(0x...)`、`34.sp`、`250`。
 *
 * 苹果式极简：近白 / 近黑背景、单一强调色、克制的语义色、极轻阴影、短促动效。
 */

// ---------------------------------------------------------------------------------------------
// 颜色
// ---------------------------------------------------------------------------------------------

/** 界面色彩令牌：浅色与深色两套（docs/specs/design-system.md §2）。 */
@Immutable
data class CodeTreeColors(
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
    val overlay: Color,
    val isDark: Boolean = false,
) {
    /** 浮层遮罩：`overlay` 已带透明度，可直接作为 scrim 使用。 */
    val scrim: Color get() = overlay
}

/**
 * 终端配色：始终深色，不随主题反色（避免关键字高亮在浅色下失效）。
 * 对应 `runtime:api` 的 `TokenKind`（语义名称见注释）。
 */
@Immutable
data class TerminalColors(
    val bg: Color,
    val text: Color, // PLAIN
    val keyword: Color, // KEYWORD
    val builtin: Color, // BUILTIN
    val string: Color, // STRING
    val comment: Color, // COMMENT
    val number: Color, // NUMBER
    val operator: Color, // OPERATOR
    val function: Color, // FUNCTION
    val punctuation: Color, // PUNCTUATION
    val identifier: Color, // IDENTIFIER
    val error: Color,
    val prompt: Color,
    val selection: Color,
) {
    /** 供 `TerminalColors` 消费方按 `TokenKind.name.lowercase()` 取色；未知类型回退正文色。 */
    fun colorFor(kind: String): Color = when (kind) {
        "KEYWORD" -> keyword
        "BUILTIN" -> builtin
        "STRING" -> string
        "COMMENT" -> comment
        "NUMBER" -> number
        "OPERATOR" -> operator
        "FUNCTION" -> function
        "PUNCTUATION" -> punctuation
        "IDENTIFIER" -> identifier
        else -> text
    }
}

/** 浅色模式（docs/specs/design-system.md §2 表）。 */
val LightCodeTreeColors: CodeTreeColors = CodeTreeColors(
    background = Color(0xFFF5F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1D1D1F),
    textSecondary = Color(0xFF6E6E73),
    textTertiary = Color(0xFFAEAEB2),
    separator = Color(0xFFD2D2D7),
    accent = Color(0xFF0A84FF),
    accentSoft = Color(0xFFE5F0FF),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    error = Color(0xFFFF453A),
    overlay = Color(0x66000000),
    isDark = false,
)

/** 深色模式：背景纯黑、卡片 #1C1C1E、强调色保持一致。 */
val DarkCodeTreeColors: CodeTreeColors = CodeTreeColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceElevated = Color(0xFF2C2C2E),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF98989D),
    textTertiary = Color(0xFF636366),
    separator = Color(0xFF38383A),
    accent = Color(0xFF0A84FF),
    accentSoft = Color(0xFF12233B),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    error = Color(0xFFFF453A),
    overlay = Color(0x99000000),
    isDark = true,
)

/** 终端配色（固定深色，浅/深主题共用）。 */
val DefaultTerminalColors: TerminalColors = TerminalColors(
    bg = Color(0xFF0B0B0D),
    text = Color(0xFFE5E5EA),
    keyword = Color(0xFFFF7AB2),
    builtin = Color(0xFF7BD88F),
    string = Color(0xFFF2C46D),
    comment = Color(0xFF7A7A80),
    number = Color(0xFFC7A0FF),
    operator = Color(0xFFE5E5EA),
    function = Color(0xFF6FBFFF),
    punctuation = Color(0xFFAEAEB2),
    identifier = Color(0xFFE5E5EA),
    error = Color(0xFFFF453A),
    prompt = Color(0xFF30D158),
    selection = Color(0x330A84FF),
)

// ---------------------------------------------------------------------------------------------
// 字体
// ---------------------------------------------------------------------------------------------

/**
 * 字体阶梯（docs/specs/design-system.md §3）：SF 风格的号数 / 行高 / 字重 / 字距。
 * 使用 `sp`，自动跟随系统 fontScale；`CodeTreeTheme(fontScale = ...)` 可再叠加应用内字号。
 */
@Immutable
data class CodeTreeTypography(
    val largeTitle: TextStyle = TextStyle(
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.37.sp,
    ),
    val title1: TextStyle = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.36.sp,
    ),
    val title2: TextStyle = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.35.sp,
    ),
    val headline: TextStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = -0.41.sp,
    ),
    val body: TextStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = -0.41.sp,
    ),
    val callout: TextStyle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = -0.32.sp,
    ),
    val subhead: TextStyle = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = -0.24.sp,
    ),
    val footnote: TextStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = -0.08.sp,
    ),
    val caption: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    /** 代码 / 终端 / 标识符；行高保证多行代码可读。 */
    val mono: TextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
    ),
    /** 行内代码、语言标签等小号等宽文本。 */
    val monoSmall: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
    ),
    /** 按钮 / 分段控件标签：semibold 17。 */
    val button: TextStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = -0.41.sp,
    ),
)

val DefaultCodeTreeTypography: CodeTreeTypography = CodeTreeTypography()

// ---------------------------------------------------------------------------------------------
// 间距与尺寸（4/8pt 网格）
// ---------------------------------------------------------------------------------------------

/** 间距刻度：2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64。 */
@Immutable
data class CodeTreeSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val huge: Dp = 40.dp,
    val giant: Dp = 64.dp,
    /** 页面水平边距（小屏 16dp，大屏可由页面覆盖为 24dp）。 */
    val pageHorizontal: Dp = 16.dp,
    /** 卡片内边距。 */
    val cardPadding: Dp = 16.dp,
    /** 触控目标下限（a11y）。 */
    val minTouchTarget: Dp = 48.dp,
    /** 按钮最小高度（48dp 触控区域内）。 */
    val minButtonHeight: Dp = 44.dp,
    /** 列表行最小高度。 */
    val listRowMinHeight: Dp = 48.dp,
    /** 大标题下方内容起始间距。 */
    val pageTop: Dp = 8.dp,
)

val DefaultCodeTreeSpacing: CodeTreeSpacing = CodeTreeSpacing()

// ---------------------------------------------------------------------------------------------
// 圆角与描边
// ---------------------------------------------------------------------------------------------

/** 圆角 / 描边令牌（docs/specs/design-system.md §4）。 */
@Immutable
data class CodeTreeShapes(
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val xlarge: Dp = 24.dp,
    val pill: Dp = 500.dp,
) {
    val roundedSmall: Shape get() = RoundedCornerShape(small)
    val roundedMedium: Shape get() = RoundedCornerShape(medium)
    val roundedLarge: Shape get() = RoundedCornerShape(large)
    val roundedXLarge: Shape get() = RoundedCornerShape(xlarge)
    val pillShape: Shape get() = RoundedCornerShape(pill)

    /** 分割线 0.5dp。 */
    val hairline: Dp get() = 0.5.dp

    /** 选中态描边 1.5dp（不靠粗阴影）。 */
    val selectedBorder: Dp get() = 1.5.dp
}

val DefaultCodeTreeShapes: CodeTreeShapes = CodeTreeShapes()

// ---------------------------------------------------------------------------------------------
// 动效
// ---------------------------------------------------------------------------------------------

/**
 * 动效令牌（docs/specs/design-system.md §5）。
 *
 * `reduceMotion = true` 时所有时长归零（`fastMs/normalMs/slowMs == 0`），
 * 页面动效退化为无位移 / 无缩放的淡入淡出；组件用 `fast()/normal()/slow()` 自动降级。
 */
@Immutable
class CodeTreeMotion(
    val reduceMotion: Boolean = false,
    val durationFast: Int = 150,
    val durationNormal: Int = 250,
    val durationSlow: Int = 400,
    val easingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val easingExit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f),
    val springDampingRatio: Float = 0.8f,
    val springStiffness: Float = 400f,
    /** 页面进入的轻微上移距离；减少动效时为 0。 */
    val enterOffset: Dp = 12.dp,
) {
    val fastMs: Int get() = if (reduceMotion) 0 else durationFast
    val normalMs: Int get() = if (reduceMotion) 0 else durationNormal
    val slowMs: Int get() = if (reduceMotion) 0 else durationSlow

    val enterOffsetDp: Dp get() = if (reduceMotion) 0.dp else enterOffset

    fun <T> fast(): TweenSpec<T> = tween(fastMs, easing = if (reduceMotion) LinearEasing else easingStandard)
    fun <T> normal(): TweenSpec<T> = tween(normalMs, easing = if (reduceMotion) LinearEasing else easingStandard)
    fun <T> slow(): TweenSpec<T> = tween(slowMs, easing = if (reduceMotion) LinearEasing else easingStandard)
    fun <T> exit(): TweenSpec<T> = tween(fastMs, easing = if (reduceMotion) LinearEasing else easingExit)

    /** 按压回弹；减少动效时退化为 snap（无回弹）。 */
    val pressSpec: FiniteAnimationSpec<Float>
        get() = if (reduceMotion) snap() else spring(dampingRatio = springDampingRatio, stiffness = springStiffness)

    fun <T> springPress(): SpringSpec<T> = spring(dampingRatio = springDampingRatio, stiffness = springStiffness)
}

// ---------------------------------------------------------------------------------------------
// CompositionLocals（packages 外只经 CodeTreeTheme 访问器读取）
// ---------------------------------------------------------------------------------------------

internal val LocalCodeTreeColors = staticCompositionLocalOf<CodeTreeColors> { LightCodeTreeColors }
internal val LocalCodeTreeTypography = staticCompositionLocalOf<CodeTreeTypography> { DefaultCodeTreeTypography }
internal val LocalCodeTreeSpacing = staticCompositionLocalOf<CodeTreeSpacing> { DefaultCodeTreeSpacing }
internal val LocalCodeTreeShapes = staticCompositionLocalOf<CodeTreeShapes> { DefaultCodeTreeShapes }
internal val LocalCodeTreeMotion = staticCompositionLocalOf<CodeTreeMotion> { CodeTreeMotion() }
internal val LocalCodeTreeTerminal = staticCompositionLocalOf<TerminalColors> { DefaultTerminalColors }
