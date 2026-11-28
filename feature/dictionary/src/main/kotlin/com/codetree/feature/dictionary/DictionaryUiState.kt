package com.codetree.feature.dictionary

import com.codetree.domain.common.AppError
import com.codetree.domain.content.BilingualMode
import com.codetree.domain.model.Term
import com.codetree.domain.model.Topic
import com.codetree.domain.search.SearchMatchKind
import com.codetree.domain.search.TermSearchHit
import com.codetree.plugin.DiagnosticRule
import com.codetree.plugin.LanguageSyntax

/** 搜索结果行（已在 ViewModel 中解析好展示文本，UI 不做业务查询）。 */
data class SearchResultItem(
    val termId: String,
    val displayName: String,
    val definitionFirstLine: String,
    val kind: SearchMatchKind,
    val matchedField: String,
    val suggestion: Boolean,
    val reason: String?,
)

/** 按 match 层级分组的结果（精确/别名/前缀/全文/模糊建议）。 */
data class SearchResultGroup(
    val title: String,
    val items: List<SearchResultItem>,
)

/**
 * 词典 Tab 单一状态：搜索（查询/防抖/结果/历史/收藏/热词）+ 内部术语导航栈。
 * 历史与收藏为会话内存储（MVP）；TODO(T7/P1)：用 DataStore 持久化（DIC-006）。
 */
data class DictionaryUiState(
    val query: String = "",
    val searching: Boolean = false,
    val validQuery: Boolean = false,
    val groups: List<SearchResultGroup> = emptyList(),
    val suggestions: List<SearchResultItem> = emptyList(),
    val error: AppError? = null,
    val elapsedMs: Long = 0L,
    val hasSearched: Boolean = false,
    val history: List<String> = emptyList(),
    val favorites: Set<String> = emptySet(),
    /** 收藏术语（会话内解析好的展示数据；P1 迁移 DataStore）。 */
    val favoriteTermList: List<Term> = emptyList(),
    val hotTerms: List<Term> = emptyList(),
    /** 术语详情导航栈（支持相关术语递归跳转后逐级返回）。 */
    val navStack: List<String> = emptyList(),
    val installedPackCount: Int = 0,
    val catalogCount: Int = 0,
) {
    val selectedTermId: String? get() = navStack.lastOrNull()

    val isResultEmpty: Boolean
        get() = hasSearched && !searching && groups.isEmpty() && suggestions.isEmpty()

    /** 无内容包且无热词：给出去「我的 → 知识库」的空态。 */
    val isContentEmpty: Boolean get() = hotTerms.isEmpty() && installedPackCount == 0

    fun isFavorite(termId: String): Boolean = termId in favorites
}

/** 术语详情状态（三模式双语由 settings.bilingualMode 驱动并回写）。 */
data class TermDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val error: AppError? = null,
    val term: Term? = null,
    val chineseDefinition: String = "",
    val englishGrammar: String = "",
    val examples: List<com.codetree.domain.model.SyntaxExample> = emptyList(),
    val syntax: LanguageSyntax? = null,
    val diagnostics: List<DiagnosticRule> = emptyList(),
    val relatedTerms: List<Term> = emptyList(),
    val topics: List<Topic> = emptyList(),
    val mode: BilingualMode = BilingualMode.SIDE_BY_SIDE,
    val favorite: Boolean = false,
    val modeSaveError: AppError? = null,
) {
    val hasChinese: Boolean get() = chineseDefinition.isNotBlank()
    val hasEnglish: Boolean get() = englishGrammar.isNotBlank()
}

/** 命中层级 → 分组标题与顺序（rank 升序，模糊建议置顶之外独立展示）。 */
internal fun groupMeta(kind: SearchMatchKind): Pair<String, Int> = when (kind) {
    SearchMatchKind.EXACT_ID, SearchMatchKind.EXACT_NAME -> DictionaryStrings.GROUP_EXACT to 0
    SearchMatchKind.EXACT_ALIAS -> DictionaryStrings.GROUP_ALIAS to 1
    SearchMatchKind.PREFIX -> DictionaryStrings.GROUP_PREFIX to 2
    SearchMatchKind.FTS -> DictionaryStrings.GROUP_FTS to 3
    SearchMatchKind.FUZZY -> DictionaryStrings.GROUP_FUZZY to 4
}

internal fun TermSearchHit.toResultItem(
    displayName: String,
    definitionFirstLine: String,
): SearchResultItem = SearchResultItem(
    termId = termId,
    displayName = displayName,
    definitionFirstLine = definitionFirstLine,
    kind = kind,
    matchedField = matchedField,
    suggestion = suggestion || kind == SearchMatchKind.FUZZY,
    reason = reason?.get(LOCALE)?.takeIf { it.isNotBlank() },
)

internal const val LOCALE = "zh-CN"
