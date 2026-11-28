package com.codetree.feature.assistant.model

import com.codetree.domain.ai.AiContextItem

enum class ChatRole { USER, ASSISTANT }

/** 上下文条目的 UI 摘要（发送前展示 + 会话记录展示，不含密钥）。 */
data class ContextSummary(
    val kind: String,
    val label: String,
    val chars: Int,
)

fun AiContextItem.toSummary(): ContextSummary = when (this) {
    is AiContextItem.TopicRef -> ContextSummary(kind = "知识节点", label = topicId, chars = topicId.length)
    is AiContextItem.TermRef -> ContextSummary(kind = "术语", label = termId, chars = termId.length)
    is AiContextItem.CodeSnippet -> ContextSummary(kind = "代码 ${language.ifBlank { "文本" }}", label = language, chars = code.length)
    is AiContextItem.TerminalError -> ContextSummary(kind = "终端报错", label = command, chars = command.length + stderr.length)
}

fun AiContextItem.displayText(): String = when (this) {
    is AiContextItem.TopicRef -> "知识节点：$topicId"
    is AiContextItem.TermRef -> "术语：$termId"
    is AiContextItem.CodeSnippet -> "代码片段（${language.ifBlank { "文本" }}，${code.length} 字符）"
    is AiContextItem.TerminalError -> "终端报错：$ $command"
}

/** 待发送上下文（发送前必须由用户确认，默认不自动附带）。 */
data class PendingContext(
    val items: List<AiContextItem>,
    val sourceLabel: String,
) {
    val totalChars: Int get() = items.sumOf {
        when (it) {
            is AiContextItem.TopicRef -> it.topicId.length
            is AiContextItem.TermRef -> it.termId.length
            is AiContextItem.CodeSnippet -> it.code.length
            is AiContextItem.TerminalError -> it.command.length + it.stderr.length
        }
    }

    fun without(index: Int): PendingContext =
        if (index in items.indices) copy(items = items.filterIndexed { itemIndex, _ -> itemIndex != index }) else this
}

/**
 * 一条对话消息。AI 回答永远带「AI 建议，未验证」标注（由 UI 渲染），
 * 错误只保存 `AppError.code/userHint`，不保存密钥。
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val createdAtEpochMs: Long,
    val streaming: Boolean = false,
    val cancelled: Boolean = false,
    val errorCode: String? = null,
    val errorHint: String? = null,
    val contextItems: List<ContextSummary> = emptyList(),
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val truncated: Boolean = false,
) {
    val isError: Boolean get() = errorCode != null || errorHint != null
    val isEmptyAnswer: Boolean get() = role == ChatRole.ASSISTANT && content.isBlank() && !streaming
}

data class SessionSummaryUi(
    val id: String,
    val title: String,
    val updatedAtEpochMs: Long,
    val messageCount: Int,
)

/** 助手页面状态（ViewModel → Compose 单向状态）。 */
data class AssistantUiState(
    val configured: Boolean = false,
    val modelName: String = "",
    val baseUrl: String = "",
    val hasApiKey: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val composer: String = "",
    val streaming: Boolean = false,
    val activeRequestId: String? = null,
    val streamingMessageId: String? = null,
    val pendingContext: PendingContext? = null,
    val awaitingContextConfirm: Boolean = false,
    val notice: String? = null,
    val sessions: List<SessionSummaryUi> = emptyList(),
    val currentSessionId: String? = null,
    val canRetry: Boolean = false,
    val lastRetryHint: String? = null,
    val loadingSessions: Boolean = false,
) {
    val isEmptyConversation: Boolean get() = messages.isEmpty()
    val lastAssistantMessage: ChatMessage? get() = messages.lastOrNull { it.role == ChatRole.ASSISTANT }
    val hasContext: Boolean get() = pendingContext?.items?.isNotEmpty() == true
}
