package com.codetree.feature.assistant.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTButtonStyle
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTListRow
import com.codetree.designsystem.CTSheetHeader
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.feature.assistant.model.SessionSummaryUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 会话历史：打开 / 新建 / 删除单会话（删除需确认，不可恢复）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionsSheet(
    sessions: List<SessionSummaryUi>,
    currentSessionId: String?,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    var pendingDelete by remember { mutableStateOf<SessionSummaryUi?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CTSheetHeader(
                title = "会话历史",
                subtitle = "保存在本机 sessions 目录（JSON，不含密钥）",
                onClose = onDismiss,
            )
            CTDivider()
            if (sessions.isEmpty()) {
                Text(
                    text = "还没有保存的会话。发送第一条消息后会自动保存。",
                    style = CodeTreeTheme.typography.callout,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(spacing.lg),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        CTListRow(
                            title = session.title,
                            subtitle = "${session.messageCount} 条消息 · ${formatTime(session.updatedAtEpochMs)}" +
                                if (session.id == currentSessionId) " · 当前" else "",
                            onClick = { onOpen(session.id) },
                            trailing = {
                                CTIconButton(
                                    icon = CTIcons.Trash,
                                    contentDescription = "删除会话 ${session.title}",
                                    onClick = { pendingDelete = session },
                                    tint = colors.error,
                                )
                            },
                        )
                        CTDivider(inset = spacing.pageHorizontal)
                    }
                }
            }
            Spacer(Modifier.height(spacing.sm))
            CTButton(
                text = "新建会话",
                onClick = onNewChat,
                style = CTButtonStyle.TINTED,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.pageHorizontal),
            )
            Spacer(Modifier.height(spacing.xxl))
        }
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除会话？") },
            text = { Text("「${session.title}」删除后不可恢复，相关消息会从本机移除。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(session.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
}
