package com.codetree.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.codetree.designsystem.CTToast
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.ai.AiContextDraft
import com.codetree.domain.ai.AiContextItem
import com.codetree.domain.content.ContentRepository
import com.codetree.feature.terminal.terminal.TerminalViewModel
import com.codetree.runtime.api.RuntimeRegistry

/**
 * 终端 Tab（入口签名固定，app 组合根按此调用）。
 *
 * - 始终深色：内部用 `CodeTreeTheme(darkTheme = true)` 包一层，避免浅色主题下终端配色失效；
 * - 执行只经 [RuntimeRegistry]，不 import 具体运行时实现；
 * - 「问 AI」只把**真实** stderr/退出信息作为 [AiContextItem.TerminalError] 回调出去，由 app 导航到 AI 页。
 */
@Composable
fun TerminalTab(
    content: ContentRepository,
    runtimeRegistry: RuntimeRegistry,
    workspaceDir: String,
    onAskAi: (AiContextDraft) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val factory = remember(content, runtimeRegistry, workspaceDir) {
        TerminalViewModelFactory(content, runtimeRegistry, workspaceDir)
    }
    val viewModel: TerminalViewModel = viewModel(key = "terminal", factory = factory)
    val state by viewModel.state.collectAsState()

    CodeTreeTheme(darkTheme = true) {
        TerminalScreen(
            state = state,
            viewModel = viewModel,
            onAskAi = onAskAi,
            modifier = modifier,
        )
    }
}

@Composable
private fun TerminalScreen(
    state: com.codetree.feature.terminal.model.TerminalUiState,
    viewModel: TerminalViewModel,
    onAskAi: (AiContextDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHistory by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val terminal = CodeTreeTheme.terminal
    val listState = rememberLazyListState()
    val lastBlock = state.blocks.lastOrNull()

    LaunchedEffect(
        state.blocks.size,
        lastBlock?.stdout?.length,
        lastBlock?.stderr?.length,
        lastBlock?.finished,
    ) {
        if (state.blocks.isNotEmpty()) {
            listState.animateScrollToItem(state.blocks.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(terminal.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            TerminalToolbar(
                state = state,
                onSelectRuntime = viewModel::selectRuntime,
                onClearScreen = viewModel::clearScreen,
                onOpenHistory = { showHistory = true },
                onOpenSettings = { showSettings = true },
                modifier = Modifier.fillMaxWidth(),
            )
            TerminalOutput(
                state = state,
                listState = listState,
                onToggleCollapse = viewModel::toggleBlockCollapsed,
                onUseCommand = { command -> viewModel.updateInput(command, command.length) },
                onAskAi = { block ->
                    val errorText = block.errorTextForAi()
                    if (errorText != null) {
                        onAskAi(
                            AiContextDraft(
                                items = listOf(AiContextItem.TerminalError(command = block.command, stderr = errorText)),
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            if (state.completions.isNotEmpty() && state.input.isNotBlank() && !state.running) {
                CompletionBar(
                    completions = state.completions,
                    onSelect = viewModel::applyCompletion,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TerminalInput(
                state = state,
                onInputChange = viewModel::updateInput,
                onSubmit = viewModel::submit,
                onStop = viewModel::cancelActive,
                onHistoryPrevious = viewModel::historyPrevious,
                onHistoryNext = viewModel::historyNext,
                onOpenDiagnostics = { showDiagnostics = true },
                onOpenHistory = { showHistory = true },
                onOpenSettings = { showSettings = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.notice?.let { notice ->
            CTToast(
                message = notice,
                visible = true,
                onDismiss = viewModel::dismissNotice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            )
        }
    }

    if (showHistory) {
        HistorySheet(
            history = state.history,
            onSelect = { entry ->
                viewModel.updateInput(entry, entry.length)
                showHistory = false
            },
            onClear = {
                viewModel.clearHistory()
                showHistory = false
            },
            onDismiss = { showHistory = false },
        )
    }
    if (showDiagnostics) {
        DiagnosticSheet(
            diagnostics = state.diagnostics,
            onApplyFix = { diagnostic ->
                viewModel.applyFix(diagnostic)
                showDiagnostics = false
            },
            onDismiss = { showDiagnostics = false },
        )
    }
    if (showSettings) {
        TerminalSettingsSheet(
            state = state,
            onSelectLanguage = viewModel::setHighlightLanguage,
            onClose = { showSettings = false },
        )
    }
}

private class TerminalViewModelFactory(
    private val content: ContentRepository,
    private val runtimeRegistry: RuntimeRegistry,
    private val workspaceDir: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TerminalViewModel(content, runtimeRegistry, workspaceDir) as T
}
