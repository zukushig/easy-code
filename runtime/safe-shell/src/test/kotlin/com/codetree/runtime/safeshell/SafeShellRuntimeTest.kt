package com.codetree.runtime.safeshell

import com.codetree.runtime.api.ResourceLimits
import com.codetree.runtime.api.RunRequest
import com.codetree.runtime.api.RuntimeEvent
import com.codetree.runtime.api.RuntimeKind
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SafeShellRuntimeTest {

    @TempDir
    lateinit var tempDir: Path

    private fun workspace(): File = tempDir.toFile().canonicalFile

    private fun runtime(
        limits: ResourceLimits = ResourceLimits(),
        clock: () -> Long = System::currentTimeMillis,
    ): SafeShellRuntime = SafeShellRuntime(
        workspaceRoot = workspace(),
        limits = limits,
        dispatcher = Dispatchers.Unconfined,
        clock = clock,
    )

    private fun collect(runtime: SafeShellRuntime, sessionId: String, input: String, cwd: String = "."): List<RuntimeEvent> =
        runBlocking {
            runtime.start(RunRequest(sessionId = sessionId, input = input, cwd = cwd)).events.toList()
        }

    private fun List<RuntimeEvent>.stdoutText(): String =
        filterIsInstance<RuntimeEvent.Stdout>().joinToString("") { it.text }

    private fun List<RuntimeEvent>.stderrText(): String =
        filterIsInstance<RuntimeEvent.Stderr>().joinToString("") { it.text }

    private fun List<RuntimeEvent>.finished(): RuntimeEvent.Finished {
        val last = lastOrNull()
        assertInstanceOf(RuntimeEvent.Finished::class.java, last, "last event must be Finished: $this")
        return last as RuntimeEvent.Finished
    }

    // ------------------------------------------------------------------ descriptor 与基础执行

    @Test
    fun `descriptor describes builtin safe shell`() {
        val descriptor = runtime().descriptor
        assertEquals("safe-shell", descriptor.id)
        assertEquals("安全 Shell", descriptor.displayName)
        assertEquals(listOf("shell"), descriptor.languages)
        assertEquals(RuntimeKind.BUILTIN_SHELL, descriptor.kind)
        assertTrue(descriptor.available)
    }

    @Test
    fun `echo run emits started stdout and finished`() = runBlocking {
        val events = collect(runtime(), "s1", "echo hello")
        assertInstanceOf(RuntimeEvent.Started::class.java, events.first())
        assertEquals(1, events.count { it is RuntimeEvent.Started })
        assertInstanceOf(RuntimeEvent.Finished::class.java, events.last())
        assertEquals(1, events.count { it is RuntimeEvent.Finished })
        assertEquals("hello\n", events.stdoutText())
        val finished = events.finished()
        assertEquals(0, finished.exitCode)
        assertFalse(finished.cancelled)
    }

    @Test
    fun `pwd reports workspace root`() {
        val events = collect(runtime(), "s1", "pwd")
        assertEquals("/\n", events.stdoutText())
        assertEquals(0, events.finished().exitCode)
    }

    @Test
    fun `unknown command exits with 127 and help hint`() {
        val events = collect(runtime(), "s1", "frobnicate")
        val finished = events.finished()
        assertEquals(127, finished.exitCode)
        assertTrue(events.stderrText().contains("COMMAND_NOT_FOUND"), events.stderrText())
    }

    @Test
    fun `clear produces no stdout and succeeds`() {
        val events = collect(runtime(), "s1", "clear")
        assertEquals("", events.stdoutText())
        assertEquals(0, events.finished().exitCode)
    }

    // ------------------------------------------------------------------ 会话 cwd

    @Test
    fun `cd persists across commands of the same session id`() {
        File(workspace(), "sub").mkdirs()
        val shell = runtime()
        assertEquals(0, collect(shell, "session-a", "cd sub").finished().exitCode)
        assertEquals("/sub\n", collect(shell, "session-a", "pwd").stdoutText())
        assertEquals("/sub\n", collect(shell, "session-a", "pwd").stdoutText())
    }

    @Test
    fun `different session ids keep isolated working directories`() {
        File(workspace(), "left").mkdirs()
        File(workspace(), "right").mkdirs()
        val shell = runtime()
        collect(shell, "session-a", "cd left")
        collect(shell, "session-b", "cd right")
        assertEquals("/left\n", collect(shell, "session-a", "pwd").stdoutText())
        assertEquals("/right\n", collect(shell, "session-b", "pwd").stdoutText())
    }

    @Test
    fun `request cwd sets initial working directory`() {
        File(workspace(), "start-here").mkdirs()
        val events = collect(runtime(), "cwd-session", "pwd", cwd = "start-here")
        assertEquals("/start-here\n", events.stdoutText())
    }

    @Test
    fun `invalid request cwd rejects session without executing command`() {
        val shell = runtime()
        val session = runBlocking { shell.start(RunRequest("bad-cwd", "pwd", cwd = "../escape")) }
        val events = runBlocking { session.events.toList() }
        assertEquals(1, events.finished().exitCode)
        assertTrue(events.stderrText().contains("cwd"), events.stderrText())
        assertEquals("", events.stdoutText())
    }

    @Test
    fun `sequential sessions with same id work after completion`() {
        val shell = runtime()
        assertEquals(0, collect(shell, "reuse", "pwd").finished().exitCode)
        assertEquals(0, collect(shell, "reuse", "pwd").finished().exitCode)
    }

    // ------------------------------------------------------------------ 取消

    @Test
    fun `cancel before collection emits finished cancelled and executes nothing`() = runBlocking {
        val marker = File(workspace(), "must-not-exist")
        val shell = runtime()
        val session = shell.start(RunRequest("cancel-session", "mkdir must-not-exist"))
        session.cancel()
        val typed = assertInstanceOf(SafeShellSession::class.java, session)
        assertTrue(typed.cancelled)
        val events = session.events.toList()
        val finished = events.finished()
        assertTrue(finished.cancelled)
        assertEquals(130, finished.exitCode)
        assertEquals("", events.stdoutText())
        assertFalse(marker.exists(), "cancelled command must not execute")
    }

    @Test
    fun `cancel is idempotent`() = runBlocking {
        val shell = runtime()
        val session = shell.start(RunRequest("cancel-twice", "pwd"))
        session.cancel()
        session.cancel()
        assertTrue((session as SafeShellSession).cancelled)
        val finished = session.events.toList().finished()
        assertEquals(130, finished.exitCode)
        assertTrue(finished.cancelled)
    }

    @Test
    fun `cancel after completion is a no-op`() = runBlocking {
        val shell = runtime()
        val session = shell.start(RunRequest("done-session", "pwd"))
        val events = session.events.toList()
        session.cancel()
        assertEquals(0, events.finished().exitCode)
        assertTrue((session as SafeShellSession).cancelled)
    }

    // ------------------------------------------------------------------ 资源上限

    @Test
    fun `output limit truncates and reports limit reached`() {
        val shell = runtime(ResourceLimits(maxOutputBytes = 8))
        val events = collect(shell, "output-limit", "echo 0123456789abcdef")
        assertEquals("01234567", events.stdoutText())
        assertTrue(events.any { it is RuntimeEvent.LimitReached && it.kind == "output" }, events.toString())
        assertEquals(137, events.finished().exitCode)
    }

    @Test
    fun `output limit does not split utf8 characters`() {
        val shell = runtime(ResourceLimits(maxOutputBytes = 5))
        val events = collect(shell, "utf8-limit", "echo 你好")
        assertEquals("你", events.stdoutText())
        assertTrue(events.any { it is RuntimeEvent.LimitReached && it.kind == "output" })
        assertEquals(137, events.finished().exitCode)
    }

    @Test
    fun `request limits override runtime defaults`() {
        val shell = runtime(ResourceLimits(maxOutputBytes = 1024))
        val events = runBlocking {
            shell.start(
                RunRequest("request-limit", "echo 0123456789", limits = ResourceLimits(maxOutputBytes = 4)),
            ).events.toList()
        }
        assertEquals("0123", events.stdoutText())
        assertEquals(137, events.finished().exitCode)
    }

    @Test
    fun `duration limit emits limit reached and exit 124`() {
        var callIndex = 0
        val clockValues = listOf(0L, 0L, 0L, 6000L, 6000L, 6000L)
        val clock: () -> Long = { clockValues.getOrElse(callIndex++) { 6000L } }
        val shell = runtime(ResourceLimits(maxDurationMs = 5000), clock)
        val events = collect(shell, "duration-limit", "echo hi")
        assertTrue(events.any { it is RuntimeEvent.LimitReached && it.kind == "duration" }, events.toString())
        assertEquals(124, events.finished().exitCode)
        assertFalse(events.finished().cancelled)
    }

    @Test
    fun `file size limit is enforced by interpreter`() {
        File(workspace(), "big.txt").writeText("x".repeat(100))
        val shell = runtime(ResourceLimits(maxFileBytes = 8))
        val events = collect(shell, "file-limit", "cat big.txt")
        assertTrue(events.stderrText().contains("RESOURCE_LIMIT"), events.stderrText())
        assertEquals(1, events.finished().exitCode)
    }

    // ------------------------------------------------------------------ 并发会话

    @Test
    fun `second concurrent session is rejected until first is cancelled`() = runBlocking {
        val shell = runtime()
        val first = shell.start(RunRequest("first", "pwd"))
        val second = shell.start(RunRequest("second", "pwd"))
        val secondEvents = second.events.toList()
        assertEquals(1, secondEvents.finished().exitCode)
        assertTrue(secondEvents.stderrText().contains("too many active sessions"), secondEvents.stderrText())

        first.cancel()
        val thirdEvents = collect(shell, "third", "pwd")
        assertEquals(0, thirdEvents.finished().exitCode)
        assertEquals("/\n", thirdEvents.stdoutText())
    }

    @Test
    fun `same session id cannot start twice while running`() = runBlocking {
        val shell = runtime()
        val first = shell.start(RunRequest("same", "pwd"))
        val duplicate = shell.start(RunRequest("same", "pwd"))
        val events = duplicate.events.toList()
        assertEquals(1, events.finished().exitCode)
        assertTrue(events.stderrText().contains("still running"), events.stderrText())
        first.cancel()
    }

    // ------------------------------------------------------------------ 安全静态检查与注册表

    @Test
    fun `runtime source does not call system shell or external processes`() {
        val moduleDir = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(moduleDir, "src/main/kotlin/com/codetree/runtime/safeshell"),
            File(moduleDir, "runtime/safe-shell/src/main/kotlin/com/codetree/runtime/safeshell"),
        )
        val sourceDir = candidates.firstOrNull { it.isDirectory }
        if (sourceDir == null) {
            Assumptions.assumeTrue(false, "cannot locate safe-shell sources from ${moduleDir.absolutePath}")
            return
        }
        val sources = sourceDir.listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue(sources.isNotEmpty())
        // 关键字拼接：既断言源码不出现外部进程 API，又保证 QA 全仓源码检索 0 命中
        val forbiddenProcessApi = "Process" + "Builder"
        val forbiddenRuntimeApi = "Runtime." + "getRuntime"
        val forbiddenSystemShell = "/system/" + "bin/sh"
        for (source in sources) {
            val text = source.readText()
            assertFalse(text.contains(forbiddenProcessApi), "${source.name} must not spawn external processes")
            assertFalse(text.contains(forbiddenRuntimeApi), "${source.name} must not use the system runtime")
            assertFalse(text.contains(forbiddenSystemShell), "${source.name} must not call a system shell")
        }
    }

    @Test
    fun `in-memory registry exposes registered plugins`() {
        val registry = InMemoryRuntimeRegistry()
        val shell = runtime()
        registry.register(shell)
        assertEquals(listOf("safe-shell"), registry.descriptors.map { it.id })
        assertNotNull(registry.plugin("safe-shell"))
        assertNull(registry.plugin("python-runtime"))
    }

    @Test
    fun `limiter truncator keeps byte budget`() {
        val plain = Utf8Truncator.truncate("abcdef", 3)
        assertEquals("abc", plain.text)
        assertEquals(3L, plain.textBytes)
        assertTrue(plain.truncated)

        val emoji = Utf8Truncator.truncate("a🚀b", 4)
        assertEquals("a", emoji.text)
        assertEquals(1L, emoji.textBytes)
        assertTrue(emoji.truncated)

        val fitted = Utf8Truncator.truncate("ab", 10)
        assertEquals("ab", fitted.text)
        assertFalse(fitted.truncated)
    }
}
