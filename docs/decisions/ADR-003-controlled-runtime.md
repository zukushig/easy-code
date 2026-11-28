# ADR-003：受控运行时（D-003）

| 字段 | 内容 |
| --- | --- |
| 记录编号 | ADR-003（对应决策索引 D-003） |
| 状态 | Accepted（P0 部分已接受；Python/JavaScript 运行时部分 **Unresolved（ADR 未决）**，M3 后再评估） |
| 日期 | 2026-11-15（Asia/Shanghai） |
| 提案人 | ARCH（架构负责人） |
| 评审人 | Lead、engine（运行时内核）、tools（终端 UI）、QA |
| 关联任务/需求 | TASK-106、TASK-108；TRM-001、TRM-002、TRM-005、TRM-006、TRM-007、TRM-008、CT-400-01/02/04/05/11/12 |
| 替代关系 | 无；P1 运行时（Python/JavaScript）在本 ADR 内保持未决，后续以新增 ADR 决策 |

## 背景

终端是 CodeTree 的实践入口。PRD 与安全规范要求：P0 只实现白名单内置命令，不把输入拼接给系统 shell；工作区在应用私有目录，路径必须规范化；输出、时长、并发、文件大小有上限；取消必须可验证；页面退出后不得留下孤儿进程。同时，`docs/01-PRD.md` TRM-007 把 Python/JavaScript 运行时列为 P1，TRM-008 把 Termux/完整 Linux 列为 P2。

已确认事实：

- 本机是 aarch64 手机 proot，不具备可信的 PTY 与进程隔离能力；系统 shell 行为不可用于安全边界。
- 仓库已落盘 `:runtime:api`（`RuntimeContracts.kt`：`RuntimeKind`、`RuntimeDescriptor`、`ResourceLimits`、`RunRequest`、`RuntimeEvent`、`RuntimeSession`、`RuntimePlugin`、`RuntimeRegistry`、`SyntaxAnalyzer`）与 `:runtime:safe-shell` 模块骨架，二者均为纯 JVM。
- `docs/02-ARCHITECTURE.md` §8 与 `docs/07-SECURITY-RELEASE.md` §4 已明确 P0 安全模型。
- 终端 UI 的诊断与补全接口已在 `RuntimeContracts.kt` 中定义为 `SyntaxAnalyzer` + `LanguageSyntax` 数据（见 `docs/specs/terminal.md`）。

## 决策驱动因素

- **安全边界**：终端输入是不可信输入，不得进入系统 shell、不得逃逸应用私有目录。
- **零原生依赖**：低内存 aarch64 设备上不引入 Python/JS 原生库或解释器二进制，避免体积、许可与兼容风险。
- **可验证**：取消、超时、资源超限必须可观察、可测试，能给出退出状态而非“看起来结束”。
- **多 Agent 边界**：运行时 SPI 与 UI 分离，`:runtime:api` 不依赖任何 `feature:*`，`safe-shell` 可独立单测。

## 候选方案

| 方案 | 优点 | 代价/风险 | 验证结果 |
| --- | --- | --- | --- |
| A：纯 Kotlin 白名单 safe-shell（应用私有沙箱、资源上限、协程可取消） | 无原生依赖；无 PTY；路径与命令完全受控；纯 JVM 可单测；与低内存设备兼容 | 只能覆盖内置命令，不能运行任意程序；需要自行实现命令语义与错误模型 | 仓库骨架已就位，行为契约见 `docs/specs/terminal.md`，待 TASK-106 实现 |
| B：内置真实 PTY + 系统 shell | 体验接近桌面终端 | 与 `docs/07` P0 安全模型冲突；aarch64 proot 下隔离不可信；无法保证路径沙箱 | 不采用（违反安全门禁） |
| C：P0 直接集成 Python/JavaScript 运行时 | 学习价值高 | 需要原生库/解释器、许可评估、资源与取消模型；低内存设备体积与稳定性风险 | 不采用；列为 P1，ADR 未决 |
| D：Termux 作为外部依赖 | 复用生态 | 跨应用边界、权限与商店政策问题；用户数据与学习闭环不可控 | 不采用；见 ADR-007 |

## 决策

1. **P0 只启用 `:runtime:safe-shell`**：纯 Kotlin 实现，命令按白名单解析执行，不启动 PTY，不调用 `/system/bin/sh` 或任何系统 shell，不 `ProcessBuilder` 外部程序。
2. **沙箱文件系统**：所有文件命令只能访问 `RuntimeSession` 绑定的应用私有工作区（默认 `filesDir/terminal/workspace`）；路径先经 `SafePath.checkRelative` 规范化，再经 `isUnderRoot` 复核；绝对路径、`..`、符号链接、NUL 字节、Windows 驱动器前缀一律拒绝。
3. **资源上限**：默认使用 `ResourceLimits(maxOutputBytes = 64KiB, maxDurationMs = 5000L, maxFileBytes = 1MiB, maxSessions = 1)`；超限产生 `RuntimeEvent.LimitReached` 并以明确退出状态结束，不允许无限输出或无限等待。
4. **取消语义**：`RuntimeSession.cancel()` 幂等；取消后事件流以 `RuntimeEvent.Finished(exitCode, durationMs, cancelled = true)` 收敛，不得再追加 `Stdout`/`Stderr`；页面销毁必须触发取消并验证无遗留任务。
5. **运行时注册**：`RuntimeRegistry` 可注册多个 `RuntimePlugin`，但 P0 只注册 `BUILTIN_SHELL`；`PYTHON`/`JAVASCRIPT` 的 `RuntimeKind` 已预留在 API 中，无实现时 `RuntimeDescriptor.available = false`，UI 显示“未安装/不可用”，不得伪造成功输出。
6. **P1 运行时暂未决**：Python/JavaScript 运行时至少需回答许可、安装与卸载、资源限制、取消与崩溃恢复、网络与文件权限、包体积；在这些问题有结论前，保持本 ADR 的 P0 约束生效。

## 理由

- `docs/07-SECURITY-RELEASE.md` §4.1 明确要求“只实现白名单内置命令，不把输入拼接给系统 shell”。真实 PTY 与系统 shell 在本机 proot 环境下无法提供可信隔离，满足不了该约束。
- 纯 JVM 白名单实现可以用 JUnit 5 覆盖参数注入、路径穿越、超限与取消等负例，为低内存设备提供可复制证据。
- `RuntimeContracts.kt` 已经把执行、事件、限制、取消、诊断、补全拆成独立类型；白名单实现是最小可行的 `RuntimePlugin`，后续接入其他运行时不需要改 UI 契约。

## 后果

### 正面

- 终端行为可预测、可测试、无原生依赖，符合 P0 安全门禁。
- 运行时 SPI 与 UI 解耦，未来新增运行时只需要实现 `RuntimePlugin`。
- 资源上限与取消语义由类型系统承载，UI 不需要猜测进程状态。

### 负面与限制

- 学习者不能运行真实 Python/JavaScript 代码，产品价值受限；需要在页面明确标注“P0 仅内置安全命令”。
- 命令语义需要自行实现与维护（如 `ls`、`grep`、`head` 等），与真实 Unix 存在差异，帮助文案必须如实说明。
- 不支持管道、重定向、后台任务与作业控制；遇到这些语法应给出可读错误与替代建议。

### 对其他模块的影响

- `feature:terminal` 只依赖 `:runtime:api` 与 `:runtime:safe-shell`，不直接做文件 IO 或命令解析。
- `docs/specs/terminal.md` 固化白名单、错误码与取消语义，作为 engine 与 tools 的共同契约。
- `feature:learn` 的“运行示例”通过 `RuntimeRegistry` 调用，示例缺失运行时时显示明确状态。
- `plugin-api` 的 `runtime` 贡献类型在 P0 被拒绝（见 ADR-005），不得以插件方式绕过本 ADR。

## 兼容与迁移

- `RuntimeKind` 已预留 `PYTHON`/`JAVASCRIPT` 枚举值，未来接入时不需要修改既有类型。
- 若未来启用真实进程，必须修订本 ADR、更新 `docs/07` 威胁模型，并保留当前的路径与资源约束作为兜底。
- 回滚条件：safe-shell 无法覆盖 MVP 学习闭环所需的安全示例时，先扩充白名单命令或新增内置命令，不以引入系统 shell 作为回退。

## 验证计划

- 单元测试（`:runtime:safe-shell:test`）：白名单命中/拒绝、参数注入、引号与转义、`..`/绝对路径/符号链接、超长输出、超时、取消后无后续事件、并发会话上限。
- 集成测试（TASK-108）：会话 UI 的输入/输出/错误/清屏/历史，取消后状态收敛，资源超限提示。
- 文档验收：`docs/specs/terminal.md` 的命令清单与错误码与实际实现一致。
- 推翻条件：白名单命令无法支持 PRD 中“运行预置安全示例”的学习闭环，或资源限制导致正常示例不可用。

## 后续行动

| 动作 | 负责人 | 前置依赖 | 完成证据 |
| --- | --- | --- | --- |
| 实现 safe-shell 白名单、沙箱路径、资源与取消 | engine | 本 ADR + `docs/specs/terminal.md` | `:runtime:safe-shell:test` 输出 |
| 终端 UI 接入 SPI 并展示状态 | tools | engine 契约 | 构建输出 + 验收说明 |
| P1 运行时（Python/JavaScript）新 ADR | ARCH | M3 后 | 新 ADR + 威胁模型 |
| 负例与安全测试入 QA 报告 | qa | TASK-106/108 | QA 报告 |
