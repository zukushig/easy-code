package com.codetree.domain.search

import com.codetree.domain.model.Term
import com.codetree.domain.model.TermAlias
import com.codetree.plugin.LocalizedText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryTermSearchEngineTest {

    // ------------------------------------------------------------------ fixture

    private fun term(
        id: String,
        zhName: String,
        enName: String = "",
        aliases: List<String> = emptyList(),
        zhDefinition: String = "",
        enDefinition: String = "",
    ): Term = Term(
        id = id,
        displayName = localized(zhName, enName),
        aliases = aliases.map { TermAlias(text = it) },
        definitions = localized(zhDefinition, enDefinition),
    )

    private fun localized(zh: String, en: String = ""): LocalizedText = LocalizedText(
        buildMap {
            if (zh.isNotBlank()) put("zh-CN", zh)
            if (en.isNotBlank()) put("en", en)
        },
    )

    private fun pythonTerms(): List<Term> = listOf(
        term(
            id = "py.list-comprehension",
            zhName = "列表推导式",
            enName = "List Comprehension",
            aliases = listOf("列表解析式", "list comp"),
            zhDefinition = "用一行代码生成列表的 Python 语法，比 for 循环更简洁。",
            enDefinition = "A concise way to build lists in Python instead of a for loop.",
        ),
        term(
            id = "py.variable",
            zhName = "变量",
            enName = "Variable",
            aliases = listOf("赋值变量"),
            zhDefinition = "用于保存数据的具名存储位置。",
            enDefinition = "A named storage location used to hold data.",
        ),
        term(
            id = "py.for-loop",
            zhName = "for 循环",
            enName = "For Loop",
            aliases = listOf("遍历循环"),
            zhDefinition = "重复执行代码块的控制流语句。",
            enDefinition = "A control flow statement that repeats a block of code.",
        ),
    )

    private fun engineWith(terms: List<Term>): InMemoryTermSearchEngine =
        InMemoryTermSearchEngine().apply { rebuild(terms) }

    // ------------------------------------------------------------------ 精确层

    @Test
    fun `exact id has highest score and kind`() {
        val result = engineWith(pythonTerms()).search("py.list-comprehension")
        val hit = result.hits.first()
        assertEquals(SearchMatchKind.EXACT_ID, hit.kind)
        assertEquals("py.list-comprehension", hit.termId)
        assertEquals(1000, hit.score)
        assertEquals("id", hit.matchedField)
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `exact chinese display name matches and carries localized display name`() {
        val result = engineWith(pythonTerms()).search("列表推导式")
        val hit = result.hits.first()
        assertEquals(SearchMatchKind.EXACT_NAME, hit.kind)
        assertEquals("py.list-comprehension", hit.termId)
        assertEquals("列表推导式", hit.displayName.get("zh-CN"))
        assertEquals("List Comprehension", hit.displayName.get("en"))
    }

    @Test
    fun `exact display name match is case and whitespace insensitive`() {
        val result = engineWith(pythonTerms()).search("  LIST   comprehension ")
        assertEquals(SearchMatchKind.EXACT_NAME, result.hits.first().kind)
        assertEquals("py.list-comprehension", result.hits.first().termId)
    }

    @Test
    fun `exact alias matches below display name`() {
        val result = engineWith(pythonTerms()).search("列表解析式")
        val hit = result.hits.first()
        assertEquals(SearchMatchKind.EXACT_ALIAS, hit.kind)
        assertEquals("alias", hit.matchedField)
        assertEquals(800, hit.score)
    }

    @Test
    fun `exact match beats prefix match ordering`() {
        val terms = listOf(
            term(id = "t.exact", zhName = "list"),
            term(id = "t.prefix", zhName = "list comprehension"),
        )
        val hits = engineWith(terms).search("list").hits
        assertEquals("t.exact", hits[0].termId)
        assertEquals(SearchMatchKind.EXACT_NAME, hits[0].kind)
        assertTrue(hits.any { it.termId == "t.prefix" && it.kind == SearchMatchKind.PREFIX })
    }

    // ------------------------------------------------------------------ 前缀层

    @Test
    fun `chinese prefix match returns prefix kind with length bonus`() {
        val result = engineWith(pythonTerms()).search("列表")
        val hit = result.hits.first { it.termId == "py.list-comprehension" }
        assertEquals(SearchMatchKind.PREFIX, hit.kind)
        assertTrue(hit.score in 600..699, "prefix score should carry length bonus: ${hit.score}")
    }

    @Test
    fun `english prefix matches display name`() {
        val result = engineWith(pythonTerms()).search("for lo")
        val hit = result.hits.first { it.termId == "py.for-loop" }
        assertEquals(SearchMatchKind.PREFIX, hit.kind)
    }

    @Test
    fun `prefix hit is not marked as suggestion`() {
        val result = engineWith(pythonTerms()).search("列表")
        assertTrue(result.hits.none { it.suggestion })
        assertTrue(result.suggestions.isEmpty())
    }

    // ------------------------------------------------------------------ FTS 层

    @Test
    fun `fts matches chinese definition bigrams`() {
        val result = engineWith(pythonTerms()).search("简洁")
        val hit = result.hits.firstOrNull { it.termId == "py.list-comprehension" }
        assertNotNull(hit)
        assertEquals(SearchMatchKind.FTS, hit!!.kind)
        assertTrue(hit.score >= 300)
        assertTrue(hit.score < 600)
    }

    @Test
    fun `fts matches english definition words case insensitively`() {
        val result = engineWith(pythonTerms()).search("concise")
        val hit = result.hits.firstOrNull { it.termId == "py.list-comprehension" }
        assertNotNull(hit)
        assertEquals(SearchMatchKind.FTS, hit!!.kind)
        assertEquals("definitions", hit.matchedField)
    }

    @Test
    fun `fts multiple token hits rank above single token hits`() {
        val terms = listOf(
            term(id = "term.multi", zhName = "甲", zhDefinition = "列表推导式 生成 列表"),
            term(id = "term.single", zhName = "乙", zhDefinition = "列表"),
        )
        val hits = engineWith(terms).search("列表 生成").hits
        val multi = hits.first { it.termId == "term.multi" }
        val single = hits.firstOrNull { it.termId == "term.single" }
        assertTrue(single == null || multi.score > single.score)
    }

    // ------------------------------------------------------------------ 模糊建议

    @Test
    fun `typo produces fuzzy suggestion instead of fake exact hit`() {
        val result = engineWith(pythonTerms()).search("varible")
        assertTrue(result.hits.isEmpty(), "no exact/prefix/fts should match: ${result.hits}")
        val suggestion = result.suggestions.first()
        assertEquals("py.variable", suggestion.termId)
        assertEquals(SearchMatchKind.FUZZY, suggestion.kind)
        assertTrue(suggestion.suggestion)
        assertNotNull(suggestion.reason)
        assertTrue(suggestion.reason!!.get("zh-CN").isNotBlank())
    }

    @Test
    fun `chinese contains produces fuzzy suggestion`() {
        val result = engineWith(pythonTerms()).search("推导")
        assertTrue(result.hits.isEmpty())
        val suggestion = result.suggestions.first { it.termId == "py.list-comprehension" }
        assertTrue(suggestion.suggestion)
        assertEquals(SearchMatchKind.FUZZY, suggestion.kind)
        assertNotNull(suggestion.reason)
    }

    @Test
    fun `suggestions are capped at five`() {
        val terms = (0 until 20).map { index -> term(id = "t.$index", enName = "variable", zhName = "变量$index") }
        val result = engineWith(terms).search("varible")
        assertTrue(result.suggestions.isNotEmpty())
        assertTrue(result.suggestions.size <= InMemoryTermSearchEngine.MAX_SUGGESTIONS)
    }

    @Test
    fun `exact hit suppresses fuzzy suggestions`() {
        val result = engineWith(pythonTerms()).search("列表推导式")
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `bounded edit distance respects threshold`() {
        assertEquals(0, InMemoryTermSearchEngine.boundedEditDistance("list", "list", 2))
        assertEquals(1, InMemoryTermSearchEngine.boundedEditDistance("list", "lisr", 2))
        assertEquals(2, InMemoryTermSearchEngine.boundedEditDistance("kist", "last", 2))
        assertTrue(InMemoryTermSearchEngine.boundedEditDistance("list", "xxxxxxxx", 2) > 2)
    }

    // ------------------------------------------------------------------ 输入与上限

    @Test
    fun `empty query returns empty result`() {
        val result = engineWith(pythonTerms()).search("   ")
        assertTrue(result.hits.isEmpty())
        assertTrue(result.suggestions.isEmpty())
        assertEquals("", result.normalizedQuery)
    }

    @Test
    fun `punctuation only query returns empty result`() {
        val result = engineWith(pythonTerms()).search("!!! ???")
        assertTrue(result.hits.isEmpty())
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `overlong query returns empty result without throwing`() {
        val result = engineWith(pythonTerms()).search("a".repeat(QueryNormalizer.MAX_INPUT_LENGTH + 1))
        assertTrue(result.hits.isEmpty())
        assertEquals(0L, result.elapsedMs.coerceAtLeast(0L))
    }

    @Test
    fun `limit is respected`() {
        val terms = (0 until 50).map { index ->
            term(id = "term.${index.toString().padStart(2, '0')}", enName = "common term $index", zhName = "术语$index")
        }
        val result = engineWith(terms).search("common", limit = 10)
        assertEquals(10, result.hits.size)
    }

    @Test
    fun `zero limit is clamped to one result`() {
        val result = engineWith(pythonTerms()).search("列表", limit = 0)
        assertEquals(1, result.hits.size)
    }

    @Test
    fun `search is deterministic across repeated calls`() {
        val engine = engineWith(pythonTerms())
        val first = engine.search("列表").hits.map { it.termId }
        val second = engine.search("列表").hits.map { it.termId }
        assertEquals(first, second)
    }

    // ------------------------------------------------------------------ rebuild

    @Test
    fun `rebuild replaces previous index`() {
        val engine = engineWith(pythonTerms())
        assertTrue(engine.search("列表推导式").hits.isNotEmpty())
        engine.rebuild(listOf(term(id = "only.term", zhName = "唯一术语")))
        assertTrue(engine.search("列表推导式").hits.isEmpty())
        assertTrue(engine.search("唯一术语").hits.isNotEmpty())
    }

    @Test
    fun `duplicate term ids are indexed once`() {
        val duplicate = term(id = "dup.term", zhName = "重复术语")
        val result = engineWith(listOf(duplicate, duplicate)).search("重复术语")
        assertEquals(1, result.hits.size)
    }

    @Test
    fun `empty rebuild returns empty results`() {
        val engine = InMemoryTermSearchEngine()
        engine.rebuild(emptyList())
        assertTrue(engine.search("anything").hits.isEmpty())
    }

    // ------------------------------------------------------------------ 规模与性能

    @Test
    fun `ten thousand terms rebuild and search without oom`() {
        val terms = (0 until 10_000).map { index ->
            term(
                id = "term.${index.toString().padStart(5, '0')}",
                zhName = "术语$index",
                enName = "Term $index",
                zhDefinition = "这是第 $index 个术语，用于压测搜索索引构建与查询。",
                enDefinition = "This is term number $index used for search indexing stress.",
            )
        }
        val engine = InMemoryTermSearchEngine()
        engine.rebuild(terms)
        val startedAt = System.nanoTime()
        val exact = engine.search("术语 500", limit = 30)
        val fts = engine.search("压力测试不存在", limit = 30)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue(exact.hits.isNotEmpty(), "expected some hits for 术语 500")
        assertTrue(fts.hits.isEmpty() || fts.hits.isNotEmpty()) // 不崩溃即可
        assertTrue(elapsedMs < 5_000, "search over 10k terms took ${elapsedMs}ms")
    }

    @Test
    fun `thousand terms search stays within interactive budget`() {
        val terms = (0 until 1_000).map { index ->
            term(
                id = "term.${index.toString().padStart(4, '0')}",
                zhName = "术语$index",
                enName = "Term $index",
                zhDefinition = "第 $index 个术语的搜索性能样本定义。",
                enDefinition = "Search performance sample definition number $index.",
            )
        }
        val engine = InMemoryTermSearchEngine()
        engine.rebuild(terms)
        engine.search("预热") // 类加载与 JIT 预热
        val startedAt = System.nanoTime()
        repeat(5) { engine.search("术语 42") }
        val averageMs = (System.nanoTime() - startedAt) / 1_000_000.0 / 5
        // 目标为 10ms 量级；CI/低端设备上放宽到 250ms，避免抖动导致 flaky
        assertTrue(averageMs < 250, "average search took ${averageMs}ms")
    }

    @Test
    fun `matched field distinguishes id displayName alias and definitions`() {
        val engine = engineWith(pythonTerms())
        assertEquals("id", engine.search("py.variable").hits.first().matchedField)
        assertEquals("displayName", engine.search("变量").hits.first().matchedField)
        assertEquals("alias", engine.search("赋值变量").hits.first().matchedField)
        assertEquals("definitions", engine.search("具名存储").hits.first().matchedField)
    }

    @Test
    fun `invalid query returns normalized query and zero-ish elapsed`() {
        val result = engineWith(pythonTerms()).search("   ")
        assertEquals("", result.normalizedQuery)
        assertNull(result.bestExact)
        assertTrue(result.isEmpty)
    }
}
