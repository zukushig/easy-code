package com.codetree.feature.terminal.model

import com.codetree.feature.terminal.analysis.BracketPair
import com.codetree.runtime.api.CodeDiagnostic
import com.codetree.runtime.api.CompletionSuggestion
import com.codetree.runtime.api.DiagnosticSeverity
import com.codetree.runtime.api.TokenSpan

/** 输入高亮语言选项（安全 Shell / Python / JavaScript…）。 */
data class LanguageOption(val id: String, val label: String)

/** 顶部运行时选择器的一项：available=false 时显示“未安装”，不伪造输出。 */
data class RuntimeOption(
    val id: String,
    val displayName: String,
    val available: Boolean,
    val kindLabel: String,
)

/**
 * 一条命令的执行记录。所有输出字段都只来自 [com.codetree.runtime.api.RuntimeEvent]，
 * 本地失败（运行时不可用/启动异常）使用 [errorHint] 明确标注，不产生假 stdout/exitCode。
 */
data class TerminalBlock(
    val id: String,
    val command: String,
    val cwd: String,
    val startedAtEpochMs: Long,
    val stdout: String = "",
    val stderr: String = "",
    val diagnostics: List<CodeDiagnostic> = emptyList(),
    val limits: List<String> = emptyList(),
    val exitCode: Int? = null,
    val durationMs: Long? = null,
    val cancelled: Boolean = false,
    val finished: Boolean = false,
    val cancelRequested: Boolean = false,
    val errorHint: String? = null,
    val collapsed: Boolean = false,
) {
    val running: Boolean get() = !finished

    /** 是否有失败信号（退出码非 0 / stderr / 本地错误），用于「问 AI」入口。 */
    val hasFailure: Boolean
        get() = errorHint != null || (exitCode ?: 0) != 0 || stderr.isNotBlank()

    val hasOutput: Boolean
        get() = stdout.isNotBlank() || stderr.isNotBlank() || diagnostics.isNotEmpty() || limits.isNotEmpty()

    /** 「问 AI」优先携带 stderr；没有 stderr 时用退出摘要作为错误上下文。 */
    fun errorTextForAi(): String? = when {
        stderr.isNotBlank() -> stderr
        errorHint != null -> errorHint
        (exitCode ?: 0) != 0 -> "命令失败，退出码 $exitCode"
        else -> null
    }
}

/** 终端页面状态（ViewModel 暴露给 Compose 的唯一状态）。 */
data class TerminalUiState(
    val runtimeOptions: List<RuntimeOption> = emptyList(),
    val selectedRuntimeId: String = "",
    val syntaxLanguages: List<LanguageOption> = emptyList(),
    val highlightLanguage: String = "",
    val input: String = "",
    val cursor: Int = 0,
    val cwd: String = ".",
    val blocks: List<TerminalBlock> = emptyList(),
    val history: List<String> = emptyList(),
    val running: Boolean = false,
    val activeBlockId: String? = null,
    val diagnostics: List<CodeDiagnostic> = emptyList(),
    val completions: List<CompletionSuggestion> = emptyList(),
    val tokens: List<TokenSpan> = emptyList(),
    val bracketPair: BracketPair? = null,
    val notice: String? = null,
) {
    val selectedRuntime: RuntimeOption? get() = runtimeOptions.firstOrNull { it.id == selectedRuntimeId }
    val runtimeReady: Boolean get() = selectedRuntime?.available == true
    val latestBlock: TerminalBlock? get() = blocks.lastOrNull()
    val lastFailedBlock: TerminalBlock? get() = blocks.lastOrNull { it.hasFailure }
    val errorCount: Int get() = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val diagnosticsCount: Int get() = diagnostics.size
    val historyCount: Int get() = history.size
    val isEmpty: Boolean get() = blocks.isEmpty()
}
