package com.codetree.feature.library.library

import androidx.lifecycle.ViewModel
import com.codetree.domain.common.AppError
import com.codetree.domain.common.AppResult
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.LibraryRepository
import com.codetree.domain.model.InstalledPack
import com.codetree.domain.model.PackCatalogEntry
import com.codetree.feature.library.model.CatalogItemUi
import com.codetree.feature.library.model.ImportResultUi
import com.codetree.feature.library.model.InstalledPackUi
import com.codetree.feature.library.model.LibraryUiState
import com.codetree.feature.library.model.PackStatus
import com.codetree.plugin.SemVer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 知识库 / 插件管理 ViewModel。
 *
 * 边界（docs/specs/plugin-api.md §6、PRD PLG/PKG）：
 * - 下载、导入、卸载全部通过 [LibraryRepository]；UI 不直接写文件；
 * - 失败展示 [AppError.code] + [AppError.userHint]（校验明细受契约限制，见报告简化说明）；
 * - 被其它已安装包依赖时阻止卸载并展示影响清单。
 */
class LibraryViewModel(
    private val library: LibraryRepository,
    private val content: ContentRepository,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null
    private var importedCatalog: List<PackCatalogEntry> = emptyList()

    init {
        load()
        observeInstalled()
    }

    // ---------------- 加载 ----------------

    fun load() {
        scope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            try {
                val catalog = content.catalog()
                importedCatalog = catalog
                val installed = runCatching { library.installed() }.getOrDefault(emptyList())
                val storage = runCatching { library.storageUsageBytes() }.getOrDefault(0L)
                _state.update { state ->
                    state.copy(
                        catalog = buildCatalog(catalog, installed, state.catalog),
                        installed = buildInstalled(installed, catalog),
                        storageUsageBytes = storage,
                        loading = false,
                        loadError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _state.update {
                    it.copy(loading = false, loadError = AppError.Internal(t::class.simpleName ?: "加载失败", t))
                }
            }
        }
    }

    private fun observeInstalled() {
        scope.launch {
            try {
                library.observeInstalled().collect { installed ->
                    val catalog = importedCatalog
                    _state.update { state ->
                        state.copy(
                            installed = buildInstalled(installed, catalog),
                            catalog = mergeCatalogInstalled(state.catalog, installed),
                            loading = if (state.catalog.isEmpty() && installed.isEmpty()) state.loading else false,
                        )
                    }
                    refreshStorage()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                // 已安装列表流不可用时保留上次结果，不覆盖页面
            }
        }
    }

    private fun refreshStorage() {
        scope.launch {
            val storage = runCatching { library.storageUsageBytes() }.getOrNull() ?: return@launch
            _state.update { it.copy(storageUsageBytes = storage) }
        }
    }

    private suspend fun refreshAfterMutation() {
        val installed = runCatching { library.installed() }.getOrDefault(emptyList())
        val storage = runCatching { library.storageUsageBytes() }.getOrDefault(_state.value.storageUsageBytes)
        val catalog = importedCatalog
        _state.update { state ->
            state.copy(
                installed = buildInstalled(installed, catalog),
                catalog = mergeCatalogInstalled(state.catalog, installed),
                storageUsageBytes = storage,
            )
        }
    }

    // ---------------- 下载 / 取消 ----------------

    fun download(entry: PackCatalogEntry) {
        if (_state.value.downloadingPackId != null) {
            _state.update { it.copy(notice = "已有下载任务进行中，请先取消或等待完成") }
            return
        }
        if (_state.value.importing) {
            _state.update { it.copy(notice = "导入进行中，请稍后再下载") }
            return
        }
        val title = entry.title.primary.ifBlank { entry.packId }
        downloadJob = scope.launch {
            _state.update { it.copy(downloadingPackId = entry.packId, notice = null) }
            markDownloading(entry.packId, 0L, 0L)
            try {
                val result = library.download(entry) { bytesRead, contentLength ->
                    markDownloading(entry.packId, bytesRead, contentLength)
                }
                when (result) {
                    is AppResult.Success -> {
                        _state.update { it.copy(downloadingPackId = null, notice = "已安装：$title") }
                        refreshAfterMutation()
                    }
                    is AppResult.Failure -> {
                        markFailed(entry.packId, result.error.userHint)
                        _state.update {
                            it.copy(downloadingPackId = null, notice = "${result.error.message}：${result.error.userHint}")
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                markFailed(entry.packId, "下载异常，请重试")
                _state.update { it.copy(downloadingPackId = null, notice = "下载异常，请重试") }
            }
        }
    }

    fun cancelDownload() {
        val packId = _state.value.downloadingPackId ?: return
        downloadJob?.cancel()
        downloadJob = null
        _state.update { state ->
            state.copy(
                downloadingPackId = null,
                notice = "已请求取消下载；未完成文件由安装流程清理",
                catalog = state.catalog.map { item ->
                    if (item.packId == packId) {
                        item.copy(
                            status = if (item.installed != null) PackStatus.INSTALLED else PackStatus.NOT_INSTALLED,
                            progress = null,
                            bytesRead = 0L,
                            contentLength = 0L,
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun markDownloading(packId: String, bytesRead: Long, contentLength: Long) {
        _state.update { state ->
            state.copy(
                catalog = state.catalog.map { item ->
                    if (item.packId != packId) {
                        item
                    } else {
                        item.copy(
                            status = PackStatus.DOWNLOADING,
                            bytesRead = bytesRead,
                            contentLength = contentLength,
                            progress = if (contentLength > 0L) {
                                (bytesRead.toDouble() / contentLength.toDouble()).toFloat().coerceIn(0f, 1f)
                            } else {
                                null
                            },
                            errorHint = null,
                        )
                    }
                },
            )
        }
    }

    private fun markFailed(packId: String, hint: String) {
        _state.update { state ->
            state.copy(
                catalog = state.catalog.map { item ->
                    if (item.packId == packId) {
                        item.copy(status = PackStatus.FAILED, progress = null, errorHint = hint)
                    } else {
                        item
                    }
                },
            )
        }
    }

    // ---------------- 导入 ----------------

    fun importCodepack(bytes: ByteArray, fileName: String) {
        if (_state.value.importing) return
        scope.launch {
            _state.update { it.copy(importing = true, importResult = null, notice = null) }
            try {
                val result = library.importCodepack(bytes, fileName)
                when (result) {
                    is AppResult.Success -> {
                        val pack = result.value
                        _state.update {
                            it.copy(
                                importing = false,
                                importResult = ImportResultUi(
                                    success = true,
                                    fileName = fileName,
                                    packId = pack.packId,
                                    version = pack.version,
                                    title = pack.title.primary.ifBlank { pack.packId },
                                    sourceLabel = sourceLabelOf(pack),
                                    message = "已校验并安装",
                                ),
                                notice = "已导入：${pack.title.primary.ifBlank { pack.packId }}",
                            )
                        }
                        refreshAfterMutation()
                    }
                    is AppResult.Failure -> _state.update {
                        it.copy(
                            importing = false,
                            importResult = importFailure(fileName, result.error),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        importing = false,
                        importResult = importFailure(fileName, AppError.Internal(t::class.simpleName ?: "导入异常", t)),
                    )
                }
            }
        }
    }

    /** SAF 读取失败（文件过大 / 无法读取）时复用同一结果卡片。 */
    fun importReadFailed(fileName: String, message: String, hint: String) {
        _state.update {
            it.copy(
                importResult = ImportResultUi(
                    success = false,
                    fileName = fileName,
                    message = message,
                    errorCode = "import.read_failed",
                    errorHint = hint,
                ),
                importing = false,
            )
        }
    }

    fun dismissImportResult() {
        _state.update { it.copy(importResult = null) }
    }

    private fun importFailure(fileName: String, error: AppError): ImportResultUi = ImportResultUi(
        success = false,
        fileName = fileName,
        message = error.message,
        errorCode = error.code,
        errorHint = error.userHint,
    )

    // ---------------- 启停 / 卸载 ----------------

    fun setEnabled(packId: String, enabled: Boolean) {
        scope.launch {
            val result = runCatching { library.setEnabled(packId, enabled) }
                .getOrElse { AppResult.Failure(AppError.Internal(it::class.simpleName ?: "启停失败", it)) }
            when (result) {
                is AppResult.Success -> {
                    _state.update { state ->
                        state.copy(
                            installed = state.installed.map { item ->
                                if (item.packId == packId) item.copy(pack = item.pack.copy(enabled = enabled)) else item
                            },
                            notice = if (enabled) "已启用" else "已停用",
                        )
                    }
                    refreshAfterMutation()
                }
                is AppResult.Failure -> _state.update { it.copy(notice = result.error.userHint) }
            }
        }
    }

    fun requestRemove(packId: String) {
        val target = _state.value.installed.firstOrNull { it.packId == packId } ?: return
        _state.update {
            it.copy(
                pendingDelete = target,
                notice = if (target.hasDependents) "以下已安装包依赖它，不能直接卸载" else null,
            )
        }
    }

    fun dismissPendingDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmRemove(target: InstalledPackUi) {
        scope.launch {
            val result = runCatching { library.remove(target.packId) }
                .getOrElse { AppResult.Failure(AppError.Internal(it::class.simpleName ?: "卸载失败", it)) }
            when (result) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(pendingDelete = null, notice = "已卸载：${target.title}")
                    }
                    refreshAfterMutation()
                }
                is AppResult.Failure -> _state.update {
                    it.copy(pendingDelete = null, notice = "${result.error.message}：${result.error.userHint}")
                }
            }
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    // ---------------- 组装 ----------------

    private fun buildCatalog(
        catalog: List<PackCatalogEntry>,
        installed: List<InstalledPack>,
        previous: List<CatalogItemUi>,
    ): List<CatalogItemUi> {
        val installedById = installed.associateBy { it.packId }
        return catalog.map { entry ->
            val pack = installedById[entry.packId]
            val previousItem = previous.firstOrNull { it.packId == entry.packId }
            val baseStatus = when {
                pack == null -> PackStatus.NOT_INSTALLED
                isNewerVersion(entry.version, pack.version) -> PackStatus.UPDATE_AVAILABLE
                else -> PackStatus.INSTALLED
            }
            CatalogItemUi(
                entry = entry,
                installed = pack,
                status = if (previousItem?.status == PackStatus.DOWNLOADING) PackStatus.DOWNLOADING else baseStatus,
                progress = if (previousItem?.status == PackStatus.DOWNLOADING) previousItem.progress else null,
                bytesRead = if (previousItem?.status == PackStatus.DOWNLOADING) previousItem.bytesRead else 0L,
                contentLength = if (previousItem?.status == PackStatus.DOWNLOADING) previousItem.contentLength else 0L,
                errorHint = previousItem?.errorHint.takeIf { baseStatus == PackStatus.NOT_INSTALLED },
            )
        }
    }

    private fun mergeCatalogInstalled(
        catalog: List<CatalogItemUi>,
        installed: List<InstalledPack>,
    ): List<CatalogItemUi> {
        val installedById = installed.associateBy { it.packId }
        return catalog.map { item ->
            if (item.status == PackStatus.DOWNLOADING) return@map item
            val pack = installedById[item.packId]
            val status = when {
                pack == null -> PackStatus.NOT_INSTALLED
                isNewerVersion(item.entry.version, pack.version) -> PackStatus.UPDATE_AVAILABLE
                else -> PackStatus.INSTALLED
            }
            item.copy(installed = pack, status = status, progress = null)
        }
    }

    private fun buildInstalled(
        installed: List<InstalledPack>,
        catalog: List<PackCatalogEntry>,
    ): List<InstalledPackUi> = installed
        .sortedBy { it.title.primary.ifBlank { it.packId } }
        .map { pack ->
            InstalledPackUi(pack = pack, dependents = dependentsOf(pack.packId, installed, catalog))
        }

    private fun dependentsOf(
        packId: String,
        installed: List<InstalledPack>,
        catalog: List<PackCatalogEntry>,
    ): List<String> {
        val dependents = LinkedHashSet<String>()
        installed.forEach { candidate ->
            if (candidate.packId == packId) return@forEach
            val manifestDependencies = content.pack(candidate.packId)?.manifest?.dependencies.orEmpty().map { it.packId }
            val catalogDependencies = catalog.firstOrNull { it.packId == candidate.packId }
                ?.dependencies.orEmpty().map { it.packId }
            if (packId in manifestDependencies || packId in catalogDependencies) {
                dependents += candidate.title.primary.ifBlank { candidate.packId }
            }
        }
        return dependents.toList()
    }

    private fun isNewerVersion(candidate: String, installed: String): Boolean {
        val candidateVersion = SemVer.parse(candidate) ?: return false
        val installedVersion = SemVer.parse(installed) ?: return false
        return candidateVersion > installedVersion
    }

    private fun sourceLabelOf(pack: InstalledPack): String = when (pack.source) {
        com.codetree.domain.model.InstallSource.BUNDLED -> "内置"
        com.codetree.domain.model.InstallSource.IMPORTED -> "导入"
        com.codetree.domain.model.InstallSource.DOWNLOADED -> "下载"
    }

    override fun onCleared() {
        downloadJob?.cancel()
        scope.cancel()
    }
}
