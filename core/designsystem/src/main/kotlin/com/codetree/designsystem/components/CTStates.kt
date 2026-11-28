package com.codetree.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codetree.domain.common.AppError
import kotlinx.coroutines.delay

/**
 * 空态（docs/specs/design-system.md §6 `EmptyState`）：线性图标 + 标题 + 说明 + 一个可执行操作。
 * 不得只有插图：没有操作时也必须给用户下一步（文案由调用方传入）。
 */
@Composable
fun CTEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = CTIcons.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xxl, vertical = spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(spacing.lg))
        Text(
            text = title,
            style = CodeTreeTheme.typography.title2,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = description,
            style = CodeTreeTheme.typography.callout,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(spacing.xl))
            CTButton(text = actionLabel, onClick = onAction)
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(Modifier.height(spacing.xs))
            CTTextButton(text = secondaryActionLabel, onClick = onSecondaryAction)
        }
    }
}

/**
 * 错误态：原因来自 [AppError.message]，下一步来自 [AppError.userHint]（必须有可执行下一步）；
 * 默认提供「重试」；`onSettings` 非空时提供设置入口。不展示堆栈（安全与体验）。
 */
@Composable
fun CTErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    retryLabel: String = "重试",
    settingsLabel: String = "去设置",
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xxl, vertical = spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = CTIcons.Warning,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(spacing.lg))
        Text(
            text = error.message,
            style = CodeTreeTheme.typography.headline,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = error.userHint,
            style = CodeTreeTheme.typography.callout,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (onRetry != null) {
                CTButton(text = retryLabel, onClick = onRetry)
            }
            if (onSettings != null) {
                CTButton(
                    text = settingsLabel,
                    onClick = onSettings,
                    style = if (onRetry != null) CTButtonStyle.TINTED else CTButtonStyle.FILLED,
                )
            }
        }
    }
}

/**
 * 加载态：细进度指示 + 可选说明。规范要求超过 300ms 才显示，避免闪烁
 * （短任务由调用方决定是否渲染本组件；本组件内部再做一次 300ms 延迟兜底）。
 */
@Composable
fun CTLoading(
    modifier: Modifier = Modifier,
    message: String? = null,
    delayMs: Long = 300L,
    fullScreen: Boolean = false,
) {
    var visible by remember { mutableStateOf(delayMs <= 0L) }
    LaunchedEffect(delayMs) {
        if (!visible) {
            delay(delayMs)
            visible = true
        }
    }
    if (!visible) return

    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    Box(
        modifier = modifier
            .then(if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.width(spacing.md))
                Text(
                    text = message,
                    style = CodeTreeTheme.typography.footnote,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** 离线横幅：顶部细条，说明“离线可用内容”与在线能力边界（PRD UI-004）。 */
@Composable
fun CTOfflineBanner(
    modifier: Modifier = Modifier,
    message: String = "当前离线：已下载内容与词典仍可阅读",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.16f))
            .padding(horizontal = spacing.pageHorizontal, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CTIcons.Offline,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = message,
            style = CodeTreeTheme.typography.footnote,
            color = colors.warning,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            CTTextButton(text = actionLabel, onClick = onAction, color = colors.warning)
        }
    }
}
