package com.codetree.plugin

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * 本地化文本（docs/05 §3.2）：JSON 形态为 {"zh-CN": "...", "en": "..."}。
 * 回退顺序：请求语言 → 语言主标签 → zh-CN → en → 第一个可用值。
 * 代码关键字、函数名不翻译；本类型只承载解释性文本。
 */
@Serializable(with = LocalizedTextSerializer::class)
data class LocalizedText(val values: Map<String, String> = emptyMap()) {

    fun get(locale: String): String {
        val exact = values[locale]
        if (!exact.isNullOrBlank()) return exact
        val primary = locale.substringBefore('-').lowercase()
        values.entries.firstOrNull { it.key.substringBefore('-').lowercase() == primary }?.let {
            if (it.value.isNotBlank()) return it.value
        }
        values[FALLBACK_LOCALE]?.takeIf { it.isNotBlank() }?.let { return it }
        values[SECONDARY_LOCALE]?.takeIf { it.isNotBlank() }?.let { return it }
        return values.values.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    val isEmpty: Boolean get() = values.values.all { it.isBlank() }
    val primary: String get() = get(FALLBACK_LOCALE)
    fun nonEmptyLocales(): List<String> = values.filterValues { it.isNotBlank() }.keys.toList()

    companion object {
        const val FALLBACK_LOCALE = "zh-CN"
        const val SECONDARY_LOCALE = "en"
        val SUPPORTED_LOCALES = listOf("zh-CN", "en")
        val EMPTY = LocalizedText()

        fun of(zh: String, en: String): LocalizedText =
            LocalizedText(mapOf(FALLBACK_LOCALE to zh, SECONDARY_LOCALE to en))
    }
}

private val StringStringMapSerializer: KSerializer<Map<String, String>> =
    MapSerializer(String.serializer(), String.serializer())

object LocalizedTextSerializer : KSerializer<LocalizedText> {
    override val descriptor: SerialDescriptor get() = StringStringMapSerializer.descriptor
    override fun serialize(encoder: Encoder, value: LocalizedText) =
        StringStringMapSerializer.serialize(encoder, value.values)

    override fun deserialize(decoder: Decoder): LocalizedText =
        LocalizedText(StringStringMapSerializer.deserialize(decoder))
}

/** 本地化字符串列表：JSON 形态为 {"zh-CN": ["..."], "en": ["..."]}。 */
@Serializable(with = LocalizedListSerializer::class)
data class LocalizedList(val values: Map<String, List<String>> = emptyMap()) {
    fun get(locale: String): List<String> =
        values[locale] ?: values[LOCALE_PRIMARY] ?: values[LocalizedText.SECONDARY_LOCALE]
        ?: values[LocalizedText.FALLBACK_LOCALE] ?: values.values.firstOrNull().orEmpty()

    val isEmpty: Boolean get() = values.values.all { it.isEmpty() }

    companion object {
        const val LOCALE_PRIMARY = "zh-CN"
        val EMPTY = LocalizedList()

        fun of(zh: List<String>, en: List<String>): LocalizedList =
            LocalizedList(mapOf(LOCALE_PRIMARY to zh, LocalizedText.SECONDARY_LOCALE to en))
    }
}

private val StringListMapSerializer: KSerializer<Map<String, List<String>>> =
    MapSerializer(String.serializer(), ListSerializer(String.serializer()))

object LocalizedListSerializer : KSerializer<LocalizedList> {
    override val descriptor: SerialDescriptor get() = StringListMapSerializer.descriptor
    override fun serialize(encoder: Encoder, value: LocalizedList) =
        StringListMapSerializer.serialize(encoder, value.values)

    override fun deserialize(decoder: Decoder): LocalizedList =
        LocalizedList(StringListMapSerializer.deserialize(decoder))
}

/** 全局 Json 配置：未知字段容忍（旧应用读新内容不崩溃），但必填字段缺失即失败。 */
val ContentJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
    encodeDefaults = true
}
