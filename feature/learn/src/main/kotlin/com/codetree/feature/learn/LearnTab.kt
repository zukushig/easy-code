package com.codetree.feature.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codetree.domain.ai.AiContextDraft
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.ProgressRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.feature.learn.ui.LearnHomeScreen
import com.codetree.feature.learn.ui.TopicDetailScreen
import com.codetree.feature.learn.ui.learnPalette

/**
 * 学习 Tab 对外唯一入口（签名固定，`:app` 按此调用）。
 *
 * 内部自管状态实现首页 ⇄ 节点详情二级页面（不依赖全局导航单例）：
 * - `initialTopicId` 非空时直接打开对应节点详情（LRN-005：从术语/练习/AI 上下文回到知识节点）；
 * - 返回后首页的选中节点、相机与列表滚动位置由 ViewModel / rememberLazyListState 保留；
 * - 主题（CodeTreeTheme）与 Scaffold 由 `:app` 壳统一提供，feature 只读取设计令牌。
 *
 * @param onOpenTerm 关联术语被点按时通知 `:app` 切换词典 Tab
 * @param onAskAi 节点详情「问 AI」时携带 AiContextDraft（TopicRef + 代码片段）
 */
@Composable
fun LearnTab(
    content: ContentRepository,
    progress: ProgressRepository,
    settings: SettingsRepository,
    initialTopicId: String? = null,
    onOpenTerm: (termId: String) -> Unit = {},
    onAskAi: (AiContextDraft) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: LearnViewModel = viewModel(factory = LearnViewModel.factory(content, progress, settings))
    val state by viewModel.state.collectAsState()
    val homeListState = rememberLazyListState()
    val density = LocalDensity.current
    val minHitRadiusPx = with(density) { 24.dp.toPx() }

    LaunchedEffect(initialTopicId) {
        if (!initialTopicId.isNullOrBlank()) {
            viewModel.openTopicDetail(initialTopicId)
        }
    }

    val palette = learnPalette()
    Box(modifier = modifier.fillMaxSize().background(palette.background)) {
        val detailTopicId = state.detailTopicId
        if (detailTopicId != null) {
            TopicDetailScreen(
                content = content,
                progress = progress,
                settings = settings,
                topicId = detailTopicId,
                reduceMotion = state.reduceMotion,
                onBack = viewModel::backFromDetail,
                onOpenTopic = viewModel::openTopicDetail,
                onOpenTerm = onOpenTerm,
                onAskAi = onAskAi,
            )
        } else {
            LearnHomeScreen(
                state = state,
                listState = homeListState,
                onRefresh = viewModel::refresh,
                onViewModeChange = viewModel::setViewMode,
                onGroupModeChange = viewModel::setGroupMode,
                onCanvasSizeChanged = viewModel::onCanvasSizeChanged,
                onTransform = viewModel::onTransformGesture,
                onTapNode = { x, y ->
                    val hit = viewModel.hitTest(x, y, minHitRadiusPx)
                    viewModel.selectTopic(hit)
                },
                onDoubleTap = viewModel::resetCamera,
                onCenterCurrent = viewModel::centerOnCurrentTopic,
                onResetView = viewModel::resetCamera,
                onOpenDetail = viewModel::openTopicDetail,
                onClearSelection = { viewModel.selectTopic(null) },
            )
        }
    }
}
