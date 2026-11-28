package com.codetree.domain.search

import com.codetree.domain.model.Term
import com.codetree.plugin.LocalizedText

/**
 * 词典搜索引擎的纯内存实现（docs/specs/knowledge.md §8）。
 *
 * 索引结构（[rebuild] 时一次性构建，之后只读；`@Volatile` 快照保证并发搜索安全）：
 * - 规范化键：id / displayName 各语言 / aliases 统一 `trim + 压缩空白 + lowercase`；
 * - 精确映射：id、显示名、别名 → 候选条目；
 * - 前缀桶：每个键的前 1~3 个字符 → 候选条目，限制前缀查询扫描量；
 * - FTS 词表：definitions 分词后 → 条目；英文按非字母数字切分，中文按 2-gram；
 * - 模糊：仅在没有任何精确命中时才计算，且只进 `suggestions`（suggestion = true），
 *   候选池上限 [FUZZY_CANDIDATE_POOL]，编辑距离阈值：长度 ≤ 4 为 1，否则为 2。
 *
 * 排序：先按 [SearchMatchKind.rank] 升序，再按得分降序，最后按 termId 字典序（保证确定性）。
 * 上限：输入 ≤ [QueryNormalizer.MAX_INPUT_LENGTH]、结果 ≤ min(limit, [MAX_RESULT_LIMIT])、
 * 模糊建议 ≤ [MAX_SUGGESTIONS]；空查询 / 超长 / 仅标点返回空结果，不抛异常。
 */
class InMemoryTermSearchEngine : TermSearchEngine {

    private class IndexedTerm(val term: Term) {
        val idKey: String = term.id.lowercase()
        val nameKeys: List<String> = keysOf(term.displayName)
        val aliasKeys: List<String> = term.aliases.mapNotNull { normalizeKey(it.text) }.distinct()
        val definitionValues: List<String> = term.definitions.values.values.filter { it.isNotBlank() }

        private companion object {
            fun keysOf(text: LocalizedText): List<String> =
                text.values.values.mapNotNull { normalizeKey(it) }.distinct()
        }
    }

    private class Index(
        val terms: List<IndexedTerm> = emptyList(),
        val byIdKey: Map<String, List<Int>> = emptyMap(),
        val byNameKey: Map<String, List<Int>> = emptyMap(),
        val byAliasKey: Map<String, List<Int>> = emptyMap(),
        val prefixBuckets: Map<String, List<Int>> = emptyMap(),
        val ftsPostings: Map<String, List<Int>> = emptyMap(),
    )

    @Volatile
    private var index: Index = Index()

    override fun rebuild(terms: List<Term>) {
        index = buildIndex(terms)
    }

    override fun search(query: String, limit: Int): TermSearchResult {
        val startedAt = System.nanoTime()
        val normalizedQuery = QueryNormalizer.normalize(query)
        val safeLimit = limit.coerceIn(1, MAX_RESULT_LIMIT)
        if (!QueryNormalizer.isValid(query)) {
            return TermSearchResult(
                query = query,
                normalizedQuery = normalizedQuery,
                elapsedMs = elapsedMs(startedAt),
            )
        }
        val key = QueryNormalizer.searchKey(query)
        val snapshot = index
        val best = LinkedHashMap<Int, TermSearchHit>()

        fun offer(entryIndex: Int, kind: SearchMatchKind, field: String, score: Int, reason: LocalizedText? = null) {
            val candidate = TermSearchHit(
                termId = snapshot.terms[entryIndex].term.id,
                kind = kind,
                matchedField = field,
                score = score,
                displayName = snapshot.terms[entryIndex].term.displayName,
                reason = reason,
                suggestion = false,
            )
            val existing = best[entryIndex]
            if (existing == null ||
                kind.rank < existing.kind.rank ||
                (kind == existing.kind && score > existing.score)
            ) {
                best[entryIndex] = candidate
            }
        }

        // 1) 精确层：id -> 显示名 -> 别名
        snapshot.byIdKey[key]?.forEach { offer(it, SearchMatchKind.EXACT_ID, "id", EXACT_ID_SCORE) }
        snapshot.byNameKey[key]?.forEach { offer(it, SearchMatchKind.EXACT_NAME, "displayName", EXACT_NAME_SCORE) }
        snapshot.byAliasKey[key]?.forEach { offer(it, SearchMatchKind.EXACT_ALIAS, "alias", EXACT_ALIAS_SCORE) }
        val hasExact = best.isNotEmpty()

        // 2) 前缀层：借助 1~3 字符桶限制扫描量；命中长度奖励与查询前缀长度正相关
        val bucketKey = key.take(minOf(PREFIX_BUCKET_SIZE, key.length))
        snapshot.prefixBuckets[bucketKey]?.forEach { entryIndex ->
            val indexed = snapshot.terms[entryIndex]
            for (nameKey in indexed.nameKeys) {
                if (nameKey.length > key.length && nameKey.startsWith(key)) {
                    offer(entryIndex, SearchMatchKind.PREFIX, "displayName", prefixScore(key))
                }
            }
            for (aliasKey in indexed.aliasKeys) {
                if (aliasKey.length > key.length && aliasKey.startsWith(key)) {
                    offer(entryIndex, SearchMatchKind.PREFIX, "alias", prefixScore(key))
                }
            }
            if (indexed.idKey.length > key.length && indexed.idKey.startsWith(key)) {
                offer(entryIndex, SearchMatchKind.PREFIX, "id", prefixScore(key))
            }
        }

        // 3) FTS 层：definitions 分词命中项数加权
        val queryTokens = SearchTokenizer.tokens(key)
        if (queryTokens.isNotEmpty()) {
            val matchedTokens = HashMap<Int, Int>()
            var exhausted = false
            for (token in queryTokens) {
                val postings = snapshot.ftsPostings[token] ?: continue
                for (entryIndex in postings) {
                    matchedTokens[entryIndex] = (matchedTokens[entryIndex] ?: 0) + 1
                    if (matchedTokens.size > FTS_CANDIDATE_POOL) {
                        exhausted = true
                        break
                    }
                }
                if (exhausted) break
            }
            for ((entryIndex, count) in matchedTokens) {
                offer(entryIndex, SearchMatchKind.FTS, "definitions", FTS_SCORE + minOf(199, count * 10))
            }
        }

        // 4) 模糊层：只有精确层完全未命中时才作为建议返回（不得冒充精确结果）
        val suggestions = if (hasExact) emptyList() else buildSuggestions(snapshot, key, best)
        val hits = best.values
            .sortedWith(
                compareBy<TermSearchHit> { it.kind.rank }
                    .thenByDescending { it.score }
                    .thenBy { it.termId },
            )
            .take(safeLimit)

        return TermSearchResult(
            query = query,
            normalizedQuery = normalizedQuery,
            hits = hits,
            suggestions = suggestions,
            elapsedMs = elapsedMs(startedAt),
        )
    }

    // ------------------------------------------------------------------ 模糊建议

    private fun buildSuggestions(
        snapshot: Index,
        key: String,
        existing: Map<Int, TermSearchHit>,
    ): List<TermSearchHit> {
        val distanceLimit = if (key.length <= 4) 1 else 2
        val isChinese = SearchTokenizer.containsCjk(key)
        val suggestions = mutableListOf<TermSearchHit>()
        var evaluated = 0

        for (entryIndex in snapshot.terms.indices) {
            if (entryIndex in existing) continue
            if (evaluated >= FUZZY_CANDIDATE_POOL) break
            evaluated++
            val indexed = snapshot.terms[entryIndex]
            var best: TermSearchHit? = null

            fun consider(candidateKey: String, field: String, fromAlias: Boolean): TermSearchHit? {
                if (candidateKey.length > MAX_FUZZY_CANDIDATE_LENGTH) return null
                if (isChinese && candidateKey.contains(key)) {
                    return fuzzyHit(indexed, field, CONTAINS_REASON, 100)
                }
                if (candidateKey == key) return null
                if (kotlin.math.abs(candidateKey.length - key.length) > distanceLimit) return null
                val distance = boundedEditDistance(key, candidateKey, distanceLimit)
                if (distance > distanceLimit) return null
                val reason = if (fromAlias) ALIAS_REASON else SPELLING_REASON
                return fuzzyHit(indexed, field, reason, 100 - distance * 10)
            }

            for (nameKey in indexed.nameKeys) {
                val hit = consider(nameKey, "displayName", fromAlias = false)
                if (hit != null && (best == null || hit.score > best.score)) best = hit
            }
            for (aliasKey in indexed.aliasKeys) {
                val hit = consider(aliasKey, "alias", fromAlias = true)
                if (hit != null && (best == null || hit.score > best.score)) best = hit
            }
            indexed.idKey.let { idKey ->
                val hit = consider(idKey, "id", fromAlias = false)
                if (hit != null && (best == null || hit.score > best.score)) best = hit
            }

            best?.let { suggestions += it.suggestionCopy() }
        }

        return suggestions
            .sortedWith(compareByDescending<TermSearchHit> { it.score }.thenBy { it.termId })
            .take(MAX_SUGGESTIONS)
    }

    private fun fuzzyHit(
        indexed: IndexedTerm,
        field: String,
        reason: LocalizedText,
        score: Int,
    ): TermSearchHit = TermSearchHit(
        termId = indexed.term.id,
        kind = SearchMatchKind.FUZZY,
        matchedField = field,
        score = score,
        displayName = indexed.term.displayName,
        reason = reason,
        suggestion = true,
    )

    private fun TermSearchHit.suggestionCopy(): TermSearchHit = copy(suggestion = true)

    // ------------------------------------------------------------------ 构建

    private fun buildIndex(terms: List<Term>): Index {
        if (terms.isEmpty()) return Index()
        val indexed = terms.distinctBy { it.id }.sortedBy { it.id }.map { IndexedTerm(it) }
        val byIdKey = HashMap<String, MutableList<Int>>()
        val byNameKey = HashMap<String, MutableList<Int>>()
        val byAliasKey = HashMap<String, MutableList<Int>>()
        val prefixBuckets = HashMap<String, MutableList<Int>>()
        val ftsPostings = HashMap<String, MutableList<Int>>()

        for (entryIndex in indexed.indices) {
            val indexedTerm = indexed[entryIndex]
            addTo(byIdKey, indexedTerm.idKey, entryIndex)
            indexedTerm.nameKeys.forEach { addTo(byNameKey, it, entryIndex) }
            indexedTerm.aliasKeys.forEach { addTo(byAliasKey, it, entryIndex) }

            val allKeys = buildSet {
                add(indexedTerm.idKey)
                addAll(indexedTerm.nameKeys)
                addAll(indexedTerm.aliasKeys)
            }
            for (prefixKey in allKeys) {
                val maxPrefix = minOf(PREFIX_BUCKET_SIZE, prefixKey.length)
                for (length in 1..maxPrefix) {
                    addTo(prefixBuckets, prefixKey.substring(0, length), entryIndex)
                }
            }

            for (definition in indexedTerm.definitionValues) {
                for (token in SearchTokenizer.tokens(definition)) {
                    addTo(ftsPostings, token, entryIndex)
                }
            }
        }

        return Index(
            terms = indexed,
            byIdKey = byIdKey,
            byNameKey = byNameKey,
            byAliasKey = byAliasKey,
            prefixBuckets = prefixBuckets,
            ftsPostings = ftsPostings,
        )
    }

    private fun addTo(map: HashMap<String, MutableList<Int>>, key: String, value: Int) {
        if (key.isEmpty()) return
        map.getOrPut(key) { ArrayList() }.add(value)
    }

    companion object {
        const val MAX_RESULT_LIMIT = 500
        const val MAX_SUGGESTIONS = 5
        const val FUZZY_CANDIDATE_POOL = 500
        const val FTS_CANDIDATE_POOL = 500
        const val PREFIX_BUCKET_SIZE = 3
        const val EXACT_ID_SCORE = 1000
        const val EXACT_NAME_SCORE = 900
        const val EXACT_ALIAS_SCORE = 800
        const val PREFIX_SCORE = 600
        const val FTS_SCORE = 300
        const val FUZZY_SCORE = 100
        private const val MAX_FUZZY_CANDIDATE_LENGTH = 120

        internal val SPELLING_REASON = LocalizedText.of("拼写相近", "similar spelling")
        internal val ALIAS_REASON = LocalizedText.of("别名匹配", "alias match")
        internal val CONTAINS_REASON = LocalizedText.of("名称包含", "name contains")

        internal fun normalizeKey(raw: String): String? =
            QueryNormalizer.searchKey(raw).ifBlank { null }

        /** 前缀长度奖励：查询前缀越长越具体，得分越高（上限 99）。 */
        internal fun prefixScore(key: String): Int = PREFIX_SCORE + minOf(99, key.length * 10)

        /** 带阈值的编辑距离：距离大于 [limit] 时提前返回 [limit] + 1。 */
        internal fun boundedEditDistance(left: String, right: String, limit: Int): Int {
            if (left == right) return 0
            if (kotlin.math.abs(left.length - right.length) > limit) return limit + 1
            var previous = IntArray(right.length + 1) { it }
            var current = IntArray(right.length + 1)
            for (i in 1..left.length) {
                current[0] = i
                var rowMin = current[0]
                for (j in 1..right.length) {
                    val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                    current[j] = minOf(
                        previous[j] + 1,
                        current[j - 1] + 1,
                        previous[j - 1] + cost,
                    )
                    if (current[j] < rowMin) rowMin = current[j]
                }
                if (rowMin > limit) return limit + 1
                val swap = previous
                previous = current
                current = swap
            }
            return previous[right.length]
        }

        private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000L
    }
}

/**
 * 受控分词（搜索与索引用同一套规则，保证可复现）：
 * - 英文/数字按非字母数字切分并小写，单字母噪声词（长度 1 且非纯数字）丢弃；
 * - 中文连续段按 2-gram 切分，单字段保留 1-gram。
 */
internal object SearchTokenizer {
    private val LATIN_WORD = Regex("[a-z0-9]+")
    private val CJK_RUN = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]+")

    fun tokens(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val lower = text.lowercase()
        val result = LinkedHashSet<String>()
        for (match in LATIN_WORD.findAll(lower)) {
            val word = match.value
            if (word.length >= 2 || word.all { it.isDigit() }) result += word
        }
        for (match in CJK_RUN.findAll(lower)) {
            val run = match.value
            if (run.length == 1) {
                result += run
            } else {
                for (index in 0 until run.length - 1) {
                    result += run.substring(index, index + 2)
                }
            }
        }
        return result
    }

    fun containsCjk(text: String): Boolean = CJK_RUN.containsMatchIn(text)
}
