package com.codetree.runtime.api

import kotlinx.coroutines.flow.Flow

/** 运行时类型：P0 只有内置安全 shell；Python/JavaScript 为后续版本。 */
enum class RuntimeKind { BUILTIN_SHELL, PYTHON, JAVASCRIPT }

data class RuntimeDescriptor(
    val id: String,
    val displayName: String,
    val languages: List<String> = emptyList(),
    val kind: RuntimeKind = RuntimeKind.BUILTIN_SHELL,
    val available: Boolean = true,
)

/** 资源上限（docs/02 §8）：输出、时长、单文件大小都必须是显式上限。 */
data class ResourceLimits(
    val maxOutputBytes: Long = 64L * 1024,
    val maxDurationMs: Long = 5000L,
    val maxFileBytes: Long = 1024L * 1024,
    val maxSessions: Int = 1,
)

data class RunRequest(
    val sessionId: String,
    val input: String,
    val cwd: String = ".",
    val limits: ResourceLimits = ResourceLimits(),
)

enum class DiagnosticSeverity { ERROR, WARNING, INFO }

enum class FixKind { APPEND, REPLACE, NONE }

data class DiagnosticFix(val kind: FixKind, val text: String? = null)

/** 代码诊断：位置 + 原因 + 可选修复（docs/01 TRM-004）。 */
data class CodeDiagnostic(
    val severity: DiagnosticSeverity,
    val line: Int,
    val column: Int = 0,
    val endColumn: Int = 0,
    val message: String,
    val hint: String? = null,
    val fix: DiagnosticFix? = null,
    val ruleId: String? = null,
)

sealed interface RuntimeEvent {
    data class Started(val atEpochMs: Long) : RuntimeEvent
    data class Stdout(val text: String) : RuntimeEvent
    data class Stderr(val text: String) : RuntimeEvent
    data class Diagnostic(val diagnostic: CodeDiagnostic) : RuntimeEvent
    data class LimitReached(val kind: String) : RuntimeEvent
    data class Finished(val exitCode: Int, val durationMs: Long, val cancelled: Boolean = false) : RuntimeEvent
}

/** 一次执行会话：事件流与取消语义分离，取消必须可验证（docs/02 §4.5）。 */
interface RuntimeSession {
    val id: String
    val events: Flow<RuntimeEvent>
    suspend fun cancel()
}

/** 运行时插件 SPI：只负责执行，不具备 UI/网络/密钥能力。 */
interface RuntimePlugin {
    val descriptor: RuntimeDescriptor
    suspend fun start(request: RunRequest): RuntimeSession
}

interface RuntimeRegistry {
    val descriptors: List<RuntimeDescriptor>
    fun plugin(runtimeId: String): RuntimePlugin?
    fun register(plugin: RuntimePlugin)
}

// ---------------- 终端分析 SPI（高亮 / 诊断 / 补全） ----------------

enum class TokenKind { PLAIN, KEYWORD, BUILTIN, STRING, COMMENT, NUMBER, OPERATOR, IDENTIFIER, FUNCTION, PUNCTUATION }

data class TokenSpan(val start: Int, val end: Int, val kind: TokenKind)

enum class CompletionKind { COMMAND, KEYWORD, BUILTIN, WORD, PATH }

/** 补全建议带上替换区间，接受建议后不覆盖后续文本（docs/01 TRM-005）。 */
data class CompletionSuggestion(
    val text: String,
    val insertText: String,
    val detail: String = "",
    val kind: CompletionKind = CompletionKind.WORD,
    val replaceStart: Int,
    val replaceEnd: Int,
)

interface SyntaxAnalyzer {
    fun highlight(code: String): List<TokenSpan>
    fun diagnose(code: String): List<CodeDiagnostic>
    fun complete(code: String, cursor: Int, limit: Int = 8): List<CompletionSuggestion>
}
