package com.codetree.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTSheetHeader
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.runtime.api.CodeDiagnostic
import com.codetree.runtime.api.DiagnosticSeverity
import com.codetree.runtime.api.FixKind

/** 诊断面板：位置 / 原因 / 提示 / 修复预览；修复必须由用户点按才应用（不静默改代码）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticSheet(
    diagnostics: List<CodeDiagnostic>,
    onApplyFix: (CodeDiagnostic) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            CTSheetHeader(
                title = "输入诊断 ${diagnostics.size} 条",
                subtitle = "编辑期提示；真实执行错误以命令输出与退出码为准",
                onClose = onDismiss,
            )
            if (diagnostics.isEmpty()) {
                Text(
                    text = "当前输入没有诊断。",
                    style = CodeTreeTheme.typography.callout,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(spacing.lg),
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = spacing.pageHorizontal),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    diagnostics.forEach { diagnostic ->
                        DiagnosticCard(diagnostic = diagnostic, onApplyFix = { onApplyFix(diagnostic) })
                    }
                }
            }
            Spacer(Modifier.height(spacing.xxl))
        }
    }
}

@Composable
private fun DiagnosticCard(diagnostic: CodeDiagnostic, onApplyFix: () -> Unit) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography
    val severityColor = when (diagnostic.severity) {
        DiagnosticSeverity.ERROR -> colors.error
        DiagnosticSeverity.WARNING -> colors.warning
        DiagnosticSeverity.INFO -> colors.accent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated, shapes.roundedMedium)
            .padding(spacing.md),
    ) {
        Text(
            text = diagnostic.message,
            style = typography.callout,
            color = severityColor,
        )
        Text(
            text = "第 ${diagnostic.line} 行 · 第 ${diagnostic.column + 1} 列" +
                (diagnostic.ruleId?.let { " · $it" } ?: ""),
            style = typography.caption,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = spacing.xxs),
        )
        val hint = diagnostic.hint
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                style = typography.subhead,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
        val fix = diagnostic.fix
        if (fix != null && fix.kind != FixKind.NONE && !fix.text.isNullOrBlank()) {
            val preview = when (fix.kind) {
                FixKind.APPEND -> "修复预览：在本行末尾追加「${fix.text}」"
                FixKind.REPLACE -> "修复预览：把标记区间替换为「${fix.text}」"
                FixKind.NONE -> ""
            }
            Text(
                text = preview,
                style = typography.footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.xs),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                CTTextButton(text = "应用修复", onClick = onApplyFix, color = colors.accent)
            }
        }
    }
}
