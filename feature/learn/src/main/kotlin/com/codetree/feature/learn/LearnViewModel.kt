package com.codetree.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.ProgressRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.domain.graph.GraphLayout
import com.codetree.domain.graph.TopicGraph
import com.codetree.domain.model.LearningStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 学习首页状态持有者：知识树布局、视图切换、选中节点、相机（缩放/平移/复位）、
 * 推荐路径与进度状态。所有交互意图都通过方法回传，UI 不做业务计算。
 *
 * 图布局算法在 `:core:domain` 的 [GraphLayout]（ADR-002：数据与布局分离）。
 */
class LearnViewModel(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LearnUiState())
    val state: StateFlow<LearnUiState> = _state.asStateFlow()

    private var cameraJob: Job? = null

    init {
        viewModelScope.launch {
            progress.observeAll().collect { map ->
                val statuses = map.mapValues { it.value.status }
                val lastVisited = map.values
                    .filter { it.lastVisitedAtEpochMs > 0L }
                    .maxByOrNull { it.lastVisitedAtEpochMs }
                    ?.topicId
                _state.update { st ->
                    val mastered = statuses.filterValues { it == LearningStatus.MASTERED }.keys
                    st.copy(
                        statuses = statuses,
                        lastVisitedTopicId = lastVisited ?: st.lastVisitedTopicId,
                        suggested = if (st.graph.topics.isEmpty()) {
                            emptyList()
                        } else {
                            st.graph.suggestedNext(mastered, SUGGESTED_LIMIT)
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.settings.collect { appSettings ->
                _state.update { it.copy(activeDirection = appSettings.activeDirection, reduceMotion = appSettings.reduceMotion) }
            }
        }
        refresh()
    }

    /** 重新加载内容并重建布局；内容为空时也会给出明确状态而不是白屏。 */
    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, loading = it.graph.topics.isEmpty()) }
            val result = content.reload()
            val graph = content.graph()
            applyGraph(graph, error = result.errorOrNull())
        }
    }

    private fun applyGraph(graph: TopicGraph, error: com.codetree.domain.common.AppError?) {
        val layout = if (graph.topics.isEmpty()) {
            GraphLayout.EMPTY
        } else {
            GraphLayout.compute(graph, LAYOUT_WIDTH, LAYOUT_HEIGHT)
        }
        val installed = runCatching { content.installedPacks() }.getOrDefault(emptyList())
        val catalog = runCatching { content.catalog() }.getOrDefault(emptyList())
        _state.update { st ->
            val mastered = st.statuses.filterValues { it == LearningStatus.MASTERED }.keys
            st.copy(
                loading = false,
                refreshing = false,
                graph = graph,
                layout = layout,
                error = error?.takeIf { graph.topics.isEmpty() },
                suggested = if (graph.topics.isEmpty()) emptyList() else graph.suggestedNext(mastered, SUGGESTED_LIMIT),
                installedPackCount = installed.size,
                catalogCount = catalog.size,
                selectedTopicId = st.selectedTopicId?.takeIf { graph.topic(it) != null },
                detailStack = st.detailStack.filter { graph.topic(it) != null },
                // 布局变化后重置相机，由画布在拿到尺寸后重新 fit。
                camera = GraphCamera(),
            )
        }
    }

    fun setViewMode(mode: LearnViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun setGroupMode(mode: TopicGroupMode) {
        _state.update { it.copy(groupMode = mode) }
    }

    fun selectTopic(topicId: String?) {
        _state.update { it.copy(selectedTopicId = topicId) }
    }

    fun openTopicDetail(topicId: String) {
        if (_state.value.graph.topic(topicId) == null) return
        _state.update { st ->
            val stack = (st.detailStack.filter { it != topicId } + topicId).take(MAX_DETAIL_STACK)
            st.copy(detailStack = stack, selectedTopicId = topicId)
        }
    }

    fun backFromDetail() {
        _state.update { it.copy(detailStack = it.detailStack.dropLast(1)) }
    }

    fun consumeProgressError() {
        _state.update { it.copy(progressError = null) }
    }

    /** 设置学习状态；失败时保留 UI 原状态并给出错误提示（不伪造成功）。 */
    fun setStatus(topicId: String, status: LearningStatus) {
        viewModelScope.launch {
            val result = progress.setStatus(topicId, status)
            _state.update {
                it.copy(progressError = result.errorOrNull())
            }
        }
    }

    // ---------------------------------------------------------------------
    // 图谱相机：缩放 / 平移 / 回到当前位置 / 复位
    // ---------------------------------------------------------------------

    /** 画布尺寸就绪或变化时调用；首次就绪时自动 fit 全图。 */
    fun onCanvasSizeChanged(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        _state.update { st ->
            val fitted = fitCamera(st.layout, width, height)
            if (!st.camera.initialized) {
                st.copy(camera = fitted)
            } else if (abs(st.camera.canvasWidth - width) > 1f || abs(st.camera.canvasHeight - height) > 1f) {
                // 旋转/分屏：保持缩放，把视口中心固定回原图中心。
                st.copy(
                    camera = st.camera.copy(
                        canvasWidth = width,
                        canvasHeight = height,
                        offsetX = width / 2f - st.layout.canvasCenterX() * st.camera.scale,
                        offsetY = height / 2f - st.layout.canvasCenterY() * st.camera.scale,
                    ),
                )
            } else {
                st
            }
        }
    }

    /** `detectTransformGestures` 手势：同时处理双指缩放与单指拖动。 */
    fun onTransformGesture(focusX: Float, focusY: Float, panX: Float, panY: Float, zoom: Float) {
        _state.update { st ->
            val cam = st.camera
            if (!cam.initialized) return@update st
            val newScale = (cam.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
            val graphFocusX = cam.screenToGraphX(focusX)
            val graphFocusY = cam.screenToGraphY(focusY)
            st.copy(
                camera = cam.copy(
                    scale = newScale,
                    offsetX = focusX + panX - graphFocusX * newScale,
                    offsetY = focusY + panY - graphFocusY * newScale,
                ),
            )
        }
    }

    /** 点按命中测试：命中半径取布局半径与 24dp 中较大者（ADR-002 §4）。 */
    fun hitTest(screenX: Float, screenY: Float, minHitRadiusPx: Float): String? {
        val st = _state.value
        val cam = st.camera
        if (!cam.initialized || st.layout.positions.isEmpty()) return null
        val gx = cam.screenToGraphX(screenX)
        val gy = cam.screenToGraphY(screenY)
        val radius = max(st.layout.nodeRadius, minHitRadiusPx / cam.scale)
        var best: String? = null
        var bestDistance = Float.MAX_VALUE
        st.layout.positions.forEach { (id, point) ->
            val dx = point.x - gx
            val dy = point.y - gy
            val distance = dx * dx + dy * dy
            if (distance <= radius * radius && distance < bestDistance) {
                bestDistance = distance
                best = id
            }
        }
        return best
    }

    /** 双击复位：回到 fit 全图的视图。 */
    fun resetCamera() {
        val st = _state.value
        if (st.camera.canvasWidth <= 0f || st.camera.canvasHeight <= 0f) return
        animateCameraTo(fitCamera(st.layout, st.camera.canvasWidth, st.camera.canvasHeight))
    }

    /** 「回到当前位置」：平移（必要时缩放）到当前选中/上次访问/推荐节点。 */
    fun centerOnCurrentTopic() {
        val st = _state.value
        val topicId = st.currentTopicId ?: return
        val point = st.layout.position(topicId) ?: return
        val cam = st.camera
        if (cam.canvasWidth <= 0f || cam.canvasHeight <= 0f) return
        animateCameraTo(
            cam.copy(
                initialized = true,
                scale = cam.scale.coerceIn(MIN_SCALE, MAX_SCALE),
                offsetX = cam.canvasWidth / 2f - point.x * cam.scale,
                offsetY = cam.canvasHeight / 2f - point.y * cam.scale,
            ),
        )
    }

    private fun animateCameraTo(target: GraphCamera) {
        cameraJob?.cancel()
        if (_state.value.reduceMotion) {
            _state.update { it.copy(camera = target) }
            return
        }
        cameraJob = viewModelScope.launch {
            val start = _state.value.camera
            val steps = CAMERA_ANIM_STEPS
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                // smoothstep，避免首尾生硬
                val e = t * t * (3f - 2f * t)
                _state.update { st ->
                    st.copy(
                        camera = start.copy(
                            initialized = true,
                            scale = lerp(start.scale, target.scale, e),
                            offsetX = lerp(start.offsetX, target.offsetX, e),
                            offsetY = lerp(start.offsetY, target.offsetY, e),
                            canvasWidth = target.canvasWidth,
                            canvasHeight = target.canvasHeight,
                        ),
                    )
                }
                delay(CAMERA_ANIM_STEP_MS)
            }
        }
    }

    private fun fitCamera(layout: GraphLayout, width: Float, height: Float): GraphCamera {
        if (layout.positions.isEmpty()) {
            return GraphCamera(initialized = true, scale = 1f, canvasWidth = width, canvasHeight = height)
        }
        val minX = layout.positions.values.minOf { it.x }
        val maxX = layout.positions.values.maxOf { it.x }
        val minY = layout.positions.values.minOf { it.y }
        val maxY = layout.positions.values.maxOf { it.y }
        val padding = max(layout.nodeRadius * 2.4f, FIT_MIN_PADDING)
        val contentWidth = max(maxX - minX, 1f) + padding * 2f
        val contentHeight = max(maxY - minY, 1f) + padding * 2f
        val scale = min(width / contentWidth, height / contentHeight)
            .coerceIn(FIT_MIN_SCALE, FIT_MAX_SCALE)
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        return GraphCamera(
            initialized = true,
            scale = scale,
            offsetX = width / 2f - centerX * scale,
            offsetY = height / 2f - centerY * scale,
            canvasWidth = width,
            canvasHeight = height,
        )
    }

    companion object {
        const val SUGGESTED_LIMIT = 3
        private const val MAX_DETAIL_STACK = 16
        private const val MIN_SCALE = 0.25f
        private const val MAX_SCALE = 3.5f
        private const val FIT_MIN_SCALE = 0.2f
        private const val FIT_MAX_SCALE = 1.6f
        private const val FIT_MIN_PADDING = 120f
        private const val CAMERA_ANIM_STEPS = 16
        private const val CAMERA_ANIM_STEP_MS = 14L

        /** 布局虚拟视口（布局单位，与屏幕像素无关；UI 再整体 fit）。 */
        private const val LAYOUT_WIDTH = 1600f
        private const val LAYOUT_HEIGHT = 1200f

        fun factory(
            content: ContentRepository,
            progress: ProgressRepository,
            settings: SettingsRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LearnViewModel(content, progress, settings) as T
        }
    }
}

private fun GraphLayout.canvasCenterX(): Float =
    if (positions.isEmpty()) 0f else (positions.values.minOf { it.x } + positions.values.maxOf { it.x }) / 2f

private fun GraphLayout.canvasCenterY(): Float =
    if (positions.isEmpty()) 0f else (positions.values.minOf { it.y } + positions.values.maxOf { it.y }) / 2f

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction
