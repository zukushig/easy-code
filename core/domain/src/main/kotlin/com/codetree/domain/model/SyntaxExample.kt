package com.codetree.domain.model

import com.codetree.plugin.LocalizedText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExampleVerification {
    @SerialName("unverified") UNVERIFIED,
    @SerialName("static-checked") STATIC_CHECKED,
    @SerialName("execution-verified") EXECUTION_VERIFIED,
}

/** 示例内按字符区间标注中英解释，避免按行号脆弱绑定（docs/05 §7.2）。 */
@Serializable
data class ExampleAnnotation(
    val start: Int,
    val end: Int,
    val note: LocalizedText = LocalizedText.EMPTY,
)

@Serializable
data class SyntaxExample(
    val id: String,
    val language: String,
    val languageVersion: String = "",
    val title: LocalizedText = LocalizedText.EMPTY,
    val explanation: LocalizedText = LocalizedText.EMPTY,
    val code: String = "",
    val annotations: List<ExampleAnnotation> = emptyList(),
    val expectedOutput: String? = null,
    val runtimeId: String? = null,
    val verificationStatus: ExampleVerification = ExampleVerification.STATIC_CHECKED,
) {
    val isRunnable: Boolean get() = runtimeId != null
}
