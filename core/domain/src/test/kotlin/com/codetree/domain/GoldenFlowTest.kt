package com.codetree.domain

import com.codetree.domain.ai.AiEvent
import com.codetree.domain.ai.OpenAiSseParser
import com.codetree.domain.graph.TopicGraph
import com.codetree.domain.model.Difficulty
import com.codetree.domain.model.EdgeType
import com.codetree.domain.model.ExampleVerification
import com.codetree.domain.model.LearningStats
import com.codetree.domain.model.LearningStatus
import com.codetree.domain.model.SyntaxExample
import com.codetree.domain.model.Term
import com.codetree.domain.model.TermAlias
import com.codetree.domain.model.Topic
import com.codetree.domain.model.TopicEdge
import com.codetree.domain.model.TopicProgress
import com.codetree.domain.search.InMemoryTermSearchEngine
import com.codetree.domain.search.SearchMatchKind
import com.codetree.domain.search.TermSearchEngine
import com.codetree.plugin.LocalizedList
import com.codetree.plugin.LocalizedText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 「学 → 查 → 写 → 问」JVM 侧黄金路径（不依赖 Android / 网络 / 文件系统）。
 *
 * 用少量内存内容串起核心领域逻辑：知识图分层与路径 → 词典搜索 → 打开节点拿示例代码 →
 * Mock SSE 流式解析 AI 回答 → 进度状态机。所有外部输入都是内存 fixture / 固定 SSE 字符串。
 */
class GoldenFlowTest {

    // ------------------------------------------------------------------ fixture

    private fun localized(zh: String, en: String = ""): LocalizedText = LocalizedText(
        buildMap {
            if (zh.isNotBlank()) put("zh-CN", zh)
            if (en.isNotBlank()) put("en", en)
        },
    )

    private data class GoldenContent(
        val terms: List<Term>,
        val topics: List<Topic>,
        val examples: List<SyntaxExample>,
        val graph: TopicGraph,
        val searchEngine: TermSearchEngine,
    )

    private fun buildContent(): GoldenContent {
        val terms = listOf(
            Term(
                id = "term.py.list-comprehension",
                displayName = localized("列表推导式", "List Comprehension"),
                aliases = listOf(TermAlias("列表解析式"), TermAlias("list comp")),
                definitions = localized(
                    "用一行代码生成列表的 Python 语法，比 for 循环更简洁。",
                    "A concise way to build lists in Python instead of a for loop.",
                ),
                exampleIds = listOf("example.list-comprehension"),
                topicIds = listOf("python.list-comprehension"),
            ),
            Term(
                id = "term.py.for-loop",
                displayName = localized("for 循环", "For Loop"),
                definitions = localized("重复执行代码块的控制流语句。", "A control flow statement that repeats a block of code."),
                topicIds = listOf("python.basics"),
            ),
        )

        val topics = listOf(
            Topic(
                id = "python.basics",
                domain = "python",
                difficulty = Difficulty.BEGINNER,
                title = localized("Python 基础", "Python Basics"),
                summary = localized("变量、表达式与基本控制流。", "Variables, expressions and basic control flow."),
                objectives = LocalizedList.of(listOf("能写出第一条 Python 语句"), listOf("Write your first Python statement")),
                termIds = listOf("term.py.for-loop"),
                estimatedMinutes = 10,
            ),
            Topic(
                id = "python.list-comprehension",
                domain = "python",
                difficulty = Difficulty.INTERMEDIATE,
                title = localized("列表推导式", "List Comprehensions"),
                summary = localized("用一行表达式构建列表。", "Build lists with a single expression."),
                objectives = LocalizedList.of(
                    listOf("能读懂并写出列表推导式", "能判断何时该用推导式"),
                    listOf("Read and write list comprehensions", "Decide when a comprehension is appropriate"),
                ),
                termIds = listOf("term.py.list-comprehension"),
                exampleIds = listOf("example.list-comprehension"),
                estimatedMinutes = 15,
            ),
            Topic(
                id = "python.functions",
                domain = "python",
                difficulty = Difficulty.INTERMEDIATE,
                title = localized("函数", "Functions"),
                summary = localized("把重复逻辑封装成可复用函数。", "Reuse repeated logic through functions."),
                objectives = LocalizedList.of(listOf("能定义并调用函数"), listOf("Define and call functions")),
                estimatedMinutes = 20,
            ),
        )

        val edges = listOf(
            TopicEdge("python.basics", "python.list-comprehension", EdgeType.PREREQUISITE),
            TopicEdge("python.list-comprehension", "python.functions", EdgeType.PREREQUISITE),
            TopicEdge("python.basics", "python.functions", EdgeType.RELATED),
        )

        val examples = listOf(
            SyntaxExample(
                id = "example.list-comprehension",
                language = "python",
                languageVersion = "3.12",
                title = localized("平方数列表", "Squares with a comprehension"),
                explanation = localized(
                    "推导式把循环、条件与表达式合并到一个位置，读起来更接近自然语言。",
                    "A comprehension folds loop, condition and expression into one place.",
                ),
                code = "squares = [x * x for x in range(5)]\nprint(squares)",
                annotations = emptyList(),
                expectedOutput = "[0, 1, 4, 9, 16]",
                runtimeId = null, // JVM 黄金路径不执行代码，只验证内容与解析链路
                verificationStatus = ExampleVerification.STATIC_CHECKED,
            ),
        )

        val graph = TopicGraph(topics = topics.associateBy { it.id }, edges = edges)
        val engine = InMemoryTermSearchEngine().apply { rebuild(terms) }
        return GoldenContent(terms, topics, examples, graph, engine)
    }

    /** 最小进度状态机：只允许 NOT_STARTED → LEARNING → MASTERED，非法跃迁返回 false。 */
    private class InMemoryProgressMachine {
        private val progress = mutableMapOf<String, TopicProgress>()

        fun status(topicId: String): LearningStatus = progress[topicId]?.status ?: LearningStatus.NOT_STARTED

        fun markLearning(topicId: String, nowMs: Long): Boolean {
            val current = status(topicId)
            if (current == LearningStatus.MASTERED) return false
            progress[topicId] = TopicProgress(topicId, LearningStatus.LEARNING, nowMs, nowMs)
            return true
        }

        fun master(topicId: String, nowMs: Long): Boolean {
            if (status(topicId) != LearningStatus.LEARNING) return false
            val existing = progress.getValue(topicId)
            progress[topicId] = existing.copy(status = LearningStatus.MASTERED, updatedAtEpochMs = nowMs)
            return true
        }

        fun stats(allTopicIds: List<String>): LearningStats {
            var mastered = 0
            var learning = 0
            var notStarted = 0
            for (id in allTopicIds) {
                when (status(id)) {
                    LearningStatus.MASTERED -> mastered++
                    LearningStatus.LEARNING -> learning++
                    LearningStatus.NOT_STARTED -> notStarted++
                }
            }
            return LearningStats(allTopicIds.size, mastered, learning, notStarted)
        }
    }

    private fun mockSseStream(): List<String> = listOf(
        ": keep-alive",
        "",
        """data: {"choices":[{"delta":{"content":"列表"}}]}""",
        """data: {"choices":[{"delta":{"content":"推导式"}}]}""",
        """data: {"choices":[{"delta":{"content":"是 Python 里用一行表达式"}}]}""",
        """data: {"choices":[{"delta":{"content":"生成列表的语法，例如 [x * x for x in range(5)]。"}}]}""",
        """data: {"choices":[],"usage":{"prompt_tokens":42,"completion_tokens":18}}""",
        "data: [DONE]",
    )

    // ------------------------------------------------------------------ 黄金路径

    @Test
    fun `learn search write ask golden path`() {
        val content = buildContent()
        val parser = OpenAiSseParser()

        // ① 学：知识图可拓扑排序，推荐从无前置的基础节点开始
        val order = content.graph.topologicalOrder()
        assertNotNull(order)
        assertEquals("python.basics", order!!.first())
        val suggested = content.graph.suggestedNext(mastered = emptySet())
        assertTrue(suggested.any { it.id == "python.basics" })

        // ② 查：搜「列表推导式」精确命中术语，结果带本地化展示名
        val searchResult = content.searchEngine.search("列表推导式")
        assertFalse(searchResult.isEmpty)
        val best = searchResult.hits.first()
        assertEquals(SearchMatchKind.EXACT_NAME, best.kind)
        assertEquals("term.py.list-comprehension", best.termId)
        assertEquals("列表推导式", best.displayName.get("zh-CN"))
        assertTrue(searchResult.suggestions.isEmpty())

        // 术语 → 知识节点 → 示例代码（查→写）
        val term = content.terms.first { it.id == best.termId }
        val topicId = term.topicIds.first()
        val topic = content.graph.topic(topicId)
        assertNotNull(topic)
        assertEquals("python.list-comprehension", topic!!.id)
        assertEquals(listOf("term.py.list-comprehension"), topic.termIds)

        val exampleId = topic.exampleIds.first()
        val example = content.examples.first { it.id == exampleId }
        assertTrue(example.code.contains("[x * x for x in range(5)]"))
        assertEquals("[0, 1, 4, 9, 16]", example.expectedOutput)
        assertEquals(ExampleVerification.STATIC_CHECKED, example.verificationStatus)
        assertNull(example.runtimeId) // 无安全运行时时只读，不伪造执行结果
        assertFalse(example.isRunnable)
        assertTrue(example.explanation.get("zh-CN").contains("推导式"))
        assertTrue(example.explanation.get("en").contains("comprehension"))

        // ③ 问：Mock SSE 流式回答完整拼装，不依赖网络
        val answer = StringBuilder()
        var completed = false
        var usage: AiEvent.Usage? = null
        var failed: AiEvent.Failed? = null
        for (line in mockSseStream()) {
            for (event in parser.parseLine(line)) {
                when (event) {
                    is AiEvent.Delta -> answer.append(event.text)
                    is AiEvent.Usage -> usage = event
                    AiEvent.Completed -> completed = true
                    is AiEvent.Failed -> failed = event
                    else -> Unit
                }
            }
        }
        assertNull(failed)
        assertTrue(completed)
        assertEquals(AiEvent.Usage(42, 18), usage)
        assertTrue(answer.toString().contains("列表推导式"))
        assertTrue(answer.toString().contains("[x * x for x in range(5)]"))

        // ④ 进度状态机：学习 → 掌握，统计与图推荐联动
        val progress = InMemoryProgressMachine()
        assertEquals(LearningStatus.NOT_STARTED, progress.status(topic.id))
        assertTrue(progress.markLearning(topic.id, nowMs = 1_000L))
        assertEquals(LearningStatus.LEARNING, progress.status(topic.id))
        assertTrue(progress.master(topic.id, nowMs = 2_000L))
        assertEquals(LearningStatus.MASTERED, progress.status(topic.id))

        val stats = progress.stats(content.topics.map { it.id })
        assertEquals(3, stats.totalTopics)
        assertEquals(1, stats.mastered)
        assertEquals(0, stats.learning) // python.basics 仍为未开始（进度按节点独立保存）
        assertEquals(2, stats.notStarted)
        assertEquals(1f / 3f, stats.masteryRatio)

        // ⑤ 学习路径：从已掌握的 python.basics 到列表推导式
        assertEquals(
            listOf("python.basics", "python.list-comprehension"),
            content.graph.path("python.basics", "python.list-comprehension"),
        )
        val next = content.graph.suggestedNext(mastered = setOf("python.basics"))
        assertTrue(next.any { it.id == "python.list-comprehension" })
        // 掌握列表推导式后，函数（前置=list-comprehension）才进入推荐
        val afterMastering = content.graph.suggestedNext(
            mastered = setOf("python.basics", "python.list-comprehension"),
        )
        assertTrue(afterMastering.any { it.id == "python.functions" })
    }

    @Test
    fun `search suggestion recovers from typo and opens the same topic`() {
        val content = buildContent()
        // 拼写错误不应冒充精确结果（英文拼写错误不共享中文 2-gram，避免 FTS 误命中）
        val result = content.searchEngine.search("list comprahension")
        assertTrue(result.hits.isEmpty())
        assertFalse(result.suggestions.isEmpty())
        val suggestion = result.suggestions.first()
        assertTrue(suggestion.suggestion)
        assertEquals(SearchMatchKind.FUZZY, suggestion.kind)

        // 用户点选建议 → 与精确搜索落到同一个术语 / 节点
        val term = content.terms.first { it.id == suggestion.termId }
        val topic = content.graph.topic(term.topicIds.first())
        assertNotNull(topic)
        assertEquals("python.list-comprehension", topic!!.id)
    }

    @Test
    fun `ai mid stream error keeps partial answer and maps to AppError`() {
        val parser = OpenAiSseParser()
        val lines = listOf(
            """data: {"choices":[{"delta":{"content":"先给结论："}}]}""",
            """data: {"choices":[{"delta":{"content":"列表推导式更简洁。"}}]}""",
            """data: {"error":{"message":"upstream connection reset"}}""",
        )
        val answer = StringBuilder()
        var failed: AiEvent.Failed? = null
        for (line in lines) {
            for (event in parser.parseLine(line)) {
                when (event) {
                    is AiEvent.Delta -> answer.append(event.text)
                    is AiEvent.Failed -> failed = event
                    else -> Unit
                }
            }
        }
        assertEquals("先给结论：列表推导式更简洁。", answer.toString())
        assertNotNull(failed)
        assertEquals("internal", failed!!.error.code)
        assertTrue(failed!!.error.message.contains("upstream connection reset"))
    }

    @Test
    fun `progress state machine rejects illegal transitions`() {
        val progress = InMemoryProgressMachine()
        // 未学习不能直接标记掌握
        assertFalse(progress.master("python.basics", nowMs = 1L))
        assertEquals(LearningStatus.NOT_STARTED, progress.status("python.basics"))
        assertTrue(progress.markLearning("python.basics", nowMs = 2L))
        assertTrue(progress.markLearning("python.basics", nowMs = 3L))
        assertTrue(progress.master("python.basics", nowMs = 4L))
        // 已掌握不能回退为学习中
        assertFalse(progress.markLearning("python.basics", nowMs = 5L))
        assertEquals(LearningStatus.MASTERED, progress.status("python.basics"))
    }
}
