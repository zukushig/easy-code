package com.codetree.domain.ai

import com.codetree.domain.common.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class AiRole { SYSTEM, USER, ASSISTANT }

data class AiMessage(val role: AiRole, val content: String)

/** OpenAI 兼容配置：不硬编码供应商地址（docs/01 AIA-001）。 */
@Serializable
data class AiConfig(
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Double = 0.3,
    val maxTokens: Int? = null,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
    val endpoint: String get() = baseUrl.trimEnd('/') + "/chat/completions"
}

data class AiRequest(
    val requestId: String,
    val config: AiConfig,
    val apiKey: String?,
    val messages: List<AiMessage>,
)

sealed interface AiEvent {
    data object Started : AiEvent
    data class Delta(val text: String) : AiEvent
    data class Usage(val promptTokens: Int, val completionTokens: Int) : AiEvent
    data object Completed : AiEvent
    data class Failed(val error: AppError) : AiEvent
}

/** AI 提供方抽象：只返回回答事件，不具备终端执行权限（docs/02 §4.4）。 */
interface AiProvider {
    fun stream(request: AiRequest): Flow<AiEvent>
    suspend fun cancel(requestId: String)
}

/** 可引用的上下文条目（docs/01 AIA-003）：发送前必须由用户确认。 */
sealed interface AiContextItem {
    data class TopicRef(val topicId: String) : AiContextItem
    data class TermRef(val termId: String) : AiContextItem
    data class CodeSnippet(val language: String, val code: String) : AiContextItem
    data class TerminalError(val command: String, val stderr: String) : AiContextItem
}

data class AiContextDraft(val items: List<AiContextItem> = emptyList()) {
    val totalChars: Int
        get() = items.sumOf {
            when (it) {
                is AiContextItem.TopicRef -> it.topicId.length
                is AiContextItem.TermRef -> it.termId.length
                is AiContextItem.CodeSnippet -> it.code.length
                is AiContextItem.TerminalError -> it.command.length + it.stderr.length
            }
        }

    fun render(): String = items.joinToString("\n") {
        when (it) {
            is AiContextItem.TopicRef -> "[知识节点] ${it.topicId}"
            is AiContextItem.TermRef -> "[术语] ${it.termId}"
            is AiContextItem.CodeSnippet -> "[代码 ${it.language}]\n${it.code}"
            is AiContextItem.TerminalError -> "[终端报错] $ ${it.command}\n${it.stderr}"
        }
    }
}

/** SSE 行解析器：按 `data: {...}` 协议解析，[DONE] 结束；解析失败返回空列表而不是崩溃。 */
interface AiSseParser {
    fun parseLine(line: String): List<AiEvent>
}
