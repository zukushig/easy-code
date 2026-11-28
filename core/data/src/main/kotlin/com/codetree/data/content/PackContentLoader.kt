package com.codetree.data.content

import com.codetree.domain.content.ContentPack
import com.codetree.domain.model.SyntaxExample
import com.codetree.domain.model.Term
import com.codetree.domain.model.Topic
import com.codetree.domain.model.TopicEdge
import com.codetree.domain.model.InstalledPack
import com.codetree.plugin.ContentJson
import com.codetree.plugin.LanguageSyntax
import com.codetree.plugin.PackManifest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File

/**
 * 内容包 JSON 解析器（docs/specs/knowledge.md §2/§5）。
 *
 * - 统一用 `ContentJson`（ignoreUnknownKeys、非宽松、写出默认值）。
 * - 兼容两种数组形态：`{"topics":[...]}`（当前内容流水线产物）与裸数组 `[...]`（规范初版）；
 *   单条实体解析失败只跳过该条，不拒绝整个包（安装前已由 validator 做完整性校验）。
 * - 文件缺失 / 目录缺失时安全降级为空列表，不崩溃。
 */
class PackContentLoader(private val json: Json = ContentJson) {

    /** 从安装目录加载一个完整内容包快照；缺失文件按空列表处理。 */
    fun loadFromDirectory(packRoot: File, installed: InstalledPack): ContentPack {
        val manifest = readManifest(packRoot)
            ?: PackManifest(
                packId = installed.packId,
                packType = installed.packType,
                version = installed.version,
                title = installed.title,
            )
        val contentDir = File(packRoot, "content")
        val topics = readContentFile(File(contentDir, "topics.json"), "topics") { element ->
            json.decodeFromJsonElement(Topic.serializer(), element)
        }
        val edges = readContentFile(File(contentDir, "edges.json"), "edges") { element ->
            json.decodeFromJsonElement(TopicEdge.serializer(), element)
        }
        val terms = readContentFile(File(contentDir, "terms.json"), "terms") { element ->
            json.decodeFromJsonElement(Term.serializer(), element)
        }
        val examples = readContentFile(File(contentDir, "examples.json"), "examples") { element ->
            json.decodeFromJsonElement(SyntaxExample.serializer(), element)
        }
        val syntax = loadSyntax(packRoot)
        return ContentPack(
            manifest = manifest,
            topics = topics,
            edges = edges,
            terms = terms,
            examples = examples,
            syntax = syntax,
            installed = installed,
        )
    }

    /** 读取 manifest.json；解析失败返回 null（调用方降级或报错）。 */
    fun readManifest(packRoot: File): PackManifest? {
        val file = File(packRoot, "manifest.json")
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString(PackManifest.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun parseManifest(raw: String): PackManifest? =
        runCatching { json.decodeFromString(PackManifest.serializer(), raw) }.getOrNull()

    /** 解析 topics.json 文本（也供测试与导入预览使用）。 */
    fun parseTopics(raw: String): List<Topic> = decodeArray(raw, "topics") { element ->
        json.decodeFromJsonElement(Topic.serializer(), element)
    }

    fun parseEdges(raw: String): List<TopicEdge> = decodeArray(raw, "edges") { element ->
        json.decodeFromJsonElement(TopicEdge.serializer(), element)
    }

    fun parseTerms(raw: String): List<Term> = decodeArray(raw, "terms") { element ->
        json.decodeFromJsonElement(Term.serializer(), element)
    }

    fun parseExamples(raw: String): List<SyntaxExample> = decodeArray(raw, "examples") { element ->
        json.decodeFromJsonElement(SyntaxExample.serializer(), element)
    }

    fun parseSyntax(raw: String): LanguageSyntax? = runCatching {
        json.decodeFromString(LanguageSyntax.serializer(), raw)
    }.getOrNull()

    // ------------------------------------------------------------------ 内部实现

    private fun loadSyntax(packRoot: File): List<LanguageSyntax> {
        val dir = File(packRoot, "syntax")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.name }
            ?.mapNotNull { file ->
                runCatching { parseSyntax(file.readText(Charsets.UTF_8)) }.getOrNull()
            }
            .orEmpty()
    }

    private fun <T> readContentFile(
        file: File,
        wrapperKey: String,
        decode: (JsonElement) -> T,
    ): List<T> {
        if (!file.isFile) return emptyList()
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        return decodeArray(raw, wrapperKey, decode)
    }

    /**
     * 数组兼容解析：支持 `{"<wrapperKey>": [...]}` 与顶层数组。
     * 单条解析失败跳过，保持「一个坏条目不拖垮整包」的降级策略。
     */
    private fun <T> decodeArray(raw: String, wrapperKey: String, decode: (JsonElement) -> T): List<T> {
        val array = runCatching {
            when (val element = json.parseToJsonElement(raw)) {
                is JsonArray -> element
                is JsonObject -> (element[wrapperKey] as? JsonArray) ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
        }.getOrElse { JsonArray(emptyList()) }
        return array.mapNotNull { element ->
            runCatching { decode(element) }.getOrNull()
        }
    }

    /** 供导入/调试预览：解析整个目录但不要求 installed 记录。 */
    fun previewDirectory(packRoot: File): ContentPack? {
        val manifest = readManifest(packRoot) ?: return null
        val placeholder = InstalledPack(
            packId = manifest.packId,
            version = manifest.version,
            packType = manifest.packType,
            title = manifest.title,
            installedAtEpochMs = System.currentTimeMillis(),
            sizeBytes = 0L,
        )
        return loadFromDirectory(packRoot, placeholder)
    }
}

/** 供测试/工具复用：`ListSerializer` 包装。 */
internal fun <T> listSerializer(serializer: KSerializer<T>) = ListSerializer(serializer)
