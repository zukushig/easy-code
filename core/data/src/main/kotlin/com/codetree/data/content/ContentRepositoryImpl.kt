package com.codetree.data.content

import com.codetree.data.platform.toAppError
import com.codetree.domain.common.AppResult
import com.codetree.domain.content.ContentPack
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.graph.TopicGraph
import com.codetree.domain.model.InstalledPack
import com.codetree.domain.model.PackCatalogEntry
import com.codetree.domain.model.SyntaxExample
import com.codetree.domain.model.Term
import com.codetree.domain.model.Topic
import com.codetree.domain.model.TopicEdge
import com.codetree.domain.search.InMemoryTermSearchEngine
import com.codetree.domain.search.TermSearchEngine
import com.codetree.plugin.LanguageSyntax
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * 内容只读视图实现（docs/specs/knowledge.md §7：JSON + 内存索引 + DataStore）。
 *
 * - [reload] 扫描 `installed.json` 与 `content/packs/<packId>/<version>/`，合并所有**已启用**包：
 *   Topic/Term/Example 全局唯一（先到先得，冲突记录 warning 不崩溃）、边去重、语法按语言索引；
 * - 词典索引在 reload 后整体 `rebuild`（`InMemoryTermSearchEngine` 发布不可变快照，可并发 search）；
 * - 所有 getter 读取 `@Volatile` 快照，UI 与搜索线程安全；无 Room / 无后台同步。
 */
class ContentRepositoryImpl(
    private val installer: CodepackInstaller,
    private val loader: PackContentLoader = PackContentLoader(),
    private val catalogRepository: CatalogRepository? = null,
    private val searchEngine: TermSearchEngine = InMemoryTermSearchEngine(),
) : ContentRepository {

    data class Snapshot(
        val installedPacks: List<InstalledPack> = emptyList(),
        val catalog: List<PackCatalogEntry> = emptyList(),
        val packs: Map<String, ContentPack> = emptyMap(),
        val topics: Map<String, Topic> = emptyMap(),
        val terms: Map<String, Term> = emptyMap(),
        val examples: Map<String, SyntaxExample> = emptyMap(),
        val syntax: Map<String, LanguageSyntax> = emptyMap(),
        val domains: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val loadedAtEpochMs: Long = 0L,
    ) {
        val topicCount: Int get() = topics.size
        val termCount: Int get() = terms.size
        val exampleCount: Int get() = examples.size
        val isEmpty: Boolean get() = topics.isEmpty() && terms.isEmpty()
    }

    private val state = MutableStateFlow(Snapshot())

    /** 供 App 组合根观察加载完成 / 内容变化（接口本身不暴露 Flow）。 */
    val snapshots: StateFlow<Snapshot> = state.asStateFlow()

    val isLoaded: Boolean get() = state.value.loadedAtEpochMs > 0L

    private val lastError = AtomicReference<String?>(null)

    fun lastLoadError(): String? = lastError.get()

    /** 内容包安装目录（诊断/存储展示使用）。 */
    val packsDirectory: java.io.File get() = installer.packsDirectory

    override fun installedPacks(): List<InstalledPack> = state.value.installedPacks

    override fun catalog(): List<PackCatalogEntry> = state.value.catalog

    override fun graph(): TopicGraph = TopicGraph(state.value.topics, state.value.allEdges())

    override fun pack(packId: String): ContentPack? = state.value.packs[packId]

    override fun topic(id: String): Topic? = state.value.topics[id]

    override fun term(id: String): Term? = state.value.terms[id]

    override fun example(id: String): SyntaxExample? = state.value.examples[id]

    override fun languageSyntax(language: String): LanguageSyntax? =
        state.value.syntax[language.lowercase()]

    override fun searchEngine(): TermSearchEngine = searchEngine

    override fun domains(): List<String> = state.value.domains

    override fun topicsOfDomain(domain: String): List<Topic> =
        state.value.topics.values.filter { it.domain == domain }

    override suspend fun reload(): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val installed = installer.readInstalled()
            val packs = LinkedHashMap<String, ContentPack>()
            val warnings = mutableListOf<String>()

            for (record in installed.filter { it.enabled }) {
                val dir = installer.installedDirectory(record)
                if (!dir.isDirectory) {
                    warnings += "内容包 ${record.packId} 目录缺失，已跳过"
                    continue
                }
                runCatching { loader.loadFromDirectory(dir, record) }
                    .onSuccess { packs[record.packId] = it }
                    .onFailure { warnings += "内容包 ${record.packId} 解析失败：${it.message.orEmpty()}" }
            }

            val topics = LinkedHashMap<String, Topic>()
            val terms = LinkedHashMap<String, Term>()
            val examples = LinkedHashMap<String, SyntaxExample>()
            val syntax = LinkedHashMap<String, LanguageSyntax>()
            val edges = LinkedHashMap<String, TopicEdge>()

            for (pack in packs.values) {
                pack.topics.forEach { topic ->
                    if (topics.putIfAbsent(topic.id, topic) != null) {
                        warnings += "Topic ID 冲突：${topic.id}（包 ${pack.manifest.packId}）"
                    }
                }
                pack.terms.forEach { term ->
                    if (terms.putIfAbsent(term.id, term) != null) {
                        warnings += "Term ID 冲突：${term.id}（包 ${pack.manifest.packId}）"
                    }
                }
                pack.examples.forEach { example ->
                    if (examples.putIfAbsent(example.id, example) != null) {
                        warnings += "Example ID 冲突：${example.id}（包 ${pack.manifest.packId}）"
                    }
                }
                pack.syntax.forEach { language ->
                    syntax.putIfAbsent(language.language.lowercase(), language)
                }
                pack.edges.forEach { edge ->
                    edges.putIfAbsent("${edge.source}|${edge.target}|${edge.type.name}", edge)
                }
            }

            val graph = TopicGraph(topics, edges.values.toList())
            val brokenEdges = edges.values.count { it.source !in topics || it.target !in topics }
            if (brokenEdges > 0) warnings += "存在 $brokenEdges 条断链边（引用不存在的节点）"

            val domains = topics.values
                .map { it.domain }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            val catalog = catalogRepository?.load().orEmpty().ifEmpty {
                installed.map { it.toCatalogEntry() }
            }

            searchEngine.rebuild(terms.values.toList())
            lastError.set(null)
            state.value = Snapshot(
                installedPacks = installed,
                catalog = catalog,
                packs = packs,
                topics = topics,
                terms = terms,
                examples = examples,
                syntax = syntax,
                domains = domains,
                warnings = warnings,
                loadedAtEpochMs = System.currentTimeMillis(),
            )
            AppResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            lastError.set(t.message ?: t::class.simpleName)
            AppResult.Failure(t.toAppError("加载内容"))
        }
    }

    private fun Snapshot.allEdges(): List<TopicEdge> =
        packs.values.flatMap { it.edges }
            .distinctBy { "${it.source}|${it.target}|${it.type.name}" }

    private fun InstalledPack.toCatalogEntry(): PackCatalogEntry = PackCatalogEntry(
        packId = packId,
        version = version,
        title = title,
        sizeBytes = sizeBytes,
        downloadUrl = "",
        dependencies = emptyList(),
    )
}

/** 内容包目录的绝对路径（App 诊断 / 存储展示使用）。 */
fun ContentRepositoryImpl.contentRoot(): java.io.File =
    packsDirectory.parentFile ?: packsDirectory
