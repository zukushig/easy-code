package com.codetree.domain.graph

import com.codetree.domain.model.EdgeType
import com.codetree.domain.model.LearningStatus
import com.codetree.domain.model.Topic
import com.codetree.domain.model.TopicEdge

/**
 * 有向知识图（docs/02 §7）：图遍历必须维护 visited 集，遇到循环依赖终止并报告内容问题。
 * 提供路径、前置闭包与推荐下一节点；UI 的「树」只是可视化称谓。
 */
class TopicGraph(
    val topics: Map<String, Topic> = emptyMap(),
    val edges: List<TopicEdge> = emptyList(),
) {
    fun topic(id: String): Topic? = topics[id]

    fun ids(): Set<String> = topics.keys

    fun outgoing(id: String, type: EdgeType? = null): List<TopicEdge> =
        edges.filter { it.source == id && (type == null || it.type == type) }

    fun incoming(id: String, type: EdgeType? = null): List<TopicEdge> =
        edges.filter { it.target == id && (type == null || it.type == type) }

    /** 前置节点：source 是 target 的前置（docs/05 §6.2）。 */
    fun prerequisitesOf(id: String): List<String> =
        incoming(id, EdgeType.PREREQUISITE).map { it.source }.distinct()

    fun dependentsOf(id: String): List<String> =
        outgoing(id, EdgeType.PREREQUISITE).map { it.target }.distinct()

    fun relatedOf(id: String): List<String> = buildList {
        outgoing(id).filter { it.type == EdgeType.RELATED || it.type == EdgeType.CONFUSED_WITH || it.type == EdgeType.APPLIED_IN }
            .forEach { add(it.target) }
        incoming(id).filter { it.type == EdgeType.RELATED || it.type == EdgeType.CONFUSED_WITH || it.type == EdgeType.APPLIED_IN }
            .forEach { add(it.source) }
    }.distinct()

    /** 前置闭包（含自身），带 visited 集防止循环。 */
    fun prerequisiteClosure(id: String): Set<String> {
        val visited = linkedSetOf(id)
        val stack = ArrayDeque(listOf(id))
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            for (edge in incoming(current, EdgeType.PREREQUISITE)) {
                if (visited.add(edge.source)) stack.addLast(edge.source)
            }
        }
        return visited
    }

    /** 检测前置关系中的循环；返回每个环的节点序列（不含重复首节点）。 */
    fun detectPrerequisiteCycles(): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val state = mutableMapOf<String, Int>() // 0=未访问 1=访问中 2=已完成
        val stack = mutableListOf<String>()
        fun dfs(id: String) {
            state[id] = 1
            stack.add(id)
            for (next in dependentsOf(id)) {
                when (state[next] ?: 0) {
                    0 -> dfs(next)
                    1 -> {
                        val startIndex = stack.indexOf(next)
                        if (startIndex >= 0) cycles.add(stack.subList(startIndex, stack.size).toList())
                    }
                }
            }
            stack.removeAt(stack.lastIndex)
            state[id] = 2
        }
        topics.keys.forEach { if ((state[it] ?: 0) == 0) dfs(it) }
        return cycles
    }

    /** 前置拓扑序；存在环时返回 null（由调用方给出内容问题提示）。 */
    fun topologicalOrder(): List<String>? {
        if (detectPrerequisiteCycles().isNotEmpty()) return null
        val inDegree = topics.keys.associateWith { 0 }.toMutableMap()
        edges.filter { it.type == EdgeType.PREREQUISITE && it.source in topics && it.target in topics }
            .forEach { inDegree[it.target] = (inDegree[it.target] ?: 0) + 1 }
        val queue = ArrayDeque(inDegree.filterValues { it == 0 }.keys.sorted())
        val order = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            order.add(current)
            for (next in dependentsOf(current)) {
                val remaining = (inDegree[next] ?: 0) - 1
                inDegree[next] = remaining
                if (remaining == 0) queue.addLast(next)
            }
        }
        return if (order.size == topics.size) order else null
    }

    /** 两节点间的学习路径：在「前置 + 相关」无向图上做 BFS。 */
    fun path(from: String, to: String, maxVisited: Int = 2000): List<String>? {
        if (from == to) return listOf(from)
        if (from !in topics || to !in topics) return null
        val visited = mutableSetOf(from)
        val parent = mutableMapOf<String, String>()
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty() && visited.size <= maxVisited) {
            val current = queue.removeFirst()
            val neighbours = (prerequisitesOf(current) + dependentsOf(current) + relatedOf(current)).distinct()
            for (next in neighbours) {
                if (next !in topics || !visited.add(next)) continue
                parent[next] = current
                if (next == to) {
                    val result = mutableListOf<String>()
                    var node: String? = to
                    while (node != null) {
                        result.add(node)
                        node = parent[node]
                    }
                    return result.reversed()
                }
                queue.addLast(next)
            }
        }
        return null
    }

    /** 推荐下一个学习节点：前置已完成、自身未掌握、按难度排序。 */
    fun suggestedNext(mastered: Set<String>, limit: Int = 3): List<Topic> =
        topics.values
            .filter { it.id !in mastered }
            .filter { prerequisitesOf(it.id).all { dep -> dep in mastered } }
            .sortedWith(compareBy({ it.difficulty.ordinal }, { it.estimatedMinutes }))
            .take(limit)

    /** 前置未满足的节点（用于节点详情页的引导文案）。 */
    fun missingPrerequisites(id: String, statusOf: (String) -> LearningStatus): List<String> =
        prerequisitesOf(id).filter { statusOf(it) != LearningStatus.MASTERED }
}
