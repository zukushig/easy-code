package com.codetree.app.di

import android.content.Context
import com.codetree.data.ai.OpenAiCompatibleProvider
import com.codetree.data.content.CatalogRepository
import com.codetree.data.content.CodepackInstaller
import com.codetree.data.content.ContentRepositoryImpl
import com.codetree.data.content.SeedInstaller
import com.codetree.data.library.LibraryRepositoryImpl
import com.codetree.data.progress.ProgressRepositoryImpl
import com.codetree.data.settings.AndroidKeystoreSecretStore
import com.codetree.data.settings.SettingsRepositoryImpl
import com.codetree.domain.ai.AiProvider
import com.codetree.domain.content.LibraryRepository
import com.codetree.domain.content.ProgressRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.plugin.DefaultCodepackValidator
import com.codetree.runtime.api.RuntimeRegistry
import com.codetree.runtime.safeshell.InMemoryRuntimeRegistry
import com.codetree.runtime.safeshell.SafeShellRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 手写依赖容器（ADR-009 §4）：`Application` 创建并持有，Activity / NavHost / feature 通过参数注入。
 *
 * 组合根职责：
 * - 私有目录布局：`filesDir/content`（安装的 codepack）、`filesDir/terminal/workspace`（安全终端沙箱）、
 *   `filesDir/ai/sessions`（AI 会话存储，由 feature:assistant 使用）；
 * - 创建四个仓库实现 + AI Provider + `RuntimeRegistry`（内置 safe-shell）；
 * - `start()` 后台执行种子安装与索引重建，暴露 `contentReady` / `startupWarnings` 给 UI 与诊断页。
 */
class AppContainer(context: Context, appVersion: String = DEFAULT_APP_VERSION) {

    private val appContext: Context = context.applicationContext
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ------------------------------------------------------------------ 目录

    val contentRoot: File = File(appContext.filesDir, "content").apply { mkdirs() }
    val workspaceDir: File = File(appContext.filesDir, "terminal/workspace").apply { mkdirs() }
    val sessionsDir: File = File(appContext.filesDir, "ai/sessions").apply { mkdirs() }

    // ------------------------------------------------------------------ 数据层

    private val validator = DefaultCodepackValidator()

    val codepackInstaller: CodepackInstaller = CodepackInstaller(
        contentRoot = contentRoot,
        validator = validator,
        appVersion = appVersion,
    )

    val catalogRepository: CatalogRepository = CatalogRepository(appContext, contentRoot)

    val contentRepository: ContentRepositoryImpl = ContentRepositoryImpl(
        installer = codepackInstaller,
        catalogRepository = catalogRepository,
    )

    val progressRepository: ProgressRepository = ProgressRepositoryImpl(appContext) { contentRepository }

    private val secretStore = AndroidKeystoreSecretStore(appContext)

    val settingsRepositoryImpl: SettingsRepositoryImpl = SettingsRepositoryImpl(appContext, secretStore)

    val settingsRepository: SettingsRepository = settingsRepositoryImpl

    val aiProvider: AiProvider = OpenAiCompatibleProvider()

    val libraryRepository: LibraryRepository = LibraryRepositoryImpl(
        context = appContext,
        installer = codepackInstaller,
        contentRepository = contentRepository,
        catalogRepository = catalogRepository,
    )

    val seedInstaller: SeedInstaller = SeedInstaller(appContext, codepackInstaller, catalogRepository)

    // ------------------------------------------------------------------ 运行时（内置安全 shell）

    val runtimeRegistry: RuntimeRegistry = InMemoryRuntimeRegistry().apply {
        register(SafeShellRuntime(workspaceRoot = workspaceDir))
    }

    // ------------------------------------------------------------------ 启动状态

    private val _contentReady = MutableStateFlow(false)

    /** 种子安装 + 内容索引重建是否已结束（失败也置 true，避免无限加载）。 */
    val contentReady: StateFlow<Boolean> = _contentReady.asStateFlow()

    private val _startupWarnings = MutableStateFlow<List<String>>(emptyList())

    /** 启动警告（种子包缺失/校验失败/内容冲突），供「诊断」页展示。 */
    val startupWarnings: StateFlow<List<String>> = _startupWarnings.asStateFlow()

    private val _installedPackCount = MutableStateFlow(0)

    val installedPackCount: StateFlow<Int> = _installedPackCount.asStateFlow()

    /** 后台执行首次启动准备；可重复调用（幂等，已安装同版本会跳过）。 */
    fun start() {
        startupScope.launch {
            val warnings = mutableListOf<String>()
            try {
                when (val seed = seedInstaller.ensureInstalled()) {
                    is com.codetree.domain.common.AppResult.Success -> {
                        warnings += seed.value.warnings
                        _installedPackCount.value = seed.value.installedPacks.size
                    }

                    is com.codetree.domain.common.AppResult.Failure ->
                        warnings += "种子内容安装失败：${seed.error.userHint}"
                }
                when (val reload = contentRepository.reload()) {
                    is com.codetree.domain.common.AppResult.Failure ->
                        warnings += "内容索引加载失败：${reload.error.userHint}"

                    is com.codetree.domain.common.AppResult.Success -> Unit
                }
                _installedPackCount.value = contentRepository.installedPacks().size
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                warnings += "启动准备异常：${t.message.orEmpty()}"
            } finally {
                _startupWarnings.value = warnings
                _contentReady.value = true
            }
        }
    }

    /** 需要人工重试时调用（例如用户从设置页重试下载/导入后）。 */
    fun reloadContentAsync() {
        startupScope.launch { contentRepository.reload() }
    }

    companion object {
        const val DEFAULT_APP_VERSION = "0.1.0"
    }
}
