package com.codetree.domain.model

import com.codetree.plugin.LocalizedText
import com.codetree.plugin.PackDependency
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 安装来源：内置种子 / 用户导入 / 远程下载。 */
@Serializable
enum class InstallSource {
    @SerialName("bundled") BUNDLED,
    @SerialName("imported") IMPORTED,
    @SerialName("downloaded") DOWNLOADED,
}

/** 已安装内容包。显示名可随内容版本变化，关联一律使用 packId。 */
@Serializable
data class InstalledPack(
    val packId: String,
    val version: String,
    val packType: String = "knowledge",
    val title: LocalizedText = LocalizedText.EMPTY,
    val installedAtEpochMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val source: InstallSource = InstallSource.BUNDLED,
    val enabled: Boolean = true,
)

/** 远程目录条目（docs/05 §9）：知识库选择性下载的数据契约。 */
@Serializable
data class PackCatalogEntry(
    val packId: String,
    val version: String,
    val title: LocalizedText = LocalizedText.EMPTY,
    val locales: List<String> = listOf("zh-CN", "en"),
    val domain: String = "",
    val difficulty: String = "beginner",
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val downloadUrl: String = "",
    val dependencies: List<PackDependency> = emptyList(),
    val minAppVersion: String = "0.1.0",
    val publishedAt: String = "",
)

@Serializable
data class ContentCatalog(
    val schemaVersion: Int = 1,
    val generatedAt: String = "",
    val packs: List<PackCatalogEntry> = emptyList(),
)
