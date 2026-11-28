package com.codetree.feature.terminal.analysis

import com.codetree.runtime.api.CodeDiagnostic
import com.codetree.runtime.api.DiagnosticFix
import com.codetree.runtime.api.DiagnosticSeverity
import com.codetree.runtime.api.FixKind

/**
 * 安全 shell 的编辑期诊断（不依赖内容包）：引号未闭合、不支持的语法、未知命令、参数数量、路径越界。
 *
 * 说明：这些是**编辑提示**，执行期的真实错误仍以 `RuntimeEvent.Diagnostic/Stderr/Finished` 为准，
 * 两者不会互相替代。
 */
internal object ShellDiagnostics {

    private data class UnsupportedToken(val token: String, val label: String)

    private val UNSUPPORTED: List<UnsupportedToken> = listOf(
        UnsupportedToken(">>", "重定向 >>"),
        UnsupportedToken("&&", "命令串联 &&"),
        UnsupportedToken("||", "命令串联 ||"),
        UnsupportedToken("$(", "命令替换 $()"),
        UnsupportedToken("|", "管道 |"),
        UnsupportedToken(">", "重定向 >"),
        UnsupportedToken("<", "重定向 <"),
        UnsupportedToken(";", "命令串联 ;"),
        UnsupportedToken("&", "后台任务 &"),
        UnsupportedToken("`", "命令替换 ``"),
    )

    fun diagnose(code: String): List<CodeDiagnostic> {
        if (code.isBlank()) return emptyList()
        val result = ArrayList<CodeDiagnostic>()
        result += unclosedQuoteDiagnostics(code)
        result += unsupportedSyntaxDiagnostics(code)
        result += unknownCommandDiagnostic(code)
        result += arityDiagnostics(code)
        result += pathEscapeDiagnostics(code)
        return result
    }

    private fun unclosedQuoteDiagnostics(code: String): List<CodeDiagnostic> {
        val result = ArrayList<CodeDiagnostic>()
        var lineStart = 0
        var lineNumber = 1
        while (lineStart <= code.length) {
            val lineEnd = AnalysisText.lineEnd(code, lineStart)
            val line = code.substring(lineStart, lineEnd)
            var index = 0
            var openQuote: Char? = null
            var openColumn = 0
            while (index < line.length) {
                val ch = line[index]
                if ((ch == '"' || ch == '\'') && !AnalysisText.isEscaped(line, index)) {
                    if (openQuote == null) {
                        openQuote = ch
                        openColumn = index
                    } else if (openQuote == ch) {
                        openQuote = null
                    }
                }
                index += 1
            }
            if (openQuote != null) {
                result += CodeDiagnostic(
                    severity = DiagnosticSeverity.ERROR,
                    line = lineNumber,
                    column = openColumn,
                    endColumn = openColumn + 1,
                    message = "引号未闭合：缺少结束的「$openQuote」",
                    hint = "补上结束引号后再执行；一次只能写一条完整命令",
                    fix = DiagnosticFix(FixKind.APPEND, openQuote.toString()),
                    ruleId = "unclosed_quote",
                )
            }
            if (lineEnd >= code.length) break
            lineStart = lineEnd + 1
            lineNumber += 1
        }
        return result
    }

    private fun unsupportedSyntaxDiagnostics(code: String): List<CodeDiagnostic> {
        val result = ArrayList<CodeDiagnostic>()
        var index = 0
        var line = 1
        var column = 0
        var quote: Char? = null
        while (index < code.length) {
            val ch = code[index]
            if (ch == '\n') {
                line += 1
                column = 0
                index += 1
                continue
            }
            if (quote != null) {
                if (ch == quote && !AnalysisText.isEscaped(code, index)) quote = null
                index += 1
                column += 1
                continue
            }
            if (ch == '"' || ch == '\'') {
                quote = ch
                index += 1
                column += 1
                continue
            }
            val match = UNSUPPORTED.firstOrNull { code.startsWith(it.token, index) }
            if (match != null) {
                result += CodeDiagnostic(
                    severity = DiagnosticSeverity.ERROR,
                    line = line,
                    column = column,
                    endColumn = column + match.token.length,
                    message = "不支持的语法：${match.label}",
                    hint = "安全终端一次只执行一条命令；请拆分为多次执行，或输入 help 查看支持的命令",
                    fix = null,
                    ruleId = "unsupported_syntax",
                )
                if (result.size >= MAX_UNSUPPORTED) return result
                index += match.token.length
                column += match.token.length
                continue
            }
            index += 1
            column += 1
        }
        return result
    }

    private fun unknownCommandDiagnostic(code: String): List<CodeDiagnostic> {
        val trimmed = code.trimStart()
        if (trimmed.isEmpty()) return emptyList()
        val offset = code.length - trimmed.length
        val firstLine = trimmed.lineSequence().firstOrNull().orEmpty()
        val word = firstLine.takeWhile { !it.isWhitespace() && !AnalysisText.isShellOperator(it) }
        if (word.isEmpty() || word.startsWith("#")) return emptyList()
        if (ShellCommandCatalog.find(word) != null) return emptyList()
        val column = AnalysisText.columnAt(code, offset)
        return listOf(
            CodeDiagnostic(
                severity = DiagnosticSeverity.ERROR,
                line = AnalysisText.lineNumberAt(code, offset),
                column = column,
                endColumn = column + word.length,
                message = "未知命令：$word",
                hint = "输入 help 查看内置命令列表；安全终端不是完整 Linux",
                fix = null,
                ruleId = "command_not_found",
            ),
        )
    }

    private fun arityDiagnostics(code: String): List<CodeDiagnostic> {
        val args = AnalysisText.splitArgs(code.trim())
        if (args.isEmpty()) return emptyList()
        val name = args[0]
        val spec = ShellCommandCatalog.find(name) ?: return emptyList()
        val positional = args.drop(1).filterNot { it.startsWith("-") && it.length > 1 }
        val column = AnalysisText.columnAt(code, code.indexOf(name).coerceAtLeast(0))
        return when {
            positional.size < spec.minArgs -> listOf(
                CodeDiagnostic(
                    severity = DiagnosticSeverity.ERROR,
                    line = 1,
                    column = column,
                    endColumn = column + name.length,
                    message = "参数不足：${spec.usage}",
                    hint = "用法：${spec.usage}",
                    fix = null,
                    ruleId = "invalid_arguments",
                ),
            )
            spec.maxArgs >= 0 && positional.size > spec.maxArgs -> listOf(
                CodeDiagnostic(
                    severity = DiagnosticSeverity.ERROR,
                    line = 1,
                    column = column,
                    endColumn = column + name.length,
                    message = "参数过多：${spec.usage}",
                    hint = "用法：${spec.usage}",
                    fix = null,
                    ruleId = "invalid_arguments",
                ),
            )
            else -> emptyList()
        }
    }

    private fun pathEscapeDiagnostics(code: String): List<CodeDiagnostic> {
        val result = ArrayList<CodeDiagnostic>()
        var index = 0
        var line = 1
        var column = 0
        while (index < code.length) {
            val ch = code[index]
            if (ch == '\n') {
                line += 1
                column = 0
                index += 1
                continue
            }
            if (ch.isWhitespace()) {
                index += 1
                column += 1
                continue
            }
            val tokenStart = index
            val tokenLine = line
            val tokenColumn = column
            while (index < code.length && !code[index].isWhitespace()) {
                if (code[index] == '\n') break
                index += 1
                column += 1
            }
            val token = code.substring(tokenStart, index).trim('"', '\'')
            if (AnalysisText.looksLikeSandboxEscape(token)) {
                result += CodeDiagnostic(
                    severity = DiagnosticSeverity.ERROR,
                    line = tokenLine,
                    column = tokenColumn,
                    endColumn = tokenColumn + index - tokenStart,
                    message = "路径越界：$token",
                    hint = "路径必须是终端工作区内的相对路径（不能以 / 或 ~ 开头，也不能用 .. 越出工作区）",
                    fix = null,
                    ruleId = "path_out_of_sandbox",
                )
                if (result.size >= MAX_PATH_ISSUES) return result
            }
        }
        return result
    }

    private const val MAX_UNSUPPORTED = 3
    private const val MAX_PATH_ISSUES = 3
}
