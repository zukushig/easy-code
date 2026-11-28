package com.codetree.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 圆角搜索框 + 取消按钮（iOS 风格）：
 * - 高度 36dp（触控区 48dp 由外容器保证），`surface` 底 + pill 圆角 + 细描边。
 * - 左侧放大镜、右侧一键清空；有内容时显示「取消」（`showCancel` 可强制/隐藏）。
 * - 键盘 Search 触发 `onSearch`；文案全部由调用方本地化传入。
 * - 可选自动聚焦：`autoFocus = true` 时首次组合即请求焦点（词典搜索页打开即输入）；
 *   外部也可传入 `focusRequester` 自行控制（例如「点击搜索图标聚焦」）。
 */
@Composable
fun CTSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onSearch: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    cancelLabel: String = "取消",
    showCancel: Boolean = value.isNotEmpty(),
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    autoFocus: Boolean = false,
) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    val interaction = remember { MutableInteractionSource() }
    val internalRequester = remember { FocusRequester() }
    val effectiveRequester = focusRequester ?: internalRequester

    if (autoFocus) {
        LaunchedEffect(Unit) {
            runCatching { effectiveRequester.requestFocus() }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .padding(vertical = 6.dp)
                .background(colors.surface, shapes.pillShape)
                .border(shapes.hairline, colors.separator, shapes.pillShape)
                .padding(horizontal = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CTIcons.Search,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(spacing.sm))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = CodeTreeTheme.typography.body,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(effectiveRequester),
                    enabled = enabled,
                    singleLine = true,
                    textStyle = CodeTreeTheme.typography.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    interactionSource = interaction,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                )
            }
            if (value.isNotEmpty()) {
                CTIconButton(
                    icon = CTIcons.Close,
                    contentDescription = cancelLabel,
                    onClick = {
                        onValueChange("")
                        onCancel?.invoke()
                    },
                    tint = colors.textTertiary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (showCancel) {
            Spacer(Modifier.width(spacing.xs))
            CTTextButton(
                text = cancelLabel,
                onClick = { onCancel?.invoke() },
            )
        }
    }
}
