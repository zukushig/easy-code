package com.codetree.feature.assistant.data

import com.codetree.feature.assistant.model.ChatMessage
import com.codetree.feature.assistant.model.ChatRole
import com.codetree.feature.assistant.model.ContextSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 落盘的消息（不含任何密钥/配置；只保存展示所需字段）。 */
@Serializable
data class StoredMessage(
    val role: String,
    val content: String,
    val createdAtEpochMs: Long = 0L,
    val cancelled: Boolean = false,
    val errorCode: String? = null,
    val errorHint: String? = null,
    val contextItems: List<StoredContext> = emptyList(),
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val truncated: Boolean = false,
)

@Serializable
data class StoredContext(
    val kind: String,
    val label: String,
    val chars: Int,
)

@Serializable
data class StoredSession(
    val schemaVersion: Int = 1,
    val id: String,
    val title: String = "新会话",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val messages: List<StoredMessage> = emptyList(),
)

/**
 * 会话本地存储：`sessionsDir/<sessionId>.json`，一个文件一个会话。
 *
 * 安全约束：只写消息与上下文摘要；**绝不写入 apiKey / baseUrl / 请求头**。
 * 文件名只接受 UUID 风格字符，避免路径穿越。
 */
class SessionStore(private val sessionsDir: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private fun directory(): File = File(sessionsDir).apply { if (!exists()) mkdirs() }

    private fun fileFor(sessionId: String): File? {
        if (!isSafeId(sessionId)) return null
        return File(directory(), "$sessionId.json")
    }

    suspend fun listSessions(): List<StoredSession> = withContext(Dispatchers.IO) {
        val files = directory().listFiles { file -> file.isFile && file.name.endsWith(".json") } ?: return@withContext emptyList()
        files.mapNotNull { file ->
            runCatching {
                val text = file.readText(Charsets.UTF_8)
                json.decodeFromString(StoredSession.serializer(), text)
            }.getOrNull()
        }.sortedByDescending { it.updatedAtEpochMs }
    }

    suspend fun load(sessionId: String): StoredSession? = withContext(Dispatchers.IO) {
        val file = fileFor(sessionId) ?: return@withContext null
        if (!file.isFile) return@withContext null
        runCatching {
            json.decodeFromString(StoredSession.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    suspend fun save(session: StoredSession): Boolean = withContext(Dispatchers.IO) {
        val file = fileFor(session.id) ?: return@withContext false
        runCatching {
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.encodeToString(StoredSession.serializer(), session), Charsets.UTF_8)
            if (file.exists()) file.delete()
            temporary.renameTo(file)
        }.isSuccess
    }

    suspend fun delete(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val file = fileFor(sessionId) ?: return@withContext false
        runCatching { !file.exists() || file.delete() }.getOrDefault(false)
    }

    fun toStoredMessage(message: ChatMessage): StoredMessage = StoredMessage(
        role = if (message.role == ChatRole.USER) ROLE_USER else ROLE_ASSISTANT,
        content = message.content,
        createdAtEpochMs = message.createdAtEpochMs,
        cancelled = message.cancelled,
        errorCode = message.errorCode,
        errorHint = message.errorHint,
        contextItems = message.contextItems.map { StoredContext(it.kind, it.label, it.chars) },
        promptTokens = message.promptTokens,
        completionTokens = message.completionTokens,
        truncated = message.truncated,
    )

    fun toChatMessage(message: StoredMessage, fallbackIndex: Int): ChatMessage = ChatMessage(
        id = "stored-$fallbackIndex-${message.createdAtEpochMs}",
        role = if (message.role == ROLE_USER) ChatRole.USER else ChatRole.ASSISTANT,
        content = message.content,
        createdAtEpochMs = message.createdAtEpochMs,
        cancelled = message.cancelled,
        errorCode = message.errorCode,
        errorHint = message.errorHint,
        contextItems = message.contextItems.map { ContextSummary(it.kind, it.label, it.chars) },
        promptTokens = message.promptTokens,
        completionTokens = message.completionTokens,
        truncated = message.truncated,
    )

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

        fun isSafeId(sessionId: String): Boolean = sessionId.isNotEmpty() && SAFE_ID.matches(sessionId)
    }
}
