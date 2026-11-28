package com.codetree.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 苹果式大标题导航栏（docs/specs/design-system.md §6 `LargeTitleScaffold`）：
 * - 展开态：返回按钮行 + `largeTitle`(34sp Bold) 大标题（可带副标题）。
 * - `collapsed = true`：大标题收起，导航行中央显示 `headline` 小标题。
 * - 返回按钮 48dp 触控目标，标题单行省略；`actions` 放右侧操作按钮。
 *
 * 与列表联动用 [rememberLargeTitleCollapsed]：滚动超过阈值即收起（无动画抖动）。
 */
@Composable
fun CTLargeTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    collapsed: Boolean = false,
    backContentDescription: String = "返回",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    val motion = CodeTreeTheme.motion
    val shapes = CodeTreeTheme.shapes

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.minTouchTarget)
                .padding(horizontal = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(spacing.minTouchTarget), contentAlignment = Alignment.Center) {
                if (onBack != null) {
                    CTIconButton(
                        icon = CTIcons.Back,
                        contentDescription = backContentDescription,
                        onClick = onBack,
                        tint = colors.accent,
                    )
                }
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (collapsed) {
                    Text(
                        text = title,
                        style = typography.headline,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }

        AnimatedVisibility(
            visible = !collapsed,
            enter = if (motion.reduceMotion) {
                fadeIn(animationSpec = motion.fast())
            } else {
                fadeIn(animationSpec = motion.normal()) + expandVertically(animationSpec = tween(motion.normalMs))
            },
            exit = if (motion.reduceMotion) {
                fadeOut(animationSpec = motion.fast())
            } else {
                fadeOut(animationSpec = motion.fast()) + shrinkVertically(animationSpec = tween(motion.fastMs))
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.pageHorizontal)
                    .padding(top = spacing.xxs, bottom = spacing.pageTop),
            ) {
                Text(
                    text = title,
                    style = typography.largeTitle,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = typography.subhead,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
            }
        }

        // 收起态下方画 0.5dp 细线；展开态不画线（大标题直接贴内容）。
        if (collapsed) {
            CTDivider()
        }
    }
}

/** 列表滚动 → 大标题收起状态（阈值 48dp，避免一点滚动就切换）。 */
@Composable
fun rememberLargeTitleCollapsed(scrollState: LazyListState, threshold: Int = 48): State<Boolean> =
    remember(scrollState, threshold) {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > threshold
        }
    }
