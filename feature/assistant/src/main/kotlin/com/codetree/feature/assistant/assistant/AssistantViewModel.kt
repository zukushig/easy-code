package com.codetree.feature.assistant.assistant

import androidx.lifecycle.ViewModel
import com.codetree.domain.ai.AiConfig
import com.codetree.domain.ai.AiContextDraft
import com.codetree.domain.ai.AiContextItem
import com.codetree.domain.ai.AiEvent
import com.codetree.domain.ai.AiMessage
import com.codetree.domain.ai.AiProvider
import com.codetree.domain.ai.AiRequest
import com.codetree.domain.ai.AiRole
import com.codetree.domain.common.AppError
import com.codetree.domain.common.AppResult
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.feature.assistant.data.SessionStore
import com.codetree.feature.assistant.data.StoredSession
import com.codetree.feature.assistant.model.AssistantUiState
import com.codetree.feature.assistant.model.ChatMessage
import com.codetree.feature.assistant.model.ChatRole
import com.codetree.feature.assistant.model.PendingContext
import com.codetree.feature.assistant.model.SessionSummaryUi
import com.codetree.feature.assistant.model.toSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * AI 助手 ViewModel。
 *
 * 边界（docs/specs/ai-provider.md）：
 * - 只通过 [AiProvider.stream] 获取回答，不具备终端执行能力；
 * - 密钥只在构造请求时从 [SettingsRepository.apiKey] 读取一次，不进入状态、不落盘；
 * - 上下文必须用户确认后才发送；重试只能重发用户已确认过的请求；
 * - 失败映射 [AppError.userHint]，不显示堆栈。
 */
class AssistantViewModel(
    private val content: ContentRepository,
    private val settings: SettingsRepository,
    private val aiProvider: AiProvider,
    sessionsDir: String,
    private val initialDraft: AiContextDraft? = null,
) : ViewModel() {

    private val store = SessionStore(sessionsDir)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    private var currentSessionCreatedAt: Long = 0L
    private var lastRetryPayload: RetryPayload? = null
    private var lastFailedError: AppError? = null

    init {
        scope.launch {
            settings.settings.collect { appSettings ->
                _state.update {
                    it.copy(
                        configured = appSettings.aiConfig.isConfigured,
                        modelName = appSettings.aiConfig.model,
                        baseUrl = appSettings.aiConfig.baseUrl,
                        hasApiKey = appSettings.hasApiKey,
                    )
                }
            }
        }
        val draftItems = initialDraft?.items.orEmpty()
        if (draftItems.isNotEmpty()) {
            _state.update {
                it.copy(pendingContext = PendingContext(draftItems, sourceLabel = "来自学习/终端页"))
            }
        }
        refreshSessions()
    }

    // ---------------- 输入 ----------------

    fun updateComposer(text: String) {
        _state.update { it.copy(composer = text.take(MAX_COMPOSER_CHARS)) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    // ---------------- 上下文确认 ----------------

    /** 由 app 在导航时传入的上下文草稿（终端报错、知识节点等）；只进入待确认状态，不自动发送。 */
    fun adoptDraft(draft: AiContextDraft?) {
        val items = draft?.items.orEmpty()
        if (items.isEmpty()) return
        _state.update {
            it.copy(
                pendingContext = PendingContext(items = items, sourceLabel = "来自学习/终端页"),
                notice = "有新的上下文待确认：发送前会展示将发送的内容",
            )
        }
    }

    fun addCodeSnippet(language: String, code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        val item = AiContextItem.CodeSnippet(language = language.ifBlank { "text" }, code = trimmed.take(MAX_CONTEXT_ITEM_CHARS))
        val current = _state.value.pendingContext
        val context = if (current == null) {
            PendingContext(items = listOf(item), sourceLabel = "手动添加")
        } else {
            current.copy(items = current.items + item)
        }
        _state.update { it.copy(pendingContext = context, notice = "已添加上下文，发送前请确认") }
    }

    fun removeContextItem(index: Int) {
        val current = _state.value.pendingContext ?: return
        val updated = current.without(index)
        _state.update {
            it.copy(pendingContext = if (updated.items.isEmpty()) null else updated)
        }
    }

    fun clearContext() {
        _state.update { it.copy(pendingContext = null, awaitingContextConfirm = false) }
    }

    fun contextItemLabel(item: AiContextItem): String = when (item) {
        is AiContextItem.TopicRef -> content.topic(item.topicId)?.title?.primary?.ifBlank { item.topicId } ?: item.topicId
        is AiContextItem.TermRef -> content.term(item.termId)?.displayNameIn("zh-CN") ?: item.termId
        is AiContextItem.CodeSnippet -> item.language.ifBlank { "text" }
        is AiContextItem.TerminalError -> "$ ${item.command}"
    }

    // ---------------- 发送 / 流式 ----------------

    fun send() {
        val snapshot = _state.value
        if (snapshot.streaming) return
        val text = snapshot.composer.trim()
        if (text.isEmpty()) return
        if (!snapshot.configured) {
            _state.update {
                it.copy(notice = "尚未配置 AI 服务：请先在设置中填写 Base URL 与模型名")
            }
            return
        }
        if (snapshot.pendingContext != null) {
            _state.update {
                it.copy(awaitingContextConfirm = true, notice = "发送前请确认将发送的上下文内容")
            }
            return
        }
        startRequest(text = text, contextItems = emptyList(), awaitingUserConfirm = false)
    }

    /** 用户在上下文确认卡上点「确认发送」。 */
    fun confirmContextAndSend() {
        val snapshot = _state.value
        if (snapshot.streaming) return
        val text = snapshot.composer.trim()
        if (text.isEmpty()) return
        if (!snapshot.configured) {
            _state.update { it.copy(notice = "尚未配置 AI 服务：请先在设置中填写 Base URL 与模型名") }
            return
        }
        val items = snapshot.pendingContext?.items.orEmpty()
        if (items.isEmpty()) {
            startRequest(text, emptyList(), false)
            return
        }
        startRequest(text = text, contextItems = items, awaitingUserConfirm = true)
    }

    private fun startRequest(text: String, contextItems: List<AiContextItem>, awaitingUserConfirm: Boolean) {
        val requestId = UUID.randomUUID().toString()
        val assistantMessageId = UUID.randomUUID().toString()
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            content = text,
            createdAtEpochMs = System.currentTimeMillis(),
            contextItems = contextItems.map { it.toSummary() },
        )
        val assistantMessage = ChatMessage(
            id = assistantMessageId,
            role = ChatRole.ASSISTANT,
            content = "",
            createdAtEpochMs = System.currentTimeMillis(),
            streaming = true,
            contextItems = emptyList(),
        )
        val historyMessages = _state.value.messages
            .filter { it.content.isNotBlank() && !it.streaming && it.errorCode == null }
            .takeLast(MAX_HISTORY_MESSAGES)
            .map { it.toAiMessage() }

        _state.update {
            it.copy(
                messages = it.messages + userMessage + assistantMessage,
                composer = "",
                streaming = true,
                activeRequestId = requestId,
                streamingMessageId = assistantMessageId,
                pendingContext = null,
                awaitingContextConfirm = false,
                notice = null,
                canRetry = false,
            )
        }
        lastRetryPayload = RetryPayload(
            text = text,
            contextItems = contextItems,
            history = historyMessages,
            confirmed = awaitingUserConfirm || contextItems.isEmpty(),
        )

        scope.launch(Dispatchers.IO) {
            val appSettings = settings.settings.value
            val apiKey = settings.apiKeyOrNull()
            val userContent = if (contextItems.isEmpty()) {
                text
            } else {
                text + "\n\n" + AiContextDraft(contextItems).render()
            }
            val messages = buildList {
                add(AiMessage(AiRole.SYSTEM, SYSTEM_PROMPT))
                addAll(lastRetryPayload?.history.orEmpty())
                add(AiMessage(AiRole.USER, userContent.take(MAX_MESSAGE_CHARS)))
            }
            val request = AiRequest(
                requestId = requestId,
                config = appSettings.aiConfig,
                apiKey = apiKey,
                messages = messages,
            )
            var terminal = false
            try {
                aiProvider.stream(request).collect { event ->
                    if (activeRequestId(_state.value) != requestId) return@collect
                    when (event) {
                        is AiEvent.Started -> Unit
                        is AiEvent.Delta -> appendDelta(requestId, assistantMessageId, event.text)
                        is AiEvent.Usage -> recordUsage(requestId, assistantMessageId, event)
                        is AiEvent.Completed -> {
                            terminal = true
                            completeMessage(requestId, assistantMessageId)
                        }
                        is AiEvent.Failed -> {
                            terminal = true
                            failMessage(requestId, assistantMessageId, event.error)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (!terminal) {
                    failMessage(
                        requestId,
                        assistantMessageId,
                        AppError.Internal(t::class.simpleName ?: "AI 请求异常", t),
                    )
                }
            } finally {
                if (!terminal) {
                    // 流提前结束（未收到 Completed/Failed）：如实收敛，不伪造成功
                    failMessage(
                        requestId,
                        assistantMessageId,
                        AppError.Internal("AI 流提前结束（未收到完成事件）"),
                    )
                }
                _state.update { if (it.activeRequestId == requestId) it.copy(streaming = false, activeRequestId = null, streamingMessageId = null) else it }
                persistCurrentSession()
            }
        }
    }

    fun stop() {
        val requestId = _state.value.activeRequestId ?: return
        scope.launch {
            try {
                aiProvider.cancel(requestId)
            } catch (t: Throwable) {
                // 取消失败不扩散；UI 已停止追加
            }
            val streamingMessageId = _state.value.streamingMessageId
            _state.update { state ->
                state.copy(
                    streaming = false,
                    activeRequestId = null,
                    streamingMessageId = null,
                    canRetry = false,
                    notice = "已停止生成，已生成内容保留",
                    messages = state.messages.map { message ->
                        if (message.id == streamingMessageId) {
                            message.copy(
                                streaming = false,
                                cancelled = true,
                                content = message.content.ifBlank { "（已停止，未生成内容）" },
                            )
                        } else {
                            message
                        }
                    },
                )
            }
            persistCurrentSession()
        }
    }

    private fun activeRequestId(state: AssistantUiState): String? = state.activeRequestId

    private fun appendDelta(requestId: String, messageId: String, delta: String) {
        if (delta.isEmpty()) return
        _state.update { state ->
            if (state.activeRequestId != requestId) return@update state
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id != messageId) return@map message
                    val remaining = MAX_REPLY_CHARS - message.content.length
                    if (remaining <= 0) {
                        message.copy(truncated = true)
                    } else {
                        message.copy(content = message.content + delta.take(remaining), truncated = delta.length > remaining)
                    }
                },
            )
        }
    }

    private fun recordUsage(requestId: String, messageId: String, usage: AiEvent.Usage) {
        _state.update { state ->
            if (state.activeRequestId != requestId) return@update state
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id != messageId) return@map message
                    message.copy(promptTokens = usage.promptTokens, completionTokens = usage.completionTokens)
                },
            )
        }
    }

    private fun completeMessage(requestId: String, messageId: String) {
        _state.update { state ->
            if (state.activeRequestId != requestId) return@update state
            state.copy(
                streaming = false,
                activeRequestId = null,
                streamingMessageId = null,
                canRetry = false,
                messages = state.messages.map { message ->
                    if (message.id != messageId) return@map message
                    if (message.content.isBlank()) {
                        message.copy(streaming = false, errorCode = "empty_response", errorHint = "模型没有返回内容，可重试或更换模型")
                    } else {
                        message.copy(streaming = false)
                    }
                },
            )
        }
    }

    private fun failMessage(requestId: String, messageId: String, error: AppError) {
        lastFailedError = error
        _state.update { state ->
            if (state.activeRequestId != requestId) return@update state
            state.copy(
                streaming = false,
                activeRequestId = null,
                streamingMessageId = null,
                canRetry = lastRetryPayload?.confirmed == true,
                notice = error.userHint,
                messages = state.messages.map { message ->
                    if (message.id != messageId) return@map message
                    message.copy(
                        streaming = false,
                        errorCode = error.code,
                        errorHint = error.userHint,
                    )
                },
            )
        }
    }

    // ---------------- 重试 ----------------

    fun retry() {
        val payload = lastRetryPayload ?: return
        if (_state.value.streaming) return
        if (!payload.confirmed) {
            _state.update { it.copy(notice = "请先确认上下文后再重试") }
            return
        }
        val failed = lastFailedError
        val warning = when (failed) {
            is AppError.NoNetwork, is AppError.Timeout -> "上次请求可能已发送，本次为重试（请确认服务端是否已收到）"
            else -> "正在重试上一条请求"
        }
        val requestId = UUID.randomUUID().toString()
        val assistantMessageId = UUID.randomUUID().toString()
        // 移除最后一条失败的 AI 消息，保留用户消息，重新生成
        _state.update { state ->
            val messages = state.messages.toMutableList()
            val lastAssistantIndex = messages.indexOfLast { it.role == ChatRole.ASSISTANT }
            if (lastAssistantIndex >= 0) messages.removeAt(lastAssistantIndex)
            messages += ChatMessage(
                id = assistantMessageId,
                role = ChatRole.ASSISTANT,
                content = "",
                createdAtEpochMs = System.currentTimeMillis(),
                streaming = true,
            )
            state.copy(
                messages = messages,
                streaming = true,
                activeRequestId = requestId,
                streamingMessageId = assistantMessageId,
                canRetry = false,
                notice = warning,
            )
        }
        launchRetryRequest(requestId, assistantMessageId, payload.text, payload.contextItems, payload.history)
    }

    private fun launchRetryRequest(
        requestId: String,
        assistantMessageId: String,
        text: String,
        contextItems: List<AiContextItem>,
        history: List<AiMessage>,
    ) {
        scope.launch(Dispatchers.IO) {
            val appSettings = settings.settings.value
            val apiKey = settings.apiKeyOrNull()
            val userContent = if (contextItems.isEmpty()) text else text + "\n\n" + AiContextDraft(contextItems).render()
            val request = AiRequest(
                requestId = requestId,
                config = appSettings.aiConfig,
                apiKey = apiKey,
                messages = buildList {
                    add(AiMessage(AiRole.SYSTEM, SYSTEM_PROMPT))
                    addAll(history)
                    add(AiMessage(AiRole.USER, userContent.take(MAX_MESSAGE_CHARS)))
                },
            )
            var terminal = false
            try {
                aiProvider.stream(request).collect { event ->
                    if (_state.value.activeRequestId != requestId) return@collect
                    when (event) {
                        is AiEvent.Started -> Unit
                        is AiEvent.Delta -> appendDelta(requestId, assistantMessageId, event.text)
                        is AiEvent.Usage -> recordUsage(requestId, assistantMessageId, event)
                        is AiEvent.Completed -> {
                            terminal = true
                            completeMessage(requestId, assistantMessageId)
                        }
                        is AiEvent.Failed -> {
                            terminal = true
                            failMessage(requestId, assistantMessageId, event.error)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (!terminal) failMessage(requestId, assistantMessageId, AppError.Internal(t::class.simpleName ?: "AI 请求异常", t))
            } finally {
                if (!terminal) failMessage(requestId, assistantMessageId, AppError.Internal("AI 流提前结束（未收到完成事件）"))
                _state.update { if (it.activeRequestId == requestId) it.copy(streaming = false, activeRequestId = null, streamingMessageId = null) else it }
                persistCurrentSession()
            }
        }
    }

    // ---------------- 会话历史 ----------------

    fun refreshSessions() {
        scope.launch {
            _state.update { it.copy(loadingSessions = true) }
            val sessions = runCatching { store.listSessions() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    loadingSessions = false,
                    sessions = sessions.map { session ->
                        SessionSummaryUi(
                            id = session.id,
                            title = session.title.ifBlank { "未命名会话" },
                            updatedAtEpochMs = session.updatedAtEpochMs,
                            messageCount = session.messages.size,
                        )
                    },
                )
            }
        }
    }

    fun openSession(sessionId: String) {
        scope.launch {
            val stored = runCatching { store.load(sessionId) }.getOrNull() ?: return@launch
            currentSessionCreatedAt = stored.createdAtEpochMs
            lastRetryPayload = null
            lastFailedError = null
            _state.update {
                it.copy(
                    messages = stored.messages.mapIndexed { index, message -> store.toChatMessage(message, index) },
                    currentSessionId = stored.id,
                    streaming = false,
                    activeRequestId = null,
                    streamingMessageId = null,
                    canRetry = false,
                    pendingContext = null,
                    composer = "",
                    notice = null,
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        scope.launch {
            runCatching { store.delete(sessionId) }
            if (_state.value.currentSessionId == sessionId) {
                newChat()
            }
            refreshSessions()
        }
    }

    fun newChat() {
        currentSessionCreatedAt = 0L
        lastRetryPayload = null
        lastFailedError = null
        _state.update {
            it.copy(
                messages = emptyList(),
                composer = "",
                currentSessionId = null,
                streaming = false,
                activeRequestId = null,
                streamingMessageId = null,
                canRetry = false,
                pendingContext = null,
                awaitingContextConfirm = false,
                notice = null,
            )
        }
    }

    private fun persistCurrentSession() {
        val state = _state.value
        val messages = state.messages.filter { it.content.isNotBlank() || it.errorCode != null }
        if (messages.isEmpty()) return
        val sessionId = state.currentSessionId ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val createdAt = if (currentSessionCreatedAt > 0) currentSessionCreatedAt else now
        currentSessionCreatedAt = createdAt
        val session = StoredSession(
            id = sessionId,
            title = messages.firstOrNull { it.role == ChatRole.USER }?.content
                ?.replace('\n', ' ')?.trim()?.take(MAX_TITLE_CHARS).orEmpty().ifBlank { "新会话" },
            createdAtEpochMs = createdAt,
            updatedAtEpochMs = now,
            messages = messages.map { store.toStoredMessage(it) },
        )
        scope.launch {
            val saved = runCatching { store.save(session) }.getOrDefault(false)
            if (saved) {
                _state.update { it.copy(currentSessionId = sessionId) }
                refreshSessions()
            }
        }
    }

    // ---------------- 配置 ----------------

    fun saveConfig(baseUrl: String, model: String, apiKey: String?, temperature: Double, maxTokens: Int?) {
        scope.launch {
            val normalizedBaseUrl = baseUrl.trim()
            val normalizedModel = model.trim()
            val newConfig = AiConfig(
                baseUrl = normalizedBaseUrl,
                model = normalizedModel,
                temperature = temperature.coerceIn(0.0, 2.0),
                maxTokens = maxTokens?.takeIf { it > 0 },
            )
            val updateResult = settings.update { it.copy(aiConfig = newConfig) }
            if (updateResult is AppResult.Failure) {
                _state.update { it.copy(notice = updateResult.error.userHint) }
                return@launch
            }
            if (apiKey != null) {
                val keyResult = settings.setApiKey(apiKey.takeIf { it.isNotBlank() })
                if (keyResult is AppResult.Failure) {
                    _state.update { it.copy(notice = keyResult.error.userHint) }
                    return@launch
                }
            }
            _state.update { it.copy(notice = "AI 配置已保存") }
        }
    }

    fun clearApiKey() {
        scope.launch {
            settings.setApiKey(null)
            _state.update { it.copy(hasApiKey = false, notice = "已清除 API Key") }
        }
    }

    private fun ChatMessage.toAiMessage(): AiMessage = AiMessage(
        role = if (role == ChatRole.USER) AiRole.USER else AiRole.ASSISTANT,
        content = content.take(MAX_MESSAGE_CHARS),
    )

    private suspend fun SettingsRepository.apiKeyOrNull(): String? = runCatching { apiKey() }.getOrNull()

    override fun onCleared() {
        val requestId = _state.value.activeRequestId
        if (requestId != null) {
            scope.launch { runCatching { aiProvider.cancel(requestId) } }
        }
        scope.cancel()
    }

    private data class RetryPayload(
        val text: String,
        val contextItems: List<AiContextItem>,
        val history: List<AiMessage>,
        val confirmed: Boolean,
    )

    companion object {
        const val SYSTEM_PROMPT =
            "你是编程学习助手。只解释概念、分析错误并给出建议；不要声称代码已运行、已测试或已验证；" +
                "不要执行命令；明确区分「建议」与「实际结果」，并提醒用户到终端或测试中独立验证。"
        const val MAX_REPLY_CHARS = 64 * 1024
        const val MAX_COMPOSER_CHARS = 8000
        private const val MAX_MESSAGE_CHARS = 8000
        private const val MAX_HISTORY_MESSAGES = 20
        private const val MAX_CONTEXT_ITEM_CHARS = 8000
        private const val MAX_TITLE_CHARS = 40
    }
}
