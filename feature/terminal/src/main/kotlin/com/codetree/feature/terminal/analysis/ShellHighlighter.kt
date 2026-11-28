package com.codetree.feature.terminal.analysis

import com.codetree.runtime.api.TokenKind
import com.codetree.runtime.api.TokenSpan

/**
 * 安全 shell 的内置高亮规则：命令名 = KEYWORD，`-flag` = BUILTIN，引号 = STRING，
 * 数字 = NUMBER，不支持的运算符（| & ; > < $ 等）= OPERATOR。
 *
 * 与运行时白名单同源的是 [ShellCommandCatalog]；这里只影响配色，不影响执行。
 */
internal object ShellHighlighter {

    fun highlight(code: String): List<TokenSpan> {
        val spans = ArrayList<TokenSpan>()
        var index = 0
        var atCommandStart = true
        while (index < code.length) {
            val ch = code[index]
            when {
                ch == '\n' -> {
                    atCommandStart = true
                    index += 1
                }
                ch.isWhitespace() -> index += 1
                ch == '"' || ch == '\'' -> {
                    val end = AnalysisText.scanQuoted(code, index, ch)
                    spans += TokenSpan(index, end, TokenKind.STRING)
                    index = end
                    atCommandStart = false
                }
                ch.isDigit() -> {
                    val end = AnalysisText.scanNumber(code, index)
                    spans += TokenSpan(index, end, TokenKind.NUMBER)
                    index = end
                    atCommandStart = false
                }
                AnalysisText.isShellOperator(ch) -> {
                    var end = index + 1
                    if (end < code.length) {
                        val next = code[end]
                        val twoChar = when {
                            ch == '>' && next == '>' -> true
                            ch == '&' && next == '&' -> true
                            ch == '|' && next == '|' -> true
                            ch == '$' && next == '(' -> true
                            else -> false
                        }
                        if (twoChar) end += 1
                    }
                    spans += TokenSpan(index, end, TokenKind.OPERATOR)
                    index = end
                    atCommandStart = false
                }
                else -> {
                    val end = AnalysisText.scanShellWord(code, index)
                    if (end <= index) {
                        index += 1
                        continue
                    }
                    val word = code.substring(index, end)
                    val kind = when {
                        atCommandStart -> TokenKind.KEYWORD
                        word.startsWith("-") && word.length > 1 -> TokenKind.BUILTIN
                        else -> TokenKind.PLAIN
                    }
                    if (kind != TokenKind.PLAIN) spans += TokenSpan(index, end, kind)
                    atCommandStart = false
                    index = end
                }
            }
        }
        return spans
    }
}
