package com.codetree.feature.terminal.analysis

/**
 * 轻量模糊匹配：子序列匹配 + 前缀/连续字符加权。
 *
 * 只用于补全排序，不做编辑距离计算（避免长输入时的性能问题）。
 */
object FuzzyMatch {

    /** 匹配得分；不匹配返回 null。空查询视为 0 分（匹配任何候选）。 */
    fun score(query: String, candidate: String): Int? {
        if (query.isEmpty()) return 0
        if (candidate.isEmpty()) return null
        val q = query.lowercase()
        val c = candidate.lowercase()
        var queryIndex = 0
        var score = 0
        var streak = 0
        var lastMatchIndex = -2
        var index = 0
        while (index < c.length && queryIndex < q.length) {
            if (c[index] == q[queryIndex]) {
                if (index == lastMatchIndex + 1) {
                    streak += 1
                    score += 2 + streak
                } else {
                    streak = 0
                    score += 1
                    if (index == 0) score += 3
                }
                lastMatchIndex = index
                queryIndex += 1
            }
            index += 1
        }
        if (queryIndex != q.length) return null
        if (c.startsWith(q)) score += 10
        return score
    }

    fun matches(query: String, candidate: String): Boolean = score(query, candidate) != null

    /** 按匹配度排序并截断；同分时短候选优先（更像命令名而不是长句子）。 */
    fun <T> rank(
        query: String,
        candidates: List<T>,
        text: (T) -> String,
        limit: Int,
    ): List<T> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()
        val scored = candidates.mapNotNull { candidate ->
            val candidateText = text(candidate)
            val score = score(query, candidateText) ?: return@mapNotNull null
            Triple(candidate, score, candidateText.length)
        }
        return scored
            .sortedWith(compareByDescending<Triple<T, Int, Int>> { it.second }.thenBy { it.third })
            .take(limit)
            .map { it.first }
    }
}
