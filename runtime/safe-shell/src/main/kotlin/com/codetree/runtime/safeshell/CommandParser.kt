package com.codetree.runtime.safeshell

/** 命令解析结果：成功得到命令 + 参数；失败得到稳定错误码（供诊断事件使用）。 */
sealed interface ParseResult {
    data class Success(val command: String, val args: List<String>) : ParseResult
    data class Failure(val code: String, val message: String) : ParseResult
}

/**
 * 单条命令解析器（docs/specs/terminal.md §2）：
 * - 按空白分词，支持单/双引号包裹含空格参数，支持 `\` 转义下一个字符；
 * - **不支持**管道 `|`、重定向 `>`/`<`、命令串联 `;`、后台 `&`、命令替换反引号或 `$(`、
 *   变量展开（`$VAR` 原样保留字面量）；遇到不支持语法返回明确错误且不执行任何副作用；
 * - 参数数量 ≤ [MAX_ARGUMENTS]、输入总长 ≤ [MAX_INPUT_LENGTH]；
 * - 通配符 `*`/`?` 不展开，按普通字符传递（是否拒绝由具体命令决定，`rm` 会明确拒绝）。
 */
object CommandParser {

    const val MAX_ARGUMENTS = 32
    const val MAX_INPUT_LENGTH = 4096

    fun parse(input: String): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ParseResult.Failure("EMPTY_COMMAND", "empty command; type 'help' to list built-in commands")
        }
        if (trimmed.length > MAX_INPUT_LENGTH) {
            return ParseResult.Failure(
                "INVALID_ARGUMENTS",
                "command is too long (max $MAX_INPUT_LENGTH characters)",
            )
        }

        val tokens = ArrayList<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escaped = false
        var index = 0
        while (index < trimmed.length) {
            val char = trimmed[index]
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }

                char == '\\' && !inSingleQuote -> escaped = true
                char == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
                char == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                char.isWhitespace() && !inSingleQuote && !inDoubleQuote -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }

                else -> {
                    if (!inSingleQuote && !inDoubleQuote) {
                        if (char in UNSUPPORTED_CHARS) {
                            return ParseResult.Failure("UNSUPPORTED_SYNTAX", unsupportedSyntax(char))
                        }
                        if (char == '$' && index + 1 < trimmed.length && trimmed[index + 1] == '(') {
                            return ParseResult.Failure(
                                "UNSUPPORTED_SYNTAX",
                                "command substitution ('\$(') is not supported",
                            )
                        }
                    }
                    current.append(char)
                }
            }
            index++
        }

        if (escaped) {
            return ParseResult.Failure("UNSUPPORTED_SYNTAX", "dangling escape character ('\\' at end of input)")
        }
        if (inSingleQuote || inDoubleQuote) {
            return ParseResult.Failure("UNCLOSED_QUOTE", "unclosed quote; quotes must be paired")
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        if (tokens.isEmpty()) {
            return ParseResult.Failure("EMPTY_COMMAND", "empty command; type 'help' to list built-in commands")
        }

        val command = tokens.first()
        val args = tokens.drop(1)
        if (args.size > MAX_ARGUMENTS) {
            return ParseResult.Failure("INVALID_ARGUMENTS", "too many arguments (max $MAX_ARGUMENTS)")
        }
        return ParseResult.Success(command, args)
    }

    private const val UNSUPPORTED_CHARS = "|><;&`"

    private fun unsupportedSyntax(char: Char): String = when (char) {
        '|' -> "pipes ('|') are not supported; run one command at a time"
        '>' -> "redirection ('>') is not supported; command output is returned directly"
        '<' -> "redirection ('<') is not supported"
        ';' -> "command chaining (';') is not supported"
        '&' -> "background jobs ('&') are not supported"
        '`' -> "command substitution ('`') is not supported"
        else -> "unsupported shell syntax: '$char'"
    }
}
