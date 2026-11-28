# ADR-004：AI 直连与后端代理默认模式（D-004）

| 字段 | 内容 |
| --- | --- |
| 记录编号 | ADR-004（对应决策索引 D-004） |
| 状态 | Accepted（MVP 默认直连已接受；后端代理为 P1，接口预留） |
| 日期 | 2026-11-15（Asia/Shanghai） |
| 提案人 | ARCH（架构负责人） |
| 评审人 | Lead、engine（AI 契约）、tools（AI UI）、QA、SEC |
| 关联任务/需求 | TASK-106、TASK-108；AIA-001、AIA-002、AIA-003、AIA-004、AIA-005、AIA-006、AIA-007、CT-500-01～09 |
| 替代关系 | 无 |

## 背景

CodeTree 的 AI 助手需要在移动端提供流式解释与排错辅助。`docs/02-ARCHITECTURE.md` §4.4 规定：`ContextDraft` 由用户确认后发送，默认不发送文件全文、命令历史或设备信息；`AiProvider` 只返回回答与可验证元数据，不具备终端执行权限。`docs/07-SECURITY-RELEASE.md` §2 要求优先受控后端代理，个人直连模式允许用户提供密钥并用 Android Keystore 保护。

已确认事实：

- MVP 阶段没有可运营的后端账号体系与统一商业密钥，也不允许把任何供应商密钥写进代码、资源或仓库。
- 本机与 CI 均无真实 AI 服务凭据；Mock 流式响应是默认测试通道，任何“真实服务已验证”的声明都需要人工联调记录。
- 仓库骨架已包含 `okhttp 4.12.0`、`kotlinx-serialization-json 1.7.3`、`datastore-preferences 1.1.1`（见 `gradle/libs.versions.toml`），足以实现 OpenAI 兼容直连、SSE 解析与本地设置存储。
- `docs/02-ARCHITECTURE.md` §5 已给出 `AiProvider` 草案：`suspend fun stream(request: AiRequest): Flow<AiEvent>` 与 `suspend fun cancel(requestId: String)`。

## 决策驱动因素

- **密码学责任**：MVP 没有可审计的后端，内置统一密钥会形成“一个泄漏影响所有用户”的单点；用户自持密钥更符合个人学习工具定位。
- **隐私透明**：直连模式下用户能看到 Base URL 与模型，隐私说明可以准确描述“发送给哪个服务”。
- **无凭据可交付**：项目必须在没有真实密钥的环境下完成开发与演示，默认路径不能依赖商业服务。
- **可演进**：P1 要能加入后端代理而不改 UI 与 `AiProvider` 契约。
- **安全**：密钥不得进入代码、日志、Room/DataStore 明文、崩溃附件或导出。

## 候选方案

| 方案 | 优点 | 代价/风险 | 验证结果 |
| --- | --- | --- | --- |
| A：默认 OpenAI 兼容直连（Base URL / 模型 / 密钥用户配置，SSE 流式，Keystore 加密保存） | 无后端成本；用户可换任意兼容服务；隐私路径清晰；无内置密钥 | 用户需自备密钥；不同服务兼容性差异由用户承担；密钥保护责任在客户端 | 骨架依赖已具备；待 TASK-106/108 实现并以 Mock 验证 |
| B：内置统一商业密钥直连 | 零配置 | 密钥可被反编译提取；成本与滥用不可控；违反 `docs/07` §2 | 不采用 |
| C：仅支持后端代理 | 密钥集中管理、可审计 | MVP 无后端；离线项目无法交付；增加部署与合规负担 | P1 候选，不设为默认 |
| D：同时默认代理 + 直连兜底 | 兼顾 | MVP 无代理可用，默认路径空转 | 不采用；P1 再评估 |

## 决策

1. **MVP 默认 OpenAI 兼容直连**：设置页由用户配置 Base URL（默认留空，示例提示 `https://api.openai.com/v1` 但不得自动请求）、模型名与密钥；未配置时进入引导态，不报错、不静默使用任何内置端点。
2. **SSE 流式**：使用 OkHttp 流式响应读取 `text/event-stream`，按 SSE 规则解析 `data:` 行与 `[DONE]`；事件模型为 `AiEvent.Started / Delta / Usage / Completed / Failed / Cancelled`（见 `docs/specs/ai-provider.md`）。
3. **密钥保护**：密钥用 Android Keystore 中不可导出的密钥（AES-256-GCM）加密后存本地（`DataStore` 只存密文与必要元数据）；设置页默认掩码显示；密钥不得写入日志、异常消息、请求快照、会话导出或崩溃附件；清除应用数据与卸载后行为写入隐私说明。
4. **上下文受控**：发送前展示 `ContextDraft`（节点 ID、术语 ID、代码片段、错误文本及各自长度），用户可逐项移除；默认不附带文件全文、完整命令历史、其他会话、设备标识与密钥。
5. **执行边界**：`AiProvider` 不具备终端执行能力；模型返回的命令/代码只作为建议展示，执行必须由用户在终端中独立确认。
6. **后端代理为 P1**：接口上以 `AiProvider` 的多实现预留（直连 Provider 与代理 Provider 共享同一事件/错误模型），切换 Provider 不改 UI；代理模式必须有隐私说明、密钥托管责任与删除策略，届时另行修订本 ADR。

## 理由

- 直连模式让 MVP 在没有后端的情况下可闭环，并且默认不携带任何项目自有凭据，符合“无密钥可交付”的工程现实。
- Keystore 加密 + 掩码显示 + 日志脱敏可在客户端把“明文密钥落盘”的风险降到当前平台能力范围内的最低，满足 `docs/07` §2 与 NFR-005。
- SSE 是 OpenAI 兼容服务的共同子集，事件模型与错误映射一旦冻结，代理模式只是换 Base URL/鉴权方式，不需要改 UI。

## 后果

### 正面

- 无内置商业密钥，仓库可以公开；用户可自由选择兼容服务。
- Mock 与真实 Provider 共用契约，CI 不依赖外部服务。
- 隐私说明可以准确到“请求发往用户填写的 Base URL”。

### 负面与限制

- 用户需要自备密钥，首次使用门槛高；引导与错误文案必须清楚。
- 不同兼容服务的字段、错误码、限流行为不一致，错误映射只能覆盖常见类别。
- 客户端无法阻止用户把密钥粘贴到不可信服务；隐私说明需提示风险。
- MVP 不能声明“AI 功能已真实联调”，除非有独立人工验收记录。

### 对其他模块的影响

- `feature:assistant` 不直接使用 OkHttp；网络与 SSE 解析放在 `:core:data`（`AiRepository` + `OpenAiCompatibleProvider`），纯解析与错误映射放在 `:core:domain`（可单测）。
- 设置项与密钥密文由 `:core:data` 的 `SettingsRepository`/`SecretStore` 管理，`:app` 注入。
- `docs/specs/ai-provider.md` 固化请求、事件、错误类别与密钥存储契约。
- QA 的测试必须区分 Mock 与真实服务；真实联调单独记录 Provider、模型版本、日期与脱敏结果。

## 兼容与迁移

- 请求/事件模型冻结在 `pluginApi` 之外（AI 不属于插件 API），版本随应用升级；`ContextDraft` 字段增加必须保持向后兼容的默认值。
- 未来引入加密后端代理时，用户已保存的直连配置保留，新增“使用代理”开关；代理模式不得读取用户直连密钥。
- 回滚条件：Keystore 方案在 API 26 设备上不可用或无法保证密钥不落明文时，退回“仅内存会话 + 每次输入密钥”，并明确记录限制。

## 验证计划

- 单元测试（`:core:domain`）：SSE 分片/跨包/空增量/提前结束/`[DONE]`/多行 data/UTF-8 边界解析；错误类别映射。
- 集成测试（`:core:data`）：Mock Server 覆盖 401/429/5xx/超时/断流；密钥不出现在日志断言中。
- UI 验收（TASK-108）：未配置引导、流式追加、取消停止追加、重试不重复发送不知情请求、上下文可移除。
- 安全验收：Room/DataStore 明文、日志、崩溃附件中检索密钥；`grep` 类检查写入 QA 报告。
- 推翻条件：主流 OpenAI 兼容服务无法在无后端情况下稳定支持 SSE，或 Keystore 方案在 API 26 设备上不可用。

## 后续行动

| 动作 | 负责人 | 前置依赖 | 完成证据 |
| --- | --- | --- | --- |
| 定义 AiProvider/事件/错误契约 | engine | 本 ADR | `docs/specs/ai-provider.md` + 单测 |
| 实现直连 Provider 与 SSE 解析 | engine / shell | TASK-106/105 | Mock 测试输出 |
| AI 设置、密钥与上下文确认 UI | tools | TASK-108 | 构建输出 + 验收说明 |
| 评估后端代理 Provider（P1） | ARCH / engine | M3 后 | 新 ADR |
| 真实服务人工验收记录（可选） | QA | 有凭据时 | 联调记录，未验证不得写已通过 |
