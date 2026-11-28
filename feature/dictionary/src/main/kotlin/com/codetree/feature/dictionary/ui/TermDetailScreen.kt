package com.codetree.feature.dictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codetree.designsystem.CTBilingualBlock
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTEmptyState
import com.codetree.designsystem.CTErrorState
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTLoading
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

/**
 * 术语详情（DIC-004/DIC-005）：定义/语法/示例/常见错误/相关术语/关联知识节点；
 * 中文解释、英文语法、双语对照三模式页内切换并持久化回 settings.bilingualMode。
 */
@Composable
fun TermDetailScreen(
    content: ContentRepository,
    settings: SettingsRepository,
    termId: String,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onOpenTerm: (String) -> Unit,
    onOpenTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: TermDetailViewModel = viewModel(
        key = "term-detail:$termId",
        factory = TermDetailViewModel.factory(content, settings, termId),
    )
    val state by vm.state.collectAsState()
    val colors = CodeTreeTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        val error = state.error
        when {
            state.loading -> CTLoading(
                modifier = Modifier.fillMaxSize(),
                message = DictionaryStrings.SEARCHING,
            )
            state.notFound -> CTEmptyState(
                title = DictionaryStrings.NOT_FOUND,
                description = DictionaryStrings.NOT_FOUND_DESC,
                modifier = Modifier.fillMaxSize(),
                actionLabel = DictionaryStrings.BACK,
                onAction = onBack,
            )
            error != null -> CTErrorState(
                error = error,
                modifier = Modifier.fillMaxSize(),
                onRetry = vm::retry,
            )
            else -> TermDetailContent(
                state = state,
                favorite = favorite,
                onToggleFavorite = onToggleFavorite,
                onBack = onBack,
                onOpenTerm = onOpenTerm,
                onOpenTopic = onOpenTopic,
                onModeChange = vm::setMode,
                onConsumeModeSaveError = vm::consumeModeSaveError,
            )
        }
    }
}

@Composable
private fun TermDetailContent(
    state: TermDetailUiState,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onOpenTerm: (String) -> Unit,
    onOpenTopic: (String) -> Unit,
    onModeChange: (BilingualMode) -> Unit,
    onConsumeModeSaveError: () -> Unit,
) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing
    val term = state.term ?: return
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.pageHorizontal, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item(key = "term-topbar") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CTIconButton(
                    icon = CTIcons.Back,
                    contentDescription = DictionaryStrings.BACK,
                    onClick = onBack,
                    tint = colors.textPrimary,
                )
                Text(
                    text = DictionaryStrings.DETAIL_TITLE,
                    style = typography.headline,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                CTIconButton(
                    icon = CTIcons.Bookmark,
                    contentDescription = if (favorite) DictionaryStrings.FAVORITE_REMOVE else DictionaryStrings.FAVORITE_ADD,
                    onClick = onToggleFavorite,
                    tint = if (favorite) colors.accent else colors.textTertiary,
                )
            }
        }
        item(key = "term-header") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = term.displayNameIn("zh-CN"),
                    style = typography.largeTitle,
                    color = colors.textPrimary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CTTag(text = DictionaryStrings.termTypeLabel(term.termType), tone = CTTagTone.ACCENT)
                    CTTag(
                        text = DictionaryStrings.reviewStatusLabel(term.reviewStatus),
                        tone = CTTagTone.NEUTRAL,
                    )
                    if (term.aliases.isNotEmpty()) {
                        CTTag(text = "别名 " + term.aliases.size, tone = CTTagTone.NEUTRAL)
                    }
                }
                if (term.aliases.isNotEmpty()) {
                    Text(
                        text = DictionaryStrings.ALIASES_TITLE + "：" + term.aliases.joinToString(" · ") { it.text },
                        style = typography.footnote,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        item(key = "term-mode-switch") {
            CTSegmentedControl(
                options = listOf(
                    DictionaryStrings.MODE_ZH,
                    DictionaryStrings.MODE_EN,
                    DictionaryStrings.MODE_BILINGUAL,
                ),
                selectedIndex = when (state.mode) {
                    BilingualMode.ZH_ONLY -> 0
                    BilingualMode.EN_GRAMMAR -> 1
                    BilingualMode.SIDE_BY_SIDE -> 2
                },
                onSelect = { index ->
                    onModeChange(
                        when (index) {
                            0 -> BilingualMode.ZH_ONLY
                            1 -> BilingualMode.EN_GRAMMAR
                            else -> BilingualMode.SIDE_BY_SIDE
                        },
                    )
                },
            )
        }
        val modeError = state.modeSaveError
        if (modeError != null) {
            item(key = "term-mode-error") {
                CTCard(onClick = onConsumeModeSaveError) {
                    Text(
                        text = "模式保存失败：" + modeError.userHint,
                        style = typography.footnote,
                        color = colors.error,
                    )
                }
            }
        }
        item(key = "term-definition") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                CTSectionHeader(title = DictionaryStrings.DEFINITION_TITLE)
                CTCard {
                    CTBilingualBlock(
                        mode = state.mode,
                        zh = state.chineseDefinition.takeIf { it.isNotBlank() },
                        en = state.englishGrammar.takeIf { it.isNotBlank() },
                        chineseLabel = DictionaryStrings.MODE_ZH,
                        englishLabel = DictionaryStrings.MODE_EN,
                        missingLabel = DictionaryStrings.DEFINITION_ZH_EMPTY,
                    )
                }
            }
        }
        item(key = "term-syntax") {
            SyntaxSection(syntax = state.syntax)
        }
        item(key = "term-examples") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                CTSectionHeader(title = DictionaryStrings.EXAMPLES_TITLE)
                if (state.examples.isEmpty()) {
                    CTCard {
                        Text(
                            text = DictionaryStrings.EXAMPLES_EMPTY,
                            style = typography.footnote,
                            color = colors.textTertiary,
                        )
                    }
                } else {
                    state.examples.forEach { example ->
                        DictExampleCard(example = example)
                    }
                }
            }
        }
        item(key = "term-errors") {
            DiagnosticsSection(diagnostics = state.diagnostics)
        }
        item(key = "term-related-terms") {
            ChipsSection(
                title = DictionaryStrings.RELATED_TERMS_TITLE,
                emptyText = DictionaryStrings.RELATED_TERMS_EMPTY,
                chips = state.relatedTerms.map { it.id to it.displayNameIn("zh-CN") },
                onChipClick = onOpenTerm,
            )
        }
        item(key = "term-topics") {
            ChipsSection(
                title = DictionaryStrings.RELATED_TOPICS_TITLE,
                emptyText = DictionaryStrings.RELATED_TOPICS_EMPTY,
                chips = state.topics.map { it.id to it.title.get("zh-CN").ifBlank { it.id } },
                onChipClick = onOpenTopic,
            )
        }
        item(key = "term-footer") {
            Text(
                text = DictionaryStrings.FOOTER_VERSION.format(term.contentVersion, term.source),
                style = typography.caption,
                color = colors.textTertiary,
            )
        }
        item(key = "term-bottom-space") { Spacer(modifier = Modifier.height(spacing.xxl)) }
    }
}
