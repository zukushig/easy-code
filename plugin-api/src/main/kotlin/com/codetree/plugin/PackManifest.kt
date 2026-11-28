package com.codetree.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 包类型（docs/05 §2）：P0 只允许知识包与声明式插件两种。 */
enum class PackType {
    @SerialName("knowledge") KNOWLEDGE,
    @SerialName("plugin") PLUGIN,
}

/** 依赖声明：版本范围使用简化的 SemVer 区间表达式，如 ">=1.0.0 <2.0.0"。 */
@Serializable
data class PackDependency(
    val packId: String,
    val versionRange: String = ">=0.1.0",
    val optional: Boolean = false,
)

/** 内容文件清单项：安装前必须逐一校验存在性、大小与 SHA-256。 */
@Serializable
data class PackFile(
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
)

/**
 * Codepack 清单（docs/05 §5）。
 * 源包（作者工作区）允许 files 为空数组，由打包工具注入真实哈希；
 * 分发产物 .codepack 内的 manifest.files 必须完整且与实际内容一致。
 */
@Serializable
data class PackManifest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val packId: String,
    val packType: String,
    val version: String,
    val title: LocalizedText = LocalizedText.EMPTY,
    val defaultLocale: String = "zh-CN",
    val locales: List<String> = emptyList(),
    val minAppVersion: String = "0.1.0",
    val pluginApiVersion: Int = PLUGIN_API_VERSION,
    val entry: String = "content/topics.json",
    val permissions: List<String> = emptyList(),
    val contributions: List<PluginContribution> = emptyList(),
    val dependencies: List<PackDependency> = emptyList(),
    val files: List<PackFile> = emptyList(),
) {
    val type: PackType get() = if (packType == "plugin") PackType.PLUGIN else PackType.KNOWLEDGE

    companion object {
        const val SCHEMA_VERSION = 1
        const val PLUGIN_API_VERSION = 1
        const val APP_VERSION = "0.1.0"
    }
}

/** 插件贡献类型（docs/05 §5.2）：P0 允许 content / dictionary / syntax / theme。 */
@Serializable
data class PluginContribution(
    val type: String,
    val path: String,
) {
    companion object {
        val ALLOWED_TYPES = listOf("content", "dictionary", "syntax", "theme")
        val REJECTED_TYPES = listOf("runtime", "native", "executable")
    }
}
