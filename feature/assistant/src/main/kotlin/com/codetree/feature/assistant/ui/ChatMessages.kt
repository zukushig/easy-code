package com.codetree.feature.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.feature.assistant.model.ChatMessage
import com.codetree.feature.assistant.model.ChatRole

/** 对话消息列表：区分用户 / AI；AI 消息固定标注「AI 建议，未验证」。 */
@Composable
internal fun ChatMessages(
    messages: List<ChatMessage>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CodeTreeTheme.spacing
    val listState = rememberLazyListState()
    val last = messages.lastOrNull()
    LaunchedEffect(messages.size, last?.content?.length, last?.streaming, last?.errorCode) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = spacing.sm),
    ) {
        items(messages, key = { it.id }) { message ->
            ChatMessageItem(message = message, onRetry = onRetry)
        }
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage, onRetry: () -> Unit) {
    if (message.role == ChatRole.USER) {
        UserMessageItem(message)
    } else {
        AssistantMessageItem(message = message, onRetry = onRetry)
    }
}

@Composable
private fun UserMessageItem(message: ChatMessage) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal, vertical = spacing.xs),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(colors.accentSoft, shapes.roundedLarge)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Text(text = message.content, style = typography.body, color = colors.textPrimary)
            }
            if (message.contextItems.isNotEmpty()) {
                Text(
                    text = "附加上下文：${message.contextItems.joinToString("、") { it.kind }}",
                    style = typography.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageItem(message: ChatMessage, onRetry: () -> Unit) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal, vertical = spacing.xs),
        horizontalAlignment = Alignment.Start,
    ) {
        CTTag(text = "AI 建议，未验证", tone = CTTagTone.WARNING)
        Box(
            modifier = Modifier
                .padding(top = spacing.xxs)
                .widthIn(max = 340.dp)
                .background(colors.surface, shapes.roundedLarge)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            if (message.content.isBlank() && message.streaming) {
                Text(text = "正在生成…▍", style = typography.body, color = colors.textSecondary)
            } else {
                Text(
                    text = streamingText(message),
                    style = typography.body,
                    color = colors.textPrimary,
                )
            }
        }
        if (message.errorCode != null || message.isError) {
            Text(
                text = message.errorHint ?: "请求失败（${message.errorCode ?: "unknown"}）",
                style = typography.callout,
                color = colors.error,
                modifier = Modifier.padding(top = spacing.xs),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CTTextButton(text = "重试上一条请求", onClick = onRetry, color = colors.accent)
                Text(
                    text = "会重新发送你已确认过的上下文",
                    style = typography.caption,
                    color = colors.textTertiary,
                )
            }
        }
        if (message.cancelled) {
            Text(
                text = "已停止生成，已生成内容保留",
                style = typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        if (message.truncated) {
            Text(
                text = "回答过长已截断（上限 64KiB）",
                style = typography.caption,
                color = colors.warning,
            )
        }
        if (message.promptTokens != null || message.completionTokens != null) {
            Text(
                text = "tokens：输入 ${message.promptTokens ?: "-"} / 输出 ${message.completionTokens ?: "-"}",
                style = typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        Text(
            text = "请到终端或测试中独立验证；不要直接执行 AI 给出的命令。",
            style = typography.caption,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    }
}

private fun streamingText(message: ChatMessage): AnnotatedString {
    val base = message.content
    if (!message.streaming) return AnnotatedString(base)
    return AnnotatedString.Builder(base).apply {
        addStyle(SpanStyle(), base.length, base.length)
        append("▍")
    }.toAnnotatedString()
}
