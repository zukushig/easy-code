package com.codetree.feature.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.ai.AiContextItem
import com.codetree.feature.assistant.model.PendingContext

/**
 * 上下文确认卡：发送前展示将发送的每一项（类型 / 标签 / 字符数）与总字符数，
 * 可逐项移除或全部移除；不自动发送。
 */
@Composable
internal fun ContextConfirmCard(
    pending: PendingContext,
    labelFor: (AiContextItem) -> String,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal)
            .padding(bottom = spacing.sm),
    ) {
        CTCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "将发送以下上下文",
                    style = typography.headline,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                CTTag(text = "需确认", tone = CTTagTone.WARNING)
            }
            Text(
                text = "共 ${pending.items.size} 项 · ${pending.totalChars} 字符 · 来源：${pending.sourceLabel}",
                style = typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
            pending.items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = labelFor(item),
                            style = typography.callout,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "${kindOf(item)} · ${charsOf(item)} 字符",
                            style = typography.caption,
                            color = colors.textTertiary,
                        )
                    }
                    CTTextButton(
                        text = "移除",
                        onClick = { onRemove(index) },
                        color = colors.error,
                    )
                }
            }
            Text(
                text = "默认不附带文件全文、完整命令历史、其他会话或密钥；确认后才会发出请求。",
                style = typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = spacing.sm),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                CTTextButton(text = "全部移除", onClick = onClear, color = colors.textSecondary)
                Spacer(Modifier.weight(1f))
                CTButton(text = "确认发送", onClick = onConfirm)
            }
        }
    }
}

private fun kindOf(item: AiContextItem): String = when (item) {
    is AiContextItem.TopicRef -> "知识节点"
    is AiContextItem.TermRef -> "术语"
    is AiContextItem.CodeSnippet -> "代码 ${item.language.ifBlank { "文本" }}"
    is AiContextItem.TerminalError -> "终端报错"
}

private fun charsOf(item: AiContextItem): Int = when (item) {
    is AiContextItem.TopicRef -> item.topicId.length
    is AiContextItem.TermRef -> item.termId.length
    is AiContextItem.CodeSnippet -> item.code.length
    is AiContextItem.TerminalError -> item.command.length + item.stderr.length
}
