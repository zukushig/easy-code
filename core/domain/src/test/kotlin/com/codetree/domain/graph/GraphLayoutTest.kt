package com.codetree.domain.graph

import com.codetree.domain.model.EdgeType
import com.codetree.domain.model.Topic
import com.codetree.domain.model.TopicEdge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphLayoutTest {

    private fun topicGraph(
        ids: List<String>,
        prerequisites: List<Pair<String, String>> = emptyList(), // prerequisite -> dependent
        related: List<Pair<String, String>> = emptyList(),
        domains: Map<String, String> = emptyMap(),
    ): TopicGraph {
        val topics = ids.associateWith { Topic(id = it, domain = domains[it] ?: "python") }
        val edges = prerequisites.map { TopicEdge(it.first, it.second, EdgeType.PREREQUISITE) } +
            related.map { TopicEdge(it.first, it.second, EdgeType.RELATED) }
        return TopicGraph(topics = topics, edges = edges)
    }

    private val viewport = ViewportSize(1000f, 800f)

    @Test
    fun `empty graph keeps viewport and has no nodes`() {
        val layout = GraphLayout.compute(TopicGraph(), viewport)
        assertTrue(layout.positions.isEmpty())
        assertTrue(layout.layers.isEmpty())
        assertEquals(viewport, layout.viewportSize)
        assertEquals(0f, layout.nodeRadius)
    }

    @Test
    fun `linear prerequisite chain is layered in order`() {
        val graph = topicGraph(
            ids = listOf("a", "b", "c"),
            prerequisites = listOf("a" to "b", "b" to "c"),
        )
        val layout = GraphLayout.compute(graph, viewport)
        assertEquals(0, layout.layers["a"])
        assertEquals(1, layout.layers["b"])
        assertEquals(2, layout.layers["c"])
        val ya = layout.position("a")!!.y
        val yb = layout.position("b")!!.y
        val yc = layout.position("c")!!.y
        assertTrue(ya < yb && yb < yc, "layers should stack top-down: $ya, $yb, $yc")
        assertEquals(3, layout.positions.size)
    }

    @Test
    fun `diamond graph puts middle nodes on the same layer`() {
        val graph = topicGraph(
            ids = listOf("root", "left", "right", "leaf"),
            prerequisites = listOf("root" to "left", "root" to "right", "left" to "leaf", "right" to "leaf"),
        )
        val layout = GraphLayout.compute(graph, viewport)
        assertEquals(0, layout.layers["root"])
        assertEquals(1, layout.layers["left"])
        assertEquals(1, layout.layers["right"])
        assertEquals(2, layout.layers["leaf"])
        assertEquals(
            layout.position("left")!!.y,
            layout.position("right")!!.y,
            "same layer must share the same y coordinate",
        )
        assertTrue(layout.position("left")!!.x != layout.position("right")!!.x)
        // 最长路径分层：leaf 必须在 left/right 之后
        assertTrue(layout.position("leaf")!!.y > layout.position("left")!!.y)
    }

    @Test
    fun `nodes without prerequisites share layer zero`() {
        val graph = topicGraph(ids = listOf("a", "b", "c"))
        val layout = GraphLayout.compute(graph, viewport)
        assertEquals(setOf(0), layout.layers.values.toSet())
        val ys = layout.positions.values.map { it.y }.toSet()
        assertEquals(1, ys.size)
        assertEquals(3, layout.positions.values.map { it.x }.toSet().size)
    }

    @Test
    fun `related edges do not affect layering`() {
        val graph = topicGraph(
            ids = listOf("a", "b", "c"),
            related = listOf("a" to "b", "b" to "c"),
        )
        val layout = GraphLayout.compute(graph, viewport)
        assertEquals(setOf(0), layout.layers.values.toSet())
    }

    @Test
    fun `cycle falls back to domain clustering without losing nodes`() {
        val graph = topicGraph(
            ids = listOf("a", "b", "c"),
            prerequisites = listOf("a" to "b", "b" to "a"),
            domains = mapOf("a" to "python", "b" to "python", "c" to "android"),
        )
        val layout = GraphLayout.compute(graph, viewport)
        assertEquals(setOf("a", "b", "c"), layout.positions.keys)
        assertEquals(setOf("a", "b", "c"), layout.layers.keys)
        assertEquals(layout.layers["a"], layout.layers["b"], "same domain -> same layer on fallback")
        assertTrue(layout.layers["c"] != layout.layers["a"], "different domains should not be forced together")
        assertNotNull(layout.position("a"))
        assertNotNull(layout.position("b"))
        assertNotNull(layout.position("c"))
    }

    @Test
    fun `self prerequisite edge falls back safely`() {
        val graph = topicGraph(ids = listOf("a", "b"), prerequisites = listOf("a" to "a", "a" to "b"))
        val layout = GraphLayout.compute(graph, viewport)
        assertEquals(setOf("a", "b"), layout.positions.keys)
        assertEquals(setOf("a", "b"), layout.layers.keys)
    }

    @Test
    fun `prerequisite to missing topic is ignored`() {
        val graph = topicGraph(ids = listOf("a", "b"), prerequisites = listOf("ghost" to "b"))
        val layout = GraphLayout.compute(graph, viewport)
        assertEquals(0, layout.layers["a"])
        assertEquals(0, layout.layers["b"])
    }

    @Test
    fun `same input produces identical deterministic output`() {
        val prerequisites = listOf("a" to "b", "a" to "c", "b" to "d", "c" to "d")
        val forward = topicGraph(ids = listOf("a", "b", "c", "d"), prerequisites = prerequisites)
        val reversed = topicGraph(ids = listOf("d", "c", "b", "a"), prerequisites = prerequisites.reversed())
        val first = GraphLayout.compute(forward, viewport)
        val second = GraphLayout.compute(reversed, viewport)
        assertEquals(first.positions, second.positions)
        assertEquals(first.layers, second.layers)
        assertEquals(first.nodeRadius, second.nodeRadius)
        assertEquals(first, GraphLayout.compute(forward, viewport))
    }

    @Test
    fun `all positions stay inside viewport`() {
        val graph = topicGraph(
            ids = listOf("a", "b", "c", "d", "e"),
            prerequisites = listOf("a" to "b", "a" to "c", "b" to "d", "c" to "e"),
        )
        val layout = GraphLayout.compute(graph, viewport)
        for (point in layout.positions.values) {
            assertTrue(point.x >= 0f && point.x <= viewport.width, "x out of viewport: ${point.x}")
            assertTrue(point.y >= 0f && point.y <= viewport.height, "y out of viewport: ${point.y}")
        }
        assertTrue(layout.nodeRadius in 4f..24f, "node radius: ${layout.nodeRadius}")
    }

    @Test
    fun `single node is centered`() {
        val layout = GraphLayout.compute(topicGraph(ids = listOf("only")), viewport)
        val point = layout.position("only")!!
        assertEquals(viewport.width / 2f, point.x, 0.001f)
        assertEquals(viewport.height / 2f, point.y, 0.001f)
    }

    @Test
    fun `zero viewport produces zero radius and finite coordinates`() {
        val graph = topicGraph(ids = listOf("a", "b"), prerequisites = listOf("a" to "b"))
        val layout = GraphLayout.compute(graph, 0f, 0f)
        assertEquals(0f, layout.nodeRadius)
        assertTrue(layout.positions.values.all { it.x == 0f && it.y == 0f })
    }

    @Test
    fun `int viewport overload works`() {
        val layout = GraphLayout.compute(topicGraph(ids = listOf("a")), 1080, 1920)
        assertEquals(1080f, layout.viewportSize.width)
        assertEquals(1920f, layout.viewportSize.height)
        assertEquals(1080f / 2f, layout.position("a")!!.x, 0.001f)
    }

    @Test
    fun `long chain does not overflow stack or drop nodes`() {
        val ids = (0 until 500).map { "n$it" }
        val prerequisites = (0 until 499).map { "n$it" to "n${it + 1}" }
        val layout = GraphLayout.compute(topicGraph(ids = ids, prerequisites = prerequisites), viewport)
        assertEquals(500, layout.positions.size)
        assertEquals(499, layout.layers["n499"])
        assertEquals(0, layout.layers["n0"])
    }

    @Test
    fun `explicit cycle detection matches fallback trigger`() {
        val graph = topicGraph(
            ids = listOf("a", "b"),
            prerequisites = listOf("a" to "b", "b" to "a"),
        )
        assertTrue(graph.detectPrerequisiteCycles().isNotEmpty())
        val layout = GraphLayout.compute(graph, viewport)
        assertTrue(layout.positions.isNotEmpty())
    }
}
