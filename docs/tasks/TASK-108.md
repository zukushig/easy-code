# 任务卡：TASK-108 终端 + AI 助手 + 知识库/插件管理（T8）

> 使用前复制到项目实际任务目录。一个任务只保留一个当前负责人；子任务分别建卡。
> 模板：`docs/templates/TASK_CARD.md`；本卡对应团队任务 **T8**。

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 任务卡编号 | TASK-108 |
| 团队任务 | T8（`team_task` id：task-8） |
| 关联 WBS | CT-300-01/02/05（知识库目录/选择/删除）、CT-400-03/06/07/08/09（终端 UI/高亮/诊断/补全/示例入口）、CT-500-04/05/06/07（AI UI/上下文/会话/错误）、CT-600-02/05/06/07（导入/安装/贡献/管理页） |
| 关联需求 | TRM-001～TRM-006、AIA-001～AIA-005、PLG-001～PLG-004、PKG-001/002/004/005、UI-003/004 |
| 优先级 | P0 |
| 状态 | Ready |
| 当前负责人 | tools |
| 评审人 | architect + qa（终端/AI/插件安全负例由 qa 复核） |
| 目标里程碑 | M3 + M4 前置 |
| 前置依赖 | TASK-105（设计系统/容器/数据层）、TASK-106（runtime/插件/AI 契约） |
| 后续消费者 | TASK-109（安全与端到端验收）、TASK-110（集成发布） |

## 背景与用户价值

终端、AI 助手与知识库/插件管理是“实践与扩展”入口：用户在终端安全运行示例并获得诊断/补全；在 AI 页以受控上下文请求解释；在知识库导入内容包、管理插件与存储。这三块的安全边界（路径沙箱、密钥、包校验）由 TASK-106 的内核保证，本任务负责把这些能力做成可用、可信、可解释的 UI。

## 目标

- [ ] `:feature:terminal`：会话 UI（输入/输出/错误/清屏/历史）、行内高亮与括号匹配/错误下划线、上下文模糊补全、诊断与修复预览、资源限制与取消、示例“一键运行”入口（运行时缺失显示明确状态）。
- [ ] `:feature:assistant`：AI 设置（Base URL/模型/密钥掩码/清除）、SSE 流式回答、取消/重试/错误状态、上下文确认卡（节点/术语/代码/报错，逐项移除）、本地会话保存与删除。
- [ ] `:feature:library`：知识库目录（版本/大小/依赖/空间）、安装/更新/删除（MVP 以内置包与本地 catalog 为准）、`.codepack` 导入（SAF）、插件依赖/启停/卸载、存储管理。
- [ ] 所有危险/不可逆操作有确认与可执行错误提示；密钥不出现在 UI 日志/导出。
- [ ] 构建通过并贴输出；与 TASK-106 内核契约集成有实际证据。

## 非目标

- 不实现 safe-shell 白名单/路径校验/SSE 解析/插件校验器（TASK-106 内核）。
- 不实现真实远程下载后端（本地 catalog 先行；远程 URL 下载后续回填，UI 不得把空 URL 显示为可下载）。
- 不实现签名插件/可执行插件（ADR-005）与 Python/JS 运行时（ADR-003）。
- 不修改 `:core:domain`/`:core:data`/`runtime/**`/`plugin-api/**` 契约（走 `CONTRACT_CHANGE`）。

## 只读上下文（必须先读，不得修改）

1. `AGENTS.md`、`docs/01-PRD.md` §4.3～§4.6、`docs/06-TESTING-ACCEPTANCE.md` §4.4～§4.6。
2. `docs/specs/{terminal,ai-provider,plugin-api,knowledge,design-system,module-layout}.md`。
3. `docs/decisions/ADR-003`、`ADR-004`、`ADR-005`。
4. `runtime/api/src/main/kotlin/com/codetree/runtime/api/RuntimeContracts.kt`、`core/domain/src/main/kotlin/com/codetree/domain/ai/AiContracts.kt`、`plugin-api/**`、`core/domain/src/main/kotlin/com/codetree/domain/content/Repositories.kt`。
5. `docs/07-SECURITY-RELEASE.md` §2/§3/§4/§5。

## 输入与契约

- 输入：`RuntimeRegistry`/`RuntimeSession`/`RuntimeEvent`、`SyntaxAnalyzer`、`AiProvider`/`AiEvent`/`AiContextDraft`、`LibraryRepository`、`CodepackValidator`、`SettingsRepository`（含 `setApiKey/apiKey`）、`AppError`。
- 输出：三个 feature 的路由入口、ViewModel（构造注入）、stateless 组件；`:app` 组装导航。
- 测试替身：AI 默认 Mock SSE；运行时用 `BUILTIN_SHELL`；导入用合法/恶意 fixture 包（不修改真实内容包）。

## 允许改动文件（写范围）

```text
feature/terminal/**
feature/assistant/**
feature/library/**
```

只读：`app/**`、`core/**`、`runtime/**`、`plugin-api/**`、其他 feature、`content/**`、`tools/**`、`gradle/**`、`.github/**`。

## 禁止事项

- 不把命令输入拼接给系统 shell；不绕过 `:runtime:safe-shell` 直接做文件 IO。
- 不在日志/UI/导出中显示密钥或 Authorization；不把密钥写进 DataStore 明文。
- 不自动执行 AI 返回的命令；不把模型回答显示为“已验证/已运行”。
- 不忽略插件校验错误继续安装；不开放可执行插件/任意网络/文件权限。
- 不执行 `git commit`/`git push`；不改 `.gitignore`、构建配置与共享文件。
- 不伪造 AI 真实联调、不伪造下载成功或安装成功。

## 子任务

| 子任务 ID | 行为 | 输出 | 状态 |
| --- | --- | --- | --- |
| TASK-108-A | 终端会话/高亮/诊断/补全 | 终端页面 + 状态 | Backlog |
| TASK-108-B | AI 设置/流式/取消/错误/上下文 | AI 页面 | Backlog |
| TASK-108-C | 知识库目录/安装/删除/存储 | 知识库页面 | Backlog |
| TASK-108-D | `.codepack` 导入与插件管理 | 导入/启停/卸载流程 | Backlog |
| TASK-108-E | 集成证据与交接 | 构建输出 + 验收说明 | Backlog |

## 可执行验收标准

1. 构建：`cd /root/workspace/easy-code && ./gradlew :feature:terminal:assembleDebug :feature:assistant:assembleDebug :feature:library:assembleDebug`（或 `:app:assembleDebug`）通过并贴输出。
2. 终端：输入/输出/错误/清屏/历史可用；长输出不阻塞输入；取消后状态收敛（无继续追加）；超限提示；路径越界尝试显示可读错误；高亮/诊断/补全在编辑后更新。
3. AI：未配置进入引导；流式追加；取消停止；Mock 覆盖 401/429/5xx/超时/断流的错误状态；上下文逐项移除后请求不再包含；密钥掩码且不可导出。
4. 知识库：内置包列表与空间显示真实（来自 `LibraryRepository.storageUsageBytes()`/catalog）；合法包导入成功、损坏/越界包失败且旧内容可读；删除被依赖包有明确提示；插件启停/卸载后无孤立引用。
5. 安全：日志/字符串检索无密钥；UI 不展示伪造 AI 成功；P0 拒绝可执行插件与越权权限。
6. 边界：`git status --short` 只出现三个 feature 目录。

## 验证计划

| 测试 ID | 层级 | 命令/步骤 | 预期 | 证据位置 |
| --- | --- | --- | --- | --- |
| TST-108-001 | 构建 | 三个 feature `assembleDebug` | 通过 | 构建输出 |
| TST-108-002 | 终端 | 会话/取消/超限/越界 | 状态收敛、错误可读 | 测试说明/截图 |
| TST-108-003 | AI | Mock 401/429/超时/断流/取消 | 每类有下一步 | 测试记录 |
| TST-108-004 | 知识库 | 合法/恶意 codepack 导入 | 成功/失败回滚 | 命令/日志 |
| TST-108-005 | 安全 | 密钥检索、可执行插件 | 0 命中 / 明确拒绝 | 报告 |
| TST-108-006 | 边界 | `git status --short` | 未越界 | 输出 |

## 完成核对

- [ ] 构建输出已贴；三个页面接线到 `:app` 可进入。
- [ ] 终端取消/超限/越界、AI 错误/取消/上下文、插件导入负例均有证据。
- [ ] 无密钥泄露、无自动执行、无可执行插件入口。
- [ ] 未修改内核契约；需要时已提交 `CONTRACT_CHANGE`。
- [ ] 交接记录完整。

## 交接说明（HANDOFF 要点）

- 当前状态、完成项、未完成项分列。
- 修改文件：`feature/terminal/**`、`feature/assistant/**`、`feature/library/**`。
- 契约变化：无 / 附 `CONTRACT_CHANGE`。
- 测试证据：构建输出、终端/AI/插件负例记录。
- 风险：远程下载未交付，知识库仅支持内置包与本地目录；UI 必须如实标注，不得伪造“已下载”。
- 下一步：通知 qa 进行安全与端到端验收；通知 lead 集成。

## 执行日志

- 2026-11-15：任务 Ready，等待 tools 认领。
