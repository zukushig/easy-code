package com.codetree.feature.terminal.ui

import androidx.compose.foundation.layout.Column
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
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTSegmentedControl
import com.codetree.designsystem.CTSectionHeader
import com.codetree.designsystem.CTSheetHeader
import com.codetree.designsystem.CTValueRow
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.feature.terminal.analysis.ShellCommandCatalog
import com.codetree.feature.terminal.model.TerminalUiState
import com.codetree.runtime.api.ResourceLimits

/** 终端设置：高亮语言（会话内）、资源上限说明、帮助文案。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalSettingsSheet(
    state: TerminalUiState,
    onSelectLanguage: (String) -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography
    val limits = ResourceLimits()
    val selectedIndex = state.syntaxLanguages.indexOfFirst { it.id == state.highlightLanguage }.coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            CTSheetHeader(
                title = "终端设置",
                subtitle = "仅本会话生效；全局设置由 app 设置页管理",
                onClose = onClose,
            )
            CTSectionHeader(title = "输入高亮语言")
            CTSegmentedControl(
                options = state.syntaxLanguages.map { it.label },
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    state.syntaxLanguages.getOrNull(index)?.let { onSelectLanguage(it.id) }
                },
                modifier = Modifier.padding(horizontal = spacing.pageHorizontal),
            )
            Text(
                text = "安全 Shell 使用内置命令高亮；Python/JavaScript 的语法来自内容包，运行时未安装时仍可阅读高亮。",
                style = typography.footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = spacing.pageHorizontal, vertical = spacing.sm),
            )
            CTDivider()
            CTSectionHeader(title = "资源与限制")
            CTValueRow(title = "执行时长上限", value = "${limits.maxDurationMs} ms")
            CTValueRow(title = "输出上限", value = "${limits.maxOutputBytes / 1024} KiB")
            CTValueRow(title = "单文件上限", value = "${limits.maxFileBytes / 1024} KiB")
            CTValueRow(title = "当前工作目录", value = state.cwd)
            CTDivider()
            CTSectionHeader(title = "内置命令帮助")
            Text(
                text = ShellCommandCatalog.helpText(),
                style = typography.monoSmall,
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.pageHorizontal, vertical = spacing.sm),
            )
            Spacer(Modifier.height(spacing.xxl))
        }
    }
}
