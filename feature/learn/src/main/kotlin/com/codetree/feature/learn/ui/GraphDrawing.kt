package com.codetree.feature.learn.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.codetree.domain.model.EdgeType
import com.codetree.feature.learn.LearnUiState
import com.codetree.domain.graph.LayoutPoint

internal fun neighboursOf(state: LearnUiState, topicId: String): Set<String> = buildSet {
    state.graph.outgoing(topicId).forEach { add(it.target) }
    state.graph.incoming(topicId).forEach { add(it.source) }
}

internal data class GraphRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(point: LayoutPoint): Boolean =
        point.x in left..right && point.y in top..bottom

    fun intersects(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val minX = minOf(x1, x2)
        val maxX = maxOf(x1, x2)
        val minY = minOf(y1, y2)
        val maxY = maxOf(y1, y2)
        return maxX >= left && minX <= right && maxY >= top && minY <= bottom
    }
}

internal fun graphVisibleRect(
    screenWidth: Float,
    screenHeight: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): GraphRect {
    if (scale <= 0f) return GraphRect(0f, 0f, 0f, 0f)
    val margin = 120f / scale
    val left = -offsetX / scale - margin
    val top = -offsetY / scale - margin
    val right = (screenWidth - offsetX) / scale + margin
    val bottom = (screenHeight - offsetY) / scale + margin
    return GraphRect(left, top, right, bottom)
}

internal fun edgeIntersects(
    from: LayoutPoint,
    to: LayoutPoint,
    rect: GraphRect,
): Boolean = rect.intersects(from.x, from.y, to.x, to.y)

/** 四类边线型：前置实线+箭头、相关虚线、应用点线、易混淆双色短线（ADR-002 §5）。 */
internal fun DrawScope.drawEdge(
    edgeType: EdgeType,
    start: Offset,
    end: Offset,
    targetRadius: Float,
    strokeWidth: Float,
    palette: LearnPalette,
    dimmed: Boolean,
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    val ux = dx / length
    val uy = dy / length
    val alpha = if (dimmed) 0.35f else 1f
    when (edgeType) {
        EdgeType.PREREQUISITE -> {
            val tip = Offset(end.x - ux * targetRadius, end.y - uy * targetRadius)
            drawLine(
                color = palette.accent.copy(alpha = alpha),
                start = start,
                end = tip,
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawArrowHead(tip = tip, ux = ux, uy = uy, color = palette.accent.copy(alpha = alpha), size = 7f)
        }
        EdgeType.RELATED -> drawLine(
            color = palette.textSecondary.copy(alpha = alpha),
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f)),
        )
        EdgeType.APPLIED_IN -> drawLine(
            color = palette.textTertiary.copy(alpha = alpha),
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 7f)),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        EdgeType.CONFUSED_WITH -> {
            // 双色短线：两条平行短线，颜色只作辅助，图例中配「易混淆」文字。
            val nx = -uy * 2.2f
            val ny = ux * 2.2f
            drawLine(
                color = palette.warning.copy(alpha = alpha),
                start = Offset(start.x + nx, start.y + ny),
                end = Offset(end.x + nx, end.y + ny),
                strokeWidth = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)),
            )
            drawLine(
                color = palette.error.copy(alpha = alpha),
                start = Offset(start.x - nx, start.y - ny),
                end = Offset(end.x - nx, end.y - ny),
                strokeWidth = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)),
            )
        }
    }
}

internal fun DrawScope.drawArrowHead(tip: Offset, ux: Float, uy: Float, color: Color, size: Float) {
    val bx = tip.x - ux * size
    val by = tip.y - uy * size
    val nx = -uy * size * 0.55f
    val ny = ux * size * 0.55f
    drawLine(color = color, start = tip, end = Offset(bx + nx, by + ny), strokeWidth = 1.6f)
    drawLine(color = color, start = tip, end = Offset(bx - nx, by - ny), strokeWidth = 1.6f)
}
