package com.codetree.feature.learn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codetree.domain.ai.AiContextDraft
import com.codetree.domain.ai.AiContextItem
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.ProgressRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.domain.model.LearningStatus
import com.codetree.feature.learn.LearnStrings
import com.codetree.feature.learn.TopicDetailUiState
import com.codetree.feature.learn.zh

/**
 * 节点详情二级页面（LRN-003）：目标、讲解、双语示例、关联术语、练习、关联节点与进度操作。
 * 缺失区块展示明确空态，不留白；页面状态全部来自 [TopicDetailViewModel]。
 */
@Composable
fun TopicDetailScreen(
    content: ContentRepository,
    progress: ProgressRepository,
    settings: SettingsRepository,
    topicId: String,
    reduceMotion: Boolean,
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onOpenTerm: (String) -> Unit,
    onAskAi: (AiContextDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: TopicDetailViewModel = viewModel(
        key = "topic-detail:$topicId",
        factory = TopicDetailViewModel.factory(content, progress, settings, topicId),
    )
    val state by vm.state.collectAsState()
    val palette = learnPalette()
    val spacing = learnSpacing()
    Box(modifier = modifier.fillMaxSize().background(palette.background)) {
        val error = state.error
        when {
            state.loading -> LearnLoadingState(message = LearnStrings.LOADING, modifier = Modifier.fillMaxSize())
            state.notFound -> LearnEmptyState(
                title = LearnStrings.NOT_FOUND,
                description = LearnStrings.NOT_FOUND_DESC,
                modifier = Modifier.fillMaxSize(),
                actionLabel = LearnStrings.BACK,
                onAction = onBack,
            )
            error != null -> LearnErrorState(
                error = error,
                modifier = Modifier.fillMaxSize(),
                onRetry = vm::retry,
            )
            else -> DetailContent(
                state = state,
                reduceMotion = reduceMotion,
                onBack = onBack,
                onOpenTopic = onOpenTopic,
                onOpenTerm = onOpenTerm,
                onAskAi = onAskAi,
                onSetStatus = vm::setStatus,
                onConsumeProgressError = vm::consumeProgressError,
            )
        }
    }
}

@Composable
private fun DetailContent(
    state: TopicDetailUiState,
    reduceMotion: Boolean,
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onOpenTerm: (String) -> Unit,
    onAskAi: (AiContextDraft) -> Unit,
    onSetStatus: (LearningStatus) -> Unit,
    onConsumeProgressError: () -> Unit,
) {
    val palette = learnPalette()
    val spacing = learnSpacing()
    val listState = rememberLazyListState()
    val topic = state.topic
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.pageHorizontal, vertical = spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        item {
            LearnTopBar(
                title = LearnStrings.DETAIL_TITLE,
                onBack = onBack,
                actions = {
                    LearnBadge(
                        text = LearnStrings.statusLabel(state.status),
                        tone = when (state.status) {
                            LearningStatus.NOT_STARTED -> LearnTone.NEUTRAL
                            LearningStatus.LEARNING -> LearnTone.WARNING
                            LearningStatus.MASTERED -> LearnTone.SUCCESS
                        },
                    )
                },
            )
        }
        if (topic != null) {
            item {
                DetailHeader(state = state, reduceMotion = reduceMotion)
            }
            if (state.missingPrerequisites.isNotEmpty()) {
                item { MissingPrerequisitesCard(state = state, onOpenTopic = onOpenTopic) }
            }
            item { ObjectivesSection(state = state) }
            item { ExplanationSection(state = state) }
            item {
                ExamplesSection(state = state)
            }
            item { TermsSection(state = state, onOpenTerm = onOpenTerm) }
            item { ExerciseSection(state = state) }
            item { RelationsSection(state = state, onOpenTopic = onOpenTopic) }
            item {
                ProgressActions(
                    state = state,
                    onSetStatus = onSetStatus,
                    onConsumeProgressError = onConsumeProgressError,
                    onAskAi = onAskAi,
                )
            }
            item {
                Text(
                    text = "节点 ID：${topic.id} · 预计 ${topic.estimatedMinutes} 分钟",
                    style = learnType().caption,
                    color = palette.textTertiary,
                )
            }
            item { Spacer(modifier = Modifier.height(spacing.xxl)) }
        }
    }
}

@Composable
private fun DetailHeader(state: TopicDetailUiState, reduceMotion: Boolean) {
    val topic = state.topic ?: return
    val palette = learnPalette()
    val type = learnType()
    val spacing = learnSpacing()
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Text(
            text = topic.title.zh().ifBlank { topic.id },
            style = type.title1,
            color = palette.textPrimary,
        )
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
            LearnBadge(text = LearnStrings.ESTIMATED_MINUTES.format(topic.estimatedMinutes))
        }
        val summary = topic.summary.zh()
        if (summary.isNotBlank()) {
            Text(text = summary, style = type.callout, color = palette.textSecondary)
        }
        if (state.savedHintVisible) {
            Text(text = LearnStrings.STATUS_SAVED, style = type.caption, color = palette.success)
        }
    }
}

@Composable
private fun ObjectivesSection(state: TopicDetailUiState) {
    val topic = state.topic ?: return
    val objectives = topic.objectives.get(com.codetree.feature.learn.LearnUiState.DEFAULT_LOCALE)
    LearnSectionHeader(title = LearnStrings.OBJECTIVES)
    if (objectives.isEmpty()) {
        EmptySectionCard(text = LearnStrings.OBJECTIVES_EMPTY)
    } else {
        LearnCard {
            objectives.forEach { objective ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    Text(text = "•", style = learnType().body, color = learnPalette().accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = objective, style = learnType().callout, color = learnPalette().textPrimary)
                }
            }
        }
    }
}

@Composable
private fun ExplanationSection(state: TopicDetailUiState) {
    val topic = state.topic ?: return
    LearnSectionHeader(title = LearnStrings.EXPLANATION)
    val summary = topic.summary.zh()
    if (summary.isBlank()) {
        EmptySectionCard(text = LearnStrings.EXPLANATION_EMPTY)
    } else {
        LearnCard {
            Text(text = summary, style = learnType().body, color = learnPalette().textPrimary)
        }
    }
}

@Composable
internal fun EmptySectionCard(text: String) {
    LearnCard {
        Text(text = text, style = learnType().footnote, color = learnPalette().textTertiary)
    }
}

@Composable
private fun ProgressActions(
    state: TopicDetailUiState,
    onSetStatus: (LearningStatus) -> Unit,
    onConsumeProgressError: () -> Unit,
    onAskAi: (AiContextDraft) -> Unit,
) {
    val palette = learnPalette()
    val spacing = learnSpacing()
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        val status = state.status
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            LearnButton(
                text = LearnStrings.MARK_LEARNING,
                onClick = { onSetStatus(LearningStatus.LEARNING) },
                enabled = status != LearningStatus.LEARNING,
                style = if (status == LearningStatus.LEARNING) LearnButtonStyle.FILLED else LearnButtonStyle.TINTED,
                modifier = Modifier.weight(1f),
            )
            LearnButton(
                text = if (status == LearningStatus.MASTERED) LearnStrings.UNMARK_MASTERED else LearnStrings.MARK_MASTERED,
                onClick = {
                    onSetStatus(if (status == LearningStatus.MASTERED) LearningStatus.LEARNING else LearningStatus.MASTERED)
                },
                style = if (status == LearningStatus.MASTERED) LearnButtonStyle.FILLED else LearnButtonStyle.TINTED,
                modifier = Modifier.weight(1f),
            )
        }
        val topic = state.topic
        LearnButton(
            text = LearnStrings.ASK_AI,
            onClick = {
                if (topic != null) {
                    val items = buildList<AiContextItem> {
                        add(AiContextItem.TopicRef(topic.id))
                        state.examples.firstOrNull()?.let { add(AiContextItem.CodeSnippet(it.language, it.code)) }
                    }
                    onAskAi(AiContextDraft(items))
                }
            },
            style = LearnButtonStyle.TINTED,
            enabled = topic != null,
            modifier = Modifier.fillMaxWidth(),
        )
        val error = state.progressError
        if (error != null) {
            LearnCard(onClick = onConsumeProgressError) {
                Text(
                    text = LearnStrings.PROGRESS_SAVE_FAILED + "：" + error.userHint,
                    style = learnType().footnote,
                    color = palette.error,
                )
            }
        }
    }
}
