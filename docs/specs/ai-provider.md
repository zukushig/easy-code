# AI Provider 契约（ai-provider）

> 适用范围：MVP M1/M2 的 AI 直连能力。事实来源：`docs/01-PRD.md` AIA-001～AIA-007、`docs/07-SECURITY-RELEASE.md` §2/§3、ADR-004；可执行契约以 `core/domain/src/main/kotlin/com/codetree/domain/ai/AiContracts.kt` 与 `core/domain/src/main/kotlin/com/codetree/domain/common/AppError.kt` 为准。
>
> 默认模式：OpenAI 兼容直连（Base URL / 模型 / 密钥由用户配置），SSE 流式；后端代理为 P1（ADR-004）。

## 1. 类型契约（与代码一致）

```kotlin
enum class AiRole { SYSTEM, USER, ASSISTANT }
data class AiMessage(val role: AiRole, val content: String)

data class AiConfig(
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Double = 0.3,
    val maxTokens: Int? = null,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
    val endpoint: String get() = baseUrl.trimEnd('/') + "/chat/completions"
}

data class AiRequest(
    val requestId: String,
    val config: AiConfig,
    val apiKey: String?,
    val messages: List<AiMessage>,
)

sealed interface AiEvent {
    data object Started : AiEvent
    data class Delta(val text: String) : AiEvent
    data class Usage(val promptTokens: Int, val completionTokens: Int) : AiEvent
    data object Completed : AiEvent
    data class Failed(val error: AppError) : AiEvent
}

interface AiProvider {
    fun stream(request: AiRequest): Flow<AiEvent>
    suspend fun cancel(requestId: String)
}

sealed interface AiContextItem {
    data class TopicRef(val topicId: String)
    data class TermRef(val termId: String)
    data class CodeSnippet(val language: String, val code: String)
    data class TerminalError(val command: String, val stderr: String)
}

data class AiContextDraft(val items: List<AiContextItem> = emptyList()) {
    val totalChars: Int
    fun render(): String
}
```

## 2. 请求构造规则

1. **端点**：`AiConfig.endpoint = baseUrl.trimEnd('/') + "/chat/completions"`；不硬编码任何供应商域名；`baseUrl` 必须为 `https://`（本地调试可用 `http://127.0.0.1` 白名单例外，并在 UI 提示明文风险）。
2. **消息**：`messages = [SYSTEM?] + 对话历史 + [USER: 用户问题 + ContextDraft.render()]`；系统提示词固定为“你是编程学习助手；只解释与建议，不声称已验证；执行由用户独立完成”，不得注入终端执行指令。
3. **上下文**：`ContextDraft` 由 UI 在发送前展示，用户可逐项移除；默认不附带文件全文、完整命令历史、其他会话、设备标识、密钥。`render()` 输出格式固定（`[知识节点]`/`[术语]`/`[代码 lang]`/`[终端报错] $ cmd + stderr`），便于日志脱敏与测试断言。
4. **鉴权**：`apiKey` 来自 `SettingsRepository.apiKey()`（Keystore 解密后仅存内存），只在请求头 `Authorization: Bearer <key>` 中使用；不得写入日志、异常消息、请求快照或导出。
5. **超时**：连接超时 10s、读超时 60s（SSE 两次数据间隔超过 60s 视为 `Timeout`）；取消协程即断开连接。
6. **重试**：只有用户显式重试才发起新请求；网络写请求结果未知时先提示“请求可能已发送”，不得自动重发（AIA-002）。

## 3. SSE 事件解析

`AiSseParser.parseLine(line: String): List<AiEvent>` 规则：

- 响应 `Content-Type` 必须为 `text/event-stream`；否则按 `AppError.Internal("非流式响应")` 处理。
- 逐行解析：忽略空行、`:` 开头注释、`event:`/`id:`/`retry:` 字段；只消费 `data:` 行，多行 `data:` 按 SSE 规范以换行拼接。
- `data: [DONE]` → `Completed` 并结束流。
- `data: {json}` 解析 `choices[0].delta.content`（可空/空增量 → 不产生 `Delta`）；`usage` 存在时产生 `Usage`；解析失败的行跳过并计入诊断计数，不崩溃。
- 流提前结束（无 `[DONE]`）→ 若已有内容则 `Completed` + 标记“可能不完整”；无内容则 `Failed(AppError.Internal("流提前结束"))`。
- 取消后到达的迟到事件必须丢弃，不追加到 UI。
- UTF-8 跨 chunk 边界按字节流解码（不允许按 chunk 直接 `toString` 破坏多字节字符）。
- 单次回答累计字符数上限 64KiB（超出截断并 `Failed(ResourceLimit)`），提示 UI 显示已截断。

## 4. 错误类别映射

所有失败统一使用 `:core:domain` 的 `AppError`，禁止裸 throw：

| 类别 | `AppError` | 典型触发 | 可重试 | 用户提示要点 |
| --- | --- | --- | --- | --- |
| 未配置 | `NotConfigured` | baseUrl/model 为空 | 否（先配置） | 引导到“我的 → AI 设置” |
| 无网络 | `NoNetwork` | 无连接/DNS 失败 | 是 | 检查网络；离线内容仍可用 |
| 超时 | `Timeout` | 连接/读超时 | 是 | 稍后重试 |
| 未授权 | `Unauthorized` | 401/403 | 否（改密钥） | 检查密钥与有效期 |
| 限流 | `RateLimited(retryAfterSeconds)` | 429 | 是（按提示） | 显示等待时间 |
| 不兼容 | `Incompatible` | 404 模型不存在/响应格式不符 | 否 | 检查模型名与 Base URL |
| 校验失败 | `ChecksumFailed`/`Internal` | 解析失败、响应体异常 | 视情况 | 重试或更换服务 |
| 依赖缺失 | `DependencyMissing` | 未安装所需内容包（间接） | 否 | 先安装内容 |
| 权限拒绝 | `PermissionDenied` | 网络权限/系统限制 | 否 | 系统设置或改用其他方式 |
| 资源超限 | `ResourceLimit` | 输出超限 | 是 | 缩短问题或分段 |
| 用户取消 | `Cancelled` | 用户点停止 | 是 | 不弹错误，状态显示已取消 |
| 内部错误 | `Internal` | 未分类异常 | 视情况 | 诊断入口 + 重试 |

UI 文案与日志映射分离：`AppError.userHint` 面向用户且必须有下一步；`AppError.code` 稳定供日志/测试断言；日志中禁止出现 key、Authorization 头、完整请求体。

## 5. 密钥与配置存储

- 密钥：Android Keystore 生成不可导出 AES-256-GCM 密钥（alias 例如 `codetree.ai.key`），加密后密文存 DataStore；解密仅在内存中短暂存在，用于构造请求头。
- `AppSettings.hasApiKey` 只表示“是否已保存密钥”，不保存明文；设置页掩码显示（如 `sk-…abcd` 仅显示末 4 位，且可一键清除）。
- 清除应用数据/卸载后密钥不可恢复；隐私说明必须写明。
- 日志/崩溃附件/测试快照中检索密钥的检查纳入 QA（`docs/07` §2、NFR-005）。

## 6. 会话与 UI 契约（TASK-108）

- 会话状态：`Idle`、`ConfiguredNeeded`、`Streaming`、`Completed`、`Failed(error)`、`Cancelled`；UI 必须覆盖未配置、离线、超时、401、429、5xx、取消、重试。
- 流式追加：`Delta` 追加到当前消息；`Completed` 后不再追加；取消后 UI 立即停止追加并保留已生成内容。
- 上下文确认卡：发送前展示 `AiContextDraft` 条目（类型、ID/语言、字符数），支持逐项移除与“全部移除”；确认后才构造请求。
- 历史会话：本地保存会话与消息（不含密钥），支持删除；删除后不可恢复。
- AI 回答不得被渲染成“编译/测试结果”；代码块显示为建议，执行入口由终端独立确认。

## 7. Mock 与真实服务边界

- 默认测试通道：本地 Mock（固定 SSE 脚本）覆盖分片、跨包、空增量、断流、401、429、5xx、超时、取消；CI 不依赖真实商业 API。
- 真实服务联调必须单独记录：Provider、Base URL 域名、模型、日期、脱敏结果；未联调时发布说明必须写“AI 仅 Mock 验证”。
- `AiProvider` 的实现（`OpenAiCompatibleProvider`）位于 `:core:data`；SSE 解析与错误映射位于 `:core:domain`（纯 JVM 可单测）。

## 8. 验收清单（对应 TASK-106/108）

- [ ] `:core:domain:test` 覆盖 SSE 解析全部分支与错误映射；`AiContextDraft.render()` 与 `totalChars` 有单测。
- [ ] Mock Server 覆盖 401/429/5xx/超时/断流/`[DONE]` 缺失；请求头包含 `Authorization` 但日志断言无密钥。
- [ ] 未配置进入引导态；取消后不再追加；重试不重复发送不知情请求。
- [ ] 上下文可预览/逐项移除；移除后实际请求不包含该项。
- [ ] 密钥不出现在 DataStore 明文、日志、崩溃附件与会话导出中。
- [ ] 真实服务未验证时，报告与发布说明明确标注。
