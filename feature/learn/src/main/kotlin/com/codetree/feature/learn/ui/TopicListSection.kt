package com.codetree.feature.learn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codetree.domain.model.Topic
import com.codetree.feature.learn.LearnStrings
import com.codetree.feature.learn.LearnUiState
import com.codetree.feature.learn.TopicGroupMode
import com.codetree.feature.learn.zh

/**
 * 列表视图（图谱的等价入口，LRN-001 可访问性要求）：
 * 按方向/难度分组，每行显示主/副文本与「未学/学习中/已掌握」状态。
 */
fun LazyListScope.topicListItems(
    state: LearnUiState,
    onGroupModeChange: (TopicGroupMode) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    item(key = "topic-list-group-switch") {
        LearnSegmentedControl(
            options = listOf(LearnStrings.GROUP_BY_DOMAIN, LearnStrings.GROUP_BY_DIFFICULTY),
            selectedIndex = if (state.groupMode == TopicGroupMode.BY_DOMAIN) 0 else 1,
            onSelected = { index ->
                onGroupModeChange(if (index == 0) TopicGroupMode.BY_DOMAIN else TopicGroupMode.BY_DIFFICULTY)
            },
        )
    }
    item(key = "topic-list-tooltip") {
        Text(
            text = LearnStrings.LIST_TOOLTIP,
            style = learnType().caption,
            color = learnPalette().textTertiary,
        )
    }
    val groups = state.groupedVisibleTopics()
    if (groups.isEmpty()) {
        item(key = "topic-list-empty") { LearnEmptyState(title = LearnStrings.LIST_EMPTY, description = LearnStrings.EMPTY_CONTENT_DESC) }
        return
    }
    groups.forEach { (title, topics) ->
        item(key = "topic-group-$title") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LearnSectionHeader(title = title, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(learnSpacing().s))
                Text(text = "${topics.size}", style = learnType().footnote, color = learnPalette().textTertiary)
            }
        }
        items(items = topics, key = { it.id }) { topic ->
            TopicRow(state = state, topic = topic, onClick = { onOpenDetail(topic.id) })
        }
    }
}

@Composable
private fun TopicRow(
    state: LearnUiState,
    topic: Topic,
    onClick: () -> Unit,
) {
    val palette = learnPalette()
    val spacing = learnSpacing()
    LearnCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = topic.title.zh().ifBlank { topic.id },
                    style = learnType().headline,
                    color = palette.textPrimary,
                )
                val summary = topic.summary.zh()
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = learnType().footnote,
                        color = palette.textSecondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    LearnBadge(text = topic.domain, tone = LearnTone.ACCENT)
                    LearnBadge(
                        text = LearnStrings.difficultyLabel(topic.difficulty),
                        tone = when (topic.difficulty) {
                            com.codetree.domain.model.Difficulty.BEGINNER -> LearnTone.SUCCESS
                            com.codetree.domain.model.Difficulty.INTERMEDIATE -> LearnTone.WARNING
                            com.codetree.domain.model.Difficulty.ADVANCED -> LearnTone.ERROR
                        },
                    )
                    LearnBadge(
                        text = LearnStrings.ESTIMATED_MINUTES.format(topic.estimatedMinutes),
                        tone = LearnTone.NEUTRAL,
                    )
                }
            }
            Spacer(modifier = Modifier.width(spacing.s))
            val status = state.statusOf(topic.id)
            LearnBadge(
                text = LearnStrings.statusLabel(status),
                tone = when (status) {
                    com.codetree.domain.model.LearningStatus.NOT_STARTED -> LearnTone.NEUTRAL
                    com.codetree.domain.model.LearningStatus.LEARNING -> LearnTone.WARNING
                    com.codetree.domain.model.LearningStatus.MASTERED -> LearnTone.SUCCESS
                },
            )
        }
    }
}
