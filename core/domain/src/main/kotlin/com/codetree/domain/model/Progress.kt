package com.codetree.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LearningStatus {
    @SerialName("not-started") NOT_STARTED,
    @SerialName("learning") LEARNING,
    @SerialName("mastered") MASTERED,
}

@Serializable
data class TopicProgress(
    val topicId: String,
    val status: LearningStatus = LearningStatus.NOT_STARTED,
    val updatedAtEpochMs: Long = 0L,
    val lastVisitedAtEpochMs: Long = 0L,
)

data class LearningStats(
    val totalTopics: Int = 0,
    val mastered: Int = 0,
    val learning: Int = 0,
    val notStarted: Int = 0,
) {
    val masteryRatio: Float get() = if (totalTopics == 0) 0f else mastered.toFloat() / totalTopics
}
