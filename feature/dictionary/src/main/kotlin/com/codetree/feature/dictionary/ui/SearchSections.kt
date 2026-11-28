package com.codetree.feature.dictionary.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTButtonStyle
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTEmptyState
import com.codetree.designsystem.CTErrorState
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTListRow
import com.codetree.designsystem.CTLoading
import com.codetree.designsystem.CTOfflineBanner
import com.codetree.designsystem.CTPill
import com.codetree.designsystem.CTSearchField
import com.codetree.designsystem.CTSectionHeader
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.model.Term
import com.codetree.feature.dictionary.DictionaryStrings
import com.codetree.feature.dictionary.DictionaryUiState
import com.codetree.feature.dictionary.SearchResultGroup
import com.codetree.feature.dictionary.SearchResultItem

/**
 * 词典搜索页（DIC-001～DIC-003 / DIC-006）：
 * CTSearchField 自动聚焦 → 150ms 防抖（ViewModel 内取消旧协程）→ 按命中层级分组展示 →
 * 空态给热词与最近搜索；无精确结果展示「你可能想找」并标注拼写建议。
 */
@Composable
internal fun HistorySection(
    history: List<String>,
    onUseHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CTSectionHeader(title = DictionaryStrings.HISTORY_TITLE, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) {
                CTTextButton(text = DictionaryStrings.CLEAR_HISTORY, onClick = onClearHistory)
            }
        }
        if (history.isEmpty()) {
            Text(text = DictionaryStrings.HISTORY_EMPTY, style = typography.footnote, color = colors.textTertiary)
        } else {
            CTCard(contentPadding = 0.dp) {
                history.forEach { entry ->
                    CTListRow(
                        title = entry,
                        onClick = { onUseHistory(entry) },
                        trailing = {
                            CTIconButton(
                                icon = CTIcons.Close,
                                contentDescription = "删除该历史",
                                onClick = { onRemoveHistory(entry) },
                                tint = colors.textTertiary,
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavoritesSection(
    favorites: List<Term>,
    onOpenTerm: (String) -> Unit,
) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        CTSectionHeader(title = DictionaryStrings.FAVORITES_TITLE)
        if (favorites.isEmpty()) {
            Text(text = DictionaryStrings.FAVORITES_EMPTY, style = typography.footnote, color = colors.textTertiary)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                favorites.forEach { term ->
                    CTPill(
                        text = term.displayNameIn("zh-CN"),
                        selected = true,
                        onClick = { onOpenTerm(term.id) },
                        leadingIcon = CTIcons.Bookmark,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HotTermsSection(hotTerms: List<Term>, onUseHotTerm: (Term) -> Unit) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        CTSectionHeader(title = DictionaryStrings.HOT_TITLE)
        if (hotTerms.isEmpty()) {
            Text(text = DictionaryStrings.HOT_EMPTY, style = typography.footnote, color = colors.textTertiary)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                hotTerms.forEach { term ->
                    CTPill(text = term.displayNameIn("zh-CN"), onClick = { onUseHotTerm(term) })
                }
            }
        }
    }
}

@Composable
internal fun ResultGroupSection(
    group: SearchResultGroup,
    isFavorite: (SearchResultItem) -> Boolean,
    onOpenTerm: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CodeTreeTheme.spacing.sm)) {
        CTSectionHeader(title = group.title, subtitle = "${group.items.size} 条")
        group.items.forEach { item ->
            ResultRow(
                item = item,
                favorite = isFavorite(item),
                onOpenTerm = onOpenTerm,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
internal fun SuggestionsSection(
    suggestions: List<SearchResultItem>,
    isFavorite: (SearchResultItem) -> Boolean,
    onOpenTerm: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CodeTreeTheme.spacing.sm)) {
        CTSectionHeader(
            title = DictionaryStrings.SUGGESTIONS_TITLE,
            subtitle = "低相关结果仅作建议，不是精确匹配",
        )
        suggestions.forEach { item ->
            ResultRow(
                item = item,
                favorite = isFavorite(item),
                onOpenTerm = onOpenTerm,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
internal fun ResultRow(
    item: SearchResultItem,
    favorite: Boolean,
    onOpenTerm: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    CTCard(onClick = { onOpenTerm(item.termId) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayName,
                        style = typography.headline,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.suggestion || item.reason != null) {
                        Spacer(modifier = Modifier.width(spacing.xs))
                        CTTag(text = DictionaryStrings.SUGGESTION_BADGE, tone = CTTagTone.WARNING)
                    }
                }
                if (item.definitionFirstLine.isNotBlank()) {
                    Text(
                        text = item.definitionFirstLine,
                        style = typography.footnote,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Column {
                        Text(text = "暂无中文释义", style = typography.footnote, color = colors.textTertiary)
                        CTButton(
                            text = "查看详情",
                            onClick = { onOpenTerm(item.termId) },
                            style = CTButtonStyle.PLAIN,
                        )
                    }
                }
                if (item.matchedField.isNotBlank()) {
                    Text(
                        text = DictionaryStrings.MATCHED_FIELD + item.matchedField +
                            (item.reason?.let { " · " + it } ?: ""),
                        style = typography.caption,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CTIconButton(
                icon = CTIcons.Bookmark,
                contentDescription = if (favorite) DictionaryStrings.FAVORITE_REMOVE else DictionaryStrings.FAVORITE_ADD,
                onClick = { onToggleFavorite(item.termId) },
                tint = if (favorite) colors.accent else colors.textTertiary,
            )
        }
    }
}
