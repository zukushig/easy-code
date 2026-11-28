package com.codetree.runtime.safeshell

/** 命令策略判定：只允许白名单内置命令；其余一律拒绝（包括系统程序与危险命令）。 */
sealed interface PolicyDecision {
    data object Allowed : PolicyDecision
    data class Denied(val code: String, val message: String) : PolicyDecision
}

/**
 * 白名单命令策略（docs/specs/terminal.md §2/§6，ADR-003）：
 * P0 只实现纯 Kotlin 内置命令，**不调用任何系统 shell 或外部程序**。
 * 未收录命令（`python`、`node`、`git`、`adb`、`su` 等）返回 `COMMAND_NOT_FOUND` 并提示 `help`。
 *
 * `help` 文案由 runtime 只提供英文 key + usage，UI/数据层映射中文。
 */
object CommandPolicy {

    /** 帮助条目：id 为稳定 key（UI 可据此本地化），usage 为英文单行用法。 */
    data class CommandHelp(val id: String, val usage: String)

    val commands: List<CommandHelp> = listOf(
        CommandHelp("pwd", "pwd - print the current working directory inside the workspace"),
        CommandHelp("ls", "ls [-l] [path] - list a directory (default: current directory)"),
        CommandHelp("cd", "cd [path] - change the current working directory (session scoped)"),
        CommandHelp("cat", "cat <file> - print a UTF-8 text file"),
        CommandHelp("echo", "echo [text...] - print text (no variable or escape expansion)"),
        CommandHelp("mkdir", "mkdir [-p] <dir> - create a directory (-p creates parents)"),
        CommandHelp("rm", "rm <file> - remove one file (directories, links and wildcards are refused)"),
        CommandHelp("head", "head [-n N] <file> - print the first N lines (default 10, max 200)"),
        CommandHelp("wc", "wc <file> - count lines, words and bytes"),
        CommandHelp("clear", "clear - clear the terminal view (UI only, no file access)"),
        CommandHelp("help", "help [command] - list built-in safe commands or show one command usage"),
    )

    private val byId: Map<String, CommandHelp> = commands.associateBy { it.id }

    val whitelist: Set<String> get() = byId.keys

    /** 已知的系统/危险程序：只用于给出更明确的拒绝原因，绝不执行。 */
    private val dangerous: Set<String> = setOf(
        "sh", "bash", "zsh", "dash", "fish", "su", "sudo", "doas",
        "python", "python3", "node", "nodejs", "npm", "npx", "git", "adb", "fastboot",
        "curl", "wget", "ssh", "scp", "nc", "netcat", "telnet", "ftp",
        "rmdir", "dd", "chmod", "chown", "mount", "umount", "systemctl", "service",
        "powershell", "cmd", "eval", "exec", "source",
    )

    fun isAllowed(command: String): Boolean = command in byId

    fun isDangerous(command: String): Boolean = command.lowercase() in dangerous

    fun helpFor(command: String): CommandHelp? = byId[command]

    fun check(command: String): PolicyDecision = when {
        isAllowed(command) -> PolicyDecision.Allowed
        isDangerous(command) -> PolicyDecision.Denied(
            "COMMAND_NOT_FOUND",
            "COMMAND_NOT_FOUND: '$command' is not available in the safe shell; no system programs are executed. " +
                "Type 'help' to list built-in commands.",
        )

        else -> PolicyDecision.Denied(
            "COMMAND_NOT_FOUND",
            "COMMAND_NOT_FOUND: command not found: $command. Type 'help' to list built-in safe commands.",
        )
    }

    /** 帮助正文：明确说明这不是完整 Linux shell，且无管道/重定向/后台任务。 */
    fun helpText(): String = buildString {
        appendLine("Built-in safe shell (not a full Linux shell).")
        appendLine("No pipes, redirection, background jobs, wildcards or system programs.")
        appendLine("All paths stay inside the workspace; destructive commands are limited.")
        appendLine()
        appendLine("Commands:")
        for (command in commands) {
            appendLine("  ${command.usage}")
        }
    }.trimEnd()
}
