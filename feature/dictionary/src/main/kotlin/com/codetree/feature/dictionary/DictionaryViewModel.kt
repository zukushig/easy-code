package com.codetree.feature.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codetree.domain.common.AppError
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.domain.model.Term
import com.codetree.domain.search.QueryNormalizer
import com.codetree.domain.search.SearchMatchKind
import com.codetree.domain.search.TermSearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 词典搜索 + 术语导航状态（DIC-001～DIC-005）。
 *
 * 搜索：输入防抖 150ms；新查询取消旧协程，旧结果不得覆盖新结果（knowledge.md §8）。
 * 排序/模糊建议由 `:core:domain` 的 [com.codetree.domain.search.TermSearchEngine] 负责，UI 不复制算法。
 * 历史/收藏为会话内存储；TODO(T7/P1)：迁移到 DataStore（DIC-006）。
 */
class DictionaryViewModel(
    private val content: ContentRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DictionaryUiState())
    val state: StateFlow<DictionaryUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private val favoriteTerms = linkedSetOf<String>()
    private var lastSearchQuery: String = ""

    init {
        loadContentMeta()
    }

    private fun loadContentMeta() {
        val installed = runCatching { content.installedPacks() }.getOrDefault(emptyList())
        val catalog = runCatching { content.catalog() }.getOrDefault(emptyList())
        val terms: List<Term> = runCatching {
            installed.flatMap { pack -> content.pack(pack.packId)?.terms.orEmpty() }
                .distinctBy { it.id }
                .sortedBy { it.id }
                .take(HOT_TERM_LIMIT)
        }.getOrDefault(emptyList())
        _state.update {
            it.copy(
                hotTerms = terms,
                installedPackCount = installed.size,
                catalogCount = catalog.size,
                favorites = favoriteTerms.toSet(),
            )
        }
    }

    fun refreshContent() {
        loadContentMeta()
        onQueryChange(_state.value.query)
    }

    // ---------------------------------------------------------------------
    // 搜索（防抖 + 取消旧查询）
    // ---------------------------------------------------------------------

    fun onQueryChange(raw: String) {
        searchJob?.cancel()
        val valid = QueryNormalizer.isValid(raw)
        _state.update {
            it.copy(
                query = raw,
                validQuery = valid,
                error = null,
                searching = valid && lastSearchQuery != raw,
                groups = if (valid) it.groups else emptyList(),
                suggestions = if (valid) it.suggestions else emptyList(),
                hasSearched = it.hasSearched && valid,
            )
        }
        if (!valid) {
            lastSearchQuery = ""
            return
        }
        val query = QueryNormalizer.normalize(raw)
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _state.update { it.copy(searching = true) }
            val engine = content.searchEngine()
            val result = withContext(Dispatchers.Default) {
                runCatching { engine.search(query) }
            }
            // 旧查询结果不得覆盖新结果（knowledge.md §8 查询取消语义）。
            if (_state.value.query.trim() != raw.trim()) return@launch
            lastSearchQuery = raw
            result
                .onSuccess { searchResult ->
                    val exact = searchResult.hits.filter { it.kind != SearchMatchKind.FUZZY }
                    val groups = exact.groupBy { groupMeta(it.kind) }
                        .toList()
                        .sortedBy { it.first.second }
                        .map { (meta, hits) ->
                            SearchResultGroup(
                                title = meta.first,
                                items = hits.map { it.toResolvedItem() },
                            )
                        }
                    val suggestions = (searchResult.suggestions + searchResult.hits.filter { it.kind == SearchMatchKind.FUZZY || it.suggestion })
                        .distinctBy { it.termId }
                        .map { it.toResolvedItem() }
                    _state.update {
                        it.copy(
                            searching = false,
                            groups = groups,
                            suggestions = suggestions,
                            elapsedMs = searchResult.elapsedMs,
                            hasSearched = true,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            searching = false,
                            hasSearched = true,
                            error = AppError.Internal(throwable.message ?: "搜索失败", throwable),
                        )
                    }
                }
        }
    }

    /** 用户主动提交（IME Search / 点热词 / 点历史）时记录搜索历史。 */
    fun submitQuery() {
        val query = QueryNormalizer.normalize(_state.value.query)
        if (!QueryNormalizer.isValid(query)) return
        addHistory(query)
        onQueryChange(query)
    }

    fun useHistory(query: String) {
        addHistory(query)
        onQueryChange(query)
    }

    fun useHotTerm(term: Term) {
        onQueryChange(term.displayNameIn(LOCALE))
        addHistory(term.displayNameIn(LOCALE))
        openTerm(term.id)
    }

    fun clearQuery() {
        searchJob?.cancel()
        lastSearchQuery = ""
        _state.update {
            it.copy(
                query = "",
                validQuery = false,
                searching = false,
                groups = emptyList(),
                suggestions = emptyList(),
                hasSearched = false,
                elapsedMs = 0L,
                error = null,
            )
        }
    }

    fun retry() {
        val query = _state.value.query
        onQueryChange(query)
    }

    private fun addHistory(query: String) {
        if (query.isBlank()) return
        _state.update { current ->
            val updated = (listOf(query) + current.history.filter { it != query }).take(HISTORY_LIMIT)
            current.copy(history = updated)
        }
    }

    fun removeHistory(query: String) {
        _state.update { it.copy(history = it.history.filter { entry -> entry != query }) }
    }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
    }

    fun toggleFavorite(termId: String) {
        if (favoriteTerms.contains(termId)) favoriteTerms.remove(termId) else favoriteTerms.add(termId)
        val resolved = favoriteTerms.mapNotNull { content.term(it) }
        _state.update { it.copy(favorites = favoriteTerms.toSet(), favoriteTermList = resolved) }
    }

    // ---------------------------------------------------------------------
    // 术语详情导航（支持相关术语递归跳转与逐级返回）
    // ---------------------------------------------------------------------

    fun openTerm(termId: String) {
        if (content.term(termId) == null) return
        _state.update { current ->
            if (current.navStack.lastOrNull() == termId) return@update current
            current.copy(navStack = (current.navStack + termId).take(MAX_NAV_STACK))
        }
    }

    fun backFromTerm() {
        _state.update {
            it.copy(
                navStack = if (it.navStack.isEmpty()) emptyList() else it.navStack.dropLast(1),
            )
        }
    }

    fun closeTerm() {
        _state.update { it.copy(navStack = emptyList()) }
    }

    fun isFavorite(termId: String): Boolean = termId in favoriteTerms

    private fun TermSearchHit.toResolvedItem(): SearchResultItem {
        val term = content.term(termId)
        val displayName = term?.displayNameIn(LOCALE).orEmpty().ifBlank {
            displayName.get(LOCALE).ifBlank { termId }
        }
        val firstLine = term?.definitions?.get(LOCALE)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotBlank() }
            .orEmpty()
        return toResultItem(displayName = displayName, definitionFirstLine = firstLine)
    }

    companion object {
        const val DEBOUNCE_MS = 150L
        private const val HOT_TERM_LIMIT = 8
        private const val HISTORY_LIMIT = 10
        private const val MAX_NAV_STACK = 16

        fun factory(
            content: ContentRepository,
            settings: SettingsRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DictionaryViewModel(content, settings) as T
        }
    }
}
