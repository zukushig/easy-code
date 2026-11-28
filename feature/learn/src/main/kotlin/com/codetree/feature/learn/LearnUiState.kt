package com.codetree.feature.learn

import com.codetree.domain.common.AppError
import com.codetree.domain.graph.GraphLayout
import com.codetree.domain.graph.TopicGraph
import com.codetree.domain.model.LearningStatus
import com.codetree.domain.model.Topic
import com.codetree.plugin.LocalizedText

/** 首页两种视图：Canvas 图谱 与 列表（无障碍等价入口）。 */
enum class LearnViewMode { GRAPH, LIST }

/** 列表分组方式：按学习方向 / 按难度。 */
enum class TopicGroupMode { BY_DOMAIN, BY_DIFFICULTY }

/**
 * 图谱相机（ADR-002 §4：交互状态由 LearnViewModel 的单一 UiState 持有，
 * 不放在 Canvas 内部，配置变化后可恢复）。
 *
 * 坐标变换：screen = graph * scale + offset。
 */
data class GraphCamera(
    val initialized: Boolean = false,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val canvasWidth: Float = 0f,
    val canvasHeight: Float = 0f,
) {
    fun graphToScreenX(graphX: Float): Float = graphX * scale + offsetX
    fun graphToScreenY(graphY: Float): Float = graphY * scale + offsetY
    fun screenToGraphX(screenX: Float): Float = (screenX - offsetX) / scale
    fun screenToGraphY(screenY: Float): Float = (screenY - offsetY) / scale
}

/**
 * 学习模块单一 UI 状态。UI 只读该状态并回传意图，不做业务计算。
 */
data class LearnUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val graph: TopicGraph = TopicGraph(),
    val layout: GraphLayout = GraphLayout.EMPTY,
    val statuses: Map<String, LearningStatus> = emptyMap(),
    val lastVisitedTopicId: String? = null,
    val activeDirection: String? = null,
    val reduceMotion: Boolean = false,
    val viewMode: LearnViewMode = LearnViewMode.GRAPH,
    val groupMode: TopicGroupMode = TopicGroupMode.BY_DOMAIN,
    val selectedTopicId: String? = null,
    /** 节点详情二级页面栈；空表示首页。 */
    val detailStack: List<String> = emptyList(),
    val suggested: List<Topic> = emptyList(),
    val installedPackCount: Int = 0,
    val catalogCount: Int = 0,
    val progressError: AppError? = null,
    val camera: GraphCamera = GraphCamera(),
) {
    val isContentEmpty: Boolean get() = graph.topics.isEmpty()

    /** 当前学习方向过滤后的节点（null / 空 = 全部）。 */
    val visibleTopics: List<Topic>
        get() = if (activeDirection.isNullOrBlank()) {
            graph.topics.values.sortedWith(compareBy({ it.domain }, { it.difficulty.ordinal }, { it.id }))
        } else {
            graph.topics.values
                .filter { it.domain == activeDirection }
                .sortedWith(compareBy({ it.difficulty.ordinal }, { it.id }))
        }

    val currentTopicId: String?
        get() = selectedTopicId?.takeIf { graph.topic(it) != null }
            ?: lastVisitedTopicId?.takeIf { graph.topic(it) != null }
            ?: suggested.firstOrNull()?.id
            ?: graph.topics.keys.firstOrNull()

    val detailTopicId: String? get() = detailStack.lastOrNull()

    val masteredCount: Int get() = statuses.count { it.value == LearningStatus.MASTERED }
    val learningCount: Int get() = statuses.count { it.value == LearningStatus.LEARNING }
    val totalCount: Int get() = graph.topics.size

    fun statusOf(topicId: String): LearningStatus = statuses[topicId] ?: LearningStatus.NOT_STARTED

    fun titleOf(topicId: String): String =
        graph.topic(topicId)?.title?.get(DEFAULT_LOCALE).orEmpty().ifBlank { topicId }

    fun summaryOf(topicId: String): String =
        graph.topic(topicId)?.summary?.get(DEFAULT_LOCALE).orEmpty()

    fun groupedVisibleTopics(): List<Pair<String, List<Topic>>> {
        if (visibleTopics.isEmpty()) return emptyList()
        return when (groupMode) {
            TopicGroupMode.BY_DOMAIN -> visibleTopics
                .groupBy { it.domain }
                .toSortedMap()
                .map { (domain, topics) -> domain.ifBlank { LearnStrings.DIRECTION_ALL } to topics }

            TopicGroupMode.BY_DIFFICULTY -> visibleTopics
                .groupBy { it.difficulty }
                .toSortedMap(compareBy { it.ordinal })
                .map { (difficulty, topics) -> LearnStrings.difficultyLabel(difficulty) to topics }
        }
    }

    companion object {
        /** MVP 中文优先；内容本地化回退由 LocalizedText 负责。 */
        const val DEFAULT_LOCALE = "zh-CN"
    }
}

/** 节点详情页 UI 状态。 */
data class TopicDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val error: AppError? = null,
    val topic: Topic? = null,
    val examples: List<com.codetree.domain.model.SyntaxExample> = emptyList(),
    val terms: List<com.codetree.domain.model.Term> = emptyList(),
    val prerequisites: List<Topic> = emptyList(),
    val related: List<Topic> = emptyList(),
    val appliedIn: List<Topic> = emptyList(),
    val confusedWith: List<Topic> = emptyList(),
    val missingPrerequisites: List<Topic> = emptyList(),
    val status: LearningStatus = LearningStatus.NOT_STARTED,
    val statusesSnapshot: Map<String, LearningStatus> = emptyMap(),
    val progressError: AppError? = null,
    val savedHintVisible: Boolean = false,
) {
    val hasPrerequisites: Boolean get() = prerequisites.isNotEmpty()
    val hasRelated: Boolean get() = related.isNotEmpty()
    val hasApplied: Boolean get() = appliedIn.isNotEmpty()
    val hasConfused: Boolean get() = confusedWith.isNotEmpty()
    val hasAnyRelation: Boolean get() = hasPrerequisites || hasRelated || hasApplied || hasConfused
}

fun LocalizedText.zh(): String = get(LearnUiState.DEFAULT_LOCALE)
