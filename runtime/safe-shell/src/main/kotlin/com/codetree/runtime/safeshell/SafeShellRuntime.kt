package com.codetree.runtime.safeshell

import com.codetree.runtime.api.ResourceLimits
import com.codetree.runtime.api.RunRequest
import com.codetree.runtime.api.RuntimeDescriptor
import com.codetree.runtime.api.RuntimeEvent
import com.codetree.runtime.api.RuntimeKind
import com.codetree.runtime.api.RuntimePlugin
import com.codetree.runtime.api.RuntimeRegistry
import com.codetree.runtime.api.RuntimeSession
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * P0 安全终端运行时（`RuntimePlugin`，ADR-003 / docs/specs/terminal.md）。
 *
 * - 只实现白名单内置命令（见 [CommandPolicy]），不启动 PTY、不调用系统 shell、不执行外部程序；
 * - 所有文件操作限制在 [workspaceRoot]，路径经 [CommandInterpreter] 段级规范化 + 符号链接检查；
 * - 资源上限：输出字节（超出截断 + `LimitReached("output")` + exit 137）、
 *   执行时长（`LimitReached("duration")` + exit 124）、单文件大小、并发会话数；
 * - 取消语义：`cancel()` 幂等；取消后事件流以 `Finished(cancelled = true)` 收敛，
 *   不再追加 `Stdout`/`Stderr`；命令执行是同步有界的文件操作，取消标记在每个事件边界检查；
 * - 事件流在**首次收集时**才执行命令：UI 进入页面即 collect、离开页面 cancel，天然避免
 *   “启动了但没人消费”的泄漏；未收集前 cancel 不会执行任何命令；
 * - cwd 会话作用域：同一 `sessionId` 的多次 `start` 共享 cwd（`cd` 生效到下一条命令），
 *   不同 `sessionId` 完全隔离。
 */
class SafeShellRuntime(
    private val workspaceRoot: File,
    private val limits: ResourceLimits = ResourceLimits(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) : RuntimePlugin {

    override val descriptor: RuntimeDescriptor = RuntimeDescriptor(
        id = DESCRIPTOR_ID,
        displayName = "安全 Shell",
        languages = listOf("shell"),
        kind = RuntimeKind.BUILTIN_SHELL,
        available = true,
    )

    private val sessions = ConcurrentHashMap<String, SessionState>()

    internal class SessionState {
        val shell: CommandInterpreter.ShellSessionState = CommandInterpreter.ShellSessionState()
        val active: AtomicBoolean = AtomicBoolean(false)
        val cancelled: AtomicBoolean = AtomicBoolean(false)
    }

    override suspend fun start(request: RunRequest): RuntimeSession {
        val effectiveLimits = effectiveLimits(request.limits)
        val sessionState = sessions.computeIfAbsent(request.sessionId) { SessionState() }

        if (sessionState.active.get()) {
            return RejectedSession(
                id = request.sessionId,
                message = "session ${request.sessionId} is still running; wait for it or cancel it first",
                clock = clock,
            )
        }
        val activeSessions = sessions.values.count { it.active.get() }
        if (activeSessions >= effectiveLimits.maxSessions.coerceAtLeast(1)) {
            return RejectedSession(
                id = request.sessionId,
                message = "too many active sessions (max ${effectiveLimits.maxSessions}); finish the current session first",
                clock = clock,
            )
        }
        if (!sessionState.active.compareAndSet(false, true)) {
            return RejectedSession(
                id = request.sessionId,
                message = "session ${request.sessionId} is still running",
                clock = clock,
            )
        }

        val interpreter = CommandInterpreter(workspaceRoot, effectiveLimits, clock)
        if (request.cwd != DEFAULT_CWD && request.cwd.isNotBlank()) {
            val resolved = interpreter.resolveWorkingDirectory(request.cwd, sessionState.shell.cwd)
            if (resolved == null) {
                sessionState.active.set(false)
                return RejectedSession(
                    id = request.sessionId,
                    message = "cwd '${request.cwd}' is not a directory inside the workspace",
                    clock = clock,
                )
            }
            sessionState.shell.cwd = resolved
        }

        return SafeShellSession(
            id = request.sessionId,
            state = sessionState,
            limits = effectiveLimits,
            interpreter = interpreter,
            input = request.input,
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            clock = clock,
        )
    }

    /** 未显式指定的字段回退到运行时构造参数默认值（显式字段优先，便于测试与上层收紧上限）。 */
    private fun effectiveLimits(requested: ResourceLimits): ResourceLimits {
        val base = limits
        val defaults = ResourceLimits()
        return ResourceLimits(
            maxOutputBytes = if (requested.maxOutputBytes != defaults.maxOutputBytes) {
                requested.maxOutputBytes
            } else {
                base.maxOutputBytes
            },
            maxDurationMs = if (requested.maxDurationMs != defaults.maxDurationMs) {
                requested.maxDurationMs
            } else {
                base.maxDurationMs
            },
            maxFileBytes = if (requested.maxFileBytes != defaults.maxFileBytes) {
                requested.maxFileBytes
            } else {
                base.maxFileBytes
            },
            maxSessions = if (requested.maxSessions != defaults.maxSessions) {
                requested.maxSessions
            } else {
                base.maxSessions
            },
        )
    }

    private companion object {
        const val DESCRIPTOR_ID = "safe-shell"
        const val DEFAULT_CWD = "."
    }
}

/**
 * 一次命令执行会话：`events` 冷启动（首次收集才执行），`cancel()` 幂等。
 * 事件顺序保证：`Started` 首个且最多一次、`Finished` 恰好一次且最后；
 * 取消后只出现 `Started` + `Finished(cancelled = true)`，不追加输出。
 */
class SafeShellSession internal constructor(
    override val id: String,
    internal val state: SafeShellRuntime.SessionState,
    private val limits: ResourceLimits,
    private val interpreter: CommandInterpreter,
    private val input: String,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) : RuntimeSession {

    private val channel = Channel<RuntimeEvent>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)

    /** 只读取消状态，便于 UI/测试验证（取消后必须为 true）。 */
    val cancelled: Boolean get() = state.cancelled.get()

    override val events: Flow<RuntimeEvent> = channel.receiveAsFlow().onStart {
        if (started.compareAndSet(false, true)) {
            scope.launch { produce() }
        }
    }

    override suspend fun cancel() {
        state.cancelled.set(true)
        if (!started.get()) {
            // 尚未开始执行：立即释放会话名额，且收集时不会执行任何命令
            state.active.set(false)
        }
    }

    private suspend fun produce() {
        val startedAt = clock()
        var exitCode = 0
        var wasCancelled = false
        try {
            channel.send(RuntimeEvent.Started(startedAt))
            if (state.cancelled.get()) {
                wasCancelled = true
                exitCode = CANCELLED_EXIT_CODE
            } else {
                val result = try {
                    interpreter.execute(input, state.shell)
                } catch (e: Exception) {
                    CommandInterpreter.CommandResult(
                        stderr = "INTERNAL: ${e.message ?: e::class.simpleName ?: "internal error"}",
                        exitCode = 1,
                    )
                }
                if (state.cancelled.get()) {
                    wasCancelled = true
                    exitCode = CANCELLED_EXIT_CODE
                } else if (result.limitKind == LIMIT_DURATION) {
                    channel.send(RuntimeEvent.LimitReached(LIMIT_DURATION))
                    exitCode = DURATION_LIMIT_EXIT_CODE
                } else {
                    var remaining = limits.maxOutputBytes
                    var truncated = false
                    if (result.stdout.isNotEmpty()) {
                        val chunk = Utf8Truncator.truncate(result.stdout, remaining)
                        if (chunk.text.isNotEmpty()) {
                            channel.send(RuntimeEvent.Stdout(chunk.text))
                            remaining -= chunk.textBytes
                        }
                        truncated = truncated || chunk.truncated
                    }
                    if (result.stderr.isNotEmpty() && !truncated) {
                        val chunk = Utf8Truncator.truncate(result.stderr, remaining)
                        if (chunk.text.isNotEmpty()) {
                            channel.send(RuntimeEvent.Stderr(chunk.text))
                            remaining -= chunk.textBytes
                        }
                        truncated = truncated || chunk.truncated
                    }
                    if (state.cancelled.get()) {
                        wasCancelled = true
                        exitCode = CANCELLED_EXIT_CODE
                    } else if (truncated) {
                        channel.send(RuntimeEvent.LimitReached(LIMIT_OUTPUT))
                        exitCode = OUTPUT_LIMIT_EXIT_CODE
                    } else {
                        exitCode = result.exitCode
                    }
                }
            }
        } catch (_: Exception) {
            if (!wasCancelled) exitCode = 1
        } finally {
            val duration = (clock() - startedAt).coerceAtLeast(0L)
            channel.trySend(RuntimeEvent.Finished(exitCode, duration, cancelled = wasCancelled))
            channel.close()
            state.active.set(false)
        }
    }

    private companion object {
        const val CANCELLED_EXIT_CODE = 130
        const val DURATION_LIMIT_EXIT_CODE = 124
        const val OUTPUT_LIMIT_EXIT_CODE = 137
        const val LIMIT_OUTPUT = "output"
        const val LIMIT_DURATION = "duration"
    }
}

/** 被拒绝的会话（并发超限 / 同一会话正在运行 / 非法 cwd）：只返回可读错误，不执行任何命令。 */
private class RejectedSession(
    override val id: String,
    message: String,
    clock: () -> Long,
) : RuntimeSession {

    override val events: Flow<RuntimeEvent> = flowOf(
        RuntimeEvent.Started(clock()),
        RuntimeEvent.Stderr("COMMAND_REJECTED: $message"),
        RuntimeEvent.Finished(exitCode = 1, durationMs = 0L, cancelled = false),
    )

    override suspend fun cancel() = Unit
}

/** 按 UTF-8 字节上限截断文本，且不切断多字节字符（emoji 也不会被劈开）。 */
internal object Utf8Truncator {

    data class Truncated(val text: String, val textBytes: Long, val truncated: Boolean)

    fun truncate(text: String, maxBytes: Long): Truncated {
        if (maxBytes <= 0L) return Truncated("", 0L, text.isNotEmpty())
        var bytes = 0L
        var end = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val byteSize = utf8Size(codePoint).toLong()
            if (bytes + byteSize > maxBytes) break
            bytes += byteSize
            end = index + charCount
            index += charCount
        }
        return Truncated(text.substring(0, end), bytes, truncated = end < text.length)
    }

    private fun utf8Size(codePoint: Int): Int = when {
        codePoint <= 0x7F -> 1
        codePoint <= 0x7FF -> 2
        codePoint <= 0xFFFF -> 3
        else -> 4
    }
}

/** 极简 `RuntimeRegistry` 实现：注册顺序稳定，供 app 组合根直接使用。 */
class InMemoryRuntimeRegistry : RuntimeRegistry {

    private val plugins = LinkedHashMap<String, RuntimePlugin>()

    override val descriptors: List<RuntimeDescriptor>
        get() = synchronized(plugins) { plugins.values.map { it.descriptor } }

    override fun plugin(runtimeId: String): RuntimePlugin? = synchronized(plugins) { plugins[runtimeId] }

    override fun register(plugin: RuntimePlugin) {
        synchronized(plugins) {
            plugins[plugin.descriptor.id] = plugin
        }
    }
}
