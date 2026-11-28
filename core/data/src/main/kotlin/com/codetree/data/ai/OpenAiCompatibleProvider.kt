package com.codetree.data.ai

import com.codetree.data.platform.toAppError
import com.codetree.domain.ai.AiConfig
import com.codetree.domain.ai.AiEvent
import com.codetree.domain.ai.AiMessage
import com.codetree.domain.ai.AiProvider
import com.codetree.domain.ai.AiRequest
import com.codetree.domain.ai.AiRole
import com.codetree.domain.ai.AiSseParser
import com.codetree.domain.ai.OpenAiSseParser
import com.codetree.domain.common.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容直连 Provider（docs/specs/ai-provider.md，ADR-004）。
 *
 * - `POST {baseUrl}/chat/completions`，`stream = true`，逐行消费 SSE（解析交给 :core:domain 的
 *   [OpenAiSseParser]），把 [AiEvent] 交给 UI；
 * - 错误统一映射 [AppError]：401/403 → Unauthorized、429 → RateLimited、超时 → Timeout、
 *   DNS/连接失败 → NoNetwork、404 → Incompatible、5xx → Internal；
 * - 取消：`cancel(requestId)` 直接 `Call.cancel()`；协程取消时通过 `Job.invokeOnCompletion`
 *   兜底断开连接，迟到事件不会追加到 UI（collector 已取消）；
 * - 不自动重试网络写请求（AIA-002：结果未知时不得静默重发）；
 * - 单次回答累计字符上限 [MAX_OUTPUT_CHARS]（超出 → `Failed(ResourceLimit)`）。
 * - 默认注入固定 system prompt（只解释与建议，不声称已验证，不注入终端执行指令）。
 */
class OpenAiCompatibleProvider(
    private val client: OkHttpClient = defaultClient(),
    private val parser: AiSseParser = OpenAiSseParser(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AiProvider {

    private val activeCalls = ConcurrentHashMap<String, Call>()

    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        emit(AiEvent.Started)
        val config = request.config
        if (!config.isConfigured) {
            emit(AiEvent.Failed(AppError.NotConfigured()))
            return@flow
        }
        val endpointError = validateEndpoint(config)
        if (endpointError != null) {
            emit(AiEvent.Failed(endpointError))
            return@flow
        }

        val httpRequest = try {
            buildRequest(request)
        } catch (t: Throwable) {
            emit(AiEvent.Failed(AppError.Internal("请求构造失败：${t.message.orEmpty()}")))
            return@flow
        }

        val call = client.newCall(httpRequest)
        activeCalls[request.requestId] = call
        val currentJob = currentCoroutineContext()[Job]
        val cancelHandle = currentJob?.invokeOnCompletion {
            activeCalls.remove(request.requestId)
            call.cancel()
        }

        var terminalEventEmitted = false
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    val detail = runCatching { response.body?.string()?.take(300) }.getOrNull()
                    emit(AiEvent.Failed(mapHttpError(response.code, retryAfter, detail)))
                    terminalEventEmitted = true
                    return@use
                }
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                    emit(AiEvent.Failed(AppError.Internal("非流式响应（Content-Type=$contentType）")))
                    terminalEventEmitted = true
                    return@use
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(AiEvent.Failed(AppError.Internal("AI 响应体为空")))
                    terminalEventEmitted = true
                    return@use
                }

                var totalChars = 0
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    for (event in parser.parseLine(line)) {
                        when (event) {
                            is AiEvent.Delta -> {
                                totalChars += event.text.length
                                if (totalChars > MAX_OUTPUT_CHARS) {
                                    emit(AiEvent.Failed(AppError.ResourceLimit("AI 回答")))
                                    terminalEventEmitted = true
                                    return@use
                                }
                                emit(event)
                            }

                            is AiEvent.Completed, is AiEvent.Failed -> {
                                emit(event)
                                terminalEventEmitted = true
                                return@use
                            }

                            else -> emit(event)
                        }
                    }
                }
                // 流提前结束且没有 [DONE]：已有内容时按 Completed 收尾（可能不完整），
                // 无内容时由 UI 依据“无任何 Delta”展示空回答状态。
                if (!terminalEventEmitted) {
                    emit(AiEvent.Completed)
                    terminalEventEmitted = true
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (timeout: java.net.SocketTimeoutException) {
            emit(AiEvent.Failed(AppError.Timeout("AI 请求", timeout)))
            terminalEventEmitted = true
        } catch (io: IOException) {
            // 取消时 OkHttp 会抛 IOException("Canceled")：协程已取消则不再发事件。
            if (!terminalEventEmitted) {
                emit(AiEvent.Failed(io.toAppError("AI 请求")))
                terminalEventEmitted = true
            }
        } catch (t: Throwable) {
            if (!terminalEventEmitted) {
                emit(AiEvent.Failed(AppError.Internal("AI 请求失败：${t.message.orEmpty()}", t)))
                terminalEventEmitted = true
            }
        } finally {
            cancelHandle?.dispose()
            activeCalls.remove(request.requestId)
        }
    }.flowOn(dispatcher)

    override suspend fun cancel(requestId: String) {
        activeCalls.remove(requestId)?.cancel()
    }

    // ------------------------------------------------------------------ 请求构造

    private fun validateEndpoint(config: AiConfig): AppError? {
        val endpoint = config.endpoint
        val isHttps = endpoint.startsWith("https://", ignoreCase = true)
        val isLocalDebug = endpoint.startsWith("http://127.0.0.1", ignoreCase = true) ||
            endpoint.startsWith("http://localhost", ignoreCase = true)
        if (!isHttps && !isLocalDebug) {
            return AppError.NotConfigured("AI 服务", "Base URL 必须使用 https://（本地调试允许 http://127.0.0.1）")
        }
        return null
    }

    private fun buildRequest(request: AiRequest): Request {
        val bodyJson = buildRequestBody(request)
        val builder = Request.Builder()
            .url(request.config.endpoint)
            .header("Accept", "text/event-stream")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
        request.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
            builder.header("Authorization", "Bearer $key")
        }
        return builder.build()
    }

    private fun buildRequestBody(request: AiRequest): String {
        val hasSystem = request.messages.any { it.role == AiRole.SYSTEM }
        val messagesJson = buildJsonArray {
            if (!hasSystem) {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", DEFAULT_SYSTEM_PROMPT)
                    },
                )
            }
            request.messages.forEach { message: AiMessage ->
                add(
                    buildJsonObject {
                        put("role", message.role.name.lowercase())
                        put("content", message.content)
                    },
                )
            }
        }
        return buildJsonObject {
            put("model", request.config.model)
            put("stream", true)
            put("temperature", request.config.temperature)
            request.config.maxTokens?.let { put("max_tokens", it) }
            put("messages", messagesJson)
        }.toString()
    }

    // ------------------------------------------------------------------ 错误映射

    private fun mapHttpError(code: Int, retryAfterSeconds: Long?, detail: String?): AppError {
        val suffix = detail?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
        return when (code) {
            401, 403 -> AppError.Unauthorized()
            404 -> AppError.Incompatible("AI 服务", "$suffix 请检查 Base URL 与模型名")
            408 -> AppError.Timeout("AI 请求")
            429 -> AppError.RateLimited(retryAfterSeconds)
            in 500..599 -> AppError.Internal("AI 服务暂时不可用（HTTP $code）$suffix")
            else -> AppError.Internal("AI 请求失败（HTTP $code）$suffix")
        }
    }

    companion object {
        private const val MAX_OUTPUT_CHARS = 64 * 1024
        private const val DEFAULT_SYSTEM_PROMPT =
            "你是编程学习助手。只解释概念、给出建议与示例，不声称代码已被编译或测试通过；" +
                "执行命令与修改代码由用户独立完成；不要输出任何终端执行指令。"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // 网络写请求结果未知时不得自动重发（AIA-002），重试由用户显式触发
            .retryOnConnectionFailure(false)
            .build()
    }
}
