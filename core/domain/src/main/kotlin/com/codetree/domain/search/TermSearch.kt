package com.codetree.domain.search

import com.codetree.domain.model.Term
import com.codetree.plugin.LocalizedText

/** 搜索命中层级（docs/05 §8）：排序不得让推荐权重越过精确匹配层级。 */
enum class SearchMatchKind(val rank: Int) {
    EXACT_ID(0),
    EXACT_NAME(1),
    EXACT_ALIAS(2),
    PREFIX(3),
    FTS(4),
    FUZZY(5),
}

data class TermSearchHit(
    val termId: String,
    val kind: SearchMatchKind,
    val matchedField: String,
    val score: Int,
    val displayName: LocalizedText = LocalizedText.EMPTY,
    val reason: LocalizedText? = null,
    val suggestion: Boolean = false,
)

data class TermSearchResult(
    val query: String,
    val normalizedQuery: String,
    val hits: List<TermSearchHit> = emptyList(),
    val suggestions: List<TermSearchHit> = emptyList(),
    val elapsedMs: Long = 0L,
) {
    val isEmpty: Boolean get() = hits.isEmpty()
    val bestExact: TermSearchHit? get() = hits.firstOrNull { it.kind.rank <= SearchMatchKind.EXACT_ALIAS.rank }
}

/** 词典搜索引擎：精确 → 别名 → 前缀 → FTS 正文 → 受控模糊；新查询取消旧查询由调用方保证。 */
interface TermSearchEngine {
    fun search(query: String, limit: Int = 30): TermSearchResult
    fun rebuild(terms: List<Term>)
}

/** 查询规范化：去首尾空白、压缩内部空白；保留原始大小写用于展示，匹配使用 lowercase。 */
object QueryNormalizer {
    const val MAX_INPUT_LENGTH = 64

    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun isValid(raw: String): Boolean {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return false
        if (normalized.length > MAX_INPUT_LENGTH) return false
        // 仅标点的输入不构成有效查询
        return normalized.any { it.isLetterOrDigit() }
    }

    fun searchKey(raw: String): String = normalize(raw).lowercase()
}
