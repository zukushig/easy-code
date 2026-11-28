package com.codetree.feature.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codetree.designsystem.CTPill
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.runtime.api.CompletionSuggestion

/** 键盘上方的补全 chips（最多 6 条，来自 `DefaultSyntaxAnalyzer.complete`）。 */
@Composable
internal fun CompletionBar(
    completions: List<CompletionSuggestion>,
    onSelect: (CompletionSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminal = CodeTreeTheme.terminal
    val spacing = CodeTreeTheme.spacing
    Row(
        modifier = modifier
            .background(terminal.bg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        completions.forEach { suggestion ->
            CTPill(
                text = suggestion.insertText,
                onClick = { onSelect(suggestion) },
            )
        }
    }
}
