package com.codetree.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * iOS 分段控件（docs/specs/design-system.md §6 `SegmentedControl`）：
 * pill 容器 + 白色选中滑块（弹簧位移）；用于双语模式、筛选等互斥选择。
 * 触控区 ≥ 44dp，TalkBack 读作 RadioButton；减少动效时滑块直接切换（无位移动画）。
 */
@Composable
fun CTSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    val motion = CodeTreeTheme.motion

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.pillShape)
            .background(colors.surfaceElevated)
            .padding(spacing.xxs),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val segmentWidth = maxWidth / options.size
            val targetOffset = segmentWidth * selectedIndex.coerceIn(0, options.lastIndex)
            val indicatorOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = if (motion.reduceMotion) {
                    snap()
                } else {
                    spring(dampingRatio = motion.springDampingRatio, stiffness = motion.springStiffness)
                },
                label = "ct-segmented-offset",
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .height(34.dp)
                    .clip(shapes.pillShape)
                    .background(colors.surface),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .selectable(
                                selected = selected,
                                onClick = { onSelect(index) },
                                role = androidx.compose.ui.semantics.Role.RadioButton,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = CodeTreeTheme.typography.footnote,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) colors.textPrimary else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 带标题的分段控件组（设置页「双语模式」等）。 */
@Composable
fun CTSegmentedSetting(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CodeTreeTheme.spacing
    Box(modifier = modifier.padding(horizontal = spacing.pageHorizontal, vertical = spacing.sm)) {
        CTSegmentedControl(
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
        )
    }
}
