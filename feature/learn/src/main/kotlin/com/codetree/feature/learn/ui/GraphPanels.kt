package com.codetree.feature.learn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codetree.feature.learn.LearnStrings
import com.codetree.designsystem.CTRelationLegend
import com.codetree.feature.learn.LearnUiState

@Composable
internal fun SelectedTopicCard(
    state: LearnUiState,
    topicId: String,
    onOpenDetail: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val topic = state.graph.topic(topicId) ?: return
    val palette = learnPalette()
    val type = learnType()
    LearnCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = LearnStrings.SELECTED_CARD_TITLE,
                style = type.caption,
                color = palette.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = LearnStrings.statusLabel(state.statusOf(topicId)),
                style = type.caption,
                color = palette.statusColor(state.statusOf(topicId)),
            )
        }
        Spacer(modifier = Modifier.height(learnSpacing().xs))
        Text(text = state.titleOf(topicId), style = type.title2, color = palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val summary = state.summaryOf(topicId)
        if (summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(learnSpacing().xs))
            Text(text = summary, style = type.subhead, color = palette.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(learnSpacing().m))
        Row(horizontalArrangement = Arrangement.spacedBy(learnSpacing().s)) {
            LearnButton(text = LearnStrings.OPEN_DETAIL, onClick = onOpenDetail, style = LearnButtonStyle.FILLED)
            LearnButton(text = LearnStrings.CLEAR_SELECTION, onClick = onClearSelection, style = LearnButtonStyle.PLAIN)
        }
    }
}

/** 关系图例：文字 + 线型（不只颜色），同时给出难度图例。 */
@Composable
internal fun GraphLegendCard() {
    val palette = learnPalette()
    val type = learnType()
    val spacing = learnSpacing()
    LearnCard {
        Text(text = LearnStrings.LEGEND_TITLE, style = type.headline, color = palette.textPrimary)
        Spacer(modifier = Modifier.height(spacing.s))
        CTRelationLegend(
            prerequisiteLabel = LearnStrings.EDGE_PREREQUISITE,
            relatedLabel = LearnStrings.EDGE_RELATED,
            appliedInLabel = LearnStrings.EDGE_APPLIED,
            confusedWithLabel = LearnStrings.EDGE_CONFUSED,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(spacing.s))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text(text = "节点颜色：", style = type.caption, color = palette.textTertiary)
            DifficultyDot(learnPalette().success, LearnStrings.DIFFICULTY_BEGINNER)
            DifficultyDot(learnPalette().warning, LearnStrings.DIFFICULTY_INTERMEDIATE)
            DifficultyDot(learnPalette().error, LearnStrings.DIFFICULTY_ADVANCED)
        }
    }
}

@Composable
private fun DifficultyDot(color: Color, label: String) {
    val type = learnType()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(999.dp)),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = type.caption, color = learnPalette().textSecondary)
    }
}
