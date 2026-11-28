package com.codetree.feature.dictionary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTEmptyState
import com.codetree.designsystem.CTErrorState
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTLoading
import com.codetree.designsystem.CTOfflineBanner
import com.codetree.designsystem.CTSearchField
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.model.Term
import com.codetree.feature.dictionary.DictionaryStrings
import com.codetree.feature.dictionary.DictionaryUiState

/**
 * 词典搜索页（DIC-001～DIC-003 / DIC-006）：
 * CTSearchField 自动聚焦 → 150ms 防抖（ViewModel 内取消旧协程）→ 按命中层级分组展示 →
 * 空态给热词与最近搜索；无精确结果展示「你可能想找」并标注拼写建议。
 */
@Composable
fun SearchScreen(
    state: DictionaryUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClearQuery: () -> Unit,
    onUseHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onUseHotTerm: (Term) -> Unit,
    onOpenTerm: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefreshContent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.pageHorizontal),
        contentPadding = PaddingValues(top = spacing.lg, bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = "dict-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DictionaryStrings.TITLE,
                    style = typography.largeTitle,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                CTIconButton(
                    icon = CTIcons.Refresh,
                    contentDescription = "刷新内容",
                    onClick = onRefreshContent,
                    tint = colors.accent,
                )
            }
            Text(
                text = DictionaryStrings.SEARCH_HINT,
                style = typography.caption,
                color = colors.textTertiary,
            )
        }
        item(key = "dict-search-field") {
            CTSearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = DictionaryStrings.SEARCH_PLACEHOLDER,
                onSearch = onSubmit,
                onCancel = onClearQuery,
                cancelLabel = DictionaryStrings.CANCEL,
                autoFocus = true,
            )
        }
        item(key = "dict-search-meta") {
            Column {
                if (state.searching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = colors.accent,
                        trackColor = colors.separator,
                    )
                } else if (state.query.isNotBlank() && !state.validQuery) {
                    Text(
                        text = DictionaryStrings.SEARCH_INVALID,
                        style = typography.footnote,
                        color = colors.warning,
                    )
                } else if (state.elapsedMs > 0L && state.hasSearched) {
                    Text(
                        text = DictionaryStrings.SEARCH_ELAPSED.format(state.elapsedMs) +
                            " · 共 " + state.groups.sumOf { it.items.size } + " 条",
                        style = typography.caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
        when {
            state.error != null -> item(key = "dict-error") {
                CTErrorState(error = state.error!!, onRetry = { onQueryChange(state.query) })
            }
            state.installedPackCount == 0 && state.hotTerms.isEmpty() -> {
                if (state.catalogCount > 0) {
                    item(key = "dict-offline") {
                        CTOfflineBanner(message = DictionaryStrings.OFFLINE_DESC)
                    }
                }
                item(key = "dict-empty-content") {
                    CTEmptyState(
                        title = DictionaryStrings.EMPTY_CONTENT_TITLE,
                        description = DictionaryStrings.EMPTY_CONTENT_DESC,
                        actionLabel = DictionaryStrings.REFRESH,
                        onAction = onRefreshContent,
                    )
                }
            }
            state.query.isBlank() -> {
                item(key = "dict-history") {
                    HistorySection(
                        history = state.history,
                        onUseHistory = onUseHistory,
                        onRemoveHistory = onRemoveHistory,
                        onClearHistory = onClearHistory,
                    )
                }
                item(key = "dict-favorites") {
                    FavoritesSection(
                        favorites = state.favoriteTermList,
                        onOpenTerm = onOpenTerm,
                    )
                }
                item(key = "dict-hot") {
                    HotTermsSection(hotTerms = state.hotTerms, onUseHotTerm = onUseHotTerm)
                }
            }
            state.searching && !state.hasSearched -> item(key = "dict-searching") {
                CTLoading(message = DictionaryStrings.SEARCHING)
            }
            state.isResultEmpty -> {
                item(key = "dict-no-result") {
                    CTEmptyState(
                        title = DictionaryStrings.NO_RESULT_TITLE,
                        description = DictionaryStrings.NO_RESULT_DESC_PREFIX + state.query +
                            DictionaryStrings.NO_RESULT_DESC_SUFFIX,
                    )
                }
                if (state.suggestions.isNotEmpty()) {
                    item(key = "dict-suggestions") {
                        SuggestionsSection(
                            suggestions = state.suggestions,
                            isFavorite = { state.isFavorite(it.termId) },
                            onOpenTerm = onOpenTerm,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                }
                item(key = "dict-hot-fallback") {
                    HotTermsSection(hotTerms = state.hotTerms, onUseHotTerm = onUseHotTerm)
                }
            }
            else -> {
                state.groups.forEach { group ->
                    item(key = "dict-group-${group.title}") {
                        ResultGroupSection(
                            group = group,
                            isFavorite = { state.isFavorite(it.termId) },
                            onOpenTerm = onOpenTerm,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                }
                if (state.suggestions.isNotEmpty()) {
                    item(key = "dict-suggestion-section") {
                        SuggestionsSection(
                            suggestions = state.suggestions,
                            isFavorite = { state.isFavorite(it.termId) },
                            onOpenTerm = onOpenTerm,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                }
            }
        }
    }
}
