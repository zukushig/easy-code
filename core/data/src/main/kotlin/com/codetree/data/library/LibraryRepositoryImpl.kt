package com.codetree.data.library

import android.content.Context
import com.codetree.data.content.CatalogRepository
import com.codetree.data.content.CodepackInstaller
import com.codetree.data.content.ContentRepositoryImpl
import com.codetree.data.platform.deleteRecursivelyQuietly
import com.codetree.data.platform.sha256Hex
import com.codetree.data.platform.toAppError
import com.codetree.domain.common.AppError
import com.codetree.domain.common.AppResult
import com.codetree.domain.content.LibraryRepository
import com.codetree.domain.model.InstallSource
import com.codetree.domain.model.InstalledPack
import com.codetree.domain.model.PackCatalogEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 知识库 / 插件管理实现（docs/specs/knowledge.md §7、docs/specs/plugin-api.md §6）：
 *
 * - `importCodepack`：小于 64MiB 的字节数组写临时文件 → [CodepackInstaller] 全流程校验安装
 *   → 重建内存索引 → 清理临时文件；
 * - `download`：`https` 流式下载（可取消协程）到 staging → `sha256` 校验 → 安装；
 *   内置包目录条目使用相对文件名（如 `core.python-1.0.0.codepack`），从 assets 读取而不发网络请求；
 * - `remove` / `setEnabled`：操作 installed.json 后强制重建索引，保证无孤立引用；
 * - `storageUsageBytes`：内容区占用，供存储管理展示。
 */
class LibraryRepositoryImpl(
    private val context: Context,
    private val installer: CodepackInstaller,
    private val contentRepository: ContentRepositoryImpl,
    private val catalogRepository: CatalogRepository? = null,
    private val httpClient: OkHttpClient = downloadClient(),
) : LibraryRepository {

    private val installedState = MutableStateFlow(installer.readInstalled())

    override fun observeInstalled(): Flow<List<InstalledPack>> = installedState.asStateFlow()

    override suspend fun installed(): List<InstalledPack> = withContext(Dispatchers.IO) {
        refreshInstalled()
        installedState.value
    }

    override suspend fun importCodepack(bytes: ByteArray, fileName: String): AppResult<InstalledPack> =
        withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext AppResult.Failure(AppError.Internal("导入文件为空"))
            if (bytes.size > MAX_ARCHIVE_BYTES) {
                return@withContext AppResult.Failure(AppError.ResourceLimit("内容包体积超过 64MiB"))
            }
            val staging = File(context.cacheDir, "import-${UUID.randomUUID()}.codepack")
            try {
                staging.writeBytes(bytes)
                installFromArchive(staging, InstallSource.IMPORTED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                AppResult.Failure(t.toAppError("导入内容包"))
            } finally {
                staging.deleteRecursivelyQuietly()
            }
        }

    override suspend fun download(
        entry: PackCatalogEntry,
        onProgress: (bytesRead: Long, contentLength: Long) -> Unit,
    ): AppResult<InstalledPack> = withContext(Dispatchers.IO) {
        val url = entry.downloadUrl.trim()
        if (url.isEmpty()) {
            return@withContext AppResult.Failure(
                AppError.NotConfigured("下载地址", "该内容包未提供下载地址；内置内容包已随应用分发"),
            )
        }
        val staging = File(context.cacheDir, "download-${UUID.randomUUID()}.codepack")
        try {
            val fetched = if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                downloadRemote(url, entry, staging, onProgress)
            } else {
                copyBundledAsset(url, staging, onProgress)
            }
            when (fetched) {
                is AppResult.Failure -> fetched
                is AppResult.Success -> installFromArchive(staging, InstallSource.DOWNLOADED)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            AppResult.Failure(t.toAppError("下载内容包"))
        } finally {
            staging.deleteRecursivelyQuietly()
        }
    }

    override suspend fun remove(packId: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        when (val result = installer.remove(packId)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                contentRepository.reload()
                refreshInstalled()
                AppResult.Success(Unit)
            }
        }
    }

    override suspend fun setEnabled(packId: String, enabled: Boolean): AppResult<Unit> = withContext(Dispatchers.IO) {
        when (val result = installer.setEnabled(packId, enabled)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                contentRepository.reload()
                refreshInstalled()
                AppResult.Success(Unit)
            }
        }
    }

    override suspend fun storageUsageBytes(): Long = withContext(Dispatchers.IO) {
        installer.storageUsageBytes()
    }

    // ------------------------------------------------------------------ 内部实现

    private suspend fun installFromArchive(archive: File, source: InstallSource): AppResult<InstalledPack> {
        val result = installer.install(archive, source)
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                contentRepository.reload()
                refreshInstalled()
                AppResult.Success(result.value.pack)
            }
        }
    }

    private fun refreshInstalled() {
        installedState.value = installer.readInstalled()
    }

    /** 内置包：目录条目里 downloadUrl 是相对文件名，直接从 APK assets `seed/` 读取。 */
    private fun copyBundledAsset(
        url: String,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ): AppResult<Unit> {
        val candidates = buildList {
            add(if (url.startsWith("seed/")) url else "seed/$url")
            add(url)
        }
        var source: java.io.InputStream? = null
        for (candidate in candidates) {
            source = runCatching { context.assets.open(candidate) }.getOrNull()
            if (source != null) break
        }
        val input = source ?: return AppResult.Failure(
            AppError.Internal("找不到内置内容包 $url（assets/seed 未同步）"),
        )
        return try {
            var total = 0L
            input.use { stream ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_ARCHIVE_BYTES) {
                            return AppResult.Failure(AppError.ResourceLimit("内容包体积超过 64MiB"))
                        }
                        output.write(buffer, 0, read)
                        onProgress(total, total)
                    }
                }
            }
            onProgress(total, total)
            AppResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            AppResult.Failure(t.toAppError("读取内置内容包"))
        }
    }

    private suspend fun downloadRemote(
        url: String,
        entry: PackCatalogEntry,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ): AppResult<Unit> {
        if (!url.startsWith("https://", ignoreCase = true) && !isLocalDebugUrl(url)) {
            return AppResult.Failure(AppError.NotConfigured("下载地址", "下载地址必须使用 https://"))
        }
        val call = httpClient.newCall(Request.Builder().url(url).get().build())
        val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        val cancelHandle = job?.invokeOnCompletion { call.cancel() }
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val mapped = when (response.code) {
                        401, 403 -> AppError.Unauthorized()
                        404 -> AppError.Incompatible("内容包下载", "（HTTP 404）")
                        408 -> AppError.Timeout("内容包下载")
                        429 -> AppError.RateLimited(response.header("Retry-After")?.toLongOrNull())
                        else -> AppError.Internal("下载失败（HTTP ${response.code}）")
                    }
                    return AppResult.Failure(mapped)
                }
                val body = response.body ?: return AppResult.Failure(AppError.Internal("下载响应为空"))
                val contentLength = body.contentLength()
                var total = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_ARCHIVE_BYTES) {
                                return AppResult.Failure(AppError.ResourceLimit("内容包体积超过 64MiB"))
                            }
                            output.write(buffer, 0, read)
                            onProgress(total, if (contentLength > 0) contentLength else total)
                        }
                    }
                }
                onProgress(total, if (contentLength > 0) contentLength else total)
                if (entry.sizeBytes > 0 && total != entry.sizeBytes) {
                    return AppResult.Failure(AppError.ChecksumFailed(entry.packId, "大小与目录不一致"))
                }
                if (entry.sha256.isNotBlank() && !entry.sha256.equals(target.sha256Hex(), ignoreCase = true)) {
                    return AppResult.Failure(AppError.ChecksumFailed(entry.packId))
                }
                AppResult.Success(Unit)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            AppResult.Failure(t.toAppError("下载内容包"))
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun isLocalDebugUrl(url: String): Boolean =
        url.startsWith("http://127.0.0.1", true) || url.startsWith("http://localhost", true)

    companion object {
        const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024

        fun downloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
