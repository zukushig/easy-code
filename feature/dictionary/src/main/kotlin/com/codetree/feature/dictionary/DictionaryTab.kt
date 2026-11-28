package com.codetree.feature.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.ProgressRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.feature.dictionary.ui.SearchScreen
import com.codetree.feature.dictionary.ui.TermDetailScreen

/**
 * 词典 Tab 对外唯一入口（签名固定，`:app` 按此调用）。
 *
 * 内部自管状态：搜索页 ⇄ 术语详情二级页面；术语间跳转由内部导航栈支持（相关术语 chips 递归跳转），
 * `initialTermId` 非空时直接打开对应术语详情（支持从学习/终端/AI 上下文跳转）。
 *
 * 主题（CodeTreeTheme）与 Scaffold 由 `:app` 壳统一提供；本模块只读取设计令牌。
 *
 * @param onOpenTopic 关联知识节点被点按时通知 `:app` 切换到学习 Tab
 * @param onOpenTerm 术语间跳转时同步通知 `:app`（便于外部记录/深链）
 */
@Composable
fun DictionaryTab(
    content: ContentRepository,
    progress: ProgressRepository,
    settings: SettingsRepository,
    initialTermId: String? = null,
    onOpenTopic: (topicId: String) -> Unit = {},
    onOpenTerm: (termId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: DictionaryViewModel = viewModel(factory = DictionaryViewModel.factory(content, settings))
    val state by viewModel.state.collectAsState()
    val colors = CodeTreeTheme.colors

    LaunchedEffect(initialTermId) {
        if (!initialTermId.isNullOrBlank()) {
            viewModel.openTerm(initialTermId)
        }
    }
    // progress 当前不参与词典搜索，但入口签名由 :app 固定注入；保留参数以对齐契约。
    @Suppress("UNUSED_VARIABLE")
    val progressRef = progress

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        val termId = state.selectedTermId
        if (termId != null) {
            TermDetailScreen(
                content = content,
                settings = settings,
                termId = termId,
                favorite = state.isFavorite(termId),
                onToggleFavorite = { viewModel.toggleFavorite(termId) },
                onBack = viewModel::backFromTerm,
                onOpenTerm = { nextTermId ->
                    viewModel.openTerm(nextTermId)
                    onOpenTerm(nextTermId)
                },
                onOpenTopic = onOpenTopic,
            )
        } else {
            SearchScreen(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onSubmit = viewModel::submitQuery,
                onClearQuery = viewModel::clearQuery,
                onUseHistory = viewModel::useHistory,
                onRemoveHistory = viewModel::removeHistory,
                onClearHistory = viewModel::clearHistory,
                onUseHotTerm = viewModel::useHotTerm,
                onOpenTerm = { nextTermId ->
                    viewModel.openTerm(nextTermId)
                    onOpenTerm(nextTermId)
                },
                onToggleFavorite = viewModel::toggleFavorite,
                onRefreshContent = viewModel::refreshContent,
            )
        }
    }
}
