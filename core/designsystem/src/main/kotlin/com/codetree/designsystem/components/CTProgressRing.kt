package com.codetree.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 环形进度（docs/specs/design-system.md §6 `Sparkline/ProgressRing`）：
 * 弧线从 12 点方向顺时针，数字/标签与图形一致；减少动效时直接跳变。
 * 用于「我的」学习进度、知识库安装进度。
 */
@Composable
fun CTProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 6.dp,
    label: String? = null,
    trackColor: Color = CodeTreeTheme.colors.separator.copy(alpha = 0.5f),
    progressColor: Color = CodeTreeTheme.colors.accent,
) {
    val motion = CodeTreeTheme.motion
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = motion.normal(),
        label = "ct-progress-ring",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val diameter = this.size.minDimension - stroke
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = CodeTreeTheme.typography.footnote,
                color = CodeTreeTheme.colors.textPrimary,
            )
        }
    }
}

/**
 * 迷你趋势线（学习统计）：只表达形状趋势，不替代准确数字；空数据/单点不绘制。
 */
@Composable
fun CTSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = CodeTreeTheme.colors.accent,
    strokeWidth: Dp = 1.5.dp,
) {
    if (values.size < 2) return
    Canvas(modifier = modifier) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val paddingY = strokeWidth.toPx()
        val usableHeight = (size.height - paddingY * 2f).coerceAtLeast(1f)

        val path = Path()
        values.forEachIndexed { index, value ->
            val x = stepX * index
            val normalized = (value - min) / range
            val y = paddingY + usableHeight * (1f - normalized)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
