package com.codetree.runtime.safeshell

import com.codetree.runtime.api.ResourceLimits
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CommandInterpreterTest {

    @TempDir
    lateinit var tempDir: Path

    private fun workspace(): File = tempDir.toFile().canonicalFile

    private fun interpreter(limits: ResourceLimits = ResourceLimits(), clock: () -> Long = System::currentTimeMillis): CommandInterpreter =
        CommandInterpreter(workspace(), limits, clock)

    private fun writeFile(relative: String, content: String): File {
        val file = File(workspace(), relative)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    private fun mkdirs(relative: String): File = File(workspace(), relative).apply { mkdirs() }

    private fun run(command: String, session: CommandInterpreter.ShellSessionState = CommandInterpreter.ShellSessionState()): CommandInterpreter.CommandResult =
        interpreter().execute(command, session)

    // ------------------------------------------------------------------ pwd / cd

    @Test
    fun `pwd prints workspace root as slash`() {
        val result = run("pwd")
        assertEquals(0, result.exitCode)
        assertEquals("/\n", result.stdout)
    }

    @Test
    fun `cd then pwd keeps session working directory`() {
        mkdirs("sub")
        val session = CommandInterpreter.ShellSessionState()
        assertEquals(0, interpreter().execute("cd sub", session).exitCode)
        assertEquals("sub", session.cwd)
        assertEquals("/sub\n", interpreter().execute("pwd", session).stdout)
    }

    @Test
    fun `cd parent stays inside workspace`() {
        mkdirs("sub")
        val session = CommandInterpreter.ShellSessionState()
        interpreter().execute("cd sub", session)
        val result = interpreter().execute("cd ..", session)
        assertEquals(0, result.exitCode)
        assertEquals(".", session.cwd)
    }

    @Test
    fun `cd above workspace root is rejected`() {
        val result = run("cd ..")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("PATH_OUT_OF_SANDBOX"), result.stderr)
    }

    @Test
    fun `cd to file is rejected`() {
        writeFile("a.txt", "hello")
        val result = run("cd a.txt")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("NOT_A_DIRECTORY"), result.stderr)
    }

    @Test
    fun `cd without argument returns to root`() {
        mkdirs("sub")
        val session = CommandInterpreter.ShellSessionState()
        interpreter().execute("cd sub", session)
        assertEquals(0, interpreter().execute("cd", session).exitCode)
        assertEquals(".", session.cwd)
    }

    @Test
    fun `sessions do not share working directory`() {
        mkdirs("left")
        mkdirs("right")
        val first = CommandInterpreter.ShellSessionState()
        val second = CommandInterpreter.ShellSessionState()
        interpreter().execute("cd left", first)
        interpreter().execute("cd right", second)
        assertEquals("left", first.cwd)
        assertEquals("right", second.cwd)
        assertEquals("/left\n", interpreter().execute("pwd", first).stdout)
    }

    // ------------------------------------------------------------------ ls

    @Test
    fun `ls lists sorted entries with directory suffix`() {
        writeFile("b.txt", "b")
        writeFile("a.txt", "a")
        mkdirs("sub")
        val result = run("ls")
        assertEquals(0, result.exitCode)
        assertEquals("a.txt\nb.txt\nsub/\n", result.stdout)
    }

    @Test
    fun `ls accepts a subdirectory path`() {
        writeFile("sub/inner.txt", "x")
        assertEquals("inner.txt\n", run("ls sub").stdout)
    }

    @Test
    fun `ls long format includes size`() {
        writeFile("a.txt", "abc")
        val result = run("ls -l a.txt")
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("3"), result.stdout)
        assertTrue(result.stdout.contains("a.txt"), result.stdout)
    }

    @Test
    fun `ls missing path fails`() {
        val result = run("ls nope")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("NO_SUCH_FILE"), result.stderr)
    }

    @Test
    fun `ls rejects unsupported option`() {
        val result = run("ls -R")
        assertEquals(2, result.exitCode)
        assertTrue(result.stderr.contains("INVALID_ARGUMENTS"), result.stderr)
    }

    // ------------------------------------------------------------------ cat

    @Test
    fun `cat prints exact file content`() {
        writeFile("notes.txt", "第一行\nsecond line\n")
        val result = run("cat notes.txt")
        assertEquals(0, result.exitCode)
        assertEquals("第一行\nsecond line\n", result.stdout)
    }

    @Test
    fun `cat missing file fails`() {
        val result = run("cat missing.txt")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("NO_SUCH_FILE"), result.stderr)
    }

    @Test
    fun `cat directory fails`() {
        mkdirs("sub")
        val result = run("cat sub")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("NOT_A_FILE"), result.stderr)
    }

    @Test
    fun `cat absolute path is rejected`() {
        val result = run("cat /etc/passwd")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("PATH_OUT_OF_SANDBOX"), result.stderr)
    }

    @Test
    fun `cat parent traversal is rejected`() {
        val result = run("cat ../secret.txt")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("PATH_OUT_OF_SANDBOX"), result.stderr)
    }

    @Test
    fun `cat tilde and drive prefixes are rejected`() {
        assertTrue(run("cat ~/secret").stderr.contains("PATH_OUT_OF_SANDBOX"))
        assertTrue(run("cat C:/secret").stderr.contains("PATH_OUT_OF_SANDBOX"))
    }

    @Test
    fun `cat binary file is rejected`() {
        val file = File(workspace(), "bin.dat")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        val result = run("cat bin.dat")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("BINARY_FILE"), result.stderr)
    }

    @Test
    fun `cat rejects invalid utf8`() {
        File(workspace(), "bad.txt").writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41))
        val result = run("cat bad.txt")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("ENCODING_ERROR") || result.stderr.contains("BINARY_FILE"), result.stderr)
    }

    @Test
    fun `cat rejects file larger than maxFileBytes`() {
        writeFile("big.txt", "x".repeat(100))
        val result = interpreter(ResourceLimits(maxFileBytes = 8)).execute("cat big.txt")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("RESOURCE_LIMIT"), result.stderr)
    }

    @Test
    fun `cat rejects symbolic link`() {
        val outside = Files.createTempFile("codetree-outside", ".txt")
        try {
            Files.writeString(outside, "secret")
            val link = File(workspace(), "link.txt").toPath()
            try {
                Files.createSymbolicLink(link, outside)
            } catch (e: Exception) {
                Assumptions.assumeTrue(false, "symlinks not supported: ${e.message}")
                return
            }
            val result = run("cat link.txt")
            assertEquals(1, result.exitCode)
            assertTrue(result.stderr.contains("PATH_OUT_OF_SANDBOX"), result.stderr)
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    // ------------------------------------------------------------------ echo

    @Test
    fun `echo prints arguments joined by space`() {
        val result = run("echo hello world")
        assertEquals(0, result.exitCode)
        assertEquals("hello world\n", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    fun `echo keeps quoted spaces and does not expand variables`() {
        assertEquals("a b\n", run("echo \"a b\"").stdout)
        assertEquals("\$HOME\n", run("echo \$HOME").stdout)
    }

    // ------------------------------------------------------------------ mkdir / rm

    @Test
    fun `mkdir creates directory`() {
        val result = run("mkdir notes")
        assertEquals(0, result.exitCode)
        assertTrue(File(workspace(), "notes").isDirectory)
    }

    @Test
    fun `mkdir existing directory without -p is refused`() {
        mkdirs("notes")
        val result = run("mkdir notes")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("REFUSED"), result.stderr)
    }

    @Test
    fun `mkdir -p creates nested directories and is idempotent`() {
        assertEquals(0, run("mkdir -p a/b/c").exitCode)
        assertTrue(File(workspace(), "a/b/c").isDirectory)
        assertEquals(0, run("mkdir -p a/b/c").exitCode)
    }

    @Test
    fun `mkdir without -p fails when parent missing`() {
        val result = run("mkdir a/b")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("IO_ERROR"), result.stderr)
    }

    @Test
    fun `mkdir traversal is rejected and does not create anything`() {
        val result = run("mkdir ../escape")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("PATH_OUT_OF_SANDBOX"), result.stderr)
        assertFalse(File(workspace().parentFile, "escape").exists())
    }

    @Test
    fun `rm deletes a single file`() {
        val file = writeFile("doomed.txt", "bye")
        val result = run("rm doomed.txt")
        assertEquals(0, result.exitCode)
        assertFalse(file.exists())
    }

    @Test
    fun `rm directory is refused`() {
        mkdirs("keep")
        val result = run("rm keep")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("REFUSED"), result.stderr)
        assertTrue(File(workspace(), "keep").isDirectory)
    }

    @Test
    fun `rm wildcard is refused`() {
        writeFile("a.txt", "a")
        val result = run("rm *.txt")
        assertEquals(2, result.exitCode)
        assertTrue(result.stderr.contains("wildcards"), result.stderr)
        assertTrue(File(workspace(), "a.txt").exists())
    }

    @Test
    fun `rm recursive option is refused`() {
        mkdirs("keep")
        val result = run("rm -r keep")
        assertEquals(2, result.exitCode)
        assertTrue(result.stderr.contains("INVALID_ARGUMENTS"), result.stderr)
    }

    @Test
    fun `rm missing file fails`() {
        val result = run("rm nope.txt")
        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("NO_SUCH_FILE"), result.stderr)
    }

    // ------------------------------------------------------------------ head / wc

    @Test
    fun `head defaults to ten lines`() {
        writeFile("lines.txt", (1..15).joinToString("\n") { "line$it" } + "\n")
        val result = run("head lines.txt")
        assertEquals(0, result.exitCode)
        assertEquals(10, result.stdout.trimEnd('\n').lines().size)
        assertTrue(result.stdout.startsWith("line1\nline2\n"))
    }

    @Test
    fun `head -n limits lines`() {
        writeFile("lines.txt", "a\nb\nc\nd\n")
        assertEquals("a\nb\n", run("head -n 2 lines.txt").stdout)
        assertEquals("a\n", run("head -n 1 lines.txt").stdout)
        assertEquals("a\nb\nc\nd\n", run("head -n 200 lines.txt").stdout)
    }

    @Test
    fun `head rejects invalid counts and options`() {
        writeFile("lines.txt", "a\n")
        assertEquals(2, run("head -n 0 lines.txt").exitCode)
        assertEquals(2, run("head -n 201 lines.txt").exitCode)
        assertEquals(2, run("head -n abc lines.txt").exitCode)
        assertEquals(2, run("head -x lines.txt").exitCode)
    }

    @Test
    fun `head missing file fails`() {
        assertEquals(1, run("head nope.txt").exitCode)
    }

    @Test
    fun `wc counts lines words and bytes`() {
        writeFile("text.txt", "hello world\nsecond line\n")
        val result = run("wc text.txt")
        assertEquals(0, result.exitCode)
        assertEquals("2 4 24 text.txt\n", result.stdout)
    }

    @Test
    fun `wc rejects multiple files`() {
        writeFile("a.txt", "a")
        assertEquals(2, run("wc a.txt b.txt").exitCode)
    }

    // ------------------------------------------------------------------ help / clear

    @Test
    fun `help lists built-in commands`() {
        val result = run("help")
        assertEquals(0, result.exitCode)
        for (command in listOf("pwd", "ls", "cd", "cat", "echo", "mkdir", "rm", "head", "wc", "help", "clear")) {
            assertTrue(result.stdout.contains(command), "help should mention $command")
        }
        assertTrue(result.stdout.contains("No pipes"))
    }

    @Test
    fun `help with command shows usage`() {
        val result = run("help cat")
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("cat <file>"), result.stdout)
    }

    @Test
    fun `help unknown command is an argument error`() {
        val result = run("help python")
        assertEquals(2, result.exitCode)
        assertTrue(result.stderr.contains("INVALID_ARGUMENTS"), result.stderr)
    }

    @Test
    fun `clear returns empty output and success`() {
        val result = run("clear")
        assertEquals(0, result.exitCode)
        assertEquals("", result.stdout)
        assertEquals("", result.stderr)
        assertTrue(result.clearScreen)
    }

    // ------------------------------------------------------------------ 策略与错误

    @Test
    fun `unknown command returns command not found with help hint`() {
        val result = run("frobnicate --now")
        assertEquals(127, result.exitCode)
        assertTrue(result.stderr.contains("COMMAND_NOT_FOUND"), result.stderr)
        assertTrue(result.stderr.contains("help"), result.stderr)
    }

    @Test
    fun `dangerous system programs are refused`() {
        for (command in listOf("git status", "python3 main.py", "node app.js", "adb devices", "su root")) {
            val result = run(command)
            assertEquals(127, result.exitCode, "expected refusal for '$command'")
            assertTrue(result.stderr.contains("COMMAND_NOT_FOUND"), result.stderr)
        }
    }

    @Test
    fun `unsupported syntax does not execute side effects`() {
        val result = run("mkdir side-effect | ls")
        assertEquals(2, result.exitCode)
        assertFalse(File(workspace(), "side-effect").exists())
    }

    @Test
    fun `empty command is rejected`() {
        val result = run("   ")
        assertEquals(2, result.exitCode)
        assertTrue(result.stderr.contains("EMPTY_COMMAND"), result.stderr)
    }

    @Test
    fun `duration limit returns timeout result`() {
        val clockValues = ArrayDeque(listOf(0L, 0L, 6000L, 6000L, 6000L))
        val fakeClock: () -> Long = { clockValues.removeFirstOrNull() ?: 6000L }
        val result = interpreter(ResourceLimits(maxDurationMs = 5000), fakeClock).execute("echo hi")
        assertEquals(124, result.exitCode)
        assertEquals("duration", result.limitKind)
        assertTrue(result.stderr.contains("RESOURCE_LIMIT"), result.stderr)
    }

    @Test
    fun `resolveWorkingDirectory validates directory`() {
        mkdirs("sub")
        val target = interpreter()
        assertEquals("sub", target.resolveWorkingDirectory("sub"))
        assertEquals(".", target.resolveWorkingDirectory("."))
        assertNull(target.resolveWorkingDirectory("../escape"))
        assertNull(target.resolveWorkingDirectory("missing"))
    }

    @Test
    fun `ls shows symlink marker without following it`() {
        val outside = Files.createTempFile("codetree-outside", ".txt")
        try {
            Files.writeString(outside, "secret")
            try {
                Files.createSymbolicLink(File(workspace(), "link.txt").toPath(), outside)
            } catch (e: Exception) {
                Assumptions.assumeTrue(false, "symlinks not supported: ${e.message}")
                return
            }
            val result = run("ls")
            assertEquals(0, result.exitCode)
            assertTrue(result.stdout.contains("link.txt@"), result.stdout)
        } finally {
            Files.deleteIfExists(outside)
        }
    }
}
