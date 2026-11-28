package com.codetree.feature.learn.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetree.domain.model.EdgeType
import com.codetree.domain.model.LearningStatus
import com.codetree.feature.learn.LearnStrings
import com.codetree.feature.learn.LearnUiState

private const val GRAPH_CANVAS_HEIGHT_DP = 420
private const val CULL_THRESHOLD = 150
private const val LABEL_LOD_SCALE = 0.62f

/**
 * 知识树图谱区：边型图例（文字+线型，不只颜色）→ Canvas → 相机控制 → 选中节点详情卡。
 * 交互状态全部来自 [LearnUiState]（ADR-002 §4），本组件只做绘制与手势转发。
 */
@Composable
fun KnowledgeGraphSection(
    state: LearnUiState,
    modifier: Modifier = Modifier,
    onCanvasSizeChanged: (width: Float, height: Float) -> Unit,
    onTransform: (focusX: Float, focusY: Float, panX: Float, panY: Float, zoom: Float) -> Unit,
    onTap: (x: Float, y: Float) -> Unit,
    onDoubleTap: () -> Unit,
    onCenterCurrent: () -> Unit,
    onResetView: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onClearSelection: () -> Unit,
) {
    val spacing = learnSpacing()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        GraphLegendCard()
        GraphCanvas(
            state = state,
            onCanvasSizeChanged = onCanvasSizeChanged,
            onTransform = onTransform,
            onTap = onTap,
            onDoubleTap = onDoubleTap,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            LearnButton(text = LearnStrings.BACK_TO_CURRENT, onClick = onCenterCurrent, style = LearnButtonStyle.TINTED)
            LearnButton(text = LearnStrings.FIT_VIEW, onClick = onResetView, style = LearnButtonStyle.PLAIN)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${state.layout.positions.size} 个节点",
                style = learnType().footnote,
                color = learnPalette().textSecondary,
            )
        }
        val selectedId = state.selectedTopicId
        if (!selectedId.isNullOrBlank()) {
            SelectedTopicCard(
                state = state,
                topicId = selectedId,
                onOpenDetail = { onOpenDetail(selectedId) },
                onClearSelection = onClearSelection,
            )
        }
        Text(
            text = LearnStrings.GRAPH_HINT,
            style = learnType().caption,
            color = learnPalette().textTertiary,
        )
    }
}

@Composable
private fun GraphCanvas(
    state: LearnUiState,
    onCanvasSizeChanged: (Float, Float) -> Unit,
    onTransform: (Float, Float, Float, Float, Float) -> Unit,
    onTap: (Float, Float) -> Unit,
    onDoubleTap: () -> Unit,
) {
    val palette = learnPalette()
    val type = learnType()
    val density = LocalDensity.current
    val minHitRadiusPx = with(density) { 24.dp.toPx() }
    val labelTextSize = with(density) { 11.sp.toPx() }
    val nodeTextSize = with(density) { 12.sp.toPx() }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val layout = state.layout
    val camera = state.camera
    val statuses = state.statuses
    val visibleDomain = state.activeDirection

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(GRAPH_CANVAS_HEIGHT_DP.dp)
            .background(palette.surface, RoundedCornerShape(learnShapes().large))
            .onSizeChanged { size ->
                onCanvasSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(layout, camera.initialized) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    onTransform(centroid.x, centroid.y, pan.x, pan.y, zoom)
                }
            }
            .pointerInput(layout, camera.scale) {
                detectTapGestures(
                    onTap = { offset -> onTap(offset.x, offset.y) },
                    onDoubleTap = { onDoubleTap() },
                )
            },
    ) {
        if (!camera.initialized || layout.positions.isEmpty()) return@Canvas
        val visibleRect = graphVisibleRect(camera.canvasWidth, camera.canvasHeight, camera.scale, camera.offsetX, camera.offsetY)
        val cull = layout.positions.size > CULL_THRESHOLD
        val nodeRadius = (layout.nodeRadius * camera.scale).coerceAtLeast(4f)
        val strokeWidth = 1.2.dp.toPx()
        val selectedId = state.selectedTopicId
        val selectedNeighbours = if (selectedId != null) {
            neighboursOf(state, selectedId)
        } else {
            emptySet()
        }

        // 1) 边：先画，节点覆盖在上层。
        state.graph.edges.forEach { edge ->
            val from = layout.positions[edge.source] ?: return@forEach
            val to = layout.positions[edge.target] ?: return@forEach
            if (cull && !edgeIntersects(from, to, visibleRect)) return@forEach
            val sx = camera.graphToScreenX(from.x)
            val sy = camera.graphToScreenY(from.y)
            val tx = camera.graphToScreenX(to.x)
            val ty = camera.graphToScreenY(to.y)
            val highlighted = selectedId != null && (edge.source == selectedId || edge.target == selectedId)
            drawEdge(
                edgeType = edge.type,
                start = Offset(sx, sy),
                end = Offset(tx, ty),
                targetRadius = nodeRadius,
                strokeWidth = if (highlighted) strokeWidth * 1.8f else strokeWidth,
                palette = palette,
                dimmed = !highlighted && selectedId != null,
            )
        }

        // 2) 节点。
        layout.positions.forEach { (topicId, point) ->
            if (cull && !visibleRect.contains(point)) return@forEach
            val cx = camera.graphToScreenX(point.x)
            val cy = camera.graphToScreenY(point.y)
            val inDomain = visibleDomain.isNullOrBlank() || state.graph.topic(topicId)?.domain == visibleDomain
            val difficulty = state.graph.topic(topicId)?.difficulty
            val baseColor = difficulty?.let { palette.difficultyColor(it) } ?: palette.textSecondary
            val status = statuses[topicId] ?: LearningStatus.NOT_STARTED
            val selected = topicId == selectedId
            val relatedToSelected = topicId in selectedNeighbours
            val alpha = when {
                selected || relatedToSelected -> 1f
                !inDomain -> 0.28f
                selectedId != null -> 0.55f
                else -> 1f
            }
            drawCircle(
                color = (if (selected) palette.accentSoft else palette.surface).copy(alpha = alpha),
                radius = nodeRadius,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = baseColor.copy(alpha = alpha),
                radius = nodeRadius,
                center = Offset(cx, cy),
                style = Stroke(width = if (selected) strokeWidth * 2.4f else strokeWidth * 1.6f),
            )
            if (status == LearningStatus.MASTERED) {
                drawCircle(
                    color = palette.success.copy(alpha = alpha),
                    radius = nodeRadius * 0.30f,
                    center = Offset(cx + nodeRadius * 0.62f, cy - nodeRadius * 0.62f),
                )
            } else if (status == LearningStatus.LEARNING) {
                drawCircle(
                    color = palette.warning.copy(alpha = alpha),
                    radius = nodeRadius * 0.20f,
                    center = Offset(cx, cy),
                )
            }
            // LOD：低缩放只画节点与边，高缩放显示标题；选中节点永远显示标题。
            if ((camera.scale >= LABEL_LOD_SCALE || selected) && nodeRadius >= 6f) {
                val title = state.titleOf(topicId)
                labelPaint.color = (if (selected) palette.accent else palette.textPrimary).copy(alpha = alpha).toArgb()
                labelPaint.textSize = if (selected) nodeTextSize else labelTextSize
                val label = title.take(if (selected) 14 else 10)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    cx,
                    cy + nodeRadius + labelPaint.textSize + 2.dp.toPx(),
                    labelPaint,
                )
            }
        }
    }
    // 状态栏等价说明：图谱不是唯一入口（列表视图提供 TalkBack 顺序浏览）。
    if (layout.positions.isNotEmpty() && state.graph.topics.size > CULL_THRESHOLD) {
        Text(
            text = LearnStrings.GRAPH_TOO_MANY,
            style = type.caption,
            color = palette.textTertiary,
        )
    }
}
