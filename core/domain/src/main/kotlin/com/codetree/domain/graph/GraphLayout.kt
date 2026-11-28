package com.codetree.domain.graph

import java.util.TreeMap

/** 布局后的节点中心坐标（视口坐标系，单位与视口一致，左上角为原点）。 */
data class LayoutPoint(val x: Float, val y: Float)

/** 视口尺寸；非有限值或负值在 [GraphLayout.compute] 中按 0 处理。 */
data class ViewportSize(val width: Float, val height: Float)

/**
 * 知识图分层布局（ADR-002「数据与布局分离」）。
 *
 * 纯函数、无 Android 依赖、输出确定性：同一 [TopicGraph] 与视口必定得到相同坐标。
 *
 * - 分层：只取 `PREREQUISITE` 边做最长路径分层（Kahn 拓扑序），无前置节点 = 第 0 层，
 *   源节点一定排在目标节点之前；同层横向均分居中，层间纵向等距。
 * - 降级：存在前置环（或拓扑排序不完整）时按 `Topic.domain` 聚类，domain 字典序即层序，
 *   组内按 topicId 排序；**含环节点不会丢失**，全部出现在 [positions]/[layers] 中。
 * - 输出：`x = width * (i+1) / (n+1)`、`y = height * (layer+1) / (layerCount+1)`；
 *   [nodeRadius] 随视口与最大层宽收缩（有上下限），视口为 0 时为 0。
 * - 不做视口裁剪与命中测试（属于 UI 层）；不输出边路径点，UI 用 [positions] 绘制。
 */
data class GraphLayout(
    val positions: Map<String, LayoutPoint> = emptyMap(),
    val layers: Map<String, Int> = emptyMap(),
    val viewportSize: ViewportSize = ViewportSize(0f, 0f),
    val nodeRadius: Float = 0f,
) {

    fun position(topicId: String): LayoutPoint? = positions[topicId]

    companion object {
        val EMPTY = GraphLayout()

        private const val MAX_NODE_RADIUS = 24f
        private const val MIN_NODE_RADIUS = 4f
        private const val RADIUS_SCALE = 0.35f

        fun compute(graph: TopicGraph, viewportWidth: Int, viewportHeight: Int): GraphLayout =
            compute(graph, ViewportSize(viewportWidth.toFloat(), viewportHeight.toFloat()))

        fun compute(graph: TopicGraph, viewportWidth: Float, viewportHeight: Float): GraphLayout =
            compute(graph, ViewportSize(viewportWidth, viewportHeight))

        fun compute(graph: TopicGraph, viewport: ViewportSize): GraphLayout {
            val width = sanitize(viewport.width)
            val height = sanitize(viewport.height)
            val ids = graph.ids()
            if (ids.isEmpty()) return GraphLayout(viewportSize = ViewportSize(width, height))

            val layers = computeLayers(graph)
            val layerCount = (layers.values.maxOrNull() ?: 0) + 1
            val nodesPerLayer = TreeMap<Int, MutableList<String>>()
            for ((id, layer) in layers) {
                nodesPerLayer.getOrPut(layer) { ArrayList() }.add(id)
            }
            var maxLayerSize = 0
            for (nodes in nodesPerLayer.values) {
                nodes.sort()
                maxLayerSize = maxOf(maxLayerSize, nodes.size)
            }

            val radius = if (width <= 0f || height <= 0f) {
                0f
            } else {
                val raw = minOf(
                    (width / (maxLayerSize + 1)) * RADIUS_SCALE,
                    (height / (layerCount + 1)) * RADIUS_SCALE,
                )
                raw.coerceIn(MIN_NODE_RADIUS, MAX_NODE_RADIUS)
            }

            val positions = LinkedHashMap<String, LayoutPoint>(ids.size)
            for ((layer, nodes) in nodesPerLayer) {
                val y = height * (layer + 1f) / (layerCount + 1f)
                for ((index, id) in nodes.withIndex()) {
                    positions[id] = LayoutPoint(
                        x = width * (index + 1f) / (nodes.size + 1f),
                        y = y,
                    )
                }
            }
            return GraphLayout(
                positions = positions,
                layers = layers.toSortedMap(),
                viewportSize = ViewportSize(width, height),
                nodeRadius = radius,
            )
        }

        /**
         * 分层：无环时按最长前置路径；有环时按 domain 聚类降级。
         * 返回值包含 [TopicGraph.ids] 中每个节点的层号（含环节点不丢）。
         */
        fun computeLayers(graph: TopicGraph): Map<String, Int> {
            val ids = graph.ids()
            if (ids.isEmpty()) return emptyMap()
            if (graph.detectPrerequisiteCycles().isNotEmpty()) return fallbackDomainLayers(graph)

            val prerequisites: Map<String, List<String>> = ids.associateWith { id ->
                graph.prerequisitesOf(id).filter { it in ids && it != id }.distinct()
            }
            val inDegree = ids.associateWith { prerequisites.getValue(it).size }.toMutableMap()
            val dependents = HashMap<String, MutableList<String>>()
            for ((node, deps) in prerequisites) {
                for (prerequisite in deps) {
                    dependents.getOrPut(prerequisite) { ArrayList() }.add(node)
                }
            }

            val queue = java.util.ArrayDeque<String>()
            ids.filter { inDegree.getValue(it) == 0 }.sorted().forEach { queue.addLast(it) }
            val layers = HashMap<String, Int>(ids.size)
            var processed = 0
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                processed++
                val prerequisiteLayer = prerequisites.getValue(node).maxOfOrNull { layers[it] ?: 0 }
                layers[node] = if (prerequisiteLayer == null) 0 else prerequisiteLayer + 1
                for (next in dependents[node].orEmpty().sorted()) {
                    val remaining = inDegree.getValue(next) - 1
                    inDegree[next] = remaining
                    if (remaining == 0) queue.addLast(next)
                }
            }
            return if (processed == ids.size) layers else fallbackDomainLayers(graph)
        }

        /** 有环降级：按 domain 字典序分层（无 domain 的归入空字符串组），组内按 id 排序。 */
        private fun fallbackDomainLayers(graph: TopicGraph): Map<String, Int> {
            val byDomain = TreeMap<String, MutableList<String>>()
            for (id in graph.ids()) {
                val domain = graph.topic(id)?.domain.orEmpty()
                byDomain.getOrPut(domain) { ArrayList() }.add(id)
            }
            val result = LinkedHashMap<String, Int>()
            var layer = 0
            for (nodes in byDomain.values) {
                nodes.sort()
                for (id in nodes) result[id] = layer
                layer++
            }
            return result
        }

        private fun sanitize(value: Float): Float =
            if (value.isFinite() && value > 0f) value else 0f
    }
}
