package com.codetree.feature.terminal.analysis

/**
 * 白名单命令的 UI 侧元数据（与 docs/specs/terminal.md §2 对齐）。
 *
 * 说明：这里只用于**编辑期高亮/诊断/补全**，不参与执行。命令是否真正可用由
 * `RuntimePlugin` 决定；UI 不得据此伪造任何执行结果。
 */
data class ShellCommandSpec(
    val name: String,
    val usage: String,
    val summary: String,
    val flags: List<String> = emptyList(),
    val needsPathArgument: Boolean = false,
    val minArgs: Int = 0,
    val maxArgs: Int = -1,
)

object ShellCommandCatalog {

    val COMMANDS: List<ShellCommandSpec> = listOf(
        ShellCommandSpec("help", "help [command]", "列出内置命令或查看某条命令用法", maxArgs = 1),
        ShellCommandSpec("pwd", "pwd", "显示当前工作目录（相对沙箱根）", maxArgs = 0),
        ShellCommandSpec("ls", "ls [-l] [path]", "列出目录内容，-l 显示大小与时间", listOf("-l"), maxArgs = 1),
        ShellCommandSpec("cd", "cd <path>", "切换工作目录，不能越出沙箱根", needsPathArgument = true, minArgs = 1, maxArgs = 1),
        ShellCommandSpec("cat", "cat <file>", "输出文本文件内容", needsPathArgument = true, minArgs = 1, maxArgs = 1),
        ShellCommandSpec("echo", "echo <text...>", "原样输出文本", maxArgs = -1),
        ShellCommandSpec("touch", "touch <file>", "创建空文件或更新修改时间", needsPathArgument = true, minArgs = 1, maxArgs = 1),
        ShellCommandSpec("mkdir", "mkdir [-p] <path>", "创建目录，-p 递归创建", listOf("-p"), needsPathArgument = true, minArgs = 1, maxArgs = 1),
        ShellCommandSpec("rm", "rm [-r] <path>", "删除文件或目录，-r 递归", listOf("-r"), needsPathArgument = true, minArgs = 1, maxArgs = 1),
        ShellCommandSpec("cp", "cp <src> <dst>", "复制文件", needsPathArgument = true, minArgs = 2, maxArgs = 2),
        ShellCommandSpec("mv", "mv [-f] <src> <dst>", "移动或重命名文件", listOf("-f"), needsPathArgument = true, minArgs = 2, maxArgs = 2),
        ShellCommandSpec("head", "head [-n N] <file>", "查看文件前 N 行（默认 10）", listOf("-n"), needsPathArgument = true, minArgs = 1, maxArgs = 2),
        ShellCommandSpec("tail", "tail [-n N] <file>", "查看文件后 N 行（默认 10）", listOf("-n"), needsPathArgument = true, minArgs = 1, maxArgs = 2),
        ShellCommandSpec("wc", "wc [-l] [-w] [-c] <file>", "统计行/词/字节", listOf("-l", "-w", "-c"), needsPathArgument = true, minArgs = 1, maxArgs = 2),
        ShellCommandSpec("grep", "grep <pattern> <file>", "固定字符串匹配（不是正则）", needsPathArgument = true, minArgs = 2, maxArgs = 2),
        ShellCommandSpec("date", "date", "输出设备本地时间（ISO 8601）", maxArgs = 0),
        ShellCommandSpec("clear", "clear", "清空终端屏幕", maxArgs = 0),
        ShellCommandSpec("history", "history [-c]", "显示或清空会话内命令历史", listOf("-c"), maxArgs = 1),
    )

    private val byName: Map<String, ShellCommandSpec> = COMMANDS.associateBy { it.name }

    fun find(name: String): ShellCommandSpec? = byName[name.lowercase()]

    fun names(): List<String> = COMMANDS.map { it.name }

    fun usageOf(name: String): String? = byName[name.lowercase()]?.usage

    /** shell 命令名集合：高亮时作为“关键字”着色。 */
    val KEYWORDS: Set<String> = COMMANDS.map { it.name }.toSet()

    const val HELP_FOOTER =
        "内置安全命令，不是完整 Linux；不支持管道 |、重定向 >/<、命令串联 ;/&&/||、后台 &、命令替换 $()。"

    fun helpText(): String = buildString {
        appendLine("内置安全命令（不是完整 Linux 环境）：")
        COMMANDS.forEach { spec ->
            appendLine("  " + spec.usage.padEnd(26) + spec.summary)
        }
        append(HELP_FOOTER)
    }
}
