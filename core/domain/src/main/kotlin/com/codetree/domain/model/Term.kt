package com.codetree.domain.model

import com.codetree.plugin.LocalizedText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TermType {
    @SerialName("concept") CONCEPT,
    @SerialName("syntax") SYNTAX,
    @SerialName("api") API,
    @SerialName("tool") TOOL,
}

@Serializable
enum class AliasMatchType {
    @SerialName("exact") EXACT,
    @SerialName("pinyin") PINYIN,
    @SerialName("abbr") ABBR,
    @SerialName("alias") ALIAS,
}

@Serializable
data class TermAlias(
    val text: String,
    val lang: String = "zh-CN",
    val matchType: AliasMatchType = AliasMatchType.ALIAS,
)

@Serializable
enum class ReviewStatus {
    @SerialName("draft") DRAFT,
    @SerialName("aiDraft") AI_DRAFT,
    @SerialName("reviewed") REVIEWED,
    @SerialName("verifiedExample") VERIFIED_EXAMPLE,
}

/**
 * 术语实体（docs/05 §7.1）。displayName 为本地化文本：概念类术语中英名不同
 * （「变量」/「Variable」），代码标识符类术语两语言相同（「val」）。搜索索引同时收录两种语言。
 */
@Serializable
data class Term(
    val id: String,
    val termType: TermType = TermType.CONCEPT,
    val displayName: LocalizedText = LocalizedText.EMPTY,
    val aliases: List<TermAlias> = emptyList(),
    val definitions: LocalizedText = LocalizedText.EMPTY,
    val syntaxRef: String? = null,
    val exampleIds: List<String> = emptyList(),
    val relatedTermIds: List<String> = emptyList(),
    val topicIds: List<String> = emptyList(),
    val source: String = "seed",
    val contentVersion: String = "1.0.0",
    val reviewStatus: ReviewStatus = ReviewStatus.REVIEWED,
) {
    fun displayNameIn(locale: String): String = displayName.get(locale).ifBlank { id }
}
