package com.codetree.feature.dictionary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTCodeBlock
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.model.ExampleAnnotation
import com.codetree.domain.model.SyntaxExample
import com.codetree.feature.dictionary.DictionaryStrings

/**
 * 词典示例卡：代码块（`CTCodeBlock`，mono 字体 + 语言标签 + 可复制）+ 注释标注。
 * annotations 按字符区间定位（knowledge.md §5.4），展示为「行号 + 中英说明」可展开条目；
 * 代码本身不做任何翻译（DIC-005）。
 */
@Composable
fun DictExampleCard(
    example: SyntaxExample,
    modifier: Modifier = Modifier,
) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    var selected by rememberSaveable(example.id) { mutableStateOf<Int?>(null) }

    CTCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = example.title.get("zh-CN").ifBlank { example.id },
                style = typography.headline,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            CTTag(text = example.language, tone = CTTagTone.ACCENT)
            Spacer(modifier = Modifier.width(spacing.xs))
            CTTag(
                text = if (example.isRunnable) DictionaryStrings.RUNNABLE else DictionaryStrings.READ_ONLY,
                tone = if (example.isRunnable) CTTagTone.SUCCESS else CTTagTone.NEUTRAL,
            )
        }
        val explanation = example.explanation.get("zh-CN")
        if (explanation.isNotBlank()) {
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(text = explanation, style = typography.subhead, color = colors.textSecondary)
        }
        Spacer(modifier = Modifier.height(spacing.md))
        CTCodeBlock(
            code = example.code,
            language = example.language,
            title = null,
        )
        if (example.annotations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = DictionaryStrings.ANNOTATION_HINT,
                style = typography.caption,
                color = colors.textTertiary,
            )
            example.annotations.forEachIndexed { index, annotation ->
                AnnotationRow(
                    annotation = annotation,
                    code = example.code,
                    expanded = selected == index,
                    onClick = { selected = if (selected == index) null else index },
                )
            }
        }
        val output = example.expectedOutput
        if (!output.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(text = DictionaryStrings.EXPECTED_OUTPUT, style = typography.footnote, color = colors.textSecondary)
            Text(
                text = output,
                style = typography.mono,
                color = colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.xxs),
            )
        }
    }
}

@Composable
private fun AnnotationRow(
    annotation: ExampleAnnotation,
    code: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    val startLine = code.take(annotation.start.coerceIn(0, code.length)).count { it == '\n' } + 1
    val endLine = code.take(annotation.end.coerceIn(0, code.length)).count { it == '\n' } + 1
    val rangeLabel = if (startLine == endLine) "第 $startLine 行" else "第 $startLine–$endLine 行"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$rangeLabel · ${DictionaryStrings.ANNOTATION_TITLE}",
                style = typography.footnote,
                color = colors.accent,
            )
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(text = if (expanded) "收起" else "展开", style = typography.caption, color = colors.textTertiary)
        }
        if (expanded) {
            val zh = annotation.note.get("zh-CN")
            val en = annotation.note.get("en")
            if (zh.isNotBlank()) {
                Text(text = zh, style = typography.footnote, color = colors.textPrimary)
            }
            if (en.isNotBlank()) {
                Text(text = en, style = typography.footnote, color = colors.textSecondary)
            }
            if (zh.isBlank() && en.isBlank()) {
                Text(
                    text = DictionaryStrings.ANNOTATION_EMPTY,
                    style = typography.footnote,
                    color = colors.textTertiary,
                )
            }
        }
    }
}
