package com.codetree.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codetree.domain.common.AppError
import com.codetree.domain.content.BilingualMode
import com.codetree.domain.model.EdgeType

/**
 * 五态与基础组件预览（docs/specs/design-system.md §9：浅/深色、字号、减少动效四组预览）。
 * 预览只用于设计自检，不参与业务逻辑。
 */
@Composable
private fun StatesShowcase() {
    val spacing = CodeTreeTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(spacing.pageHorizontal),
    ) {
        Text("Loading", style = CodeTreeTheme.typography.headline, color = CodeTreeTheme.colors.textPrimary)
        CTLoading(message = "正在准备内容…")
        Spacer(Modifier.height(spacing.lg))

        Text("Empty", style = CodeTreeTheme.typography.headline, color = CodeTreeTheme.colors.textPrimary)
        CTEmptyState(
            title = "还没有内容",
            description = "安装内置知识包后即可开始学习。",
            actionLabel = "去知识库",
            onAction = {},
        )
        Spacer(Modifier.height(spacing.lg))

        Text("Error", style = CodeTreeTheme.typography.headline, color = CodeTreeTheme.colors.textPrimary)
        CTErrorState(
            error = AppError.Internal("内容索引损坏"),
            onRetry = {},
            onSettings = {},
        )
        Spacer(Modifier.height(spacing.lg))

        Text("Offline", style = CodeTreeTheme.typography.headline, color = CodeTreeTheme.colors.textPrimary)
        CTOfflineBanner()
        Spacer(Modifier.height(spacing.lg))

        Text("Bilingual", style = CodeTreeTheme.typography.headline, color = CodeTreeTheme.colors.textPrimary)
        CTBilingualBlock(
            mode = BilingualMode.SIDE_BY_SIDE,
            zh = "val 声明只读引用，初始化后不能重新赋值。",
            en = "val declares a read-only reference that cannot be reassigned after initialization.",
            englishLabel = "EN Grammar",
        )
        Spacer(Modifier.height(spacing.lg))

        Text("Legend", style = CodeTreeTheme.typography.headline, color = CodeTreeTheme.colors.textPrimary)
        CTRelationLegend(
            prerequisiteLabel = "前置",
            relatedLabel = "相关",
            appliedInLabel = "应用",
            confusedWithLabel = "易混淆",
        )
        Spacer(Modifier.height(spacing.lg))

        Text("Code", style = CodeTreeTheme.typography.headline, color = CodeTreeTheme.colors.textPrimary)
        CTCodeBlock(
            code = "fun main() {\n    println(\"CodeTree\")\n}",
            language = "kotlin",
        )
    }
}

@Preview(name = "五态-浅色", widthDp = 390, heightDp = 1400, showBackground = true)
@Composable
private fun StatesPreviewLight() {
    CodeTreeTheme(darkTheme = false) { StatesShowcase() }
}

@Preview(name = "五态-深色", widthDp = 390, heightDp = 1400, showBackground = true)
@Composable
private fun StatesPreviewDark() {
    CodeTreeTheme(darkTheme = true) { StatesShowcase() }
}

@Preview(name = "五态-减少动效", widthDp = 390, heightDp = 1400, showBackground = true)
@Composable
private fun StatesPreviewReduceMotion() {
    CodeTreeTheme(darkTheme = false, reduceMotion = true) { StatesShowcase() }
}

@Preview(name = "组件总览", widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun ComponentsPreview() {
    CodeTreeTheme(darkTheme = false) {
        val spacing = CodeTreeTheme.spacing
        Column(Modifier.padding(spacing.pageHorizontal)) {
            CTSegmentedControl(
                options = listOf("中文解释", "英文语法", "双语对照"),
                selectedIndex = 0,
                onSelect = {},
            )
            Spacer(Modifier.height(spacing.lg))
            CTButton(text = "开始学习", onClick = {})
            Spacer(Modifier.height(spacing.sm))
            CTButton(text = "查看说明", onClick = {}, style = CTButtonStyle.TINTED)
            Spacer(Modifier.height(spacing.sm))
            CTButton(text = "跳过", onClick = {}, style = CTButtonStyle.PLAIN)
            Spacer(Modifier.height(spacing.lg))
            CTCard {
                Text("卡片标题", style = CodeTreeTheme.typography.headline)
                CTListRow(title = "列表主文本", subtitle = "副文本说明", showChevron = true)
                CTDivider()
                CTSwitchRow(title = "减少动效", checked = true, onCheckedChange = {})
            }
            Spacer(Modifier.height(spacing.lg))
            CTProgressRing(progress = 0.62f, label = "62%")
            Spacer(Modifier.height(spacing.lg))
            CTPill(text = "Python", selected = true)
            CTTag(text = "已安装", tone = CTTagTone.SUCCESS)
        }
    }
}
