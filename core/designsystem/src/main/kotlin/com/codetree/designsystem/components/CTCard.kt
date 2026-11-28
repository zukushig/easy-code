package com.codetree.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 苹果式卡片（docs/specs/design-system.md §6 `CodeTreeCard`）：
 * `surface` 底色 + `large`(16dp) 圆角 + 16dp 内边距 + 极轻阴影（深色模式改用 `surfaceElevated`，
 * 不叠阴影）。按压态用弹簧轻微缩放（减少动效时退化为无动画）。
 *
 * 禁止嵌套双层卡片：内容需要分组时用 `CTSectionHeader` + 分割线。
 */
@Composable
fun CTCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = CodeTreeTheme.spacing.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val motion = CodeTreeTheme.motion

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedScale = if (onClick != null && pressed) 0.985f else 1f
    val scale by animateFloatAsState(
        targetValue = pressedScale,
        animationSpec = motion.pressSpec,
        label = "ct-card-press",
    )

    val cardColor = if (onClick != null && pressed) {
        if (colors.isDark) colors.surfaceElevated else colors.surface
    } else {
        colors.surface
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = shapes.roundedLarge,
        color = cardColor,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        shadowElevation = if (colors.isDark) 0.dp else 1.dp,
        border = if (colors.isDark) BorderStroke(shapes.hairline, colors.separator.copy(alpha = 0.4f)) else null,
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/** spec §6 名称别名：新代码统一使用 `CTCard`。 */
@Composable
fun CodeTreeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = CodeTreeTheme.spacing.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) = CTCard(modifier = modifier, onClick = onClick, contentPadding = contentPadding, content = content)
