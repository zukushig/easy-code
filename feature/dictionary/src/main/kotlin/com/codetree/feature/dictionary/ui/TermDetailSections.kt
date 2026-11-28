package com.codetree.feature.dictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTButtonStyle
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTBilingualBlock
import com.codetree.designsystem.CTEmptyState
import com.codetree.designsystem.CTErrorState
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTLoading
import com.codetree.designsystem.CTPill
import com.codetree.designsystem.CTSectionHeader
import com.codetree.designsystem.CTSegmentedControl
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.content.BilingualMode
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.feature.dictionary.DictionaryStrings
import com.codetree.feature.dictionary.TermDetailUiState
import com.codetree.feature.dictionary.TermDetailViewModel
import com.codetree.plugin.DiagnosticRule
import com.codetree.plugin.LanguageSyntax

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SyntaxSection(syntax: LanguageSyntax?) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        CTSectionHeader(title = DictionaryStrings.SYNTAX_TITLE)
        if (syntax == null) {
            CTCard {
                Text(text = DictionaryStrings.SYNTAX_EMPTY, style = typography.footnote, color = colors.textTertiary)
            }
            return@Column
        }
        CTCard {
            Text(
                text = DictionaryStrings.SYNTAX_LANGUAGE + syntax.displayName.get("zh-CN").ifBlank { syntax.language },
                style = typography.callout,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(spacing.xxs))
            Text(
                text = DictionaryStrings.SYNTAX_VERSION + syntax.version,
                style = typography.footnote,
                color = colors.textSecondary,
            )
            if (syntax.keywords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    text = DictionaryStrings.SYNTAX_KEYWORDS + syntax.keywords.take(16).joinToString(" "),
                    style = typography.footnote,
                    color = colors.textSecondary,
                )
            }
            val lineComment = syntax.lineComment
            if (!lineComment.isNullOrBlank()) {
                Text(
                    text = DictionaryStrings.SYNTAX_COMMENT + lineComment,
                    style = typography.footnote,
                    color = colors.textSecondary,
                )
            }
            if (syntax.brackets.isNotEmpty()) {
                Text(
                    text = DictionaryStrings.SYNTAX_BRACKETS +
                        syntax.brackets.joinToString(" ") { pair -> pair.joinToString(" ") },
                    style = typography.footnote,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
internal fun DiagnosticsSection(diagnostics: List<DiagnosticRule>) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        CTSectionHeader(
            title = DictionaryStrings.ERRORS_TITLE,
            subtitle = if (diagnostics.isEmpty()) null else "${diagnostics.size} 条语法检查规则",
        )
        if (diagnostics.isEmpty()) {
            CTCard {
                Text(text = DictionaryStrings.ERRORS_EMPTY, style = typography.footnote, color = colors.textTertiary)
            }
        } else {
            diagnostics.forEach { rule ->
                CTCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rule.id,
                            style = typography.headline,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        CTTag(
                            text = if (rule.severity == "error") "错误" else "警告",
                            tone = if (rule.severity == "error") CTTagTone.DANGER else CTTagTone.WARNING,
                        )
                    }
                    val message = rule.message.get("zh-CN")
                    if (message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(spacing.xxs))
                        Text(text = message, style = typography.footnote, color = colors.textSecondary)
                    }
                    val hint = rule.hint?.get("zh-CN")
                    if (!hint.isNullOrBlank()) {
                        Text(text = "建议：" + hint, style = typography.caption, color = colors.textTertiary)
                    }
                    val pattern = rule.pattern
                    if (pattern.isNotBlank()) {
                        Text(
                            text = "规则：" + pattern,
                            style = typography.caption,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipsSection(
    title: String,
    emptyText: String,
    chips: List<Pair<String, String>>,
    onChipClick: (String) -> Unit,
) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        CTSectionHeader(title = title)
        if (chips.isEmpty()) {
            CTCard {
                Text(text = emptyText, style = typography.footnote, color = colors.textTertiary)
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                chips.forEach { (id, label) ->
                    CTPill(text = label, onClick = { onChipClick(id) })
                }
            }
        }
    }
}
