# 任务卡：TASK-105 Android App Shell + 设计系统 + 数据层（T5）

> 使用前复制到项目实际任务目录。一个任务只保留一个当前负责人；子任务分别建卡。
> 模板：`docs/templates/TASK_CARD.md`；本卡对应团队任务 **T5**。

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 任务卡编号 | TASK-105 |
| 团队任务 | T5（`team_task` id：task-5） |
| 关联 WBS | CT-000-02/05/06/07（工程/设计令牌/导航/通用状态）、CT-010-02/03（内容加载与安装）、CT-300-04（原子安装契约）、CT-700-01/02/05（视觉/引导/可访问性） |
| 关联需求 | UI-001～UI-005、NFR-004、NFR-007、PKG-003/005、LRN-004（进度持久化） |
| 优先级 | P0 |
| 状态 | Ready（依赖已冻结，可认领） |
| 当前负责人 | shell |
| 评审人 | lead + qa（设计系统由 designsystem 负责人自审外，必须由 qa 独立验收） |
| 目标里程碑 | M1 + M2 前置 |
| 前置依赖 | TASK-101（契约）、TASK-103/104 内容格式（可先用骨架包）、TASK-102 工具链（出包验证） |
| 后续消费者 | TASK-107、TASK-108、TASK-109、TASK-110 |

## 背景与用户价值

产品的第一印象与“苹果式极简”体验由 App Shell、设计系统与首启引导决定；同时数据层负责把内容包安全安装、把进度与设置可靠持久化——这是 M2 离线学习闭环的地基。本任务交付可启动、可导航、五态齐全、可出 APK 的应用骨架，并提供 `ContentRepository`/`ProgressRepository`/`SettingsRepository`/`LibraryRepository` 的 MVP 实现。

## 目标

- [ ] `:core:designsystem`：实现 `CodeTreeTheme` 与 `docs/specs/design-system.md` 的令牌/组件（卡片、大标题导航、底部 5 Tab、分段控件、空态/错误态/离线态、引导页组件、图例）。
- [ ] `:app`：`Application` + `MainActivity` + 唯一 `NavHost`（5 Tab 路由）+ `AppContainer` 手写注入；三页首启引导与学习方向选择（持久化、可跳过、可重看）；设置页（深色/字号/减少动效/AI 配置入口/知识库入口）。
- [ ] `:core:data`：资产/目录内容包加载、`.codepack` 校验安装（复用 `:plugin-api` 校验器）、内存索引、DataStore 进度与设置、内容安装状态（原子切换、失败回滚）。
- [ ] 通用状态：Loading/Content/Empty/Error/Offline 五态组件与真实页面接线（PRD UI-004）。
- [ ] 双语资源（`zh-CN`/`en`）与终端深色主题基础；主流程无截断、触控 ≥48dp。
- [ ] `./gradlew :app:assembleDebug` 通过并贴输出（本机或 GitHub Actions 备用通道，注明）。

## 非目标

- 不实现图算法、搜索排序、SSE 解析（TASK-106 领域引擎）；UI 只消费接口。
- 不实现知识树画布/词典/终端/AI/知识库具体页面（TASK-107/108）。
- 不引入 Hilt、Room、WorkManager（ADR-006/009：M1 暂缓，M2 评估）。
- 不实现真实远程下载（内容目录与内置包先行；远程 URL 下载后续回填）。
- 不修改 15 份源文档、`content/packs/**`、`tools/content/**`、`.github/**`。

## 只读上下文（必须先读，不得修改）

1. `AGENTS.md`、`docs/01-PRD.md`（UI/信息架构/核心旅程）、`docs/02-ARCHITECTURE.md` §3/§4/§9。
2. `docs/04-WBS.md` CT-000/CT-010/CT-700 相关任务。
3. `docs/specs/module-layout.md`（依赖方向、注入约定）、`docs/specs/design-system.md`（令牌与组件）、`docs/specs/knowledge.md`（内容格式与安装状态机）、`docs/specs/plugin-api.md`（校验契约）。
4. `docs/decisions/ADR-001`（版本矩阵）、`ADR-004`（AI 密钥）、`ADR-006`（无 Room）、`ADR-009`（模块与手写 DI）。
5. 现有代码：`settings.gradle.kts`、各模块 `build.gradle.kts`、`core/domain/**` 契约、`plugin-api/**` 契约、`runtime/api/**`。

## 输入与契约

- 输入契约：`:core:domain` 的 `ContentRepository`/`ProgressRepository`/`SettingsRepository`/`LibraryRepository`/`AppSettings`/`AppError`/`AppResult`（已落盘，不得改签名）。
- 输出契约：`:core:designsystem` 的 `CodeTreeTheme` 与组件 API；`:app` 导航路由与 `AppContainer`；`:core:data` 的仓库实现与安装状态文件格式（见 `docs/specs/knowledge.md` §7）。
- 共享文件：`settings.gradle.kts`、`gradle/libs.versions.toml`、`gradle.properties`、根 `build.gradle.kts` 由 Lead 所有；改动需提交请求。模块 `build.gradle.kts` 由本卡负责人修改。
- 测试替身：内容包可用最小 fixture；DataStore 可用 `app.cash.turbine`? 不引入新依赖，使用 `runTest` + 临时目录。

## 允许改动文件（写范围）

```text
app/**
core/designsystem/**
core/data/**
```

只读：`core/domain/**`（引擎正在补齐契约，仅消费）、`plugin-api/**`、`runtime/**`、`feature/**`、`content/**`、`tools/**`、`docs/**`、`.github/**`、`gradle/**`。

## 禁止事项

- 不修改 `:core:domain` 契约签名（需要变更走 `CONTRACT_CHANGE`，由 engine 实施）。
- 不引入 Hilt/Room/WorkManager/第三方 UI 库；不升级版本矩阵。
- 不申请全盘存储/通讯录/位置/麦克风权限；文件导入只用 SAF。
- 不把密钥写入明文 DataStore/日志/资源；不伪造 AI 已配置状态。
- 不执行 `git commit`/`git push`；不修改 `.gitignore`。
- 不把“编译通过”当“页面验收通过”；必须给出可运行证据或截图/预览。

## 子任务

| 子任务 ID | 行为 | 输出 | 状态 |
| --- | --- | --- | --- |
| TASK-105-A | 设计系统令牌与主题 | `CodeTreeTheme` + 令牌 | Backlog |
| TASK-105-B | 通用组件与五态 | 卡片/导航/Tab/分段/空错离线/引导组件 | Backlog |
| TASK-105-C | App 壳与导航 | Application/MainActivity/NavHost/AppContainer | Backlog |
| TASK-105-D | 数据层 | 内容加载/安装/索引/DataStore/密钥存储 | Backlog |
| TASK-105-E | 首页/设置/引导接线 | 可操作流程 + 构建证据 | Backlog |

## 可执行验收标准

1. 构建：`cd /root/workspace/easy-code && ./gradlew :app:assembleDebug` 通过，贴输出与 APK 路径；若本机工具链未就绪，用 GitHub Actions 产物并明确标注。
2. 设计系统：`:core:designsystem` 编译通过；页面代码检索无硬编码颜色（如 `Color(0x`）/圆角/时长；五态组件有 `@Preview`。
3. 导航与引导：5 Tab 可切换；三页引导可跳过、完成状态持久化（重启后不再出现）；设置页可重看；方向选择写入 `AppSettings.activeDirection`。
4. 数据层：
   - 加载内置内容包并在日志/调试页显示 topics/terms 数量（真实数字，不写死）；
   - 导入合法 `.codepack` 成功、导入损坏包失败且旧内容可读（负例证据）；
   - 进度写入/重启恢复（DataStore）；设置项（主题/字号/减少动效/双语模式）重启保持。
5. 状态与可访问性：五态在真实页面可触发（空内容/无网络/错误/加载）；触控目标 ≥48dp；深色模式与最大字号下主流程无截断。
6. 边界：`git status --short` 只出现本卡写范围；`apk` 不含密钥/测试后门。

## 验证计划

| 测试 ID | 层级 | 命令/步骤 | 预期 | 证据位置 |
| --- | --- | --- | --- | --- |
| TST-105-001 | 构建 | `./gradlew :app:assembleDebug` | 成功 + APK 路径 | 构建输出 |
| TST-105-002 | 设计系统 | 代码检索 + Preview | 无硬编码令牌；五态预览 | 报告/截图 |
| TST-105-003 | 导航/引导 | 手动或 UI 测试 | 5 Tab、引导持久化 | 测试记录 |
| TST-105-004 | 数据层 | 加载/导入/重启 | 数字真实、失败回滚、持久化 | 日志/测试输出 |
| TST-105-005 | 可访问性 | 深色/大字号/TalkBack | 无截断、主按钮可朗读 | 截图/说明 |
| TST-105-006 | 边界 | `git status --short` | 未越界 | 输出 |

## 完成核对

- [ ] APK 或等价构建证据已贴；`assembleDebug` 输出真实。
- [ ] 五态与引导在真实页面接线，不是孤立组件。
- [ ] 数据层安装失败回滚、进度持久化有负例证据。
- [ ] 无密钥明文、无越权权限、无 Hilt/Room/WorkManager 依赖。
- [ ] 文档（CONTEXT/README 状态）已按需同步（由 Lead 统一提交前处理）。
- [ ] 交接记录完整。

## 交接说明（HANDOFF 要点）

- 当前状态：In Progress / Review；完成项与未完成项分列。
- 修改文件：`app/**`、`core/designsystem/**`、`core/data/**`（列真实路径）。
- 契约变化：无（如确需变化，附 `CONTRACT_CHANGE` 与受影响模块）。
- 测试证据：构建命令、APK、导入负例、持久化验证。
- 风险：本机工具链若不可用，出包证据来自 GitHub Actions，需在发布说明中标注。
- 下一步：通知 learn/tools 可依赖组件与容器；通知 qa 开始 UI 验收。

## 执行日志

- 2026-11-15：任务 Ready，等待 shell 认领。
