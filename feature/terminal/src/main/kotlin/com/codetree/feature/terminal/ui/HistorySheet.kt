package com.codetree.feature.terminal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTListRow
import com.codetree.designsystem.CTSheetHeader
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CodeTreeTheme

/** 命令历史面板：仅本次会话内存保存（不落盘），可回填到输入框。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistorySheet(
    history: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
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
        Column(modifier = Modifier.fillMaxWidth()) {
            CTSheetHeader(
                title = "命令历史 ${history.size} 条",
                subtitle = "本次会话内保存（仅内存，不落盘）",
                onClose = onDismiss,
            )
            CTDivider()
            if (history.isEmpty()) {
                Text(
                    text = "还没有执行过命令。",
                    style = CodeTreeTheme.typography.callout,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(spacing.lg),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(history.asReversed()) { entry ->
                        CTListRow(
                            title = entry,
                            onClick = { onSelect(entry) },
                            showChevron = true,
                        )
                        CTDivider(inset = spacing.pageHorizontal)
                    }
                }
            }
            Spacer(Modifier.height(spacing.sm))
            CTTextButton(
                text = "清空历史",
                onClick = onClear,
                color = colors.error,
                modifier = Modifier.padding(horizontal = spacing.md),
            )
            Spacer(Modifier.height(spacing.xl))
        }
    }
}
