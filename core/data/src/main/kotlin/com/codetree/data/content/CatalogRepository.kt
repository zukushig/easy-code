package com.codetree.data.content

import android.content.Context
import com.codetree.domain.model.ContentCatalog
import com.codetree.domain.model.PackCatalogEntry
import com.codetree.plugin.ContentJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内容目录（docs/specs/knowledge.md §6.2）：读取 `filesDir/content/catalog.json`，
 * 回退到 APK `assets/seed/catalog.json`；两者都缺失时返回空目录（App 仍可展示引导）。
 *
 * MVP 内置包 `downloadUrl` 为空：UI 不得把空 URL 渲染成可下载按钮。
 */
class CatalogRepository(
    private val context: Context,
    private val contentRoot: File,
    private val assetsCatalogPath: String = "seed/catalog.json",
) {
    private val fileCatalog = File(contentRoot, "catalog.json")

    suspend fun load(): List<PackCatalogEntry> = withContext(Dispatchers.IO) {
        readFromFile() ?: readFromAssets() ?: emptyList()
    }

    /** 最近一次目录是否来自本地文件（用于诊断展示来源）。 */
    fun catalogSource(): String = when {
        fileCatalog.isFile -> "files"
        else -> "assets"
    }

    private fun readFromFile(): List<PackCatalogEntry>? =
        if (fileCatalog.isFile) parseCatalog(runCatching { fileCatalog.readText(Charsets.UTF_8) }.getOrNull()) else null

    private fun readFromAssets(): List<PackCatalogEntry>? {
        val raw = runCatching {
            context.assets.open(assetsCatalogPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return null
        return parseCatalog(raw)
    }

    private fun parseCatalog(raw: String?): List<PackCatalogEntry>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            ContentJson.decodeFromString(ContentCatalog.serializer(), raw).packs
        }.getOrNull()
    }

    /** 种子安装完成后把 assets 目录复制到私有目录，后续以私有目录为准（可被更新覆盖）。 */
    suspend fun copyAssetsCatalogToFiles(): Boolean = withContext(Dispatchers.IO) {
        if (fileCatalog.isFile) return@withContext true
        val raw = runCatching {
            context.assets.open(assetsCatalogPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return@withContext false
        runCatching {
            contentRoot.mkdirs()
            fileCatalog.writeText(raw, Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }
}
