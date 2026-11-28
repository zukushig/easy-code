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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codetree.feature.learn.LearnStrings
import com.codetree.feature.learn.LearnUiState
import com.codetree.feature.learn.LearnViewMode
import com.codetree.feature.learn.TopicGroupMode
import com.codetree.feature.learn.zh

/**
 * 学习首页：大标题「学习」+ 当前学习方向 + 图谱/列表切换 + 「接下来学」推荐 + 图谱或列表。
 * 五态覆盖：Loading / Content / Empty（含未安装内容）/ Error / Offline（缓存内容可用提示）。
 */
@Composable
fun LearnHomeScreen(
    state: LearnUiState,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onViewModeChange: (LearnViewMode) -> Unit,
    onGroupModeChange: (TopicGroupMode) -> Unit,
    onCanvasSizeChanged: (Float, Float) -> Unit,
    onTransform: (Float, Float, Float, Float, Float) -> Unit,
    onTapNode: (Float, Float) -> Unit,
    onDoubleTap: () -> Unit,
    onCenterCurrent: () -> Unit,
    onResetView: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = learnPalette()
    val spacing = learnSpacing()
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
        contentPadding = PaddingValues(horizontal = spacing.pageHorizontal, vertical = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        item(key = "learn-header") {
            HomeHeader(state = state, onRefresh = onRefresh)
        }
        item(key = "learn-view-switch") {
            LearnSegmentedControl(
                options = listOf(LearnStrings.VIEW_GRAPH, LearnStrings.VIEW_LIST),
                selectedIndex = if (state.viewMode == LearnViewMode.GRAPH) 0 else 1,
                onSelected = { index ->
                    onViewModeChange(if (index == 0) LearnViewMode.GRAPH else LearnViewMode.LIST)
                },
            )
        }
        when {
            state.loading -> item(key = "learn-loading") {
                LearnLoadingState(message = LearnStrings.LOADING)
            }
            state.isContentEmpty && state.error != null -> item(key = "learn-error") {
                LearnErrorState(error = state.error!!, onRetry = onRefresh)
            }
            state.isContentEmpty -> {
                if (state.catalogCount > 0) {
                    item(key = "learn-offline-empty") { LearnOfflineBanner(text = LearnStrings.OFFLINE_DESC) }
                }
                item(key = "learn-empty") {
                    LearnEmptyState(
                        title = LearnStrings.EMPTY_CONTENT_TITLE,
                        description = LearnStrings.EMPTY_CONTENT_DESC,
                        actionLabel = LearnStrings.EMPTY_CONTENT_ACTION,
                        onAction = onRefresh,
                    )
                }
            }
            else -> {
                if (state.error != null) {
                    item(key = "learn-offline-cache") {
                        LearnOfflineBanner(text = state.error!!.userHint)
                    }
                }
                item(key = "learn-next-up") {
                    NextUpSection(state = state, onOpenDetail = onOpenDetail)
                }
                if (state.viewMode == LearnViewMode.GRAPH) {
                    item(key = "learn-graph") {
                        KnowledgeGraphSection(
                            state = state,
                            onCanvasSizeChanged = onCanvasSizeChanged,
                            onTransform = onTransform,
                            onTap = onTapNode,
                            onDoubleTap = onDoubleTap,
                            onCenterCurrent = onCenterCurrent,
                            onResetView = onResetView,
                            onOpenDetail = onOpenDetail,
                            onClearSelection = onClearSelection,
                        )
                    }
                } else {
                    topicListItems(
                        state = state,
                        onGroupModeChange = onGroupModeChange,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }
        }
        item(key = "learn-bottom-space") { Spacer(modifier = Modifier.height(spacing.xxl)) }
    }
}

@Composable
private fun HomeHeader(state: LearnUiState, onRefresh: () -> Unit) {
    val palette = learnPalette()
    val type = learnType()
    val spacing = learnSpacing()
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = LearnStrings.TITLE,
                style = type.largeTitle,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            LearnRefreshButton(onClick = onRefresh)
        }
        val direction = state.activeDirection?.takeIf { it.isNotBlank() } ?: LearnStrings.DIRECTION_ALL
        Row(verticalAlignment = Alignment.CenterVertically) {
            LearnBadge(text = LearnStrings.DIRECTION_PREFIX + "：" + direction, tone = LearnTone.ACCENT)
            Spacer(modifier = Modifier.width(spacing.s))
            Text(
                text = "已掌握 ${state.masteredCount} / ${state.totalCount} · 学习中 ${state.learningCount}",
                style = type.footnote,
                color = palette.textSecondary,
            )
        }
        val ratio = if (state.totalCount == 0) 0f else state.masteredCount.toFloat() / state.totalCount
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = palette.success,
            trackColor = palette.separator,
        )
    }
}

/** 「接下来学」：基于 TopicGraph.suggestedNext(mastered) 的前 3 个节点。 */
@Composable
private fun NextUpSection(state: LearnUiState, onOpenDetail: (String) -> Unit) {
    val palette = learnPalette()
    val type = learnType()
    val spacing = learnSpacing()
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        LearnSectionHeader(title = LearnStrings.NEXT_UP_TITLE)
        Text(text = LearnStrings.NEXT_UP_SUBTITLE, style = type.caption, color = palette.textTertiary)
        if (state.suggested.isEmpty()) {
            LearnCard {
                Text(text = LearnStrings.NEXT_UP_EMPTY, style = type.footnote, color = palette.textTertiary)
            }
        } else {
            state.suggested.forEachIndexed { index, topic ->
                LearnCard(onClick = { onOpenDetail(topic.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(palette.accentSoft, RoundedCornerShape(learnShapes().pill)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "${index + 1}", style = type.headline, color = palette.accent)
                        }
                        Spacer(modifier = Modifier.width(spacing.m))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = topic.title.zh().ifBlank { topic.id },
                                style = type.headline,
                                color = palette.textPrimary,
                            )
                            Text(
                                text = topic.summary.zh(),
                                style = type.footnote,
                                color = palette.textSecondary,
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
                        }
                        Spacer(modifier = Modifier.width(spacing.s))
                        Text(text = "›", style = type.title2, color = palette.textTertiary)
                    }
                }
            }
        }
    }
}
