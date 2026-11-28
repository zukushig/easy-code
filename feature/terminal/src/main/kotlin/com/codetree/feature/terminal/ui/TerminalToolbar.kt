package com.codetree.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTPill
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.feature.terminal.model.TerminalUiState

/** 终端顶部工具栏：运行时选择器（含未安装状态）、清屏、历史、设置。 */
@Composable
internal fun TerminalToolbar(
    state: TerminalUiState,
    onSelectRuntime: (String) -> Unit,
    onClearScreen: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodeTreeTheme.colors
    val terminal = CodeTreeTheme.terminal
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography
    var expanded by remember { mutableStateOf(false) }
    val selected = state.selectedRuntime

    Column(modifier = modifier.background(terminal.bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                CTPill(
                    text = selected?.displayName ?: "选择运行时",
                    onClick = { expanded = true },
                    selected = false,
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = colors.surfaceElevated,
                ) {
                    state.runtimeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = option.displayName,
                                        style = typography.callout,
                                        color = if (option.available) colors.textPrimary else colors.textTertiary,
                                    )
                                    Text(
                                        text = if (option.available) "${option.kindLabel} · 可用" else "${option.kindLabel} · 未安装",
                                        style = typography.caption,
                                        color = if (option.available) colors.success else colors.textTertiary,
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelectRuntime(option.id)
                            },
                            enabled = option.available,
                        )
                    }
                }
            }
            if (selected != null && !selected.available) {
                Spacer(Modifier.width(spacing.sm))
                CTTag(text = "未安装", tone = CTTagTone.DANGER)
            }
            Spacer(Modifier.weight(1f))
            CTIconButton(
                icon = CTIcons.Refresh,
                contentDescription = "清屏",
                onClick = onClearScreen,
                tint = colors.textSecondary,
            )
            CTIconButton(
                icon = CTIcons.Bookmark,
                contentDescription = "命令历史",
                onClick = onOpenHistory,
                tint = colors.textSecondary,
            )
            CTIconButton(
                icon = CTIcons.Settings,
                contentDescription = "终端设置",
                onClick = onOpenSettings,
                tint = colors.textSecondary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "工作区 ${state.cwd}",
                style = typography.monoSmall,
                color = terminal.text.copy(alpha = 0.6f),
            )
            if (!state.runtimeReady) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "运行时未安装：可编辑与高亮，不能执行",
                    style = typography.caption,
                    color = colors.warning,
                )
            }
        }
        CTDivider()
    }
}
