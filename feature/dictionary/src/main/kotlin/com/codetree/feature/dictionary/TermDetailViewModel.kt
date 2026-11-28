package com.codetree.feature.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codetree.domain.common.AppError
import com.codetree.domain.content.BilingualMode
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.domain.model.Term
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 术语详情状态（DIC-004/DIC-005）：定义/语法/示例/常见错误/相关术语/关联知识节点，
 * 中文解释、英文语法、双语对照三模式由 `settings.bilingualMode` 驱动，页内切换回写并记忆。
 */
class TermDetailViewModel(
    private val content: ContentRepository,
    private val settings: SettingsRepository,
    private val termId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TermDetailUiState())
    val state: StateFlow<TermDetailUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            settings.settings.collect { appSettings ->
                _state.update { it.copy(mode = appSettings.bilingualMode) }
            }
        }
    }

    private fun load() {
        val term: Term? = content.term(termId)
        if (term == null) {
            _state.update { it.copy(loading = false, notFound = true) }
            return
        }
        val examples = term.exampleIds.mapNotNull { content.example(it) }
        val syntax = term.syntaxRef?.let { content.languageSyntax(it) }
        val related = term.relatedTermIds.mapNotNull { content.term(it) }
        val topics = term.topicIds.mapNotNull { content.topic(it) }
        _state.update {
            it.copy(
                loading = false,
                term = term,
                chineseDefinition = term.definitions.get(LOCALE),
                englishGrammar = term.definitions.get("en"),
                examples = examples,
                syntax = syntax,
                diagnostics = syntax?.diagnostics.orEmpty(),
                relatedTerms = related,
                topics = topics,
            )
        }
    }

    fun retry() {
        _state.update { it.copy(loading = true, notFound = false, error = null) }
        load()
    }

    /** 三模式切换：立即回写 settings，由 DataStore 持久化（DIC-005：模式选择被记住）。 */
    fun setMode(mode: BilingualMode) {
        if (_state.value.mode == mode) return
        viewModelScope.launch {
            val result = settings.update { it.copy(bilingualMode = mode) }
            val error: AppError? = result.errorOrNull()
            _state.update { current ->
                current.copy(
                    mode = if (error == null) mode else current.mode,
                    modeSaveError = error,
                )
            }
        }
    }

    fun consumeModeSaveError() {
        _state.update { it.copy(modeSaveError = null) }
    }

    companion object {
        fun factory(
            content: ContentRepository,
            settings: SettingsRepository,
            termId: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TermDetailViewModel(content, settings, termId) as T
        }
    }
}
