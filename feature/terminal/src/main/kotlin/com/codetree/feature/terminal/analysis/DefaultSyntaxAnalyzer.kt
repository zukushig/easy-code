package com.codetree.feature.terminal.analysis

import com.codetree.plugin.LanguageSyntax
import com.codetree.runtime.api.CodeDiagnostic
import com.codetree.runtime.api.CompletionKind
import com.codetree.runtime.api.CompletionSuggestion
import com.codetree.runtime.api.SyntaxAnalyzer
import com.codetree.runtime.api.TokenKind
import com.codetree.runtime.api.TokenSpan

/**
 * 默认输入分析器：高亮 / 诊断 / 补全。
 *
 * - 安全 shell：使用 [ShellCommandCatalog] 内置规则（不内嵌完整语言语法）；
 * - 示例语言（Python/JavaScript…）：规则全部来自内容包 [LanguageSyntax]，无语法包时降级为
 *   全文 PLAIN + 空诊断 + 空补全，不崩溃；
 * - 补全带 `replaceStart/replaceEnd`，UI 只替换该区间，不覆盖后续文本。
 *
 * 线程约束：只在主线程（ViewModel）调用；[setLanguage]/[setHistory] 修改内部状态。
 */
class DefaultSyntaxAnalyzer(
    private val syntaxProvider: (String) -> LanguageSyntax?,
    initialLanguage: String = ShellLanguages.SHELL,
    private val locale: String = "zh-CN",
) : SyntaxAnalyzer {

    var language: String = initialLanguage
        private set

    private var syntax: LanguageSyntax? = syntaxProvider(initialLanguage)
    private var history: List<String> = emptyList()

    fun setLanguage(newLanguage: String) {
        if (newLanguage == language) return
        language = newLanguage
        syntax = syntaxProvider(newLanguage)
    }

    fun setHistory(commands: List<String>) {
        history = commands.takeLast(MAX_HISTORY)
    }

    fun currentSyntax(): LanguageSyntax? = syntax

    fun isShell(): Boolean = language == ShellLanguages.SHELL

    override fun highlight(code: String): List<TokenSpan> {
        if (code.isEmpty()) return emptyList()
        if (code.length > MAX_ANALYZE_CHARS) return listOf(TokenSpan(0, code.length, TokenKind.PLAIN))
        return try {
            when {
                isShell() -> ShellHighlighter.highlight(code)
                else -> syntax?.let { LanguageHighlighter.highlight(code, it) }
                    ?: listOf(TokenSpan(0, code.length, TokenKind.PLAIN))
            }
        } catch (t: Throwable) {
            listOf(TokenSpan(0, code.length, TokenKind.PLAIN))
        }
    }

    override fun diagnose(code: String): List<CodeDiagnostic> {
        if (code.isBlank() || code.length > MAX_ANALYZE_CHARS) return emptyList()
        return try {
            val diagnostics = if (isShell()) {
                ShellDiagnostics.diagnose(code)
            } else {
                LanguageDiagnostics.diagnose(code, syntax, locale)
            }
            diagnostics.take(MAX_DIAGNOSTICS)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    override fun complete(code: String, cursor: Int, limit: Int): List<CompletionSuggestion> {
        if (code.isBlank() || limit <= 0 || code.length > MAX_ANALYZE_CHARS) return emptyList()
        val safeCursor = cursor.coerceIn(0, code.length)
        val tokenStart = AnalysisText.wordStart(code, safeCursor)
        val tokenEnd = AnalysisText.wordEnd(code, safeCursor)
        val query = code.substring(tokenStart, safeCursor)
        val lineStart = AnalysisText.lineStart(code, safeCursor).coerceAtMost(tokenStart)
        val candidates: List<Candidate> = when {
            isShell() -> {
                val atCommandStart = code.substring(lineStart, tokenStart).isBlank()
                if (atCommandStart) commandCandidates() else parameterCandidates(code, lineStart, tokenStart)
            }
            else -> languageCandidates()
        }
        if (candidates.isEmpty()) return emptyList()
        val ranked = FuzzyMatch.rank(query, candidates, { it.insertText }, limit.coerceAtMost(MAX_COMPLETIONS))
        return ranked.map { candidate ->
            CompletionSuggestion(
                text = candidate.insertText,
                insertText = candidate.insertText,
                detail = candidate.detail,
                kind = candidate.kind,
                replaceStart = tokenStart,
                replaceEnd = tokenEnd,
            )
        }
    }

    private fun commandCandidates(): List<Candidate> = ShellCommandCatalog.COMMANDS.map { spec ->
        Candidate(insertText = spec.name, detail = spec.usage, kind = CompletionKind.COMMAND)
    }

    private fun parameterCandidates(code: String, lineStart: Int, tokenStart: Int): List<Candidate> {
        val result = ArrayList<Candidate>()
        val linePrefix = code.substring(lineStart, tokenStart).trim()
        val command = linePrefix.split(Regex("\\s+")).firstOrNull().orEmpty()
        val spec = ShellCommandCatalog.find(command)
        spec?.flags?.forEach { flag ->
            result += Candidate(insertText = flag, detail = "参数 ${spec.name}", kind = CompletionKind.BUILTIN)
        }
        val historyWords = history
            .flatMap { AnalysisText.splitArgs(it).drop(1) }
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 64 }
            .distinct()
        historyWords.forEach { word ->
            result += Candidate(insertText = word, detail = "历史参数", kind = CompletionKind.WORD)
        }
        return result
    }

    private fun languageCandidates(): List<Candidate> {
        val current = syntax ?: return emptyList()
        val result = ArrayList<Candidate>()
        current.keywords.forEach { keyword ->
            result += Candidate(insertText = keyword, detail = "关键字", kind = CompletionKind.KEYWORD)
        }
        current.builtins.forEach { builtin ->
            result += Candidate(insertText = builtin, detail = "内置", kind = CompletionKind.BUILTIN)
        }
        current.completions.forEach { group ->
            group.items.forEach { item ->
                result += Candidate(
                    insertText = item.insertText ?: item.text,
                    detail = item.detail?.get(locale)?.takeIf { it.isNotBlank() } ?: group.trigger,
                    kind = CompletionKind.WORD,
                )
            }
        }
        return result
    }

    private data class Candidate(
        val insertText: String,
        val detail: String,
        val kind: CompletionKind,
    )

    companion object {
        const val MAX_COMPLETIONS = 8
        const val MAX_ANALYZE_CHARS = 4000
        const val MAX_DIAGNOSTICS = 40
        private const val MAX_HISTORY = 100
    }
}
