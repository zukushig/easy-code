package com.codetree.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 底部 Tab 数据项（标签文字由 App 层本地化传入）。 */
data class CTTab(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String? = null,
    val badge: String? = null,
)

/**
 * 5 项底部 Tab 栏（docs/specs/design-system.md §6 `BottomTabBar`）：
 * - 细线图标 + 文字标签（不只图标），选中 `accent`，未选中 `textSecondary`。
 * - 触控目标 ≥ 48dp（行高 56dp），顶部 0.5dp 分割线，随主题深浅色。
 * - 底部自动处理 navigationBars inset。
 */
@Composable
fun CTTabBar(
    tabs: List<CTTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding(),
    ) {
        CTDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                CTTabItem(
                    icon = tab.icon,
                    label = tab.label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                    contentDescription = tab.contentDescription,
                    badge = tab.badge,
                )
            }
        }
    }
}

/** 单个 Tab：图标 + 文字，选中态强调色；48dp 以上触控区域。 */
@Composable
fun CTTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    badge: String? = null,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    val tint = if (selected) colors.accent else colors.textSecondary
    Column(
        modifier = modifier
            .heightIn(min = spacing.minTouchTarget)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription ?: label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = 12.dp)
                        .background(colors.error, CodeTreeTheme.shapes.pillShape),
                ) {
                    Text(
                        text = badge,
                        style = CodeTreeTheme.typography.caption,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = label,
            style = CodeTreeTheme.typography.caption,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** spec §6 名称别名。 */
@Composable
fun BottomTabBar(
    tabs: List<CTTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = CTTabBar(tabs = tabs, selectedIndex = selectedIndex, onSelect = onSelect, modifier = modifier)

/** 空 Tab 占位（feature 未落盘时由 App 层使用，不属于正式 UI）。 */
@Composable
fun CTTabPlaceholder(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CodeTreeTheme.colors.textTertiary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = CodeTreeTheme.typography.footnote,
            color = CodeTreeTheme.colors.textTertiary,
        )
    }
}
