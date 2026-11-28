package com.codetree.feature.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTEmptyState
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTPill
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CTToast
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.ai.AiContextDraft
import com.codetree.domain.ai.AiProvider
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.feature.assistant.assistant.AssistantViewModel
import com.codetree.feature.assistant.model.AssistantUiState

/**
 * AI 助手 Tab（入口签名固定）。
 *
 * - 回答区始终标注「AI 建议，未验证」，不渲染成编译/测试结果；
 * - 上下文发送前必须确认；密钥只经 `SettingsRepository` 读取，不进入 UI 状态；
 * - 会话写入 `sessionsDir` 的 JSON（不含密钥）。
 */
@Composable
fun AssistantTab(
    content: ContentRepository,
    settings: SettingsRepository,
    aiProvider: AiProvider,
    sessionsDir: String,
    initialDraft: AiContextDraft? = null,
    modifier: Modifier = Modifier,
) {
    val factory = remember(content, settings, aiProvider, sessionsDir) {
        AssistantViewModelFactory(content, settings, aiProvider, sessionsDir, initialDraft)
    }
    val viewModel: AssistantViewModel = viewModel(key = "assistant", factory = factory)
    val state by viewModel.state.collectAsState()

    var showConfig by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showAddCode by remember { mutableStateOf(false) }

    LaunchedEffect(initialDraft) { viewModel.adoptDraft(initialDraft) }

    AssistantScreen(
        state = state,
        viewModel = viewModel,
        onOpenConfig = { showConfig = true },
        onOpenSessions = { showSessions = true },
        onAddCode = { showAddCode = true },
        modifier = modifier,
    )

    if (showConfig) {
        AiConfigSheet(
            state = state,
            onSave = { baseUrl, model, apiKey, temperature, maxTokens ->
                viewModel.saveConfig(baseUrl, model, apiKey, temperature, maxTokens)
                showConfig = false
            },
            onClearKey = viewModel::clearApiKey,
            onDismiss = { showConfig = false },
        )
    }
    if (showSessions) {
        SessionsSheet(
            sessions = state.sessions,
            currentSessionId = state.currentSessionId,
            onOpen = { sessionId ->
                viewModel.openSession(sessionId)
                showSessions = false
            },
            onDelete = viewModel::deleteSession,
            onNewChat = {
                viewModel.newChat()
                showSessions = false
            },
            onDismiss = { showSessions = false },
        )
    }
    if (showAddCode) {
        AddCodeDialog(
            onConfirm = { language, code ->
                viewModel.addCodeSnippet(language, code)
                showAddCode = false
            },
            onDismiss = { showAddCode = false },
        )
    }
}

@Composable
private fun AssistantScreen(
    state: AssistantUiState,
    viewModel: AssistantViewModel,
    onOpenConfig: () -> Unit,
    onOpenSessions: () -> Unit,
    onAddCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            AssistantTopBar(
                state = state,
                onNewChat = viewModel::newChat,
                onOpenSessions = onOpenSessions,
                onOpenConfig = onOpenConfig,
            )
            if (!state.configured) {
                UnconfiguredGuide(onOpenConfig = onOpenConfig)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    AssistantWelcome(
                        onTemplate = { text -> viewModel.updateComposer(text) },
                        onOpenConfig = onOpenConfig,
                    )
                } else {
                    ChatMessages(
                        messages = state.messages,
                        onRetry = viewModel::retry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            state.pendingContext?.let { pending ->
                ContextConfirmCard(
                    pending = pending,
                    labelFor = viewModel::contextItemLabel,
                    onRemove = viewModel::removeContextItem,
                    onClear = viewModel::clearContext,
                    onConfirm = viewModel::confirmContextAndSend,
                )
            }
            Composer(
                state = state,
                onUpdate = viewModel::updateComposer,
                onSend = viewModel::send,
                onStop = viewModel::stop,
                onAddCode = onAddCode,
                onOpenConfig = onOpenConfig,
            )
        }
        state.notice?.let { notice ->
            CTToast(
                message = notice,
                visible = true,
                onDismiss = viewModel::dismissNotice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 132.dp),
            )
        }
    }
}

@Composable
private fun AssistantTopBar(
    state: AssistantUiState,
    onNewChat: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenConfig: () -> Unit,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "AI 助手", style = typography.title2, color = colors.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CTTag(
                        text = if (state.configured) state.modelName.ifBlank { "已配置" } else "未配置",
                        tone = if (state.configured) CTTagTone.SUCCESS else CTTagTone.WARNING,
                    )
                    Spacer(Modifier.width(spacing.xs))
                    Text(
                        text = if (state.hasApiKey) "密钥已保存" else "未保存密钥",
                        style = typography.caption,
                        color = colors.textTertiary,
                    )
                }
            }
            CTIconButton(icon = CTIcons.Plus, contentDescription = "新会话", onClick = onNewChat, tint = colors.textSecondary)
            CTIconButton(icon = CTIcons.Inbox, contentDescription = "历史会话", onClick = onOpenSessions, tint = colors.textSecondary)
            CTIconButton(icon = CTIcons.Settings, contentDescription = "AI 配置", onClick = onOpenConfig, tint = colors.textSecondary)
        }
        androidx.compose.material3.HorizontalDivider(color = colors.separator)
    }
}

@Composable
private fun UnconfiguredGuide(onOpenConfig: () -> Unit) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography
    CTCard(modifier = Modifier.padding(horizontal = spacing.pageHorizontal, vertical = spacing.sm)) {
        Text(text = "先配置 AI 服务", style = typography.headline, color = colors.textPrimary)
        Text(
            text = "填写 Base URL、模型名与密钥（OpenAI 兼容）。未配置时不会发出任何请求。",
            style = typography.callout,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = spacing.xs),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = spacing.sm), horizontalArrangement = Arrangement.End) {
            CTButton(text = "去配置", onClick = onOpenConfig)
        }
    }
}

@Composable
private fun AssistantWelcome(onTemplate: (String) -> Unit, onOpenConfig: () -> Unit) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val templates = listOf("解释这个概念", "这段代码为什么报错", "给我一个练习")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CTEmptyState(
            title = "AI 学习助手",
            description = "可解释概念、分析报错、给出练习建议。回答是「AI 建议，未验证」，请到终端或测试中独立验证。",
            icon = CTIcons.Assistant,
        )
        Column(
            modifier = Modifier.padding(horizontal = spacing.pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            templates.forEach { template ->
                CTPill(text = template, onClick = { onTemplate(template) }, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(spacing.sm))
        CTButton(text = "配置 AI 服务", onClick = onOpenConfig, style = com.codetree.designsystem.CTButtonStyle.TINTED)
        Spacer(Modifier.height(spacing.xxl))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCodeDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var language by remember { mutableStateOf("kotlin") }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加代码上下文") },
        text = {
            Column {
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("语言") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CodeTreeTheme.spacing.sm))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("代码片段（发送前会再确认）") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (code.isNotBlank()) onConfirm(language, code) }) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private class AssistantViewModelFactory(
    private val content: ContentRepository,
    private val settings: SettingsRepository,
    private val aiProvider: AiProvider,
    private val sessionsDir: String,
    private val initialDraft: AiContextDraft?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssistantViewModel(content, settings, aiProvider, sessionsDir, initialDraft) as T
}
