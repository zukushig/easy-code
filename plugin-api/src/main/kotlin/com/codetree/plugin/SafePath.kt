package com.codetree.plugin

/**
 * 路径安全策略（docs/02 §6.2）：任何来自清单、网络或用户输入的相对路径，
 * 规范化后必须仍位于目标根目录；符号链接、绝对路径与 .. 一律拒绝。
 */
object SafePath {

    sealed interface Check {
        data class Ok(val normalized: String) : Check
        data class Rejected(val reason: String) : Check
    }

    private val DRIVE_PREFIX = Regex("^[A-Za-z]:")

    /** 只校验相对路径；windowed 真机为 POSIX，仍拒绝 Windows 风格绝对路径以防恶意包。 */
    fun checkRelative(raw: String): Check {
        if (raw.isBlank()) return Check.Rejected("empty path")
        if (raw.startsWith("/")) return Check.Rejected("absolute path")
        if (DRIVE_PREFIX.containsMatchIn(raw)) return Check.Rejected("drive path")
        if (raw.contains('\u0000')) return Check.Rejected("nul byte")
        val segments = raw.replace('\\', '/').split('/')
        val normalized = ArrayDeque<String>()
        for (segment in segments) {
            when (segment) {
                "", "." -> Unit
                ".." -> return Check.Rejected("parent traversal")
                else -> {
                    if (segment.length > 255) return Check.Rejected("segment too long")
                    normalized.addLast(segment)
                }
            }
        }
        if (normalized.isEmpty()) return Check.Rejected("empty path")
        return Check.Ok(normalized.joinToString("/"))
    }

    /** 确保目标路径位于给定根目录下（调用方已在设置根目录时校验真实路径）。 */
    fun isUnderRoot(root: String, target: String): Boolean {
        val normalizedRoot = root.replace('\\', '/').trimEnd('/')
        val normalizedTarget = target.replace('\\', '/')
        return normalizedTarget == normalizedRoot || normalizedTarget.startsWith("$normalizedRoot/")
    }

    /** 安装目录中的可执行内容黑名单（P0 声明式插件不允许任何可执行入口）。 */
    fun isForbiddenEntry(path: String): Boolean {
        val lower = path.lowercase()
        val forbiddenExtensions = listOf(".so", ".dex", ".jar", ".apk", ".bin", ".elf", ".wasm", ".sh", ".bat", ".exe")
        if (forbiddenExtensions.any { lower.endsWith(it) }) return true
        return lower.startsWith("lib/") || lower.startsWith("classes/")
    }
}
