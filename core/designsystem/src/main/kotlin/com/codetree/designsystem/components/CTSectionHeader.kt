package com.codetree.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * 分组标题（设置 / 列表）：`footnote` 灰色大写风格，右侧可放操作（如「全部」「管理」）。
 * 分组之间由调用方用 [CTDivider] 或间距区分，不与卡片重复描边。
 */
@Composable
fun CTSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal)
            .padding(top = spacing.md, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CodeTreeTheme.typography.footnote,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = CodeTreeTheme.typography.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
        }
        action?.invoke()
    }
}
