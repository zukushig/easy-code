package com.codetree.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTButtonStyle
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.designsystem.TerminalColors
import com.codetree.feature.terminal.analysis.BracketPair
import com.codetree.feature.terminal.model.TerminalUiState
import com.codetree.runtime.api.CodeDiagnostic
import com.codetree.runtime.api.TokenSpan

/**
 * 终端输入区：BasicTextField + 自定义 VisualTransformation（语法高亮 / 括号匹配 / 诊断下划线）。
 * 补全与历史入口在键盘上方，不遮挡输入。
 */
@Composable
internal fun TerminalInput(
    state: TerminalUiState,
    onInputChange: (String, Int) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onHistoryPrevious: () -> Unit,
    onHistoryNext: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminal = CodeTreeTheme.terminal
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography

    val transformation = remember(state.tokens, state.diagnostics, state.bracketPair, terminal) {
        TerminalInputTransformation(
            tokens = state.tokens,
            diagnostics = state.diagnostics,
            bracketPair = state.bracketPair,
            terminal = terminal,
        )
    }
    val textFieldValue = TextFieldValue(
        text = state.input,
        selection = TextRange(state.cursor.coerceIn(0, state.input.length)),
    )

    Column(
        modifier = modifier
            .background(terminal.bg)
            .imePadding()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CTTextButton(
                text = "↑ 上条",
                onClick = onHistoryPrevious,
                enabled = state.historyCount > 0,
                color = colors.textSecondary,
            )
            CTTextButton(
                text = "↓ 下条",
                onClick = onHistoryNext,
                enabled = state.historyCount > 0,
                color = colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            if (state.diagnosticsCount > 0) {
                CTTextButton(
                    text = "查看诊断 ${state.diagnosticsCount}",
                    onClick = onOpenDiagnostics,
                    color = if (state.errorCount > 0) terminal.error else colors.warning,
                )
            }
            CTTextButton(
                text = "历史",
                onClick = onOpenHistory,
                color = colors.textSecondary,
            )
            CTIconButton(
                icon = CTIcons.Settings,
                contentDescription = "终端设置",
                onClick = onOpenSettings,
                tint = colors.textSecondary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "❯", style = typography.mono, color = terminal.prompt)
            Spacer(Modifier.width(spacing.sm))
            BasicTextField(
                value = textFieldValue,
                onValueChange = { value -> onInputChange(value.text, value.selection.start) },
                modifier = Modifier.weight(1f),
                textStyle = typography.mono.copy(color = terminal.text),
                cursorBrush = SolidColor(terminal.keyword),
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                maxLines = 6,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceElevated, shapes.roundedSmall)
                            .border(1.dp, colors.separator, shapes.roundedSmall)
                            .padding(spacing.sm),
                    ) {
                        if (state.input.isEmpty()) {
                            Text(
                                text = "输入命令，例如 help",
                                style = typography.mono,
                                color = colors.textTertiary,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        Spacer(Modifier.height(spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    state.running -> "执行中：可停止，停止后不再追加输出"
                    !state.runtimeReady -> "当前运行时未安装，不能执行"
                    else -> "Enter 发送 · 上下键取历史"
                },
                style = typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            if (state.running) {
                CTButton(
                    text = "停止",
                    onClick = onStop,
                    style = CTButtonStyle.TINTED,
                    leadingIcon = CTIcons.Stop,
                )
            } else {
                CTButton(
                    text = "运行",
                    onClick = onSubmit,
                    enabled = state.input.isNotBlank() && state.runtimeReady,
                    leadingIcon = CTIcons.Play,
                )
            }
        }
    }
}

/** 把高亮 / 括号匹配 / 诊断下划线合并到同一 AnnotatedString；偏移映射恒等，不改变文本。 */
private class TerminalInputTransformation(
    private val tokens: List<TokenSpan>,
    private val diagnostics: List<CodeDiagnostic>,
    private val bracketPair: BracketPair?,
    private val terminal: TerminalColors,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val builder = AnnotatedString.Builder(text)
        tokens.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (end > start) {
                builder.addStyle(SpanStyle(color = terminal.colorFor(span.kind.name)), start, end)
            }
        }
        bracketPair?.let { pair ->
            val style = SpanStyle(background = terminal.selection)
            addSpan(builder, text.length, pair.openStart, pair.openEnd, style)
            addSpan(builder, text.length, pair.closeStart, pair.closeEnd, style)
        }
        diagnostics.forEach { diagnostic ->
            val range = diagnosticRange(text.text, diagnostic) ?: return@forEach
            builder.addStyle(
                SpanStyle(
                    background = terminal.error.copy(alpha = 0.16f),
                    textDecoration = TextDecoration.Underline,
                ),
                range.first,
                range.second,
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun addSpan(
        builder: AnnotatedString.Builder,
        textLength: Int,
        start: Int,
        end: Int,
        style: SpanStyle,
    ) {
        val safeStart = start.coerceIn(0, textLength)
        val safeEnd = end.coerceIn(safeStart, textLength)
        if (safeEnd > safeStart) builder.addStyle(style, safeStart, safeEnd)
    }

    private fun diagnosticRange(text: String, diagnostic: CodeDiagnostic): Pair<Int, Int>? {
        val lineStart = lineStartOffset(text, diagnostic.line) ?: return null
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val start = (lineStart + diagnostic.column).coerceIn(lineStart, lineEnd)
        val end = if (diagnostic.endColumn > diagnostic.column) {
            (lineStart + diagnostic.endColumn).coerceIn(start, lineEnd)
        } else {
            (start + 1).coerceAtMost(lineEnd)
        }
        return if (end > start) start to end else null
    }

    private fun lineStartOffset(text: String, line: Int): Int? {
        if (line <= 1) return 0
        var currentLine = 1
        var index = 0
        while (index < text.length) {
            if (text[index] == '\n') {
                currentLine += 1
                if (currentLine == line) return index + 1
            }
            index += 1
        }
        return null
    }
}
