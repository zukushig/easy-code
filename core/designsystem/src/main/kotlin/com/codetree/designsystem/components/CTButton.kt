package com.codetree.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 按钮视觉层级（docs/specs/design-system.md §6 `CodeTreeButton`）。 */
enum class CTButtonStyle { FILLED, TINTED, PLAIN }

/**
 * 三态按钮：
 * - `FILLED`：`accent` 实心 + 白色文字，用于页面主操作（每屏最多一个）。
 * - `TINTED`：`accentSoft` 底 + `accent` 文字，用于次级操作。
 * - `PLAIN`：无底色、`accent` 文字，用于行内文字操作。
 *
 * 触控目标 ≥ 48dp；禁用态降低透明度但保持文字可读（不只靠颜色）。
 */
@Composable
fun CTButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: CTButtonStyle = CTButtonStyle.FILLED,
    leadingIcon: ImageVector? = null,
    contentPadding: PaddingValues? = null,
) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    val motion = CodeTreeTheme.motion

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val background: Color = when (style) {
        CTButtonStyle.FILLED -> colors.accent
        CTButtonStyle.TINTED -> colors.accentSoft
        CTButtonStyle.PLAIN -> Color.Transparent
    }
    val contentColor: Color = when (style) {
        CTButtonStyle.FILLED -> Color.White
        CTButtonStyle.TINTED, CTButtonStyle.PLAIN -> colors.accent
    }
    val shape = when (style) {
        CTButtonStyle.FILLED, CTButtonStyle.TINTED -> shapes.roundedMedium
        CTButtonStyle.PLAIN -> RoundedCornerShape(spacing.xs)
    }

    val pressedAlpha = if (pressed && enabled) 0.88f else 1f
    val alpha by animateFloatAsState(
        targetValue = if (enabled) pressedAlpha else 0.45f,
        animationSpec = motion.fast(),
        label = "ct-button-alpha",
    )

    val minHeight = spacing.minTouchTarget
    Row(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .defaultMinSize(minHeight = minHeight)
            .heightIn(min = minHeight)
            .then(
                if (style == CTButtonStyle.FILLED || style == CTButtonStyle.TINTED) {
                    Modifier.background(background, shape)
                } else {
                    Modifier
                },
            )
            .then(
                if (style == CTButtonStyle.PLAIN && enabled) {
                    Modifier
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                contentPadding ?: PaddingValues(
                    horizontal = spacing.lg,
                    vertical = spacing.md,
                ),
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(spacing.sm))
        }
        Text(
            text = text,
            style = CodeTreeTheme.typography.button,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 行内文字按钮：没有背景和最小高度约束，但按压有透明度反馈。 */
@Composable
fun CTTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = CodeTreeTheme.colors.accent,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = CodeTreeTheme.spacing.minTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = CodeTreeTheme.spacing.xs, vertical = CodeTreeTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CodeTreeTheme.typography.callout,
            color = if (enabled) color else color.copy(alpha = 0.45f),
        )
    }
}

/** spec §6 名称别名。 */
enum class CodeTreeButtonStyle { FILLED, TINTED, PLAIN }

@Composable
fun CodeTreeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: CodeTreeButtonStyle = CodeTreeButtonStyle.FILLED,
    leadingIcon: ImageVector? = null,
) = CTButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    style = when (style) {
        CodeTreeButtonStyle.FILLED -> CTButtonStyle.FILLED
        CodeTreeButtonStyle.TINTED -> CTButtonStyle.TINTED
        CodeTreeButtonStyle.PLAIN -> CTButtonStyle.PLAIN
    },
    leadingIcon = leadingIcon,
)

/** 图标按钮：固定 48dp 触控目标，必须提供 contentDescription（可访问性）。 */
@Composable
fun CTIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CodeTreeTheme.colors.accent,
) {
    val target = CodeTreeTheme.spacing.minTouchTarget
    Box(
        modifier = modifier
            .size(target)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.45f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 边框按钮：用于“次要且需要边界”的场景（例如导入按钮）。 */
@Composable
fun CTOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    Row(
        modifier = modifier
            .heightIn(min = spacing.minTouchTarget)
            .border(1.dp, colors.separator, shapes.roundedMedium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(spacing.sm))
        }
        Text(
            text = text,
            style = CodeTreeTheme.typography.button,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
    }
}
