package com.codetree.feature.terminal.analysis

/** 一对配对括号的完整区间（end 为开区间）。 */
data class BracketPair(
    val openStart: Int,
    val openEnd: Int,
    val closeStart: Int,
    val closeEnd: Int,
) {
    val openRange: IntRange get() = openStart until openEnd
    val closeRange: IntRange get() = closeStart until closeEnd
}

/**
 * 括号匹配（MVP 简化实现）。
 *
 * 已知简化：不做字符串/注释内部感知；多字符括号按 token 前缀扫描，重叠 token 的极端情况
 * 可能配错（例如斜杠星号同时作为注释起始与括号字符时）。对终端输入（单行短命令）足够；不满足时退化为 null，
 * UI 只是不高亮，不影响编辑。
 */
object BracketMatcher {

    val DEFAULT_PAIRS: List<List<String>> = listOf(
        listOf("(", ")"),
        listOf("[", "]"),
        listOf("{", "}"),
        listOf("(", "）"),
        listOf("\"", "\""),
        listOf("'", "'"),
        listOf("`", "`"),
    )

    /**
     * 返回光标所在（或紧邻光标左侧/右侧）括号的配对。
     *
     * @param cursor 光标偏移（0..code.length）
     * @param pairs 内容包 `LanguageSyntax.brackets`，元素为 [open, close]；为空时用 [DEFAULT_PAIRS]
     */
    fun pairAt(code: String, cursor: Int, pairs: List<List<String>> = DEFAULT_PAIRS): BracketPair? {
        if (code.isEmpty()) return null
        val definitions = normalize(pairs).ifEmpty { normalize(DEFAULT_PAIRS) }
        val safeCursor = cursor.coerceIn(0, code.length)
        // 优先光标左侧字符（刚输入完括号时），再尝试光标右侧字符
        val candidates = listOf(safeCursor - 1, safeCursor)
        for (charIndex in candidates) {
            if (charIndex < 0 || charIndex >= code.length) continue
            for ((open, close) in definitions) {
                val pair = tryPairFromOpenToken(code, charIndex, open, close)
                    ?: tryPairFromCloseToken(code, charIndex, open, close)
                if (pair != null) return pair
            }
        }
        return null
    }

    private fun tryPairFromOpenToken(code: String, charIndex: Int, open: String, close: String): BracketPair? {
        if (open.isEmpty() || close.isEmpty()) return null
        val tokenStart = charIndex + 1 - open.length
        if (tokenStart < 0 || !code.regionMatches(tokenStart, open, 0, open.length)) return null
        val tokenEnd = tokenStart + open.length
        if (open == close) {
            if (isEscaped(code, tokenStart)) return null
            val closeStart = findSymmetricForward(code, tokenEnd, close) ?: return null
            return BracketPair(tokenStart, tokenEnd, closeStart, closeStart + close.length)
        }
        val closeStart = findMatchingClose(code, tokenEnd, open, close)
        if (closeStart < 0) return null
        return BracketPair(tokenStart, tokenEnd, closeStart, closeStart + close.length)
    }

    private fun tryPairFromCloseToken(code: String, charIndex: Int, open: String, close: String): BracketPair? {
        if (open.isEmpty() || close.isEmpty()) return null
        if (!code.regionMatches(charIndex, close, 0, close.length)) return null
        if (open == close) {
            if (isEscaped(code, charIndex)) return null
            val openStart = findSymmetricBackward(code, charIndex - 1, open) ?: return null
            return BracketPair(openStart, openStart + open.length, charIndex, charIndex + close.length)
        }
        val openStart = findMatchingOpen(code, charIndex - 1, open, close)
        if (openStart < 0) return null
        return BracketPair(openStart, openStart + open.length, charIndex, charIndex + close.length)
    }

    private fun findMatchingClose(code: String, from: Int, open: String, close: String): Int {
        var depth = 1
        var index = from
        while (index <= code.length - 1) {
            if (code.regionMatches(index, open, 0, open.length)) {
                depth += 1
                index += open.length
            } else if (code.regionMatches(index, close, 0, close.length)) {
                depth -= 1
                if (depth == 0) return index
                index += close.length
            } else {
                index += 1
            }
        }
        return -1
    }

    private fun findMatchingOpen(code: String, before: Int, open: String, close: String): Int {
        var depth = 1
        var index = before
        while (index >= 0) {
            if (regionEndsAt(code, index, close)) {
                depth += 1
                index -= close.length
            } else if (regionEndsAt(code, index, open)) {
                depth -= 1
                if (depth == 0) return index - open.length + 1
                index -= open.length
            } else {
                index -= 1
            }
        }
        return -1
    }

    private fun findSymmetricForward(code: String, from: Int, token: String): Int? {
        var index = from
        while (index <= code.length - token.length) {
            if (code.regionMatches(index, token, 0, token.length)) {
                if (!isEscaped(code, index)) return index
                index += token.length
            } else {
                index += 1
            }
        }
        return null
    }

    private fun findSymmetricBackward(code: String, before: Int, token: String): Int? {
        var index = before - token.length + 1
        if (index > code.length - token.length) index = code.length - token.length
        while (index >= 0) {
            if (code.regionMatches(index, token, 0, token.length) && !isEscaped(code, index)) return index
            index -= 1
        }
        return null
    }

    private fun regionEndsAt(code: String, end: Int, token: String): Boolean {
        val start = end - token.length + 1
        if (start < 0) return false
        if (!code.regionMatches(start, token, 0, token.length)) return false
        return true
    }

    private fun isEscaped(code: String, index: Int): Boolean {
        var backslashes = 0
        var cursor = index - 1
        while (cursor >= 0 && code[cursor] == '\\') {
            backslashes += 1
            cursor -= 1
        }
        return backslashes % 2 == 1
    }

    private fun normalize(pairs: List<List<String>>): List<Pair<String, String>> = pairs.mapNotNull { entry ->
        when {
            entry.size >= 2 && entry[0].isNotEmpty() && entry[1].isNotEmpty() -> entry[0] to entry[1]
            entry.size == 1 && entry[0].isNotEmpty() -> entry[0] to entry[0]
            else -> null
        }
    }
}
