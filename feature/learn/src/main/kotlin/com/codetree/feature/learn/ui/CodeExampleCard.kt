package com.codetree.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codetree.domain.model.ExampleAnnotation
import com.codetree.domain.model.ExampleVerification
import com.codetree.domain.model.SyntaxExample
import com.codetree.feature.learn.LearnStrings
import com.codetree.feature.learn.zh

private data class CodeLine(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val annotationIndices: List<Int>,
)

/**
 * 双语代码示例卡：语言标签 + 仅阅读/可运行标识 + 代码块 + annotation 点按显示中英注释。
 * annotations 按字符区间定位（docs/specs/knowledge.md §5.4），不按行号绑定。
 */
@Composable
fun CodeExampleCard(
    example: SyntaxExample,
    modifier: Modifier = Modifier,
) {
    val palette = learnPalette()
    val type = learnType()
    val spacing = learnSpacing()
    val lines = androidx.compose.runtime.remember(example.code, example.annotations) {
        buildCodeLines(example.code, example.annotations)
    }
    var selectedAnnotation by rememberSaveable(example.id) { mutableStateOf<Int?>(null) }

    LearnCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = example.title.zh().ifBlank { example.id },
                style = type.headline,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            LearnBadge(text = example.language, tone = LearnTone.ACCENT)
            Spacer(modifier = Modifier.width(spacing.xs))
            LearnBadge(
                text = if (example.isRunnable) LearnStrings.DETAIL_CODECARD_RUNNABLE else LearnStrings.DETAIL_CODECARD_READONLY,
                tone = if (example.isRunnable) LearnTone.SUCCESS else LearnTone.NEUTRAL,
            )
        }
        val explanation = example.explanation.zh()
        if (explanation.isNotBlank()) {
            Spacer(modifier = Modifier.height(spacing.s))
            Text(text = explanation, style = type.subhead, color = palette.textSecondary)
        }
        Spacer(modifier = Modifier.height(spacing.m))
        CodeBlock(
            lines = lines,
            selectedAnnotation = selectedAnnotation,
            onLineClick = { annotationIndex ->
                selectedAnnotation = if (selectedAnnotation == annotationIndex) null else annotationIndex
            },
        )
        if (example.annotations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(text = LearnStrings.ANNOTATION_HINT, style = type.caption, color = palette.textTertiary)
        }
        val current = selectedAnnotation
        if (current != null && current in example.annotations.indices) {
            Spacer(modifier = Modifier.height(spacing.s))
            AnnotationNote(annotation = example.annotations[current])
        }
        val output = example.expectedOutput
        if (!output.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(spacing.s))
            Text(text = "预期输出", style = type.footnote, color = palette.textSecondary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceElevated, RoundedCornerShape(learnShapes().small))
                    .padding(spacing.s),
            ) {
                Text(text = output, style = type.mono, color = palette.textPrimary)
            }
        }
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = "校验状态：" + verificationLabel(example.verificationStatus) +
                " · 语言版本：" + example.languageVersion.ifBlank { "未标注" },
            style = type.caption,
            color = palette.textTertiary,
        )
    }
}

@Composable
private fun CodeBlock(
    lines: List<CodeLine>,
    selectedAnnotation: Int?,
    onLineClick: (Int) -> Unit,
) {
    val palette = learnPalette()
    val type = learnType()
    val spacing = learnSpacing()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surfaceElevated, RoundedCornerShape(learnShapes().small))
            .horizontalScroll(rememberScrollState())
            .padding(vertical = spacing.s),
    ) {
        Column {
            lines.forEach { line ->
                val linkedAnnotation = line.annotationIndices.firstOrNull()
                val highlighted = when {
                    selectedAnnotation != null && linkedAnnotation == selectedAnnotation -> true
                    selectedAnnotation == null && linkedAnnotation != null -> true
                    else -> false
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (highlighted) palette.accentSoft else Color.Transparent)
                        .let {
                            if (linkedAnnotation != null) {
                                it.clickable { onLineClick(linkedAnnotation) }
                            } else {
                                it
                            }
                        }
                        .padding(horizontal = spacing.s, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (line.index + 1).toString().padStart(2, ' '),
                        style = type.mono,
                        color = palette.textTertiary,
                    )
                    Spacer(modifier = Modifier.width(spacing.s))
                    Text(text = line.text.ifEmpty { " " }, style = type.mono, color = palette.textPrimary)
                    if (linkedAnnotation != null) {
                        Spacer(modifier = Modifier.width(spacing.xs))
                        Text(text = "●", style = type.caption, color = palette.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationNote(annotation: ExampleAnnotation) {
    val palette = learnPalette()
    val type = learnType()
    val spacing = learnSpacing()
    val zh = annotation.note.zh()
    val en = annotation.note.get("en")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.accentSoft, RoundedCornerShape(learnShapes().small))
            .padding(spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        if (zh.isNotBlank()) {
            Text(text = "注释（中文）：$zh", style = type.footnote, color = palette.textPrimary)
        }
        if (en.isNotBlank()) {
            Text(text = "Note (EN): $en", style = type.footnote, color = palette.textSecondary)
        }
        if (zh.isBlank() && en.isBlank()) {
            Text(text = LearnStrings.ANNOTATION_EMPTY, style = type.footnote, color = palette.textSecondary)
        }
    }
}

private fun buildCodeLines(code: String, annotations: List<ExampleAnnotation>): List<CodeLine> {
    val lines = mutableListOf<CodeLine>()
    var offset = 0
    val rawLines = code.split('\n')
    rawLines.forEachIndexed { index, raw ->
        val lineStart = offset
        val lineEnd = offset + raw.length
        val linked = annotations.indices.filter { annotationIndex ->
            val annotation = annotations[annotationIndex]
            annotation.start <= lineEnd && annotation.end >= lineStart && annotation.start != annotation.end
        }
        lines.add(CodeLine(index, raw, lineStart, lineEnd, linked))
        offset = lineEnd + 1
    }
    return lines
}

private fun verificationLabel(status: ExampleVerification): String = when (status) {
    ExampleVerification.UNVERIFIED -> "未校验"
    ExampleVerification.STATIC_CHECKED -> "静态检查通过"
    ExampleVerification.EXECUTION_VERIFIED -> "执行验证通过"
}
