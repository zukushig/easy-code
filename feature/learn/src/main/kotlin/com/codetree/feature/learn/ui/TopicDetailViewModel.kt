package com.codetree.feature.learn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codetree.domain.common.AppError
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.ProgressRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.domain.graph.TopicGraph
import com.codetree.domain.model.EdgeType
import com.codetree.domain.model.LearningStatus
import com.codetree.domain.model.Term
import com.codetree.feature.learn.TopicDetailUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 节点详情状态：目标/讲解素材/双语示例/关联术语/关联节点/练习/进度。
 * 进入详情即 markVisited（LRN-004 状态持久化；LRN-005 返回保留学习位置）。
 */
class TopicDetailViewModel(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val settings: SettingsRepository,
    private val topicId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TopicDetailUiState())
    val state: StateFlow<TopicDetailUiState> = _state.asStateFlow()

    private var savedHintJob: Job? = null
    private var latestStatuses: Map<String, LearningStatus> = emptyMap()

    init {
        load()
        observeProgress()
        viewModelScope.launch { progress.markVisited(topicId) }
    }

    private fun load() {
        val topic = content.topic(topicId)
        if (topic == null) {
            _state.update { it.copy(loading = false, notFound = true) }
            return
        }
        val graph = content.graph()
        val examples = topic.exampleIds.mapNotNull { content.example(it) }
        val terms: List<Term> = topic.termIds.mapNotNull { content.term(it) }
        _state.update {
            it.copy(
                loading = false,
                topic = topic,
                examples = examples,
                terms = terms,
                prerequisites = topicListOf(graph) { graph.prerequisitesOf(topicId) },
                related = topicListOf(graph) {
                    graph.outgoing(topicId, EdgeType.RELATED).map { edge -> edge.target }
                },
                appliedIn = topicListOf(graph) {
                    graph.outgoing(topicId, EdgeType.APPLIED_IN).map { edge -> edge.target }
                },
                confusedWith = topicListOf(graph) {
                    (graph.outgoing(topicId, EdgeType.CONFUSED_WITH).map { it.target } +
                        graph.incoming(topicId, EdgeType.CONFUSED_WITH).map { it.source }).distinct()
                },
            )
        }
        recomputeMissingPrerequisites()
    }

    private fun observeProgress() {
        viewModelScope.launch {
            progress.observeStatus(topicId).collect { status ->
                _state.update { it.copy(status = status) }
            }
        }
        viewModelScope.launch {
            progress.observeAll().collect { map ->
                latestStatuses = map.mapValues { it.value.status }
                recomputeMissingPrerequisites()
            }
        }
        // settings 目前只影响文案本地化；保留依赖以对齐固定入口签名与后续 i18n 轮次。
        viewModelScope.launch { settings.settings.collect { } }
    }

    private fun recomputeMissingPrerequisites() {
        val graph = content.graph()
        val missingIds = graph.missingPrerequisites(topicId) { id ->
            latestStatuses[id] ?: LearningStatus.NOT_STARTED
        }
        val missing = missingIds.mapNotNull { graph.topic(it) }
        _state.update { it.copy(missingPrerequisites = missing, statusesSnapshot = latestStatuses) }
    }

    private fun topicListOf(graph: TopicGraph, ids: () -> List<String>) =
        ids().distinct().mapNotNull { graph.topic(it) }

    fun retry() {
        _state.update { it.copy(loading = true, notFound = false, error = null) }
        load()
    }

    fun setStatus(status: LearningStatus) {
        viewModelScope.launch {
            val result = progress.setStatus(topicId, status)
            val error: AppError? = result.errorOrNull()
            _state.update { st -> st.copy(progressError = error, savedHintVisible = error == null) }
            if (error == null) {
                savedHintJob?.cancel()
                savedHintJob = viewModelScope.launch {
                    delay(SAVED_HINT_MS)
                    _state.update { it.copy(savedHintVisible = false) }
                }
            }
        }
    }

    fun consumeProgressError() {
        _state.update { it.copy(progressError = null) }
    }

    companion object {
        private const val SAVED_HINT_MS = 1600L

        fun factory(
            content: ContentRepository,
            progress: ProgressRepository,
            settings: SettingsRepository,
            topicId: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TopicDetailViewModel(content, progress, settings, topicId) as T
        }
    }
}
