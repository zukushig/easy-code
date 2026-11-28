package com.codetree.feature.terminal.terminal

import androidx.lifecycle.ViewModel
import com.codetree.domain.common.AppError
import com.codetree.domain.content.ContentRepository
import com.codetree.feature.terminal.analysis.AnalysisText
import com.codetree.feature.terminal.analysis.BracketMatcher
import com.codetree.feature.terminal.analysis.DefaultSyntaxAnalyzer
import com.codetree.feature.terminal.analysis.ShellLanguages
import com.codetree.feature.terminal.model.RuntimeOption
import com.codetree.feature.terminal.model.TerminalBlock
import com.codetree.feature.terminal.model.TerminalUiState
import com.codetree.runtime.api.CodeDiagnostic
import com.codetree.runtime.api.CompletionSuggestion
import com.codetree.runtime.api.FixKind
import com.codetree.runtime.api.ResourceLimits
import com.codetree.runtime.api.RunRequest
import com.codetree.runtime.api.RuntimeDescriptor
import com.codetree.runtime.api.RuntimeEvent
import com.codetree.runtime.api.RuntimeKind
import com.codetree.runtime.api.RuntimePlugin
import com.codetree.runtime.api.RuntimeRegistry
import com.codetree.runtime.api.RuntimeSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 终端会话 ViewModel：只通过 [RuntimeRegistry] 执行命令，所有输出来自 [RuntimeEvent]。
 *
 * 约束（docs/specs/terminal.md）：
 * - 同一时刻只有一个活动会话；执行中禁止重复提交；
 * - 取消调用 [RuntimeSession.cancel]，等待 `Finished(cancelled = true)` 收敛；运行时未返回
 *   结束事件时如实提示，不伪造退出码；
 * - 运行时不可用时显示未安装状态，绝不产生假输出。
 */
class TerminalViewModel(
    content: ContentRepository,
    private val runtimeRegistry: RuntimeRegistry,
    val workspaceRoot: String,
    private val defaultLimits: ResourceLimits = ResourceLimits(),
) : ViewModel() {

    private val analyzer = DefaultSyntaxAnalyzer(syntaxProvider = { language -> content.languageSyntax(language) })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(TerminalUiState())
    val state: StateFlow<TerminalUiState> = _state.asStateFlow()

    private var activeSession: RuntimeSession? = null
    private var runJob: Job? = null
    private var historyCursor: Int? = null

    init {
        val options = buildRuntimeOptions()
        val selected = options.firstOrNull { it.available && it.kindLabel == KIND_BUILTIN_SHELL }?.id
            ?: options.firstOrNull { it.available }?.id
            ?: options.firstOrNull()?.id
            .orEmpty()
        _state.update {
            it.copy(
                runtimeOptions = options,
                selectedRuntimeId = selected,
                syntaxLanguages = ShellLanguages.OPTIONS,
                highlightLanguage = ShellLanguages.SHELL,
            )
        }
    }

    // ---------------- 运行时 ----------------

    private fun buildRuntimeOptions(): List<RuntimeOption> {
        val descriptors: List<RuntimeDescriptor> = try {
            runtimeRegistry.descriptors
        } catch (t: Throwable) {
            emptyList()
        }
        val known = descriptors.map { descriptor ->
            RuntimeOption(
                id = descriptor.id,
                displayName = descriptor.displayName,
                available = descriptor.available,
                kindLabel = when (descriptor.kind) {
                    RuntimeKind.BUILTIN_SHELL -> KIND_BUILTIN_SHELL
                    RuntimeKind.PYTHON -> "Python"
                    RuntimeKind.JAVASCRIPT -> "JavaScript"
                },
            )
        }
        val knownKinds = descriptors.map { it.kind }.toSet()
        val placeholders = buildList {
            if (RuntimeKind.PYTHON !in knownKinds) add(RuntimeOption("python", "Python", false, "Python"))
            if (RuntimeKind.JAVASCRIPT !in knownKinds) add(RuntimeOption("javascript", "JavaScript", false, "JavaScript"))
        }
        return known + placeholders
    }

    fun selectRuntime(runtimeId: String) {
        if (_state.value.running) {
            _state.update { it.copy(notice = "执行中不能切换运行时，请先停止或等待结束") }
            return
        }
        if (_state.value.runtimeOptions.none { it.id == runtimeId }) return
        _state.update { it.copy(selectedRuntimeId = runtimeId, notice = null) }
    }

    // ---------------- 输入 / 分析 ----------------

    fun updateInput(text: String, cursor: Int) {
        val safeText = if (text.length > MAX_INPUT_CHARS) text.take(MAX_INPUT_CHARS) else text
        val safeCursor = cursor.coerceIn(0, safeText.length)
        _state.update { it.copy(input = safeText, cursor = safeCursor) }
        analyze(safeText, safeCursor)
    }

    fun setHighlightLanguage(languageId: String) {
        analyzer.setLanguage(languageId)
        _state.update { it.copy(highlightLanguage = languageId) }
        analyze()
    }

    fun applyCompletion(suggestion: CompletionSuggestion) {
        val text = _state.value.input
        val start = suggestion.replaceStart.coerceIn(0, text.length)
        val end = suggestion.replaceEnd.coerceIn(start, text.length)
        val updated = text.substring(0, start) + suggestion.insertText + text.substring(end)
        val cursor = (start + suggestion.insertText.length).coerceIn(0, updated.length)
        _state.update { it.copy(input = updated, cursor = cursor) }
        analyze(updated, cursor)
        _state.update { it.copy(completions = emptyList()) }
    }

    fun historyPrevious() {
        val history = _state.value.history
        if (history.isEmpty()) return
        val current = historyCursor
        val next = when {
            current == null -> history.lastIndex
            current > 0 -> current - 1
            else -> 0
        }
        historyCursor = next
        val text = history[next]
        _state.update { it.copy(input = text, cursor = text.length) }
        analyze()
    }

    fun historyNext() {
        val history = _state.value.history
        if (history.isEmpty()) return
        val current = historyCursor ?: return
        val next = current + 1
        if (next >= history.size) {
            historyCursor = null
            _state.update { it.copy(input = "", cursor = 0, completions = emptyList(), diagnostics = emptyList()) }
            return
        }
        historyCursor = next
        val text = history[next]
        _state.update { it.copy(input = text, cursor = text.length) }
        analyze()
    }

    fun applyFix(diagnostic: CodeDiagnostic) {
        val fix = diagnostic.fix ?: return
        val text = _state.value.input
        val lineStart = lineStartOffset(text, diagnostic.line)
        if (lineStart < 0) return
        val lineEnd = lineEndOffset(text, lineStart)
        val updated: String
        val newCursor: Int
        when (fix.kind) {
            FixKind.APPEND -> {
                val insertText = fix.text ?: return
                val insertAt = lineEnd.coerceIn(lineStart, text.length)
                updated = text.substring(0, insertAt) + insertText + text.substring(insertAt)
                newCursor = insertAt + insertText.length
            }
            FixKind.REPLACE -> {
                val insertText = fix.text ?: return
                val start = (lineStart + diagnostic.column).coerceIn(lineStart, lineEnd)
                val end = if (diagnostic.endColumn > diagnostic.column) {
                    (lineStart + diagnostic.endColumn).coerceIn(start, lineEnd)
                } else {
                    start
                }
                updated = text.substring(0, start) + insertText + text.substring(end)
                newCursor = start + insertText.length
            }
            FixKind.NONE -> return
        }
        _state.update { it.copy(input = updated, cursor = newCursor) }
        analyze(updated, newCursor)
    }

    private fun analyze(input: String = _state.value.input, cursor: Int = _state.value.cursor) {
        val tokens = analyzer.highlight(input)
        val diagnostics = analyzer.diagnose(input)
        val bracketPair = BracketMatcher.pairAt(
            code = input,
            cursor = cursor,
            pairs = analyzer.currentSyntax()?.brackets ?: BracketMatcher.DEFAULT_PAIRS,
        )
        val completions = if (input.isBlank()) emptyList() else analyzer.complete(input, cursor, COMPLETION_LIMIT)
        _state.update {
            it.copy(
                tokens = tokens,
                diagnostics = diagnostics,
                bracketPair = bracketPair,
                completions = completions,
            )
        }
    }

    // ---------------- 执行 ----------------

    fun submit() {
        val snapshot = _state.value
        if (snapshot.running) {
            _state.update { it.copy(notice = "上一条命令仍在执行，请先停止或等待结束") }
            return
        }
        val command = snapshot.input.trim()
        if (command.isEmpty()) return
        val runtime = snapshot.runtimeOptions.firstOrNull { it.id == snapshot.selectedRuntimeId }
        if (runtime == null || !runtime.available) {
            appendLocalFailure(
                command = command,
                reason = "运行时「${runtime?.displayName ?: snapshot.selectedRuntimeId}」未安装或不可用",
                hint = "可在顶部切换到安全 Shell；Python/JavaScript 运行时 P0 未提供",
            )
            return
        }
        val plugin: RuntimePlugin? = try {
            runtimeRegistry.plugin(snapshot.selectedRuntimeId)
        } catch (t: Throwable) {
            null
        }
        if (plugin == null) {
            appendLocalFailure(
                command = command,
                reason = "运行时不可用：${snapshot.selectedRuntimeId}",
                hint = "请切换到安全 Shell 后重试；执行结果不会由界面伪造",
            )
            return
        }
        startRun(command, snapshot, plugin)
    }

    private fun startRun(command: String, snapshot: TerminalUiState, plugin: RuntimePlugin) {
        val blockId = UUID.randomUUID().toString()
        val sessionId = UUID.randomUUID().toString()
        val cwd = snapshot.cwd
        _state.update { state ->
            state.copy(
                blocks = state.blocks + TerminalBlock(
                    id = blockId,
                    command = command,
                    cwd = cwd,
                    startedAtEpochMs = System.currentTimeMillis(),
                ),
                history = (state.history + command).takeLast(MAX_HISTORY),
                input = "",
                cursor = 0,
                running = true,
                activeBlockId = blockId,
                diagnostics = emptyList(),
                completions = emptyList(),
                notice = null,
            )
        }
        analyzer.setHistory(_state.value.history)
        historyCursor = null

        runJob = scope.launch(Dispatchers.IO) {
            var sawFinished = false
            try {
                val session = plugin.start(
                    RunRequest(sessionId = sessionId, input = command, cwd = cwd, limits = defaultLimits),
                )
                activeSession = session
                session.events.collect { event ->
                    if (event is RuntimeEvent.Finished) sawFinished = true
                    handleEvent(blockId, event)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                failBlock(blockId, mapFailure(t))
            } finally {
                activeSession = null
                if (!sawFinished) finishUnexpected(blockId)
                _state.update { it.copy(running = false, activeBlockId = null) }
            }
        }
    }

    fun cancelActive() {
        val session = activeSession
        val blockId = _state.value.activeBlockId
        if (session == null || blockId == null) return
        updateBlock(blockId) { it.copy(cancelRequested = true) }
        _state.update { it.copy(notice = "正在停止…") }
        scope.launch {
            try {
                session.cancel()
            } catch (t: Throwable) {
                // cancel() 幂等且允许抛异常；这里不把取消失败扩散成执行错误
            }
            delay(CANCEL_FALLBACK_MS)
            if (_state.value.running && _state.value.activeBlockId == blockId) {
                runJob?.cancel()
                markCancelled(blockId)
                _state.update {
                    it.copy(running = false, activeBlockId = null, notice = "已停止（运行时未返回结束事件）")
                }
            }
        }
    }

    private fun handleEvent(blockId: String, event: RuntimeEvent) {
        when (event) {
            is RuntimeEvent.Started -> updateBlock(blockId) { block ->
                block.copy(startedAtEpochMs = if (block.startedAtEpochMs <= 0) event.atEpochMs else block.startedAtEpochMs)
            }
            is RuntimeEvent.Stdout -> updateBlock(blockId) { block ->
                block.copy(stdout = appendCapped(block.stdout, event.text))
            }
            is RuntimeEvent.Stderr -> updateBlock(blockId) { block ->
                block.copy(stderr = appendCapped(block.stderr, event.text))
            }
            is RuntimeEvent.Diagnostic -> updateBlock(blockId) { block ->
                block.copy(diagnostics = (block.diagnostics + event.diagnostic).take(MAX_BLOCK_DIAGNOSTICS))
            }
            is RuntimeEvent.LimitReached -> updateBlock(blockId) { block ->
                block.copy(limits = (block.limits + event.kind).distinct())
            }
            is RuntimeEvent.Finished -> {
                updateBlock(blockId) { block ->
                    block.copy(
                        finished = true,
                        exitCode = event.exitCode,
                        durationMs = event.durationMs,
                        cancelled = event.cancelled,
                    )
                }
                if (!event.cancelled && event.exitCode == 0) applyWorkingDirectory(blockId)
            }
        }
    }

    private fun applyWorkingDirectory(blockId: String) {
        val block = _state.value.blocks.firstOrNull { it.id == blockId } ?: return
        val args = AnalysisText.splitArgs(block.command)
        if (args.isEmpty() || args[0] != "cd") return
        val target = if (args.size >= 2) args[1] else "."
        val newCwd = normalizeCwd(block.cwd, target) ?: return
        _state.update { it.copy(cwd = newCwd) }
    }

    private fun appendLocalFailure(command: String, reason: String, hint: String) {
        _state.update { state ->
            state.copy(
                blocks = state.blocks + TerminalBlock(
                    id = UUID.randomUUID().toString(),
                    command = command,
                    cwd = state.cwd,
                    startedAtEpochMs = System.currentTimeMillis(),
                    finished = true,
                    errorHint = "$reason。$hint",
                ),
                history = (state.history + command).takeLast(MAX_HISTORY),
                input = "",
                cursor = 0,
                diagnostics = emptyList(),
                completions = emptyList(),
                notice = reason,
            )
        }
        analyzer.setHistory(_state.value.history)
    }

    private fun failBlock(blockId: String, error: AppError) {
        updateBlock(blockId) { block ->
            if (block.finished) block else block.copy(finished = true, errorHint = error.userHint)
        }
        _state.update { it.copy(notice = error.userHint) }
    }

    private fun finishUnexpected(blockId: String) {
        updateBlock(blockId) { block ->
            if (block.finished) {
                block
            } else {
                block.copy(
                    finished = true,
                    cancelled = block.cancelRequested,
                    errorHint = if (block.cancelRequested) null else "执行流意外结束（未收到 Finished 事件）",
                )
            }
        }
    }

    private fun markCancelled(blockId: String) {
        updateBlock(blockId) { block ->
            if (block.finished) block else block.copy(finished = true, cancelled = true)
        }
    }

    private fun mapFailure(t: Throwable): AppError = when (t) {
        is java.util.concurrent.TimeoutException -> AppError.Timeout("终端执行", t)
        is java.io.IOException -> AppError.Internal("终端读写异常", t)
        else -> AppError.Internal(t::class.simpleName ?: "未知错误", t)
    }

    // ---------------- 输出 / 历史 / 折叠 ----------------

    fun clearScreen() {
        if (_state.value.running) {
            _state.update { it.copy(notice = "执行中不能清屏，请先停止或等待结束") }
            return
        }
        _state.update { it.copy(blocks = emptyList(), diagnostics = emptyList(), notice = null) }
    }

    fun clearHistory() {
        historyCursor = null
        analyzer.setHistory(emptyList())
        _state.update { it.copy(history = emptyList()) }
    }

    fun toggleBlockCollapsed(blockId: String) {
        updateBlock(blockId) { it.copy(collapsed = !it.collapsed) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    // ---------------- 工具 ----------------

    private fun updateBlock(blockId: String, transform: (TerminalBlock) -> TerminalBlock) {
        _state.update { state ->
            state.copy(blocks = state.blocks.map { block -> if (block.id == blockId) transform(block) else block })
        }
    }

    private fun appendCapped(existing: String, chunk: String): String {
        if (chunk.isEmpty()) return existing
        if (existing.length >= MAX_OUTPUT_CHARS) return existing
        val remaining = MAX_OUTPUT_CHARS - existing.length
        return if (chunk.length <= remaining) {
            existing + chunk
        } else {
            existing + chunk.substring(0, remaining) + "\n…（输出过长，仅显示前 $MAX_OUTPUT_CHARS 字符）"
        }
    }

    private fun normalizeCwd(current: String, target: String): String? {
        if (target.startsWith("/") || target.startsWith("~")) return null
        val parts = ArrayList<String>()
        (current.split('/') + target.split('/')).forEach { segment ->
            when {
                segment.isEmpty() || segment == "." -> Unit
                segment == ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex) else return null
                else -> parts += segment
            }
        }
        return if (parts.isEmpty()) "." else parts.joinToString("/")
    }

    private fun lineStartOffset(text: String, line: Int): Int {
        if (line <= 1) return 0
        var currentLine = 1
        var index = 0
        while (index < text.length) {
            if (text[index] == '\n') {
                currentLine += 1
                if (currentLine == line) return index + 1
            }
            index += 1
        }
        return -1
    }

    private fun lineEndOffset(text: String, lineStart: Int): Int {
        val newline = text.indexOf('\n', lineStart)
        return if (newline < 0) text.length else newline
    }

    override fun onCleared() {
        val session = activeSession
        runJob?.cancel()
        if (session != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    session.cancel()
                } catch (t: Throwable) {
                    // 页面关闭时尽力取消，不向外抛
                }
            }
        }
        scope.cancel()
    }

    companion object {
        const val KIND_BUILTIN_SHELL = "安全 Shell"
        const val COMPLETION_LIMIT = 6
        const val MAX_INPUT_CHARS = DefaultSyntaxAnalyzer.MAX_ANALYZE_CHARS
        const val MAX_OUTPUT_CHARS = 200_000
        const val MAX_HISTORY = 100
        const val MAX_BLOCK_DIAGNOSTICS = 40
        private const val CANCEL_FALLBACK_MS = 2_000L
    }
}
