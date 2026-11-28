package com.codetree.feature.terminal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTEmptyState
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTPill
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.designsystem.TerminalColors
import com.codetree.feature.terminal.analysis.AnsiColor
import com.codetree.feature.terminal.analysis.AnsiParser
import com.codetree.feature.terminal.analysis.ShellCommandCatalog
import com.codetree.feature.terminal.model.TerminalBlock
import com.codetree.feature.terminal.model.TerminalUiState
import com.codetree.runtime.api.CodeDiagnostic
import com.codetree.runtime.api.DiagnosticSeverity

/** 终端输出区：命令块（可折叠）+ ANSI 颜色 + 退出码摘要 + 真实诊断/资源限制 + 问 AI。 */
@Composable
internal fun TerminalOutput(
    state: TerminalUiState,
    listState: LazyListState,
    onToggleCollapse: (String) -> Unit,
    onUseCommand: (String) -> Unit,
    onAskAi: (TerminalBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminal = CodeTreeTheme.terminal
    if (state.blocks.isEmpty()) {
        TerminalWelcome(onUseCommand = onUseCommand, modifier = modifier)
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        items(state.blocks, key = { it.id }) { block ->
            TerminalBlockView(
                block = block,
                onToggleCollapse = { onToggleCollapse(block.id) },
                onUseCommand = onUseCommand,
                onAskAi = { onAskAi(block) },
            )
            CTDivider(color = terminal.text.copy(alpha = 0.08f))
        }
    }
}

@Composable
private fun TerminalWelcome(
    onUseCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminal = CodeTreeTheme.terminal
    val spacing = CodeTreeTheme.spacing
    val quickCommands = listOf("help", "pwd", "ls", "date")
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CTEmptyState(
            title = "安全终端",
            description = "受控学习沙箱：只支持内置白名单命令，文件只能写入应用私有工作区。不是完整 Linux。",
            icon = CTIcons.Terminal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            quickCommands.forEach { command ->
                CTPill(text = command, onClick = { onUseCommand(command) })
            }
        }
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = ShellCommandCatalog.HELP_FOOTER,
            style = CodeTreeTheme.typography.caption,
            color = terminal.text.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = spacing.md),
        )
    }
}

@Composable
private fun TerminalBlockView(
    block: TerminalBlock,
    onToggleCollapse: () -> Unit,
    onUseCommand: (String) -> Unit,
    onAskAi: () -> Unit,
) {
    val terminal = CodeTreeTheme.terminal
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleCollapse)
                .padding(horizontal = spacing.md, vertical = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "❯", style = typography.mono, color = terminal.prompt)
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = block.command,
                style = typography.mono,
                color = terminal.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = summaryLabel(block),
                style = typography.caption,
                color = summaryColor(block),
            )
            Spacer(Modifier.width(spacing.xs))
            Icon(
                imageVector = if (block.collapsed) CTIcons.ChevronDown else CTIcons.ChevronUp,
                contentDescription = if (block.collapsed) "展开命令块" else "折叠命令块",
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }

        if (!block.collapsed) {
            if (block.stdout.isNotBlank()) {
                OutputText(block.stdout, Modifier.padding(horizontal = spacing.md, vertical = spacing.xxs))
            }
            if (block.stderr.isNotBlank()) {
                Text(
                    text = block.stderr,
                    style = typography.mono,
                    color = terminal.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.xxs),
                )
            }
            if (block.limits.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    block.limits.forEach { kind ->
                        CTTag(text = limitLabel(kind), tone = CTTagTone.WARNING)
                    }
                }
            }
            if (block.diagnostics.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xxs)) {
                    block.diagnostics.take(MAX_VISIBLE_DIAGNOSTICS).forEach { diagnostic ->
                        DiagnosticBrief(diagnostic)
                    }
                    if (block.diagnostics.size > MAX_VISIBLE_DIAGNOSTICS) {
                        Text(
                            text = "…还有 ${block.diagnostics.size - MAX_VISIBLE_DIAGNOSTICS} 条诊断",
                            style = typography.caption,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(top = spacing.xxs),
                        )
                    }
                }
            }
            if (block.errorHint != null) {
                Text(
                    text = block.errorHint,
                    style = typography.callout,
                    color = terminal.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.xs),
                )
            }
            if (block.hasFailure) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "退出码 ${block.exitCode ?: "—"}",
                        style = typography.caption,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.weight(1f))
                    CTTextButton(text = "问 AI：解释这条报错", onClick = onAskAi)
                }
            }
            if (block.command.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md),
                ) {
                    CTTextButton(
                        text = "再次填入",
                        onClick = { onUseCommand(block.command) },
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticBrief(diagnostic: CodeDiagnostic) {
    val terminal = CodeTreeTheme.terminal
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val color = when (diagnostic.severity) {
        DiagnosticSeverity.ERROR -> terminal.error
        DiagnosticSeverity.WARNING -> colors.warning
        DiagnosticSeverity.INFO -> colors.accent
    }
    Text(
        text = "第 ${diagnostic.line} 行 · ${diagnostic.message}",
        style = typography.caption,
        color = color,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

@Composable
private fun OutputText(text: String, modifier: Modifier = Modifier) {
    val terminal = CodeTreeTheme.terminal
    val annotated = remember(text, terminal) { buildAnsiText(text, terminal) }
    SelectionContainer {
        Text(
            text = annotated,
            style = CodeTreeTheme.typography.mono,
            color = terminal.text,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

private fun buildAnsiText(text: String, terminal: TerminalColors): AnnotatedString {
    val builder = AnnotatedString.Builder()
    AnsiParser.parse(text).forEach { segment ->
        val start = builder.length
        builder.append(segment.text)
        if (segment.color != AnsiColor.DEFAULT || segment.bold || segment.dim) {
            builder.addStyle(
                SpanStyle(
                    color = if (segment.dim) terminal.comment else ansiColor(segment.color, terminal),
                    fontWeight = if (segment.bold) FontWeight.Bold else null,
                ),
                start,
                builder.length,
            )
        }
    }
    return builder.toAnnotatedString()
}

private fun ansiColor(color: AnsiColor, terminal: TerminalColors): Color = when (color) {
    AnsiColor.DEFAULT -> terminal.text
    AnsiColor.BLACK -> terminal.comment
    AnsiColor.RED, AnsiColor.BRIGHT_RED -> terminal.error
    AnsiColor.GREEN, AnsiColor.BRIGHT_GREEN -> terminal.builtin
    AnsiColor.YELLOW, AnsiColor.BRIGHT_YELLOW -> terminal.string
    AnsiColor.BLUE, AnsiColor.BRIGHT_BLUE -> terminal.function
    AnsiColor.MAGENTA, AnsiColor.BRIGHT_MAGENTA -> terminal.number
    AnsiColor.CYAN, AnsiColor.BRIGHT_CYAN -> terminal.keyword
    AnsiColor.WHITE, AnsiColor.BRIGHT_WHITE -> terminal.text
}

@Composable
private fun summaryColor(block: TerminalBlock): Color {
    val terminal = CodeTreeTheme.terminal
    val colors = CodeTreeTheme.colors
    return when {
        !block.finished -> colors.warning
        block.cancelled -> colors.textTertiary
        block.errorHint != null && block.exitCode == null -> terminal.error
        (block.exitCode ?: 0) == 0 -> terminal.prompt
        else -> terminal.error
    }
}

private fun summaryLabel(block: TerminalBlock): String {
    if (!block.finished) return if (block.cancelRequested) "停止中…" else "运行中…"
    if (block.cancelled) return "⏹ 已停止"
    val exitCode = block.exitCode
    val base = when {
        block.errorHint != null && exitCode == null -> "✗ 未执行"
        exitCode == 0 -> "✓ 完成"
        exitCode != null -> "✗ 退出码 $exitCode"
        else -> "✗ 异常结束"
    }
    val duration = block.durationMs?.let { " · ${it}ms" }.orEmpty()
    return base + duration
}

private fun limitLabel(kind: String): String = when (kind) {
    "output" -> "输出超限（已截断）"
    "duration" -> "执行超时（已终止）"
    else -> "资源限制：$kind"
}

private const val MAX_VISIBLE_DIAGNOSTICS = 3
