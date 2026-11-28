package com.codetree.domain.ai

import com.codetree.domain.common.AppError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * OpenAI 兼容 SSE 行解析器（docs/specs/ai-provider.md §3）。
 *
 * 只消费 `data:` 行；空行、`event:`/`id:`/`retry:` 字段与 `:` 注释行忽略。
 * - `data: [DONE]` → [AiEvent.Completed]；
 * - `data: {...}` 取 `choices[0].delta.content`（空/缺省不产生 Delta）、`usage`（存在时产生 Usage）；
 * - 非法 JSON / 非对象负载忽略该行并返回空列表，不崩溃、不写入日志；
 * - 顶层 `error` / `stream_error` / `streamError` 字段 → [AiEvent.Failed] + [AppError.Internal]。
 *
 * 解析器无状态、可复用；`data:` 后的前导空格按 SSE 规范去除，内容中的转义、中文与 emoji 由 JSON 解码保留。
 */
class OpenAiSseParser : AiSseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    override fun parseLine(line: String): List<AiEvent> {
        val stripped = line.trimEnd('\r', '\n')
        if (stripped.isBlank()) return emptyList()
        val payloadStart = stripped.trimStart()
        if (payloadStart.startsWith(":")) return emptyList()
        if (!payloadStart.startsWith(DATA_PREFIX)) return emptyList()
        val payload = payloadStart.substring(DATA_PREFIX.length).trim()
        if (payload.isEmpty()) return emptyList()
        if (payload == DONE_PAYLOAD) return listOf(AiEvent.Completed)

        val element: JsonElement = try {
            json.parseToJsonElement(payload)
        } catch (_: Exception) {
            return emptyList()
        }
        val obj = element as? JsonObject ?: return emptyList()

        streamFailure(obj)?.let { return listOf(it) }

        val events = ArrayList<AiEvent>(2)
        deltaContent(obj)?.takeIf { it.isNotEmpty() }?.let { events += AiEvent.Delta(it) }
        usage(obj)?.let { events += it }
        return events
    }

    private fun deltaContent(obj: JsonObject): String? {
        val choices = obj["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull() as? JsonObject ?: return null
        val delta = first["delta"] as? JsonObject ?: return null
        return (delta["content"] as? JsonPrimitive)?.contentOrNull
    }

    private fun usage(obj: JsonObject): AiEvent.Usage? {
        val usage = obj["usage"] as? JsonObject ?: return null
        val promptTokens = intValue(usage["prompt_tokens"])
        val completionTokens = intValue(usage["completion_tokens"])
        if (promptTokens == null && completionTokens == null) return null
        return AiEvent.Usage(promptTokens ?: 0, completionTokens ?: 0)
    }

    private fun streamFailure(obj: JsonObject): AiEvent.Failed? {
        for (key in ERROR_KEYS) {
            val error = obj[key] ?: continue
            if (error is JsonNull) continue
            val message = when (error) {
                is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> error.contentOrNull
                else -> null
            }
            return AiEvent.Failed(AppError.Internal(message?.takeIf { it.isNotBlank() } ?: "stream error"))
        }
        return null
    }

    private fun intValue(element: JsonElement?): Int? =
        (element as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private companion object {
        const val DATA_PREFIX = "data:"
        const val DONE_PAYLOAD = "[DONE]"
        val ERROR_KEYS = listOf("error", "stream_error", "streamError")
    }
}
