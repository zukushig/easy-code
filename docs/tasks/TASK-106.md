# 任务卡：TASK-106 领域引擎 + 安全终端内核 + 插件 API（T6）

> 使用前复制到项目实际任务目录。一个任务只保留一个当前负责人；子任务分别建卡。
> 模板：`docs/templates/TASK_CARD.md`；本卡对应团队任务 **T6**。

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 任务卡编号 | TASK-106 |
| 团队任务 | T6（`team_task` id：task-6） |
| 关联 WBS | CT-010-01/04（Schema/内容检查）、CT-100-01（图仓库）、CT-200-01/02（搜索）、CT-400-01/02/04/05（Runtime SPI/沙箱/白名单/限制）、CT-500-01/03（AI 契约/SSE）、CT-600-01/03/04（清单/校验/依赖） |
| 关联需求 | LRN-001/002/004、DIC-001/002/003、TRM-001/002/005/006、AIA-001/002/003、PLG-002/004、NFR-002 |
| 优先级 | P0 |
| 状态 | Ready |
| 当前负责人 | engine |
| 评审人 | architect + qa（安全负例必须由 qa 独立复核） |
| 目标里程碑 | M2/M3 |
| 前置依赖 | TASK-101（契约）、TASK-103/104 内容格式（可用 fixture）、TASK-102 工具链（JVM 测试） |
| 后续消费者 | TASK-105（数据层）、TASK-107（UI）、TASK-108（UI）、TASK-109（验收） |

## 背景与用户价值

所有“难且可测”的逻辑应该在没有 UI、没有 Android 运行时的纯 JVM 模块里完成：知识图遍历与路径、词典排序、进度模型、AI SSE 解析与错误映射、Codepack 校验与依赖解析、终端白名单与沙箱路径。这样既能在低内存设备上快速得到单测证据，也能让 UI 与数据层只做薄封装。本任务是 MVP 正确性与安全性的核心。

## 目标

- [ ] `:core:domain`：图模型/遍历/路径/推荐（已有契约，补齐缺失逻辑与测试）、词典规范化与搜索排序（精确/别名/前缀/FTS 替代/模糊，带上限与原因）、进度模型与统计、AI SSE 解析与错误映射、黄金路径用例。
- [ ] `:plugin-api`：`CodepackValidator` 实现（清单/条目/兼容性校验流水线）、依赖解析（缺失/循环/冲突）、`SafePath` 负例覆盖、`SemVer`/`VersionRange` 边界。
- [ ] `:runtime:api` + `:runtime:safe-shell`：按 `docs/specs/terminal.md` 实现白名单命令、沙箱文件系统、路径规范化、资源上限、取消语义、诊断/补全内置分析器。
- [ ] 单元测试覆盖正常、边界、失败、取消与恶意输入；`./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test` 全绿并贴用例数。
- [ ] 不引入 Android 依赖：三个纯 JVM 模块 `dependencies` 中不得出现 Android artifacts。

## 非目标

- 不实现 Compose UI（TASK-105/107/108）。
- 不实现 `:core:data` 的网络/DataStore/密钥存储（TASK-105 负责），但提供纯逻辑契约与解析器。
- 不实现 Python/JavaScript 运行时（ADR-003 P1 未决）；不实现 Termux/Linux（ADR-007）。
- 不实现插件签名/可执行插件（ADR-005）；不做远程下载。
- 不修改 15 份源文档、`content/packs/**`、`.github/**`、`docs/specs/**`（发现契约问题先报告）。

## 只读上下文（必须先读，不得修改）

1. `AGENTS.md`、`docs/01-PRD.md`（LRN/DIC/TRM/AIA/PLG 需求与验收摘要）。
2. `docs/02-ARCHITECTURE.md` §4/§5/§7/§8、`docs/05-DATA-CONTENT-PLUGIN.md`（§6/§7/§8/§10/§11）、`docs/07-SECURITY-RELEASE.md` §4/§5。
3. `docs/specs/{module-layout,knowledge,terminal,ai-provider,plugin-api}.md`（本卡的验收依据）。
4. `docs/decisions/ADR-003`、`ADR-004`、`ADR-005`、`ADR-006`。
5. 现有契约：`core/domain/src/main/kotlin/com/codetree/domain/**`、`plugin-api/src/main/kotlin/com/codetree/plugin/**`、`runtime/api/src/main/kotlin/com/codetree/runtime/api/**`。

## 输入与契约

- 输入契约：既有 `AppResult`/`AppError`、领域模型、`RuntimeContracts.kt`、`plugin-api` 类型（不得改签名；需要变更走 `CONTRACT_CHANGE`，同步 UI 与文档）。
- 输出契约：搜索 `TermSearchResult` 语义、`TopicGraph` 各方法语义、`CodepackValidator` 流水线、safe-shell 命令与错误码（`docs/specs/terminal.md` §2/§6）、`SyntaxAnalyzer` 行为。
- 测试替身：AI 用本地 Mock SSE 字符串，不依赖网络；内容用最小 fixture（不读取 `content/packs/**` 做单测，避免内容变更导致测试抖动）。

## 允许改动文件（写范围）

```text
core/domain/**
plugin-api/**
runtime/api/**
runtime/safe-shell/**
```

只读：`app/**`、`core/data/**`、`core/designsystem/**`、`feature/**`、`content/**`、`tools/**`、`gradle/**`、`.github/**`、`docs/**`。

## 禁止事项

- 不把 Android/Compose 依赖加入纯 JVM 模块；不调用系统 shell/`ProcessBuilder`；不执行任意文件。
- 不修改用户进度/内容的语义以让测试变绿；不放宽断言；不删除负例。
- 不引入 Hilt/Room/第三方图搜索库；不升级版本矩阵。
- 不读取、打印或提交任何真实密钥；AI 测试只用假密钥与 Mock。
- 不执行 `git commit`/`git push`；不修改 `settings.gradle.kts`/版本目录（共享文件）。

## 子任务

| 子任务 ID | 行为 | 输出 | 状态 |
| --- | --- | --- | --- |
| TASK-106-A | 图/搜索/进度/黄金路径 | 领域逻辑 + 单测 | Backlog |
| TASK-106-B | AI 契约与 SSE 解析/错误映射 | 纯 JVM 解析器 + 单测 | Backlog |
| TASK-106-C | Codepack 校验器与依赖解析 | `CodepackValidator` 实现 + 负例 | Backlog |
| TASK-106-D | safe-shell 白名单/沙箱/限制/取消 | Runtime 实现 + 单测 | Backlog |
| TASK-106-E | 内置 SyntaxAnalyzer 与示例运行契约 | `SyntaxAnalyzer` 实现 | Backlog |
| TASK-106-F | 汇总测试与交接 | 用例数 + 命令输出 | Backlog |

## 可执行验收标准

1. 纯 JVM 测试全绿：
   `cd /root/workspace/easy-code && ./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test`，贴输出与用例数（Gradle 报告或 `build/test-results` 统计）。
2. 图与搜索：循环/自环/断链/超长输入/空查询/仅标点/中英混合/取消旧查询等负例通过；排序层级严格符合 `docs/specs/knowledge.md` §8。
3. 插件安全：Zip Slip、绝对路径、`..`、符号链接、NUL、超限条目/文件/总量/压缩比、占位哈希、未知能力、依赖缺失/循环/冲突均有负例断言。
4. 终端安全：路径越界/符号链接/绝对路径/`rm` 越界/超输出/超时长/取消后无后续事件/并发上限均有断言；代码中无系统 shell 调用（可检索 `ProcessBuilder`/`Runtime.getRuntime`）。
5. AI：SSE 分片/跨包/多行 data/空增量/`[DONE]`/断流/401/429/5xx/超时映射正确；错误均为 `AppError` 子类。
6. 依赖纯净：`./gradlew :core:domain:dependencies` 等无 Android artifacts；模块 `build.gradle.kts` 未新增非白名单依赖。

## 验证计划

| 测试 ID | 层级 | 命令/步骤 | 预期 | 证据位置 |
| --- | --- | --- | --- | --- |
| TST-106-001 | 单元 | `./gradlew :core:domain:test` | 全绿 + 用例数 | 命令输出 |
| TST-106-002 | 单元 | `./gradlew :plugin-api:test` | 全绿 + 负例通过 | 命令输出 |
| TST-106-003 | 单元 | `./gradlew :runtime:safe-shell:test` | 全绿 + 取消/越界断言 | 命令输出 |
| TST-106-004 | 静态检查 | 检索 `ProcessBuilder`/`Runtime.getRuntime` | 0 命中 | 命令输出 |
| TST-106-005 | 依赖 | `./gradlew :core:domain:dependencies` | 无 Android artifacts | 输出 |

## 完成核对

- [ ] 三项测试全绿并贴用例数；负例真实存在（不是只测 happy path）。
- [ ] 无系统 shell、无 Android 依赖、无第三方图库/DI 框架。
- [ ] 契约与 `docs/specs/*` 一致；发现不一致时已提交 `CONTRACT_CHANGE` 并同步文档/消费端。
- [ ] 未越界修改；未伪造测试输出。
- [ ] 交接记录完整。

## 交接说明（HANDOFF 要点）

- 当前状态与完成项/未完成项分列。
- 修改文件：`core/domain/**`、`plugin-api/**`、`runtime/**`。
- 契约变化：无 / 附 `CONTRACT_CHANGE` 编号与影响范围。
- 测试证据：三条命令输出、用例数、负例清单。
- 风险：白名单命令语义与真实 Unix 有差异，需在 `help` 与文档中如实说明；任何放宽都需要 ADR/契约更新。
- 下一步：通知 shell/tools/learn 契约已稳定可集成。

## 执行日志

- 2026-11-15：任务 Ready，等待 engine 认领；domain 契约已由 Lead 落盘。
