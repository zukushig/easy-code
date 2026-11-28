package com.codetree.data.content

import com.codetree.data.platform.deleteRecursivelyQuietly
import com.codetree.data.platform.ensureDirectory
import com.codetree.data.platform.sha256Hex
import com.codetree.data.platform.toAppError
import com.codetree.domain.common.AppError
import com.codetree.domain.common.AppResult
import com.codetree.domain.model.InstallSource
import com.codetree.domain.model.InstalledPack
import com.codetree.plugin.ContentJson
import com.codetree.plugin.CodepackValidationResult
import com.codetree.plugin.DependencyChecker
import com.codetree.plugin.DefaultCodepackValidator
import com.codetree.plugin.PackManifest
import com.codetree.plugin.SafePath
import com.codetree.plugin.ValidationIssue
import com.codetree.plugin.ValidationLimits
import com.codetree.plugin.ZipEntryInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Codepack 安装器（docs/specs/knowledge.md §7 / docs/specs/plugin-api.md §3）。
 *
 * 流水线：ZIP 结构 → 清单 → 兼容性 → 依赖 → 路径/体积（validator）→ 解包到 `staging/`
 * （边解包边算 SHA-256）→ 完整性复核 → 原子切换到 `content/packs/<packId>/<version>/`
 * → 写 `installed.json`。任一步失败清理暂存、保留旧版本可读。
 *
 * 所有写入串行化（[Mutex]）：避免并发导入 / 种子安装 / 下载互相覆盖 installed.json。
 */
class CodepackInstaller(
    private val contentRoot: File,
    private val validator: DefaultCodepackValidator = DefaultCodepackValidator(),
    private val dependencyChecker: DependencyChecker = DependencyChecker(),
    private val appVersion: String = PackManifest.APP_VERSION,
    private val limits: ValidationLimits = ValidationLimits(),
) {
    data class InstallResult(
        val pack: InstalledPack,
        val manifest: PackManifest,
        val warnings: List<ValidationIssue>,
    )

    private val packsRoot: File = File(contentRoot, "packs")
    private val stagingRoot: File = File(contentRoot, "staging")
    private val installedFile: File = File(contentRoot, "installed.json")
    private val mutex = Mutex()

    val packsDirectory: File get() = packsRoot

    fun installedDirectory(pack: InstalledPack): File = File(packsRoot, "${pack.packId}/${pack.version}")

    /** 已安装记录（文件不存在或损坏时返回空列表，不崩溃）。 */
    fun readInstalled(): List<InstalledPack> {
        if (!installedFile.isFile) return emptyList()
        return runCatching {
            ContentJson.decodeFromString(ListSerializer(InstalledPack.serializer()), installedFile.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    /** 记录存在且版本目录存在才算真正可用。 */
    fun isInstalled(packId: String): Boolean {
        val pack = readInstalled().firstOrNull { it.packId == packId } ?: return false
        return installedDirectory(pack).isDirectory
    }

    suspend fun install(archive: File, source: InstallSource): AppResult<InstallResult> =
        mutex.withLock {
            withContext(Dispatchers.IO) { installLocked(archive, source) }
        }

    /** 只解析清单，用于种子包“是否需要安装”的快速判断。 */
    suspend fun inspect(archive: File): AppResult<PackManifest> = withContext(Dispatchers.IO) {
        runCatching {
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry(MANIFEST_ENTRY)
                    ?: return@use AppResult.Failure(AppError.Internal("内容包缺少 manifest.json"))
                val raw = readTextLimited(zip, entry, limits.maxManifestBytes)
                    ?: return@use AppResult.Failure(AppError.Internal("内容包 manifest.json 读取失败"))
                val parsed = validator.parseAndValidateManifest(raw, limits)
                val manifest = parsed.manifest
                if (!parsed.ok || manifest == null) {
                    AppResult.Failure(parsed.issues.toAppError("内容包"))
                } else {
                    AppResult.Success(manifest)
                }
            }
        }.getOrElse { AppResult.Failure(it.toAppError("读取内容包")) }
    }

    suspend fun setEnabled(packId: String, enabled: Boolean): AppResult<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val list = readInstalled()
                if (list.none { it.packId == packId }) {
                    return@runCatching AppResult.Failure(AppError.Internal("内容包 $packId 未安装"))
                }
                writeInstalled(list.map { if (it.packId == packId) it.copy(enabled = enabled) else it })
                AppResult.Success(Unit)
            }.getOrElse { AppResult.Failure(it.toAppError("更新内容包状态")) }
        }
    }

    suspend fun remove(packId: String): AppResult<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val list = readInstalled()
                val target = list.firstOrNull { it.packId == packId }
                    ?: return@runCatching AppResult.Failure(AppError.Internal("内容包 $packId 未安装"))
                val dependents = reverseDependencies(packId, list)
                if (dependents.isNotEmpty()) {
                    return@runCatching AppResult.Failure(
                        AppError.Internal(
                            "内容包 $packId 被 ${dependents.joinToString(", ")} 依赖",
                            null,
                        ),
                    )
                }
                File(packsRoot, target.packId).deleteRecursivelyQuietly()
                writeInstalled(list.filterNot { it.packId == packId })
                AppResult.Success(Unit)
            }.getOrElse { AppResult.Failure(it.toAppError("删除内容包")) }
        }
    }

    /** 内容包目录占用（存储管理展示）。 */
    fun storageUsageBytes(): Long = packsRoot.takeIf { it.isDirectory }
        ?.walkTopDown()
        ?.filter { it.isFile }
        ?.sumOf { it.length() }
        ?: 0L

    // ------------------------------------------------------------------ 安装主流程

    private fun installLocked(archive: File, source: InstallSource): AppResult<InstallResult> {
        if (!archive.isFile) return AppResult.Failure(AppError.Internal("内容包文件不存在"))
        if (archive.length() <= 0L) return AppResult.Failure(AppError.Internal("内容包为空文件"))
        if (archive.length() > MAX_ARCHIVE_BYTES) {
            return AppResult.Failure(AppError.ResourceLimit("内容包体积超过 64MiB"))
        }

        val zip = runCatching { ZipFile(archive) }.getOrElse {
            return AppResult.Failure(AppError.Internal("不是有效的 ZIP 内容包：${it.message}"))
        }
        return zip.use { packageFile ->
            var staging: File? = null
            try {
                val entries = packageFile.entries().toList()
                val entryInfos = entries.map { entry ->
                    ZipEntryInfo(
                        path = entry.name,
                        sizeBytes = if (entry.size < 0) 0L else entry.size,
                        compressedSizeBytes = if (entry.compressedSize < 0) 0L else entry.compressedSize,
                        isDirectory = entry.isDirectory,
                    )
                }

                val manifestEntry = packageFile.getEntry(MANIFEST_ENTRY)
                    ?: return AppResult.Failure(AppError.Internal("内容包缺少 manifest.json"))
                val manifestRaw = readTextLimited(packageFile, manifestEntry, limits.maxManifestBytes)
                    ?: return AppResult.Failure(AppError.Internal("内容包 manifest.json 读取失败"))

                val parsed = validator.parseAndValidateManifest(manifestRaw, limits)
                val manifest = parsed.manifest
                if (!parsed.ok || manifest == null) {
                    return AppResult.Failure(parsed.issues.toAppError("内容包"))
                }

                val entryCheck = validator.validateEntries(manifest, entryInfos, limits)
                val compatCheck = validator.validateCompatibility(manifest, appVersion)
                val preIssues = entryCheck.issues + compatCheck.issues
                if (preIssues.any { it.severity == com.codetree.plugin.ValidationSeverity.ERROR }) {
                    return AppResult.Failure(preIssues.toAppError("内容包"))
                }

                val installedPacks = readInstalled()
                val dependencyCheck = dependencyChecker.check(
                    manifest,
                    installedPacks.map { DependencyChecker.InstalledPackSummary(it.packId, it.version) },
                )
                if (dependencyCheck.errors.isNotEmpty()) {
                    return AppResult.Failure(dependencyCheck.issues.toAppError("内容包"))
                }

                val declaredFiles = LinkedHashMap<String, com.codetree.plugin.PackFile>()
                for (file in manifest.files) {
                    val normalized = (SafePath.checkRelative(file.path) as? SafePath.Check.Ok)?.normalized ?: continue
                    declaredFiles[normalized] = file
                }

                stagingRoot.ensureDirectory()
                val stagingDir = File(stagingRoot, "${manifest.packId}-${manifest.version}-${System.nanoTime()}")
                stagingDir.deleteRecursivelyQuietly()
                stagingDir.ensureDirectory()
                staging = stagingDir

                val actualHashes = HashMap<String, String?>()
                var totalBytes = 0L

                // manifest.json 一并写入安装目录，供 PackContentLoader 读取包元信息。
                File(stagingDir, MANIFEST_ENTRY).writeText(manifestRaw, Charsets.UTF_8)

                for (entry in entries) {
                    if (entry.isDirectory) continue
                    val normalized = (SafePath.checkRelative(entry.name) as? SafePath.Check.Ok)?.normalized
                        ?: continue
                    if (normalized.startsWith("signatures/")) continue
                    if (normalized == MANIFEST_ENTRY) continue
                    val declared = declaredFiles[normalized] ?: continue

                    val target = File(stagingDir, normalized)
                    target.parentFile?.ensureDirectory()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var entryBytes = 0L
                    packageFile.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                entryBytes += read
                                totalBytes += read
                                if (totalBytes > limits.maxTotalBytes) {
                                    return AppResult.Failure(
                                        AppError.ResourceLimit("内容包解压总量超过 ${limits.maxTotalBytes} 字节"),
                                    )
                                }
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (entryBytes != declared.sizeBytes) {
                        return AppResult.Failure(
                            AppError.ChecksumFailed(normalized, "声明 ${declared.sizeBytes} 字节，实际 $entryBytes 字节"),
                        )
                    }
                    actualHashes[normalized] = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                }

                val postCheck = validator.validateEntries(manifest, entryInfos, actualHashes, limits)
                if (!postCheck.ok) {
                    return AppResult.Failure(postCheck.issues.toAppError("内容包"))
                }

                when (val published = publish(stagingDir, manifest)) {
                    is AppResult.Failure -> return AppResult.Failure(published.error)
                    is AppResult.Success -> Unit
                }
                staging = null

                val installedPack = InstalledPack(
                    packId = manifest.packId,
                    version = manifest.version,
                    packType = manifest.packType,
                    title = manifest.title,
                    installedAtEpochMs = System.currentTimeMillis(),
                    sizeBytes = totalBytes,
                    source = source,
                    enabled = true,
                )
                val nextList = readInstalled().filterNot { it.packId == manifest.packId } + installedPack
                writeInstalled(nextList)
                cleanupOldVersions(manifest.packId, manifest.version)

                val warnings = (preIssues + dependencyCheck.issues).filter {
                    it.severity == com.codetree.plugin.ValidationSeverity.WARNING
                }
                AppResult.Success(InstallResult(installedPack, manifest, warnings))
            } catch (cancelled: CancellationException) {
                staging?.deleteRecursivelyQuietly()
                throw cancelled
            } catch (t: Throwable) {
                staging?.deleteRecursivelyQuietly()
                AppResult.Failure(t.toAppError("安装内容包"))
            }
        }
    }

    private fun publish(stagingDir: File, manifest: PackManifest): AppResult<Unit> {
        val packDir = File(packsRoot, manifest.packId).ensureDirectory()
        val finalDir = File(packDir, manifest.version)
        val backupDir = File(packDir, "${manifest.version}.bak-${System.currentTimeMillis()}")

        var movedOld = false
        if (finalDir.exists()) {
            movedOld = finalDir.renameTo(backupDir)
            if (!movedOld) finalDir.deleteRecursivelyQuietly()
        }

        val renamed = stagingDir.renameTo(finalDir)
        if (!renamed) {
            val copied = runCatching {
                stagingDir.copyRecursively(finalDir, overwrite = true)
                stagingDir.deleteRecursivelyQuietly()
                true
            }.getOrDefault(false)
            if (!copied) {
                if (movedOld && !finalDir.exists()) backupDir.renameTo(finalDir)
                return AppResult.Failure(AppError.Internal("内容包写入失败（磁盘空间不足或权限受限）"))
            }
        }
        if (movedOld) backupDir.deleteRecursivelyQuietly()
        return AppResult.Success(Unit)
    }

    private fun cleanupOldVersions(packId: String, currentVersion: String) {
        val packDir = File(packsRoot, packId)
        packDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name != currentVersion && !dir.name.contains(".bak-")) {
                dir.deleteRecursivelyQuietly()
            }
        }
    }

    private fun reverseDependencies(packId: String, installed: List<InstalledPack>): List<String> =
        installed.filter { it.packId != packId }.mapNotNull { pack ->
            val manifest = readManifestOf(pack) ?: return@mapNotNull null
            val blocking = manifest.dependencies.filter { it.packId == packId && !it.optional }
            if (blocking.isNotEmpty()) pack.packId else null
        }

    private fun readManifestOf(pack: InstalledPack): PackManifest? {
        val file = File(installedDirectory(pack), MANIFEST_ENTRY)
        if (!file.isFile) return null
        return runCatching {
            ContentJson.decodeFromString(PackManifest.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun writeInstalled(packs: List<InstalledPack>) {
        contentRoot.ensureDirectory()
        val serializer = ListSerializer(InstalledPack.serializer())
        val text = ContentJson.encodeToString(serializer, packs)
        val tmp = File(contentRoot, "installed.json.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        val target = File(contentRoot, "installed.json")
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun readTextLimited(zip: ZipFile, entry: ZipEntry, maxBytes: Int): String? {
        if (entry.size > maxBytes) return null
        return runCatching {
            zip.getInputStream(entry).use { input ->
                val bytes = input.readBytes()
                if (bytes.size > maxBytes) null else bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
    }
}
