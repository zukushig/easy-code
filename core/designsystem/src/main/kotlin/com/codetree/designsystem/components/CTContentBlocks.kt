package com.codetree.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codetree.domain.content.BilingualMode
import com.codetree.domain.model.EdgeType

/**
 * 双语展示组件（docs/specs/design-system.md §6 `TermSheet` 相关）：
 * 三种模式 —— 中文解释 / 英文语法 / 双语对照，与 `BilingualMode` 枚举一一对应。
 *
 * 文案由调用方传入（内容来自 `LocalizedText.get(locale)`）；缺失时用 `missingLabel`
 * 明确说明“暂无内容”，不留空白区块。
 */
@Composable
fun CTBilingualBlock(
    mode: BilingualMode,
    zh: String?,
    en: String?,
    modifier: Modifier = Modifier,
    englishLabel: String = "EN Grammar",
    chineseLabel: String? = null,
    missingLabel: String = "—",
) {
    when (mode) {
        BilingualMode.ZH_ONLY -> {
            ZoomedParagraph(text = zh.orEmpty(), modifier = modifier, missingLabel = missingLabel)
        }

        BilingualMode.EN_GRAMMAR -> {
            Column(modifier = modifier) {
                Text(
                    text = englishLabel,
                    style = CodeTreeTheme.typography.caption,
                    color = CodeTreeTheme.colors.accent,
                )
                Spacer(Modifier.height(CodeTreeTheme.spacing.xs))
                ZoomedParagraph(text = en.orEmpty(), modifier = Modifier, missingLabel = missingLabel)
            }
        }

        BilingualMode.SIDE_BY_SIDE -> {
            Column(modifier = modifier) {
                if (!chineseLabel.isNullOrBlank()) {
                    Text(
                        text = chineseLabel,
                        style = CodeTreeTheme.typography.caption,
                        color = CodeTreeTheme.colors.textTertiary,
                    )
                    Spacer(Modifier.height(CodeTreeTheme.spacing.xs))
                }
                ZoomedParagraph(text = zh.orEmpty(), modifier = Modifier, missingLabel = missingLabel)
                Spacer(Modifier.height(CodeTreeTheme.spacing.md))
                CTDivider()
                Spacer(Modifier.height(CodeTreeTheme.spacing.md))
                Text(
                    text = englishLabel,
                    style = CodeTreeTheme.typography.caption,
                    color = CodeTreeTheme.colors.accent,
                )
                Spacer(Modifier.height(CodeTreeTheme.spacing.xs))
                ZoomedParagraph(text = en.orEmpty(), modifier = Modifier, missingLabel = missingLabel)
            }
        }
    }
}

@Composable
private fun ZoomedParagraph(text: String, modifier: Modifier, missingLabel: String) {
    if (text.isBlank()) {
        Text(
            text = missingLabel,
            style = CodeTreeTheme.typography.body,
            color = CodeTreeTheme.colors.textTertiary,
            modifier = modifier,
        )
    } else {
        Text(
            text = text,
            style = CodeTreeTheme.typography.body,
            color = CodeTreeTheme.colors.textPrimary,
            modifier = modifier,
        )
    }
}

/** spec §6 名称别名。 */
@Composable
fun BilingualBlock(
    mode: BilingualMode,
    zh: String?,
    en: String?,
    modifier: Modifier = Modifier,
    englishLabel: String = "EN Grammar",
    chineseLabel: String? = null,
) = CTBilingualBlock(mode = mode, zh = zh, en = en, modifier = modifier, englishLabel = englishLabel, chineseLabel = chineseLabel)

/**
 * 代码块：mono 字体 + 语言标签 + 圆角容器 + 可选中复制；长行横向滚动，
 * 不换行（终端/代码保持一致语义）。示例来源为 `SyntaxExample.code`。
 */
@Composable
fun CTCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String? = null,
    title: String? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    val terminal = CodeTreeTheme.terminal

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(terminal.bg, shapes.roundedMedium)
            .padding(spacing.md),
    ) {
        if (title != null || language != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (language != null) {
                    Text(
                        text = language,
                        style = CodeTreeTheme.typography.monoSmall,
                        color = terminal.keyword,
                        modifier = Modifier
                            .background(terminal.selection, shapes.roundedSmall)
                            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
                    )
                }
                if (title != null) {
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        text = title,
                        style = CodeTreeTheme.typography.footnote,
                        color = terminal.text.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(spacing.sm))
        }
        SelectionContainer {
            Text(
                text = code,
                style = CodeTreeTheme.typography.mono,
                color = terminal.text,
                maxLines = maxLines,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

/** spec §6 名称别名。 */
@Composable
fun CodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String? = null,
    title: String? = null,
) = CTCodeBlock(code = code, modifier = modifier, language = language, title = title)

/**
 * 知识图四类边图例（docs/specs/design-system.md §6 `RelationLegend`）：
 * 实线 = 前置、虚线 = 相关、点线 = 应用、双线 = 易混淆；线型 + 文字双编码，不只靠颜色。
 */
@Composable
fun CTRelationLegend(
    prerequisiteLabel: String,
    relatedLabel: String,
    appliedInLabel: String,
    confusedWithLabel: String,
    modifier: Modifier = Modifier,
    lineColor: Color = CodeTreeTheme.colors.textSecondary,
) {
    val spacing = CodeTreeTheme.spacing
    Column(modifier = modifier) {
        LegendItem(prerequisiteLabel, EdgeType.PREREQUISITE, lineColor)
        Spacer(Modifier.height(spacing.xs))
        LegendItem(relatedLabel, EdgeType.RELATED, lineColor)
        Spacer(Modifier.height(spacing.xs))
        LegendItem(appliedInLabel, EdgeType.APPLIED_IN, lineColor)
        Spacer(Modifier.height(spacing.xs))
        LegendItem(confusedWithLabel, EdgeType.CONFUSED_WITH, lineColor)
    }
}

@Composable
private fun LegendItem(label: String, type: EdgeType, color: Color) {
    val spacing = CodeTreeTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            modifier = Modifier
                .width(28.dp)
                .height(12.dp),
        ) {
            val y = size.height / 2f
            val stroke = 1.5.dp.toPx()
            when (type) {
                EdgeType.PREREQUISITE -> drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                )

                EdgeType.RELATED -> drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)),
                )

                EdgeType.APPLIED_IN -> drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)),
                )

                EdgeType.CONFUSED_WITH -> {
                    drawLine(
                        color = color,
                        start = Offset(0f, y - 2.5.dp.toPx()),
                        end = Offset(size.width, y - 2.5.dp.toPx()),
                        strokeWidth = stroke,
                    )
                    drawLine(
                        color = color,
                        start = Offset(0f, y + 2.5.dp.toPx()),
                        end = Offset(size.width, y + 2.5.dp.toPx()),
                        strokeWidth = stroke,
                    )
                }
            }
        }
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = label,
            style = CodeTreeTheme.typography.caption,
            color = CodeTreeTheme.colors.textSecondary,
        )
    }
}
