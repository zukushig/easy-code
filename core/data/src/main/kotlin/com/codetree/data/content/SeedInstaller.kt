package com.codetree.data.content

import android.content.Context
import com.codetree.data.platform.toAppError
import com.codetree.domain.common.AppResult
import com.codetree.domain.model.InstallSource
import com.codetree.domain.model.InstalledPack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 首次启动种子内容安装（docs/specs/module-layout.md §5）：
 *
 * 1. 把 APK `assets/seed` 目录下的 `.codepack` 复制到缓存临时文件；
 * 2. 用 [CodepackInstaller] 走完整校验安装流程（清单/路径/哈希/兼容性）写私有目录；
 * 3. assets 不存在或为空时**安全降级**：返回空报告，不崩溃，App 仍展示引导；
 * 4. 同时把 `assets/seed/catalog.json` 复制到 `filesDir/content/catalog.json` 作为目录。
 *
 * 已安装同版本的内置包会跳过（避免每次启动重复解包），版本升级时自动替换。
 */
class SeedInstaller(
    private val context: Context,
    private val installer: CodepackInstaller,
    private val catalogRepository: CatalogRepository? = null,
    private val assetsDirName: String = "seed",
) {
    data class Report(
        val installedPacks: List<InstalledPack>,
        val warnings: List<String> = emptyList(),
        val assetsAvailable: Boolean = false,
    )

    suspend fun ensureInstalled(): AppResult<Report> = withContext(Dispatchers.IO) {
        try {
            catalogRepository?.copyAssetsCatalogToFiles()
            val names = listSeedAssets()
            if (names.isEmpty()) {
                return@withContext AppResult.Success(Report(installedPacks = emptyList(), assetsAvailable = false))
            }

            val warnings = mutableListOf<String>()
            val newlyInstalled = mutableListOf<InstalledPack>()

            for (name in names) {
                val temp = File(context.cacheDir, "seed-$name")
                try {
                    val copied = runCatching {
                        context.assets.open("$assetsDirName/$name").use { input ->
                            temp.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    if (copied.isFailure) {
                        warnings += "种子包 $name 读取失败：${copied.exceptionOrNull()?.message.orEmpty()}"
                        continue
                    }

                    when (val inspected = installer.inspect(temp)) {
                        is AppResult.Failure -> {
                            warnings += "种子包 $name 校验失败：${inspected.error.message}"
                        }

                        is AppResult.Success -> {
                            val manifest = inspected.value
                            val existing = installer.readInstalled().firstOrNull { it.packId == manifest.packId }
                            val upToDate = existing != null &&
                                existing.version == manifest.version &&
                                installer.isInstalled(manifest.packId)
                            if (upToDate) continue

                            when (val installed = installer.install(temp, InstallSource.BUNDLED)) {
                                is AppResult.Success -> newlyInstalled += installed.value.pack
                                is AppResult.Failure -> warnings +=
                                    "种子包 $name 安装失败：${installed.error.userHint}"
                            }
                        }
                    }
                } finally {
                    runCatching { temp.delete() }
                }
            }

            AppResult.Success(
                Report(
                    installedPacks = newlyInstalled,
                    warnings = warnings,
                    assetsAvailable = true,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            AppResult.Failure(t.toAppError("安装内置内容"))
        }
    }

    /** assets 里的 codepack 清单；目录缺失时返回空列表（降级为空内容）。 */
    fun listSeedAssets(): List<String> = runCatching {
        context.assets.list(assetsDirName)
            ?.filter { it.endsWith(".codepack", ignoreCase = true) }
            ?.sorted()
            .orEmpty()
    }.getOrDefault(emptyList())
}
