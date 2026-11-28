package com.codetree.feature.terminal.analysis

/** 简化 ANSI 颜色（只处理 SGR 常见颜色；背景色与扩展色忽略）。 */
enum class AnsiColor {
    DEFAULT,
    BLACK,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    MAGENTA,
    CYAN,
    WHITE,
    BRIGHT_RED,
    BRIGHT_GREEN,
    BRIGHT_YELLOW,
    BRIGHT_BLUE,
    BRIGHT_MAGENTA,
    BRIGHT_CYAN,
    BRIGHT_WHITE,
}

data class AnsiSegment(
    val text: String,
    val color: AnsiColor = AnsiColor.DEFAULT,
    val bold: Boolean = false,
    val dim: Boolean = false,
)

/**
 * 极简 ANSI SGR 解析：处理 `ESC[...m` 的前景颜色/加粗/暗淡，其余控制序列直接丢弃，
 * 避免把转义字符渲染到屏幕上。非 SGR 内容按 DEFAULT 原样输出。
 */
object AnsiParser {

    private const val ESC = '\u001B'

    fun parse(input: String): List<AnsiSegment> {
        if (input.isEmpty()) return emptyList()
        val segments = ArrayList<AnsiSegment>()
        val buffer = StringBuilder()
        var color = AnsiColor.DEFAULT
        var bold = false
        var dim = false

        fun flush() {
            if (buffer.isEmpty()) return
            val text = buffer.toString()
            buffer.setLength(0)
            val last = segments.lastOrNull()
            if (last != null && last.color == color && last.bold == bold && last.dim == dim) {
                segments[segments.lastIndex] = last.copy(text = last.text + text)
            } else {
                segments += AnsiSegment(text = text, color = color, bold = bold, dim = dim)
            }
        }

        var index = 0
        while (index < input.length) {
            val ch = input[index]
            if (ch == ESC) {
                val sequenceStart = index
                var next = index + 1
                if (next < input.length && input[next] == '[') {
                    next += 1
                    while (next < input.length && (input[next].isDigit() || input[next] == ';')) next += 1
                    if (next < input.length) {
                        val finalChar = input[next]
                        if (finalChar == 'm') {
                            val params = input.substring(sequenceStart + 2, next)
                            val change = applySgr(params, color, bold, dim)
                            flush()
                            color = change.first
                            bold = change.second
                            dim = change.third
                        }
                        index = next + 1
                        continue
                    }
                }
                // 非 CSI 或不完整转义：跳过 ESC 本身
                index += 1
                continue
            }
            if (ch == '\r') {
                index += 1
                continue
            }
            if (ch == '\b') {
                index += 1
                continue
            }
            buffer.append(ch)
            index += 1
            if (segments.size + 1 > MAX_SEGMENTS) break
        }
        flush()
        return segments.take(MAX_SEGMENTS)
    }

    private fun applySgr(
        params: String,
        currentColor: AnsiColor,
        currentBold: Boolean,
        currentDim: Boolean,
    ): Triple<AnsiColor, Boolean, Boolean> {
        var color = currentColor
        var bold = currentBold
        var dim = currentDim
        if (params.isBlank()) return Triple(AnsiColor.DEFAULT, false, false)
        val parts = params.split(';')
        var index = 0
        while (index < parts.size) {
            val code = parts[index].toIntOrNull() ?: 0
            when {
                code == 0 -> {
                    color = AnsiColor.DEFAULT
                    bold = false
                    dim = false
                }
                code == 1 -> bold = true
                code == 2 -> dim = true
                code == 22 -> {
                    bold = false
                    dim = false
                }
                code == 39 -> color = AnsiColor.DEFAULT
                code in 30..37 -> color = basicColor(code - 30)
                code in 90..97 -> color = brightColor(code - 90)
                code == 38 || code == 48 -> {
                    // 扩展色：38;5;n 或 38;2;r;g;b，跳过对应参数（前景/背景都不渲染）
                    val mode = parts.getOrNull(index + 1)?.toIntOrNull()
                    index += when (mode) {
                        5 -> 2
                        2 -> 4
                        else -> 0
                    }
                }
                code in 40..49 -> Unit
                else -> Unit
            }
            index += 1
        }
        return Triple(color, bold, dim)
    }

    private fun basicColor(code: Int): AnsiColor = when (code) {
        0 -> AnsiColor.BLACK
        1 -> AnsiColor.RED
        2 -> AnsiColor.GREEN
        3 -> AnsiColor.YELLOW
        4 -> AnsiColor.BLUE
        5 -> AnsiColor.MAGENTA
        6 -> AnsiColor.CYAN
        else -> AnsiColor.WHITE
    }

    private fun brightColor(code: Int): AnsiColor = when (code) {
        0 -> AnsiColor.BRIGHT_RED
        1 -> AnsiColor.BRIGHT_GREEN
        2 -> AnsiColor.BRIGHT_YELLOW
        3 -> AnsiColor.BRIGHT_BLUE
        4 -> AnsiColor.BRIGHT_MAGENTA
        5 -> AnsiColor.BRIGHT_CYAN
        else -> AnsiColor.BRIGHT_WHITE
    }

    private const val MAX_SEGMENTS = 2000
}
