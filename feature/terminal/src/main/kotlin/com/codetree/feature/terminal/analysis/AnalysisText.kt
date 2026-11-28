package com.codetree.feature.terminal.analysis

/**
 * 分析器的纯文本扫描工具（无 Android 依赖，便于后续抽成 JVM 单测）。
 * 所有函数都不抛异常：非法输入退化为空区间/空列表。
 */
internal object AnalysisText {

    private val SHELL_OPERATOR_CHARS = "|<>;&()`$*?~".toSet()

    fun isShellOperator(ch: Char): Boolean = ch in SHELL_OPERATOR_CHARS

    fun isEscaped(code: String, index: Int): Boolean {
        var backslashes = 0
        var cursor = index - 1
        while (cursor >= 0 && code[cursor] == '\\') {
            backslashes += 1
            cursor -= 1
        }
        return backslashes % 2 == 1
    }

    /** 扫描引号字符串，返回结束引号之后的位置；未闭合时返回到行尾（不含换行）。 */
    fun scanQuoted(code: String, start: Int, quote: Char): Int {
        var index = start + 1
        while (index < code.length) {
            val ch = code[index]
            if (ch == quote && !isEscaped(code, index)) return index + 1
            if (ch == '\n') return index
            index += 1
        }
        return code.length
    }

    fun scanShellWord(code: String, start: Int): Int {
        var index = start
        while (index < code.length) {
            val ch = code[index]
            if (ch.isWhitespace() || ch == '"' || ch == '\'' || isShellOperator(ch)) break
            index += 1
        }
        return index
    }

    fun scanNumber(code: String, start: Int): Int {
        var index = start
        var sawDot = false
        while (index < code.length) {
            val ch = code[index]
            when {
                ch.isDigit() -> index += 1
                ch == '.' && !sawDot -> {
                    sawDot = true
                    index += 1
                }
                ch == '_' -> index += 1
                else -> break
            }
        }
        return index
    }

    fun scanIdentifier(code: String, start: Int): Int {
        var index = start
        while (index < code.length && (code[index].isLetterOrDigit() || code[index] == '_')) index += 1
        return index
    }

    fun isWordChar(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.' || ch == '/' || ch == '~' || ch == ':'

    fun wordStart(code: String, cursor: Int): Int {
        var index = cursor
        while (index > 0 && isWordChar(code[index - 1])) index -= 1
        return index
    }

    fun wordEnd(code: String, cursor: Int): Int {
        var index = cursor
        while (index < code.length && isWordChar(code[index])) index += 1
        return index
    }

    fun lineStart(code: String, cursor: Int): Int {
        if (cursor <= 0) return 0
        val newline = code.lastIndexOf('\n', cursor - 1)
        return if (newline < 0) 0 else newline + 1
    }

    fun lineEnd(code: String, offset: Int): Int {
        val newline = code.indexOf('\n', offset)
        return if (newline < 0) code.length else newline
    }

    fun lineNumberAt(code: String, offset: Int): Int {
        var line = 1
        var index = 0
        val limit = offset.coerceIn(0, code.length)
        while (index < limit) {
            if (code[index] == '\n') line += 1
            index += 1
        }
        return line
    }

    fun columnAt(code: String, offset: Int): Int = offset - lineStart(code, offset)

    /** 按空白分词，保留引号内的空格（引号本身去除）；用于参数数量/路径诊断。 */
    fun splitArgs(commandLine: String): List<String> {
        val args = ArrayList<String>()
        var index = 0
        while (index < commandLine.length) {
            val ch = commandLine[index]
            if (ch.isWhitespace()) {
                index += 1
                continue
            }
            val builder = StringBuilder()
            var quote: Char? = null
            while (index < commandLine.length) {
                val current = commandLine[index]
                when {
                    quote != null -> {
                        if (current == quote) quote = null else builder.append(current)
                        index += 1
                    }
                    current == '"' || current == '\'' -> {
                        quote = current
                        index += 1
                    }
                    current.isWhitespace() -> break
                    else -> {
                        builder.append(current)
                        index += 1
                    }
                }
            }
            args += builder.toString()
        }
        return args
    }

    /** 明显会越出沙箱的路径：绝对路径、~ 展开、.. 越级、Windows 盘符。 */
    fun looksLikeSandboxEscape(token: String): Boolean {
        if (token.isEmpty()) return false
        if (token.startsWith("/") || token.startsWith("~")) return true
        if (token == "..") return true
        if (token.startsWith("../") || token.contains("/../") || token.endsWith("/..")) return true
        if (token.length >= 3 && token[0].isLetter() && token[1] == ':' && (token[2] == '\\' || token[2] == '/')) return true
        return false
    }
}
