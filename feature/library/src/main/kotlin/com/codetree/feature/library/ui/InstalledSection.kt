package com.codetree.feature.library.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTListRow
import com.codetree.designsystem.CTSectionHeader
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.feature.library.model.LibraryUiState
import com.codetree.feature.library.util.formatBytes

/** 已安装包列表：启停开关、来源/版本、被依赖提示、卸载入口。 */
@Composable
internal fun InstalledSection(
    state: LibraryUiState,
    onSetEnabled: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    CTSectionHeader(
        title = "已安装",
        subtitle = "被其它包依赖时不能直接卸载",
    )
    if (state.installed.isEmpty()) {
        Text(
            text = "还没有安装内容包。可从下方目录下载，或用系统文件选择器导入 .codepack。",
            style = CodeTreeTheme.typography.callout,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = spacing.pageHorizontal, vertical = spacing.sm),
        )
        return
    }
    CTCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal),
        contentPadding = 0.dp,
    ) {
        state.installed.forEachIndexed { index, item ->
            CTListRow(
                title = item.title,
                subtitle = buildString {
                    append("v${item.pack.version} · ${item.sourceLabel} · ${formatBytes(item.pack.sizeBytes)}")
                    if (!item.pack.enabled) append(" · 已停用")
                    if (item.hasDependents) append("\n被依赖：${item.dependents.joinToString("、")}")
                },
                leadingIcon = CTIcons.Book,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = item.pack.enabled,
                            onCheckedChange = { enabled -> onSetEnabled(item.packId, enabled) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = colors.textTertiary.copy(alpha = 0.5f),
                            ),
                        )
                        CTIconButton(
                            icon = CTIcons.Trash,
                            contentDescription = "卸载 ${item.title}",
                            onClick = { onRemove(item.packId) },
                            tint = colors.error,
                        )
                    }
                },
            )
            if (index != state.installed.lastIndex) {
                CTDivider(inset = spacing.pageHorizontal)
            }
        }
    }
}
