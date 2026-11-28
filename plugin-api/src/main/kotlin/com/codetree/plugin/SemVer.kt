package com.codetree.plugin

/** 简化 SemVer 与版本范围，用于内容包/插件依赖解析（无需引入第三方库）。 */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")

        fun parse(text: String): SemVer? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            return SemVer(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
    }
}

/** 版本范围：由若干空格分隔的比较条件组成，全部满足才匹配，如 ">=1.0.0 <2.0.0"。 */
class VersionRange(text: String) {
    val raw: String = text.trim()
    private val conditions: List<(SemVer) -> Boolean> = parseConditions(raw)

    fun contains(version: SemVer): Boolean = conditions.all { it(version) }

    private fun parseConditions(text: String): List<(SemVer) -> Boolean> {
        if (text.isBlank()) return emptyList()
        val tokens = text.split(Regex("\\s+"))
        val result = mutableListOf<(SemVer) -> Boolean>()
        for (token in tokens) {
            val op = when {
                token.startsWith(">=") -> ">="
                token.startsWith("<=") -> "<="
                token.startsWith(">") -> ">"
                token.startsWith("<") -> "<"
                token.startsWith("=") -> "="
                else -> "="
            }
            val versionText = token.removePrefix(op)
            val version = SemVer.parse(versionText)
                ?: throw IllegalArgumentException("invalid version in range: $token")
            result.add(
                when (op) {
                    ">=" -> { v: SemVer -> v >= version }
                    "<=" -> { v: SemVer -> v <= version }
                    ">" -> { v: SemVer -> v > version }
                    "<" -> { v: SemVer -> v < version }
                    else -> { v: SemVer -> v == version }
                }
            )
        }
        return result
    }

    companion object {
        fun matches(range: String, version: String): Boolean {
            val v = SemVer.parse(version) ?: return false
            return VersionRange(range).contains(v)
        }
    }
}
