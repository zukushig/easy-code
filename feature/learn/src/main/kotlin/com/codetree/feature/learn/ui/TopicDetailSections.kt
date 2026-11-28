package com.codetree.feature.learn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codetree.domain.model.LearningStatus
import com.codetree.domain.model.Topic
import com.codetree.feature.learn.LearnStrings
import com.codetree.feature.learn.TopicDetailUiState
import com.codetree.feature.learn.zh

/** 前置未满足提示（LRN-006：缺失前置有解释）。 */
@Composable
fun MissingPrerequisitesCard(
    state: TopicDetailUiState,
    onOpenTopic: (String) -> Unit,
) {
    val palette = learnPalette()
    val type = learnType()
    val spacing = learnSpacing()
    LearnCard {
        Text(text = LearnStrings.MISSING_PREREQUISITES, style = type.headline, color = palette.warning)
        Spacer(modifier = Modifier.height(spacing.xxs))
        Text(text = LearnStrings.MISSING_PREREQUISITES_DESC, style = type.footnote, color = palette.textSecondary)
        state.missingPrerequisites.forEach { topic ->
            LearnListRow(
                title = topic.title.zh().ifBlank { topic.id },
                subtitle = topic.summary.zh().take(40).ifBlank { null },
                onClick = { onOpenTopic(topic.id) },
                trailing = {
                    val status = state.statusesSnapshot[topic.id] ?: LearningStatus.NOT_STARTED
                    LearnBadge(text = LearnStrings.statusLabel(status), tone = LearnTone.WARNING)
                },
            )
        }
    }
}

/** 双语示例区：代码块 + 语言标签 + 仅阅读/可运行 + annotation 注释。 */
@Composable
fun ExamplesSection(state: TopicDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(learnSpacing().s)) {
        LearnSectionHeader(title = LearnStrings.EXAMPLES)
        if (state.examples.isEmpty()) {
            EmptySectionCard(text = LearnStrings.EXAMPLES_EMPTY)
        } else {
            state.examples.forEach { example ->
                CodeExampleCard(example = example)
            }
        }
    }
}

/** 关联术语 chips：点击回调 onOpenTerm（跨模块跳转由 :app 导航完成）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TermsSection(
    state: TopicDetailUiState,
    onOpenTerm: (String) -> Unit,
) {
    val spacing = learnSpacing()
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        LearnSectionHeader(title = LearnStrings.TERMS)
        if (state.terms.isEmpty()) {
            EmptySectionCard(text = LearnStrings.TERMS_EMPTY)
        } else {
            LearnCard {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    state.terms.forEach { term ->
                        LearnChip(
                            text = term.displayNameIn(com.codetree.feature.learn.LearnUiState.DEFAULT_LOCALE),
                            onClick = { onOpenTerm(term.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 练习区：prompt + 可展开 hint；无练习时给出结构化理由（不显示空白）。 */
@Composable
fun ExerciseSection(state: TopicDetailUiState) {
    val topic = state.topic ?: return
    val palette = learnPalette()
    val type = learnType()
    val spacing = learnSpacing()
    var hintVisible by rememberSaveable(topic.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        LearnSectionHeader(title = LearnStrings.EXERCISE)
        val exercise = topic.exercise
        if (exercise == null) {
            LearnCard {
                Text(text = LearnStrings.EXERCISE_EMPTY, style = type.footnote, color = palette.textTertiary)
                val reason = topic.exerciseSkipReason
                if (!reason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(spacing.xxs))
                    Text(
                        text = LearnStrings.EXERCISE_SKIP_REASON + reason,
                        style = type.caption,
                        color = palette.textTertiary,
                    )
                }
            }
        } else {
            LearnCard {
                Text(text = exercise.prompt.zh(), style = type.callout, color = palette.textPrimary)
                val hint = exercise.hint?.zh().orEmpty()
                if (hint.isNotBlank()) {
                    Spacer(modifier = Modifier.height(spacing.s))
                    LearnButton(
                        text = if (hintVisible) LearnStrings.HIDE_HINT else LearnStrings.SHOW_HINT,
                        onClick = { hintVisible = !hintVisible },
                        style = LearnButtonStyle.PLAIN,
                    )
                    if (hintVisible) {
                        Spacer(modifier = Modifier.height(spacing.xs))
                        Text(text = hint, style = type.subhead, color = palette.textSecondary)
                    }
                }
            }
        }
    }
}

/** 关联节点：前置 / 相关 / 应用 / 易混淆分组（图谱的等价列表入口）。 */
@Composable
fun RelationsSection(
    state: TopicDetailUiState,
    onOpenTopic: (String) -> Unit,
) {
    val spacing = learnSpacing()
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        LearnSectionHeader(title = LearnStrings.RELATIONS)
        if (!state.hasAnyRelation) {
            EmptySectionCard(text = LearnStrings.RELATION_EMPTY)
            return@Column
        }
        RelationGroup(
            title = LearnStrings.RELATION_PREREQUISITE,
            topics = state.prerequisites,
            statusOf = { state.statusesSnapshot[it] ?: LearningStatus.NOT_STARTED },
            onOpenTopic = onOpenTopic,
        )
        RelationGroup(title = LearnStrings.RELATION_RELATED, topics = state.related, onOpenTopic = onOpenTopic)
        RelationGroup(title = LearnStrings.RELATION_APPLIED, topics = state.appliedIn, onOpenTopic = onOpenTopic)
        RelationGroup(title = LearnStrings.RELATION_CONFUSED, topics = state.confusedWith, onOpenTopic = onOpenTopic)
    }
}

@Composable
private fun RelationGroup(
    title: String,
    topics: List<Topic>,
    onOpenTopic: (String) -> Unit,
    statusOf: (String) -> LearningStatus = { LearningStatus.NOT_STARTED },
) {
    if (topics.isEmpty()) return
    val spacing = learnSpacing()
    val palette = learnPalette()
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = learnType().footnote, color = palette.textTertiary)
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(text = "${topics.size}", style = learnType().caption, color = palette.textTertiary)
        }
        topics.forEach { topic ->
            LearnListRow(
                title = topic.title.zh().ifBlank { topic.id },
                subtitle = topic.summary.zh().take(40).ifBlank { null },
                onClick = { onOpenTopic(topic.id) },
                trailing = {
                    LearnBadge(
                        text = LearnStrings.statusLabel(statusOf(topic.id)),
                        tone = when (statusOf(topic.id)) {
                            LearningStatus.NOT_STARTED -> LearnTone.NEUTRAL
                            LearningStatus.LEARNING -> LearnTone.WARNING
                            LearningStatus.MASTERED -> LearnTone.SUCCESS
                        },
                    )
                },
            )
        }
    }
}
