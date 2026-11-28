package com.codetree.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 标签语义色（docs/specs/design-system.md §2）：只作辅助底色，含义同时由文字表达。 */
enum class CTTagTone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }

/**
 * 胶囊标签：可选中的筛选项 / 状态标签。
 * `selected = true` 时使用 `accent` 实心（文字白色），否则 `surface` 底 + 细描边。
 * 触控目标 48dp 由外层列表提供；单个 pill 自身视觉高度 32dp，点击热区经 `defaultMinSize` 保证 ≥ 44dp。
 */
@Composable
fun CTPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing

    val background: Color = if (selected) colors.accent else colors.surface
    val contentColor: Color = if (selected) Color.White else colors.textPrimary
    val borderColor = if (selected) Color.Transparent else colors.separator

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .background(background, RoundedCornerShape(shapes.pill))
            .border(shapes.hairline, borderColor, RoundedCornerShape(shapes.pill))
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(PaddingValues(horizontal = spacing.md, vertical = spacing.sm)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CodeTreeTheme.typography.footnote,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
        )
    }
}

/** 只读状态标签：成功 / 警告 / 错误 / 强调 / 中性（文字 + 底色双编码，不只靠颜色）。 */
@Composable
fun CTTag(
    text: String,
    modifier: Modifier = Modifier,
    tone: CTTagTone = CTTagTone.NEUTRAL,
) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing

    val (background, foreground) = when (tone) {
        CTTagTone.NEUTRAL -> colors.surfaceElevated to colors.textSecondary
        CTTagTone.ACCENT -> colors.accentSoft to colors.accent
        CTTagTone.SUCCESS -> colors.success.copy(alpha = 0.14f) to colors.success
        CTTagTone.WARNING -> colors.warning.copy(alpha = 0.16f) to colors.warning
        CTTagTone.DANGER -> colors.error.copy(alpha = 0.14f) to colors.error
    }

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(shapes.pill))
            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CodeTreeTheme.typography.caption,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
        )
    }
}
