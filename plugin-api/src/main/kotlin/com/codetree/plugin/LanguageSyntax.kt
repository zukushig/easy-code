package com.codetree.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 语法包模型（content/<pack>/syntax/<language>.json）：终端高亮、括号匹配、
 * 错误诊断与上下文补全的统一数据来源。由内容作者维护，UI 只消费。
 */
@Serializable
data class LanguageSyntax(
    val language: String,
    val displayName: LocalizedText = LocalizedText.EMPTY,
    val version: String = "1.0.0",
    val keywords: List<String> = emptyList(),
    val builtins: List<String> = emptyList(),
    val lineComment: String? = null,
    val blockComment: List<String>? = null,
    val stringDelims: List<String> = emptyList(),
    val brackets: List<List<String>> = emptyList(),
    val diagnostics: List<DiagnosticRule> = emptyList(),
    val completions: List<CompletionGroup> = emptyList(),
)

/** 单行正则诊断规则：pattern 使用 Java/Kotlin 正则语法，作用于每一行。 */
@Serializable
data class DiagnosticRule(
    val id: String,
    val pattern: String,
    val message: LocalizedText = LocalizedText.EMPTY,
    val severity: String = "error",
    val fix: DiagnosticFix? = null,
    val hint: LocalizedText? = null,
)

@Serializable
data class DiagnosticFix(
    val kind: String,
    val text: String? = null,
)

@Serializable
data class CompletionGroup(
    @SerialName("when") val trigger: String = "word",
    val items: List<CompletionItem> = emptyList(),
)

@Serializable
data class CompletionItem(
    val text: String,
    val detail: LocalizedText? = null,
    val insertText: String? = null,
)
