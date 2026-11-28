package com.codetree.domain.model

import com.codetree.plugin.LocalizedList
import com.codetree.plugin.LocalizedText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty {
    @SerialName("beginner") BEGINNER,
    @SerialName("intermediate") INTERMEDIATE,
    @SerialName("advanced") ADVANCED,
}

/** 知识节点类型：数据层允许有向图，UI 的「知识树」只是可视化的称谓（docs/02 §7）。 */
@Serializable
data class Topic(
    val id: String,
    val domain: String,
    val difficulty: Difficulty = Difficulty.BEGINNER,
    val title: LocalizedText = LocalizedText.EMPTY,
    val summary: LocalizedText = LocalizedText.EMPTY,
    val objectives: LocalizedList = LocalizedList.EMPTY,
    val contentRef: String? = null,
    val termIds: List<String> = emptyList(),
    val exampleIds: List<String> = emptyList(),
    val estimatedMinutes: Int = 5,
    val exercise: Exercise? = null,
    val exerciseSkipReason: String? = null,
)

@Serializable
data class Exercise(
    val prompt: LocalizedText = LocalizedText.EMPTY,
    val hint: LocalizedText? = null,
)

@Serializable
enum class EdgeType {
    @SerialName("prerequisite") PREREQUISITE,
    @SerialName("related") RELATED,
    @SerialName("appliedIn") APPLIED_IN,
    @SerialName("confusedWith") CONFUSED_WITH,
}

/** 有方向的关联：PREREQUISITE 表示 source 是 target 的前置。 */
@Serializable
data class TopicEdge(
    val source: String,
    val target: String,
    val type: EdgeType,
)
