package com.codetree.feature.terminal.analysis

import com.codetree.plugin.LanguageSyntax
import com.codetree.runtime.api.CodeDiagnostic
import com.codetree.runtime.api.DiagnosticFix
import com.codetree.runtime.api.DiagnosticSeverity
import com.codetree.runtime.api.FixKind

/** 基于内容包 `LanguageSyntax.diagnostics` 的逐行正则诊断；无语法包时返回空。 */
internal object LanguageDiagnostics {

    fun diagnose(code: String, syntax: LanguageSyntax?, locale: String): List<CodeDiagnostic> {
        if (syntax == null || syntax.diagnostics.isEmpty() || code.isBlank()) return emptyList()
        val result = ArrayList<CodeDiagnostic>()
        val lines = code.split('\n')
        for ((lineIndex, line) in lines.withIndex()) {
            for (rule in syntax.diagnostics) {
                if (rule.pattern.isBlank()) continue
                val regex = try {
                    Regex(rule.pattern)
                } catch (t: Throwable) {
                    continue
                }
                val match = regex.find(line) ?: continue
                result += CodeDiagnostic(
                    severity = severityOf(rule.severity),
                    line = lineIndex + 1,
                    column = match.range.first,
                    endColumn = match.range.last + 1,
                    message = rule.message.get(locale).ifBlank { rule.id },
                    hint = rule.hint?.get(locale)?.takeIf { it.isNotBlank() },
                    fix = fixOf(rule.fix?.kind, rule.fix?.text),
                    ruleId = rule.id,
                )
                if (result.size >= MAX_DIAGNOSTICS) return result
            }
        }
        return result
    }

    private fun severityOf(raw: String): DiagnosticSeverity = when (raw.lowercase()) {
        "warning", "warn" -> DiagnosticSeverity.WARNING
        "info", "hint" -> DiagnosticSeverity.INFO
        else -> DiagnosticSeverity.ERROR
    }

    private fun fixOf(kind: String?, text: String?): DiagnosticFix? = when (kind?.lowercase()) {
        "append" -> DiagnosticFix(FixKind.APPEND, text)
        "replace" -> DiagnosticFix(FixKind.REPLACE, text)
        else -> null
    }

    private const val MAX_DIAGNOSTICS = 40
}
