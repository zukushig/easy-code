package com.codetree.feature.learn.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codetree.designsystem.CTEmptyState
import com.codetree.designsystem.CTErrorState
import com.codetree.designsystem.CTLoading
import com.codetree.designsystem.CTOfflineBanner
import com.codetree.domain.common.AppError

/** 空态：转调 designsystem `EmptyState`/CTEmptyState（图标+标题+说明+可执行操作）。 */
@Composable
fun LearnEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CTEmptyState(
        title = title,
        description = description,
        modifier = modifier,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/** 错误态：转调 designsystem `ErrorState`/CTErrorState（AppError.userHint + 重试）。 */
@Composable
fun LearnErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    CTErrorState(error = error, modifier = modifier, onRetry = onRetry)
}

/** 加载态：转调 designsystem `LoadingState`/CTLoading（>300ms 才显示）。 */
@Composable
fun LearnLoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    CTLoading(modifier = modifier, message = message)
}

/** 离线提示条：转调 designsystem `OfflineBanner`/CTOfflineBanner。 */
@Composable
fun LearnOfflineBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    CTOfflineBanner(modifier = modifier, message = text)
}
