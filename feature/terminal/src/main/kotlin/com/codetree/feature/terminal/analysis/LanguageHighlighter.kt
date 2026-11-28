package com.codetree.feature.terminal.analysis

import com.codetree.plugin.LanguageSyntax
import com.codetree.runtime.api.TokenKind
import com.codetree.runtime.api.TokenSpan

/**
 * 基于内容包 `LanguageSyntax` 的通用高亮（Python / JavaScript 等示例语言）。
 * 规则全部来自语法包，本类不内嵌任何语言关键字。
 */
internal object LanguageHighlighter {

    fun highlight(code: String, syntax: LanguageSyntax): List<TokenSpan> {
        val spans = ArrayList<TokenSpan>()
        val keywords = syntax.keywords.toSet()
        val builtins = syntax.builtins.toSet()
        val lineComment = syntax.lineComment?.takeIf { it.isNotEmpty() }
        val blockCommentStart = syntax.blockComment?.getOrNull(0)?.takeIf { it.isNotEmpty() }
        val blockCommentEnd = syntax.blockComment?.getOrNull(1)?.takeIf { it.isNotEmpty() }
        val stringTokens = (syntax.stringDelims + listOf("\"", "'")).filter { it.isNotEmpty() }.distinct()

        var index = 0
        while (index < code.length) {
            val ch = code[index]
            if (lineComment != null && code.startsWith(lineComment, index)) {
                val end = code.indexOf('\n', index).let { if (it < 0) code.length else it }
                spans += TokenSpan(index, end, TokenKind.COMMENT)
                index = end
                continue
            }
            if (blockCommentStart != null && blockCommentEnd != null && code.startsWith(blockCommentStart, index)) {
                val closeIndex = code.indexOf(blockCommentEnd, index + blockCommentStart.length)
                val end = if (closeIndex < 0) code.length else closeIndex + blockCommentEnd.length
                spans += TokenSpan(index, end, TokenKind.COMMENT)
                index = end
                continue
            }
            val stringToken = stringTokens.firstOrNull { code.startsWith(it, index) }
            if (stringToken != null) {
                val end = scanString(code, index, stringToken)
                spans += TokenSpan(index, end, TokenKind.STRING)
                index = end
                continue
            }
            when {
                ch.isLetter() || ch == '_' -> {
                    val end = AnalysisText.scanIdentifier(code, index)
                    val word = code.substring(index, end)
                    when {
                        word in keywords -> spans += TokenSpan(index, end, TokenKind.KEYWORD)
                        word in builtins -> spans += TokenSpan(index, end, TokenKind.BUILTIN)
                        isFunctionCall(code, end) -> spans += TokenSpan(index, end, TokenKind.FUNCTION)
                    }
                    index = end
                }
                ch.isDigit() -> {
                    val end = AnalysisText.scanNumber(code, index)
                    spans += TokenSpan(index, end, TokenKind.NUMBER)
                    index = end
                }
                ch in "()[]{}" -> {
                    spans += TokenSpan(index, index + 1, TokenKind.PUNCTUATION)
                    index += 1
                }
                ch in "+-*/%=<>!&|^~?:." -> {
                    spans += TokenSpan(index, index + 1, TokenKind.OPERATOR)
                    index += 1
                }
                else -> index += 1
            }
        }
        return spans
    }

    private fun isFunctionCall(code: String, wordEnd: Int): Boolean {
        var index = wordEnd
        while (index < code.length && (code[index] == ' ' || code[index] == '\t')) index += 1
        return index < code.length && code[index] == '('
    }

    private fun scanString(code: String, start: Int, token: String): Int {
        var index = start + token.length
        while (index <= code.length - token.length) {
            if (code.regionMatches(index, token, 0, token.length) && !AnalysisText.isEscaped(code, index)) {
                return index + token.length
            }
            if (code[index] == '\n' && token.length == 1) return index
            index += 1
        }
        return code.length
    }
}
