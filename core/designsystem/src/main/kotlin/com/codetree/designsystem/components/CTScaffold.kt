package com.codetree.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 页面骨架：统一背景色、系统栏 inset 与 top / bottom bar 插槽（docs/specs/design-system.md §6）。
 *
 * 默认 `contentWindowInsets` 只处理顶部与左右安全区：底部安全区由 `CTTabBar` /
 * 底部操作条自己通过 `navigationBarsPadding()` 处理，避免双重留白。
 */
@Composable
fun CTScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = CodeTreeTheme.colors.background,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentColor = CodeTreeTheme.colors.textPrimary,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/**
 * 带大标题的页面骨架：大标题 + 可选返回 + 滚动收起。
 *
 * ```kotlin
 * val listState = rememberLazyListState()
 * val collapsed by rememberLargeTitleCollapsed(listState)
 * CTScaffold(topBar = { CTLargeTitleBar("学习", collapsed = collapsed) }) { padding ->
 *     LazyColumn(state = listState, contentPadding = padding) { ... }
 * }
 * ```
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    collapsed: Boolean = false,
    topActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    CTScaffold(
        modifier = modifier,
        topBar = {
            CTLargeTitleBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                collapsed = collapsed,
                actions = topActions,
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/** 页面内容区的标准水平边距容器，避免每个页面重复写 padding。 */
@Composable
fun CTPageColumn(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = CodeTreeTheme.spacing.pageHorizontal,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = horizontalPadding),
        content = content,
    )
}

/** 页面顶部/底部留白常量：列表最后一项与底部 Tab 之间保持 24dp。 */
val CTPageBottomSpacer: Dp = 24.dp
