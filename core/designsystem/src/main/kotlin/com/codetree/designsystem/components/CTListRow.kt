package com.codetree.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 设置 / 列表通用行（docs/specs/design-system.md §6 `ListRow`）：
 * 标题（headline）+ 可选副标题（footnote）+ 左图标 + 右侧 trailing / chevron。
 * 行高 ≥ 48dp，点击整行有效；分割线由列表容器插入 [CTDivider]。
 */
@Composable
fun CTListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    leadingIcon: ImageVector? = null,
    leadingTint: Color = CodeTreeTheme.colors.accent,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.listRowMinHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = spacing.pageHorizontal, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = leadingTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(spacing.md))
        }
        leading?.let {
            it()
            Spacer(Modifier.width(spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CodeTreeTheme.typography.body,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = CodeTreeTheme.typography.footnote,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
        }
        trailing?.invoke()
        if (showChevron) {
            Spacer(Modifier.width(spacing.xs))
            Icon(
                imageVector = CTIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 开关行：右侧系统 Switch，选中色使用强调色。 */
@Composable
fun CTSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val colors = CodeTreeTheme.colors
    CTListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = colors.textTertiary.copy(alpha = 0.5f),
                    uncheckedBorderColor = colors.separator,
                ),
            )
        },
    )
}

/** 滑块行：标题 + 当前值 + Slider（字号 / 语速等设置）。 */
@Composable
fun CTSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    subtitle: String? = null,
    valueLabel: String? = null,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal, vertical = spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = CodeTreeTheme.typography.body,
                color = colors.textPrimary,
            )
            if (valueLabel != null) {
                Text(
                    text = valueLabel,
                    style = CodeTreeTheme.typography.footnote,
                    color = colors.textSecondary,
                )
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = CodeTreeTheme.typography.footnote,
                color = colors.textSecondary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.separator,
            ),
        )
    }
}

/** 值行：左标题 + 右灰色值 + 可选 chevron，用于「关于」「版本」等只读信息。 */
@Composable
fun CTValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    ) {
    val colors = CodeTreeTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CodeTreeTheme.spacing.listRowMinHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = CodeTreeTheme.spacing.pageHorizontal, vertical = CodeTreeTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = CodeTreeTheme.typography.body,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = CodeTreeTheme.typography.body,
            color = colors.textSecondary,
        )
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = CTIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
