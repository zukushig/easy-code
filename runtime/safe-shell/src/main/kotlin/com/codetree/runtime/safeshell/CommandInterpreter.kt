package com.codetree.runtime.safeshell

import com.codetree.runtime.api.ResourceLimits
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * P0 白名单命令解释器（docs/specs/terminal.md §2~§4，ADR-003）。
 *
 * 安全边界：
 * - 只实现纯 Kotlin 文件操作：不启动或加载任何外部进程/系统程序；
 * - 所有路径先按会话 cwd 拼接，再做段级规范化：`..` 不得越出 workspace 根、拒绝绝对路径 /
 *   `~` / 驱动器前缀 / NUL / 超长路径，并对每一段做符号链接检查；
 * - 文件读取上限 `limits.maxFileBytes`，非 UTF-8 / 二进制文件拒绝；不考虑写文件命令
 *   （P0 白名单只有 `mkdir`，因此 `maxFileBytes` 实际约束读取路径，留作 P1 写入兜底）；
 * - 执行时长超过 `limits.maxDurationMs` 返回 `limitKind = "duration"` + 退出码 124；
 * - 错误码稳定：`INVALID_ARGUMENTS`（退出码 2）、`PATH_OUT_OF_SANDBOX` / `NO_SUCH_FILE` /
 *   `NOT_A_FILE` / `NOT_A_DIRECTORY` / `REFUSED` / `RESOURCE_LIMIT`（退出码 1）、
 *   `COMMAND_NOT_FOUND`（退出码 127）。
 *
 * [ShellSessionState] 由会话持有，`cd` 只改会话内 cwd，不跨会话共享。
 */
class CommandInterpreter(
    private val workspaceRoot: File,
    private val limits: ResourceLimits = ResourceLimits(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class ShellSessionState(var cwd: String = ".")

    data class CommandResult(
        val stdout: String = "",
        val stderr: String = "",
        val exitCode: Int = 0,
        val limitKind: String? = null,
        val clearScreen: Boolean = false,
    ) {
        val ok: Boolean get() = exitCode == 0
    }

    private sealed interface Resolution {
        data class Ok(val relative: String, val file: File) : Resolution
        data class Rejected(val reason: String) : Resolution
    }

    private sealed interface FileRead {
        data class Ok(val content: String) : FileRead
        data class Failure(val message: String) : FileRead
    }

    @Volatile
    private var workspaceDirectoryCache: File? = null

    /**
     * 解析 `request.cwd`：返回规范化后的相对路径（根为 "."），非法或不存在返回 null。
     * 供 [SafeShellRuntime] 处理 `RunRequest.cwd` 时使用。
     */
    fun resolveWorkingDirectory(raw: String, currentCwd: String = "."): String? {
        val resolution = resolve(raw, currentCwd)
        if (resolution !is Resolution.Ok) return null
        if (!resolution.file.isDirectory) return null
        return resolution.relative.ifEmpty { "." }
    }

    fun execute(input: String, session: ShellSessionState = ShellSessionState()): CommandResult {
        val startedAt = clock()
        if (clock() - startedAt > limits.maxDurationMs) return durationLimitResult()

        val result = when (val parsed = CommandParser.parse(input)) {
            is ParseResult.Failure -> CommandResult(
                stderr = "${parsed.code}: ${parsed.message}",
                exitCode = 2,
            )

            is ParseResult.Success -> when (val decision = CommandPolicy.check(parsed.command)) {
                is PolicyDecision.Denied -> CommandResult(stderr = decision.message, exitCode = 127)
                PolicyDecision.Allowed -> try {
                    dispatch(parsed.command, parsed.args, session)
                } catch (e: Exception) {
                    CommandResult(
                        stderr = "INTERNAL: ${e.message ?: e::class.simpleName ?: "internal error"}",
                        exitCode = 1,
                    )
                }
            }
        }

        return if (clock() - startedAt > limits.maxDurationMs) durationLimitResult() else result
    }

    // ------------------------------------------------------------------ dispatch

    private fun dispatch(command: String, args: List<String>, session: ShellSessionState): CommandResult =
        when (command) {
            "pwd" -> pwd(args, session)
            "ls" -> ls(args, session)
            "cd" -> cd(args, session)
            "cat" -> cat(args, session)
            "echo" -> echo(args)
            "mkdir" -> mkdir(args, session)
            "rm" -> rm(args, session)
            "head" -> head(args, session)
            "wc" -> wc(args, session)
            "help" -> help(args)
            "clear" -> clear(args)
            else -> CommandResult(
                stderr = "COMMAND_NOT_FOUND: command not found: $command. Type 'help' to list built-in safe commands.",
                exitCode = 127,
            )
        }

    private fun pwd(args: List<String>, session: ShellSessionState): CommandResult {
        if (args.isNotEmpty()) return invalidArgs("pwd: takes no arguments")
        val display = if (session.cwd == "." || session.cwd.isBlank()) "/" else "/" + session.cwd
        return CommandResult(stdout = display + "\n")
    }

    private fun ls(args: List<String>, session: ShellSessionState): CommandResult {
        var longFormat = false
        var rawPath: String? = null
        for (arg in args) {
            when {
                arg == "-l" -> longFormat = true
                arg.length > 1 && arg.startsWith("-") -> return invalidArgs("ls: unsupported option '$arg'")
                rawPath == null -> rawPath = arg
                else -> return invalidArgs("ls: expected at most one path")
            }
        }
        val raw = rawPath ?: "."
        val resolution = resolve(raw, session.cwd)
        if (resolution is Resolution.Rejected) return pathError(raw, resolution)
        val file = (resolution as Resolution.Ok).file
        if (!file.exists()) return CommandResult(stderr = "NO_SUCH_FILE: ls: cannot access '$raw'", exitCode = 1)
        if (!file.isDirectory) {
            val line = if (longFormat) "${formatSize(file)}  ${displayEntryName(file)}" else displayEntryName(file)
            return CommandResult(stdout = line + "\n")
        }
        val children = file.listFiles()
            ?: return CommandResult(stderr = "IO_ERROR: ls: cannot list '$raw'", exitCode = 1)
        if (children.isEmpty()) return CommandResult()
        val sorted = children.sortedBy { it.name }
        val lines = sorted.map { child ->
            if (longFormat) "${formatSize(child)}  ${displayEntryName(child)}" else displayEntryName(child)
        }
        return CommandResult(stdout = lines.joinToString("\n") + "\n")
    }

    private fun cd(args: List<String>, session: ShellSessionState): CommandResult {
        if (args.size > 1) return invalidArgs("cd: expected at most one path")
        if (args.isEmpty()) {
            session.cwd = "."
            return CommandResult()
        }
        val raw = args.single()
        val resolution = resolve(raw, session.cwd)
        if (resolution is Resolution.Rejected) return pathError(raw, resolution)
        val resolved = resolution as Resolution.Ok
        if (!resolved.file.isDirectory) {
            return CommandResult(stderr = "NOT_A_DIRECTORY: cd: '$raw' is not a directory", exitCode = 1)
        }
        session.cwd = resolved.relative.ifEmpty { "." }
        return CommandResult()
    }

    private fun cat(args: List<String>, session: ShellSessionState): CommandResult {
        if (args.size != 1) return invalidArgs("cat: expected exactly one file")
        val raw = args.single()
        val resolution = resolve(raw, session.cwd)
        if (resolution is Resolution.Rejected) return pathError(raw, resolution)
        val file = (resolution as Resolution.Ok).file
        if (!file.exists()) return CommandResult(stderr = "NO_SUCH_FILE: cat: '$raw'", exitCode = 1)
        if (file.isDirectory) return CommandResult(stderr = "NOT_A_FILE: cat: '$raw' is a directory", exitCode = 1)
        return when (val read = readTextFile(file)) {
            is FileRead.Ok -> CommandResult(stdout = read.content)
            is FileRead.Failure -> CommandResult(stderr = read.message, exitCode = 1)
        }
    }

    private fun echo(args: List<String>): CommandResult =
        CommandResult(stdout = args.joinToString(" ") + "\n")

    private fun mkdir(args: List<String>, session: ShellSessionState): CommandResult {
        var recursive = false
        var rawPath: String? = null
        for (arg in args) {
            when {
                arg == "-p" -> recursive = true
                arg.length > 1 && arg.startsWith("-") -> return invalidArgs("mkdir: unsupported option '$arg'")
                rawPath == null -> rawPath = arg
                else -> return invalidArgs("mkdir: expected exactly one path")
            }
        }
        val raw = rawPath ?: return invalidArgs("mkdir: missing directory path")
        val resolution = resolve(raw, session.cwd)
        if (resolution is Resolution.Rejected) return pathError(raw, resolution)
        val file = (resolution as Resolution.Ok).file
        if (file.exists()) {
            if (file.isDirectory && recursive) return CommandResult()
            val reason = if (file.isDirectory) "already exists" else "already exists and is not a directory"
            return CommandResult(stderr = "REFUSED: mkdir: '$raw' $reason", exitCode = 1)
        }
        val created = if (recursive) file.mkdirs() else file.mkdir()
        if (!created) {
            return CommandResult(stderr = "IO_ERROR: mkdir: cannot create '$raw'", exitCode = 1)
        }
        return CommandResult()
    }

    private fun rm(args: List<String>, session: ShellSessionState): CommandResult {
        if (args.size != 1) return invalidArgs("rm: expected exactly one file")
        val raw = args.single()
        if (raw.startsWith("-")) {
            return invalidArgs("rm: options are not supported; recursive delete is disabled")
        }
        if (raw.contains('*') || raw.contains('?')) {
            return invalidArgs("rm: wildcards are not supported; pass an explicit file path")
        }
        val resolution = resolve(raw, session.cwd)
        if (resolution is Resolution.Rejected) return pathError(raw, resolution)
        val file = (resolution as Resolution.Ok).file
        if (!file.exists()) return CommandResult(stderr = "NO_SUCH_FILE: rm: '$raw'", exitCode = 1)
        if (Files.isSymbolicLink(file.toPath())) {
            return CommandResult(stderr = "REFUSED: rm: symbolic links cannot be removed", exitCode = 1)
        }
        if (file.isDirectory) {
            return CommandResult(
                stderr = "REFUSED: rm: '$raw' is a directory; recursive delete is not supported",
                exitCode = 1,
            )
        }
        return try {
            Files.delete(file.toPath())
            CommandResult()
        } catch (e: IOException) {
            CommandResult(stderr = "IO_ERROR: rm: ${e.message ?: "cannot delete '$raw'"}", exitCode = 1)
        }
    }

    private fun head(args: List<String>, session: ShellSessionState): CommandResult {
        var lines = DEFAULT_HEAD_LINES
        val positional = ArrayList<String>()
        var index = 0
        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "-n" -> {
                    val value = args.getOrNull(index + 1)
                        ?: return invalidArgs("head: -n requires a line count")
                    lines = value.toIntOrNull() ?: return invalidArgs("head: invalid line count '$value'")
                    index += 2
                }

                arg.startsWith("-n") && arg.length > 2 -> {
                    lines = arg.substring(2).toIntOrNull()
                        ?: return invalidArgs("head: invalid line count in '$arg'")
                    index++
                }

                arg.length > 1 && arg.startsWith("-") -> return invalidArgs("head: unsupported option '$arg'")
                else -> {
                    positional += arg
                    index++
                }
            }
        }
        if (lines !in 1..MAX_HEAD_LINES) {
            return invalidArgs("head: line count must be between 1 and $MAX_HEAD_LINES")
        }
        if (positional.size != 1) return invalidArgs("head: expected exactly one file")
        val raw = positional.single()
        val resolution = resolve(raw, session.cwd)
        if (resolution is Resolution.Rejected) return pathError(raw, resolution)
        val file = (resolution as Resolution.Ok).file
        if (!file.exists()) return CommandResult(stderr = "NO_SUCH_FILE: head: '$raw'", exitCode = 1)
        if (file.isDirectory) return CommandResult(stderr = "NOT_A_FILE: head: '$raw' is a directory", exitCode = 1)
        return when (val read = readTextFile(file)) {
            is FileRead.Ok -> {
                val selected = splitLines(read.content).take(lines)
                CommandResult(stdout = if (selected.isEmpty()) "" else selected.joinToString("\n") + "\n")
            }

            is FileRead.Failure -> CommandResult(stderr = read.message, exitCode = 1)
        }
    }

    /** 按行切分：CRLF/CR 归一为 LF；结尾换行不产生额外空行（head 输出不重复追加换行）。 */
    private fun splitLines(content: String): List<String> {
        if (content.isEmpty()) return emptyList()
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val parts = normalized.split('\n')
        return if (parts.isNotEmpty() && parts.last().isEmpty()) parts.dropLast(1) else parts
    }

    private fun wc(args: List<String>, session: ShellSessionState): CommandResult {
        if (args.size != 1) return invalidArgs("wc: expected exactly one file")
        val raw = args.single()
        val resolution = resolve(raw, session.cwd)
        if (resolution is Resolution.Rejected) return pathError(raw, resolution)
        val file = (resolution as Resolution.Ok).file
        if (!file.exists()) return CommandResult(stderr = "NO_SUCH_FILE: wc: '$raw'", exitCode = 1)
        if (file.isDirectory) return CommandResult(stderr = "NOT_A_FILE: wc: '$raw' is a directory", exitCode = 1)
        return when (val read = readTextFile(file)) {
            is FileRead.Ok -> {
                val lineCount = read.content.count { it == '\n' }
                val wordCount = read.content.split(Regex("\\s+")).count { it.isNotEmpty() }
                val byteCount = read.content.toByteArray(StandardCharsets.UTF_8).size
                CommandResult(stdout = "$lineCount $wordCount $byteCount $raw\n")
            }

            is FileRead.Failure -> CommandResult(stderr = read.message, exitCode = 1)
        }
    }

    private fun help(args: List<String>): CommandResult = when {
        args.isEmpty() -> CommandResult(stdout = CommandPolicy.helpText() + "\n")
        args.size > 1 -> invalidArgs("help: expected at most one command name")
        else -> {
            val help = CommandPolicy.helpFor(args.single())
                ?: return invalidArgs("help: no such built-in command: ${args.single()}")
            CommandResult(stdout = "usage: ${help.usage}\n")
        }
    }

    private fun clear(args: List<String>): CommandResult =
        if (args.isEmpty()) CommandResult(clearScreen = true) else invalidArgs("clear: takes no arguments")

    // ------------------------------------------------------------------ 路径与文件

    /**
     * 解析用户输入路径：先做危险字符/绝对路径快速拒绝，再按 cwd 拼接并做段级规范化，
     * 最后复核真实路径位于 workspace 根目录下（防符号链接逃逸）。
     */
    private fun resolve(raw: String, cwd: String): Resolution {
        if (raw.isBlank()) return Resolution.Rejected("empty path")
        if (raw.contains('\u0000')) return Resolution.Rejected("NUL byte in path is not allowed")
        if (raw.length > MAX_PATH_LENGTH) return Resolution.Rejected("path is too long (max $MAX_PATH_LENGTH characters)")
        if (raw.startsWith("/") || raw.startsWith("~")) {
            return Resolution.Rejected("absolute paths are not allowed; paths must stay inside the workspace")
        }
        if (DRIVE_PREFIX.containsMatchIn(raw)) {
            return Resolution.Rejected("absolute paths are not allowed; paths must stay inside the workspace")
        }

        val segments = ArrayList<String>()
        if (cwd != "." && cwd.isNotBlank()) {
            segments.addAll(cwd.split('/').filter { it.isNotEmpty() })
        }
        for (segment in raw.replace('\\', '/').split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    if (segments.isEmpty()) {
                        return Resolution.Rejected("path escapes the workspace root ('..')")
                    }
                    segments.removeAt(segments.lastIndex)
                }

                else -> {
                    if (segment.length > MAX_SEGMENT_LENGTH) {
                        return Resolution.Rejected("path segment is too long (max $MAX_SEGMENT_LENGTH characters)")
                    }
                    segments.add(segment)
                }
            }
        }

        val relative = segments.joinToString("/")
        val root = workspaceDirectory()
        var current: File = root
        for (segment in segments) {
            current = File(current, segment)
            if (Files.isSymbolicLink(current.toPath())) {
                return Resolution.Rejected("symbolic links are not allowed inside the workspace")
            }
        }
        val target = if (relative.isEmpty()) root else File(root, relative.replace('/', File.separatorChar))
        val rootCanonical = try {
            root.canonicalFile
        } catch (e: IOException) {
            return Resolution.Rejected("cannot resolve workspace root: ${e.message}")
        }
        val targetCanonical = try {
            target.canonicalFile
        } catch (e: IOException) {
            return Resolution.Rejected("invalid path: ${e.message}")
        }
        val underRoot = targetCanonical == rootCanonical ||
            targetCanonical.path.startsWith(rootCanonical.path + File.separator)
        if (!underRoot) return Resolution.Rejected("path escapes the workspace root")
        return Resolution.Ok(relative, target)
    }

    private fun workspaceDirectory(): File {
        workspaceDirectoryCache?.let { return it }
        synchronized(this) {
            workspaceDirectoryCache?.let { return it }
            val directory = workspaceRoot.absoluteFile
            if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
                throw IllegalStateException("cannot create workspace: ${directory.path}")
            }
            val canonical = directory.canonicalFile
            workspaceDirectoryCache = canonical
            return canonical
        }
    }

    private fun readTextFile(file: File): FileRead {
        if (!file.isFile) return FileRead.Failure("NOT_A_FILE: '${file.name}' is not a regular file")
        val size = try {
            Files.size(file.toPath())
        } catch (e: IOException) {
            return FileRead.Failure("IO_ERROR: cannot read '${file.name}': ${e.message}")
        }
        if (size > limits.maxFileBytes) {
            return FileRead.Failure(
                "RESOURCE_LIMIT: file is too large ($size bytes > ${limits.maxFileBytes} bytes)",
            )
        }
        val bytes = try {
            Files.readAllBytes(file.toPath())
        } catch (e: IOException) {
            return FileRead.Failure("IO_ERROR: cannot read '${file.name}': ${e.message}")
        }
        if (looksBinary(bytes)) {
            return FileRead.Failure("BINARY_FILE: '${file.name}' is not a UTF-8 text file")
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val content = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            return FileRead.Failure("ENCODING_ERROR: '${file.name}' is not valid UTF-8 text")
        }
        return FileRead.Ok(content)
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, BINARY_PROBE_BYTES)
        for (index in 0 until limit) {
            if (bytes[index] == 0.toByte()) return true
        }
        return false
    }

    private fun displayEntryName(file: File): String {
        val suffix = when {
            Files.isSymbolicLink(file.toPath()) -> "@"
            file.isDirectory -> "/"
            else -> ""
        }
        return file.name + suffix
    }

    private fun formatSize(file: File): String =
        if (file.isDirectory || Files.isSymbolicLink(file.toPath())) {
            "-"
        } else {
            try {
                Files.size(file.toPath()).toString()
            } catch (e: IOException) {
                "?"
            }
        }

    // ------------------------------------------------------------------ helpers

    private fun pathError(raw: String, rejection: Resolution.Rejected): CommandResult = CommandResult(
        stderr = "PATH_OUT_OF_SANDBOX: ${rejection.reason} (path: $raw)",
        exitCode = 1,
    )

    private fun invalidArgs(message: String): CommandResult =
        CommandResult(stderr = "INVALID_ARGUMENTS: $message", exitCode = 2)

    private fun durationLimitResult(): CommandResult = CommandResult(
        stderr = "RESOURCE_LIMIT: execution time limit exceeded (${limits.maxDurationMs}ms)",
        exitCode = 124,
        limitKind = "duration",
    )

    private companion object {
        const val MAX_PATH_LENGTH = 1024
        const val MAX_SEGMENT_LENGTH = 255
        const val BINARY_PROBE_BYTES = 8192
        const val DEFAULT_HEAD_LINES = 10
        const val MAX_HEAD_LINES = 200
        val DRIVE_PREFIX = Regex("^[A-Za-z]:")
    }
}
