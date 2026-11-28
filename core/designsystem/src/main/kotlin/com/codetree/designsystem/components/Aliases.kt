package com.codetree.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codetree.domain.common.AppError

/**
 * spec 名称兼容层（docs/specs/design-system.md §6 的组件清单使用 `CodeTree*` / 无前缀名称）。
 *
 * 新代码优先用 `CT*` 实现名；这些别名只做转调，避免并行开发的 feature 模块反复改名。
 * 全部实现只有一份，行为与 CT* 完全一致。
 */

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
) = CTListRow(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    leading = leading,
    trailing = trailing,
    onClick = onClick,
    showChevron = showChevron,
)

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = onClick != null,
) = CTListRow(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    trailing = trailing,
    onClick = onClick,
    showChevron = showChevron,
)

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) = CTSwitchRow(
    title = title,
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
    subtitle = subtitle,
    enabled = enabled,
)

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = CTSegmentedControl(
    options = options,
    selectedIndex = selectedIndex,
    onSelect = onSelect,
    modifier = modifier,
)

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = CTIcons.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = CTEmptyState(
    title = title,
    description = description,
    modifier = modifier,
    icon = icon,
    actionLabel = actionLabel,
    onAction = onAction,
)

@Composable
fun ErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) = CTErrorState(error = error, modifier = modifier, onRetry = onRetry, onSettings = onSettings)

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) = CTLoading(modifier = modifier, message = message)

@Composable
fun OfflineBanner(
    modifier: Modifier = Modifier,
    message: String = "当前离线：已下载内容与词典仍可阅读",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = CTOfflineBanner(modifier = modifier, message = message, actionLabel = actionLabel, onAction = onAction)

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) = CTSectionHeader(title = title, modifier = modifier, subtitle = subtitle, action = action)

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 6.dp,
    label: String? = null,
) = CTProgressRing(progress = progress, modifier = modifier, size = size, strokeWidth = strokeWidth, label = label)

@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = CodeTreeTheme.colors.accent,
) = CTSparkline(values = values, modifier = modifier, color = color)

@Composable
fun LargeTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    collapsed: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) = CTLargeTitleBar(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    onBack = onBack,
    collapsed = collapsed,
    actions = actions,
)

@Composable
fun CodeTreeScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) = CTScaffold(
    modifier = modifier,
    topBar = topBar,
    bottomBar = bottomBar,
    snackbarHost = snackbarHost,
    content = content,
)

/** 卡片内「分组」容器：统一下间距，内边距交给行组件自己控制。 */
@Composable
fun CTSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CTCard(
        modifier = modifier,
        contentPadding = 0.dp,
        content = content,
    )
}
