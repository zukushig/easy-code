package com.codetree.domain.content

import com.codetree.domain.ai.AiConfig
import com.codetree.domain.common.AppResult
import com.codetree.domain.graph.TopicGraph
import com.codetree.domain.model.InstalledPack
import com.codetree.domain.model.LearningStatus
import com.codetree.domain.model.LearningStats
import com.codetree.domain.model.PackCatalogEntry
import com.codetree.domain.model.SyntaxExample
import com.codetree.domain.model.Term
import com.codetree.domain.model.Topic
import com.codetree.domain.model.TopicEdge
import com.codetree.domain.model.TopicProgress
import com.codetree.domain.search.TermSearchEngine
import com.codetree.plugin.LanguageSyntax
import com.codetree.plugin.PackManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 一个已安装并启用的内容包在内存中的完整快照。 */
data class ContentPack(
    val manifest: PackManifest,
    val topics: List<Topic> = emptyList(),
    val edges: List<TopicEdge> = emptyList(),
    val terms: List<Term> = emptyList(),
    val examples: List<SyntaxExample> = emptyList(),
    val syntax: List<LanguageSyntax> = emptyList(),
    val installed: InstalledPack,
)

/** 内容只读视图：知识树、词典、示例、语法共用同一组稳定 ID（docs/02 §4.1）。 */
interface ContentRepository {
    fun installedPacks(): List<InstalledPack>
    fun catalog(): List<PackCatalogEntry>
    fun graph(): TopicGraph
    fun pack(packId: String): ContentPack?
    fun topic(id: String): Topic?
    fun term(id: String): Term?
    fun example(id: String): SyntaxExample?
    fun languageSyntax(language: String): LanguageSyntax?
    fun searchEngine(): TermSearchEngine
    fun domains(): List<String>
    fun topicsOfDomain(domain: String): List<Topic>
    suspend fun reload(): AppResult<Unit>
}

/** 学习状态只保存用户数据，不写回内容包（docs/02 §4.2）。 */
interface ProgressRepository {
    fun observeStatus(topicId: String): Flow<LearningStatus>
    fun observeAll(): Flow<Map<String, TopicProgress>>
    suspend fun setStatus(topicId: String, status: LearningStatus): AppResult<Unit>
    suspend fun markVisited(topicId: String): AppResult<Unit>
    suspend fun stats(): LearningStats
}

enum class BilingualMode { ZH_ONLY, EN_GRAMMAR, SIDE_BY_SIDE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val activeDirection: String? = null,
    val bilingualMode: BilingualMode = BilingualMode.SIDE_BY_SIDE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: Float = 1f,
    val reduceMotion: Boolean = false,
    val aiConfig: AiConfig = AiConfig(),
    val hasApiKey: Boolean = false,
)

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings): AppResult<Unit>
    suspend fun setApiKey(apiKey: String?): AppResult<Unit>
    suspend fun apiKey(): String?
}

/** 知识库/插件管理：安装、下载、启停与卸载（docs/01 PLG/PKG）。 */
interface LibraryRepository {
    fun observeInstalled(): Flow<List<InstalledPack>>
    suspend fun installed(): List<InstalledPack>
    suspend fun importCodepack(bytes: ByteArray, fileName: String): AppResult<InstalledPack>
    suspend fun download(
        entry: PackCatalogEntry,
        onProgress: (bytesRead: Long, contentLength: Long) -> Unit = { _, _ -> },
    ): AppResult<InstalledPack>
    suspend fun remove(packId: String): AppResult<Unit>
    suspend fun setEnabled(packId: String, enabled: Boolean): AppResult<Unit>
    suspend fun storageUsageBytes(): Long
}
