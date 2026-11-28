package com.codetree.feature.library.model

import com.codetree.domain.common.AppError
import com.codetree.domain.model.InstalledPack
import com.codetree.domain.model.PackCatalogEntry

/** 目录条目的安装状态（MVP：下载中支持取消，不实现暂停/恢复）。 */
enum class PackStatus { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE, DOWNLOADING, FAILED }

data class CatalogItemUi(
    val entry: PackCatalogEntry,
    val installed: InstalledPack? = null,
    val status: PackStatus = PackStatus.NOT_INSTALLED,
    val progress: Float? = null,
    val bytesRead: Long = 0L,
    val contentLength: Long = 0L,
    val errorHint: String? = null,
) {
    val packId: String get() = entry.packId
    val title: String get() = entry.title.primary.ifBlank { entry.packId }
    val domainLabel: String get() = entry.domain.ifBlank { "未分类" }
    val difficultyLabel: String get() = when (entry.difficulty.lowercase()) {
        "beginner" -> "入门"
        "intermediate" -> "进阶"
        "advanced" -> "高级"
        else -> entry.difficulty
    }
    val dependencyIds: List<String> get() = entry.dependencies.map { it.packId }
    val isInstalled: Boolean get() = installed != null
}

/** 已安装包 + 被依赖关系（卸载前提示影响范围）。 */
data class InstalledPackUi(
    val pack: InstalledPack,
    val dependents: List<String> = emptyList(),
) {
    val packId: String get() = pack.packId
    val title: String get() = pack.title.primary.ifBlank { pack.packId }
    val sourceLabel: String get() = when (pack.source) {
        com.codetree.domain.model.InstallSource.BUNDLED -> "内置"
        com.codetree.domain.model.InstallSource.IMPORTED -> "导入"
        com.codetree.domain.model.InstallSource.DOWNLOADED -> "下载"
    }
    val hasDependents: Boolean get() = dependents.isNotEmpty()
}

/** `.codepack` 导入结果（成功显示包信息，失败显示错误码与下一步）。 */
data class ImportResultUi(
    val success: Boolean,
    val fileName: String = "",
    val packId: String = "",
    val version: String = "",
    val title: String = "",
    val sourceLabel: String = "",
    val message: String = "",
    val errorCode: String? = null,
    val errorHint: String? = null,
)

data class LibraryUiState(
    val catalog: List<CatalogItemUi> = emptyList(),
    val installed: List<InstalledPackUi> = emptyList(),
    val storageUsageBytes: Long = 0L,
    val loading: Boolean = true,
    val loadError: AppError? = null,
    val importing: Boolean = false,
    val importResult: ImportResultUi? = null,
    val importReading: Boolean = false,
    val downloadingPackId: String? = null,
    val pendingDelete: InstalledPackUi? = null,
    val notice: String? = null,
) {
    val installedCount: Int get() = installed.size
    val catalogCount: Int get() = catalog.size
    val isEmpty: Boolean get() = catalog.isEmpty() && installed.isEmpty() && !loading && loadError == null
    val installedSizeBytes: Long get() = installed.sumOf { it.pack.sizeBytes }
    val busy: Boolean get() = importing || downloadingPackId != null
}
