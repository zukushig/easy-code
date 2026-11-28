package com.codetree.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 0.5dp 分隔线（docs/specs/design-system.md §4）：列表内部使用，可指定左侧缩进。
 * 深色模式使用 `separator` #38383A，浅色 #D2D2D7，不依赖阴影。
 */
@Composable
fun CTDivider(
    modifier: Modifier = Modifier,
    inset: Dp = 0.dp,
    color: Color = CodeTreeTheme.colors.separator,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(CodeTreeTheme.shapes.hairline)
            .background(color),
    )
}

/** 竖向细分隔线，用于按钮行 / 状态条内部。 */
@Composable
fun CTVerticalDivider(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    color: Color = CodeTreeTheme.colors.separator,
) {
    Box(
        modifier = modifier
            .height(height)
            .padding(horizontal = 0.dp)
            .background(color),
    )
}
