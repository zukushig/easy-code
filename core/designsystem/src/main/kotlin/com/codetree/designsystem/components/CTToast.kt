package com.codetree.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxScope

/**
 * 底部弹层的标题行（术语卡 / 上下文确认 / AI 设置共用）：
 * 顶部拖拽指示条（视觉提示，手势由 `ModalBottomSheet` 提供）+ 标题 + 关闭按钮。
 */
@Composable
fun CTSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    closeContentDescription: String = "关闭",
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal, vertical = spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .background(colors.separator, RoundedCornerShape(spacing.xxs)),
        )
        Spacer(Modifier.height(spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = CodeTreeTheme.typography.title2,
                    color = colors.textPrimary,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = CodeTreeTheme.typography.footnote,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            if (onClose != null) {
                CTIconButton(
                    icon = CTIcons.Close,
                    contentDescription = closeContentDescription,
                    onClick = onClose,
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * 轻提示（Snackbar / Toast）：底部浮层，2 行内文本 + 可选操作；不阻塞输入。
 * 显示/隐藏由调用方状态驱动；减少动效时只淡入淡出（无位移）。
 */
@Composable
fun CTToast(
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    val motion = CodeTreeTheme.motion

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = motion.fast()) +
                slideInVertically(animationSpec = tween(motion.fastMs), initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = motion.fast()) +
                slideOutVertically(animationSpec = tween(motion.fastMs), targetOffsetY = { it / 2 }),
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .background(colors.textPrimary, shapes.roundedMedium)
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = message,
                    style = CodeTreeTheme.typography.footnote,
                    color = if (colors.isDark) colors.background else colors.surface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.width(spacing.md))
                    Text(
                        text = actionLabel,
                        style = CodeTreeTheme.typography.footnote,
                        color = colors.accent,
                        modifier = Modifier
                            .padding(start = spacing.sm)
                            .background(Color.Transparent)
                            .padding(vertical = spacing.xxs)
                            .then(Modifier),
                    )
                }
            }
        }
    }
}

/** 弹层底部内容容器：统一底部 inset 与水平/垂直内边距。 */
@Composable
fun CTSheetBody(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTreeTheme.spacing.pageHorizontal)
            .padding(bottom = CodeTreeTheme.spacing.xl),
        content = content,
    )
}
