# ADR-009：MVP 工程落地与技术收敛（Lead 拍板）

| 字段 | 内容 |
| --- | --- |
| 记录编号 | ADR-009（新增，非执行摘要决策索引原有条目；由 Lead 在 MVP 启动时拍板） |
| 状态 | Accepted（已接受，M1/M2 生效） |
| 日期 | 2026-11-15（Asia/Shanghai） |
| 提案人 | Lead（协调者） |
| 评审人 | ARCH、buildsmith、shell、engine、learn、tools、qa |
| 关联任务/需求 | TASK-101～TASK-110；CT-000-02/03/05/06、CT-010-02、CT-800-07 |
| 替代关系 | 对 `docs/02-ARCHITECTURE.md` §3 模块清单与 §2 DI 选型的收敛；不替代 ADR-001～ADR-008 |

## 背景

`docs/02-ARCHITECTURE.md` 在规划阶段给出 17 项模块拆分（含 `core:common`、`core:database`、`core:network`、`core:security`、`core:navigation`、`feature:plugins`、`feature:downloads`、`content:seed`）并建议 Hilt。进入 MVP 实施后出现三个可观察事实：

1. 构建环境是 aarch64 手机 proot、约 1.4GB 可用内存；Android 模块越多、注解处理越多，配置与编译成本越高，OOM 风险越大（ADR-001、ADR-006 已记录）。
2. M1/M2 范围不需要 Room、WorkManager 断点续传、Hilt 的复杂依赖图；先用最小可用结构交付“可启动、可导航、离线学习闭环”，比先铺满 17 个模块更符合“先闭环、再扩展广度”的产品原则。
3. 后端/网络事实来源变化：MVP 无 Room、无真实远程目录后端、无 AI 后端；`docs/02` 的 `core:network`、`core:database`、`feature:downloads` 在 MVP 内没有独立存在的必要，强行保留会产生空模块与跨模块空转。

仓库骨架已按本 ADR 落盘（`settings.gradle.kts` 的 include 列表共 13 个模块），本 ADR 负责把“已实施的结构”与“对原架构文档的偏差”写成可追溯决策，防止后续 Agent 把偏差当成疏漏再改回去。

## 决策驱动因素

- **低内存设备可构建、可验证**：模块数量与注解处理越少越好；纯 JVM 模块可在没有 Android 设备/模拟器的情况下跑单测。
- **依赖方向清晰**：feature → data → domain → plugin-api；runtime 独立；避免循环依赖和 feature 间直接依赖。
- **首版闭环**：先交付 M1/M2，Room、Hilt、真实下载链路、签名插件等推迟并在 ADR-006/005/007 中记录。
- **多 Agent 并行**：模块所有权必须与团队名册对齐，减少共享文件冲突。
- **可演进**：推迟的能力要有明确回填条件（M2 评估 Hilt/Room），不是永久删除。

## 候选方案

| 方案 | 优点 | 代价/风险 | 验证结果 |
| --- | --- | --- | --- |
| A：严格按 `docs/02` 的 17 个模块 + Hilt 落地 | 与架构文档完全一致 | 空模块多；KSP/Hilt 构建成本高；低内存设备风险大；M1 交付慢 | 不采用 |
| B：13 模块收敛布局 + 手写 AppContainer + 纯 JVM 领域层（本决策） | 构建轻、依赖清晰、纯 JVM 可测；与团队任务一一对应 | 手写 DI 随 feature 增多会膨胀；与原 docs/02 有偏差需要 ADR 解释 | 骨架已落盘，模块结构见 `settings.gradle.kts`；构建验证由 TASK-102/105 给出 |
| C：单模块 `app` 全包（不做模块拆分） | 最快出包 | 无法并行开发；无法隔离纯 JVM 单测；契约边界模糊 | 不采用 |
| D：Gradle 复合构建（composite build）拆分 JVM 子工程 | 可完全隔离 Android 依赖 | 复杂度高、团队不熟悉；MVP 不需要 | 不采用；作为“无 SDK 单测通道”的备选方案记录 |

## 决策

### 1. 模块布局（13 个 Gradle 模块）

| 模块 | 类型 | 职责 | 负责人 | 包名 |
| --- | --- | --- | --- | --- |
| `:app` | Android application | 组合根、唯一 NavHost、AppContainer、Application/MainActivity、assets 种子内容 | shell | `com.codetree.app` |
| `:core:domain` | **纯 JVM**（`kotlin-jvm`） | 图模型与遍历/路径、词典规范化与搜索排序、学习进度模型、AI SSE 解析与错误映射、黄金路径用例 | engine | `com.codetree.domain` |
| `:core:data` | Android library | 内容加载与安装（JSON/codepack）、内存内容索引、DataStore 进度与设置、AI Provider 网络实现、密钥加密存储 | shell | `com.codetree.data` |
| `:core:designsystem` | Android library | 设计令牌、通用组件、动效、减少动效降级 | shell | `com.codetree.designsystem` |
| `:plugin-api` | **纯 JVM** | Codepack 清单、贡献类型、校验器契约、`SafePath`、`SemVer`、`LanguageSyntax`、`LocalizedText` | engine | `com.codetree.plugin` |
| `:runtime:api` | **纯 JVM** | `RuntimePlugin`/`RuntimeSession`/`RunRequest`/`RuntimeEvent`/`SyntaxAnalyzer` 契约 | engine | `com.codetree.runtime.api` |
| `:runtime:safe-shell` | **纯 JVM** | P0 白名单安全命令、沙箱文件系统、资源与取消 | engine | `com.codetree.runtime.safe` |
| `:feature:learn` | Android library | 知识树画布、节点详情、学习路径与进度 UI | learn | `com.codetree.feature.learn` |
| `:feature:dictionary` | Android library | 搜索页、术语详情、三模式双语对照 | learn | `com.codetree.feature.dictionary` |
| `:feature:terminal` | Android library | 会话 UI、高亮/诊断/补全、运行状态 | tools | `com.codetree.feature.terminal` |
| `:feature:assistant` | Android library | AI 对话、设置、上下文确认 | tools | `com.codetree.feature.assistant` |
| `:feature:library` | Android library | 知识库目录/安装/删除、`.codepack` 导入、插件管理、存储管理 | tools | `com.codetree.feature.library` |

### 2. 依赖方向（强制）

```text
:app
 ├─→ :feature:*（learn / dictionary / terminal / assistant / library）
 │     ├─→ :core:data ─→ :core:domain ─→ :plugin-api
 │     ├─→ :core:designsystem
 │     └─→ :runtime:api / :runtime:safe-shell（仅 terminal）
 └─→ :core:data、:core:designsystem、:core:domain、:plugin-api、runtime
```

- `feature:*` 之间**不得互相依赖**；跨 feature 跳转由 `:app` 的导航回调完成。
- `:core:designsystem` 只可依赖 `:core:domain` 的纯展示模型/枚举（用于主题与语言模式绑定），不得依赖 `:core:data`、`runtime:*` 或任何 `feature:*`；`plugin-api` 与 `runtime:api` 不依赖任何 Android 模块。
- `:core:data` 可以依赖 `:core:domain` 与 `:plugin-api`；`:core:domain` 只依赖 `:plugin-api`。
- `:app` 只做组合，不承载业务逻辑；不把 feature 的内部类型暴露给其他 feature。

### 3. 对 `docs/02` 模块清单的差异与理由

| `docs/02` 原模块 | 落地位置 | 理由 |
| --- | --- | --- |
| `core:common` | 合并进 `:core:domain`（Result/错误类别/时间与调度抽象）+ `:app`（协程调度器配置） | 避免空模块；纯 JVM 领域层天然承载通用类型 |
| `core:database` | 合并进 `:core:data`（MVP 无 Room，见 ADR-006） | JSON 加载 + 内存索引 + DataStore 已覆盖 MVP 数据需求 |
| `core:network` | 合并进 `:core:data`（OkHttp SSE/下载）；纯解析与错误映射放 `:core:domain` | 网络实现是数据层细节；解析逻辑必须纯 JVM 可测 |
| `core:security` | 密钥加密/秘密存储 → `:core:data`；路径安全 → `:plugin-api`（`SafePath`）；终端沙箱 → `:runtime:safe-shell` | 按能力拆到最接近消费方的模块，避免“安全上帝模块” |
| `core:navigation` | 合并进 `:app`（唯一 NavHost）+ 各 feature 暴露 route/参数常量 | 路由是组合根职责；跨 feature 依赖用回调，降低耦合 |
| `feature:plugins`、`feature:downloads` | 合并为 `:feature:library` | 同一入口“我的/知识库”；M1/M2 先落地本地目录与导入，远程下载后续回填 |
| `content:seed` | 仓库根 `content/packs/**` + `tools/content` + `:app` assets | 内容不是代码模块；内容工具与校验独立演进 |

**不变式**（不得因合并而破坏）：`feature:*` 不直接访问其他功能的内部实现；`plugin-api`/`runtime:api` 不依赖 `feature:*`；`app` 只组合；公开契约变更必须先改 ADR/契约文档再改实现。

### 4. 依赖注入：M1 暂缓 Hilt，使用手写 AppContainer

- `:app` 内实现 `AppContainer`：持有 `:core:data` 的仓库实例、`:core:domain` 用例、`RuntimeRegistry`、AI Provider 与设置/密钥存储。
- `:feature:*` 的 ViewModel 使用**构造函数注入**，依赖类型为 `:core:data`/`:core:domain` 中的接口；由 `:app` 的导航层创建 ViewModel 工厂并传入依赖。
- `:feature:*` 不持有全局单例，不使用 Service Locator 静态访问业务依赖（可测性优先）。
- **M2 评估 Hilt** 的触发条件：feature 模块与仓库依赖数量增长到手写工厂明显重复，或需要跨模块作用域管理；评估时必须同时评估 KSP 对低内存设备构建的影响（ADR-001）。评估结论写入新 ADR 或本 ADR 修订。

### 5. 纯 JVM 模块的单测保证

- `:core:domain`、`:plugin-api`、`:runtime:api`、`:runtime:safe-shell` 使用 `kotlin-jvm` 插件，不依赖 Android SDK、模拟器或设备；测试使用 JUnit 5（`junit-jupiter` + `junit-platform-launcher`，见版本目录）。
- 目标：`./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test` 可在只安装 JDK 17 与 Gradle 8.9 的环境执行核心逻辑验证。
- 注意：当前工程同时包含 Android 模块，Gradle 配置阶段仍需要 Android SDK 路径；因此“无 SDK 单测通道”由 TASK-102 提供并验证（独立仅含 JVM 模块的 settings 子构建或等价脚本，见后续行动），在本 ADR 中作为**待验证项**记录，不得在本机验证完成前宣称已达成。

## 理由

- 13 模块布局与团队名册一一对应，每个模块有明确负责人与写入范围，减少并行冲突（`docs/03` 的共享文件协议）。
- `:core:domain` 纯 JVM 是“低内存设备可验证”的关键：图算法、搜索排序、SSE 解析、安装状态机都不需要 Android 运行时。
- 合并 `core:database/network/security/navigation` 不是删除能力，而是把 MVP 不需要的独立模块收进消费方模块；每项推迟都有 ADR 记录回填条件。
- 手写 AppContainer 在 M1/M2 的依赖规模下成本低于 Hilt + KSP，且避免在低内存设备上增加构建内存与 KSP 失败面；M2 有明确评估触发条件，不是永久技术债。

## 后果

### 正面

- 模块少、构建轻、依赖方向单一，纯 JVM 单测可覆盖核心逻辑。
- feature 之间无直接依赖，UI 可并行开发；跨 feature 跳转集中在 `:app`，冲突面小。
- 与 `docs/05`/`docs/07` 的契约模块（`plugin-api`、`runtime:api`）保持纯 JVM，契约变更不影响 Android 构建。

### 负面与限制

- 与 `docs/02-ARCHITECTURE.md` §3 的模块清单存在偏差，新成员需要读本 ADR 才能理解为什么没有 `core:common` 等模块；`docs/02` 正文按“规划基线”保留原文（不修改，避免 merge 校验失败），差异以本 ADR 与 `docs/specs/module-layout.md` 为准。
- 手写 DI 在 feature 增多后会膨胀；M2 评估 Hilt 前需要接受一定样板代码。
- `:core:data` 同时承担内容、设置、网络与密钥，模块内包结构必须严格分层（见 `docs/specs/module-layout.md`），否则会退化为“什么都有”的 God module。
- `:feature:library` 合并了下载与插件，M4 回填远程下载时可能需要再次拆分（届时另立 ADR）。

### 对其他模块的影响

- 所有 `build.gradle.kts` 必须按本 ADR 的插件类型与依赖方向实现；`settings.gradle.kts`、`libs.versions.toml`、`gradle.properties` 为 Lead 所有的共享文件，模块 Agent 不得擅自修改（需要时提交修改请求）。
- `docs/specs/module-layout.md` 是本 ADR 的可执行细化（包名、公开入口、依赖矩阵、共享文件与所有权）。
- `docs/CONTEXT.md` 记录模块布局、名册与环境约束，供新 Agent 快速进入。
- QA 的构建验收以 13 模块为范围，不再按 `docs/02` 的 17 模块检查。

## 兼容与迁移

- 本 ADR 不改变内容 Schema、插件 API、运行时 API 的版本；`:plugin-api` 与 `:runtime:api` 已按契约落盘。
- 回填路径：M2 评估 Hilt、Room（ADR-006）、远程下载；M4 之后评估插件签名（ADR-005）与可执行运行时（ADR-003）；每次回填先修订/新增 ADR，再改模块结构。
- 回滚条件：13 模块布局导致构建或验证无法完成且无法在当轮修复时，允许临时把 Android feature 合并进 `:app` 但保留纯 JVM 模块与依赖方向，并记录偏差待修。

## 验证计划

- 构建：`./gradlew projects` 输出 13 个模块；`./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test` 通过（TASK-102/106 提供证据）。
- 依赖方向：`./gradlew :core:domain:dependencies`、`:feature:learn:dependencies` 检查无反向依赖；禁止 `feature→feature` 依赖由代码评审与构建约束保证。
- 纯 JVM 通道：TASK-102 验证“无 Android SDK 的仅 JVM 子构建/脚本”可跑测试，或明确记录不可行与替代证据。
- 推翻条件：低内存设备无法完成 13 模块的配置与编译，或手写 DI 在 M1 内已造成明显重复而阻塞交付（提前触发 Hilt 评估）。

## 后续行动

| 动作 | 负责人 | 前置依赖 | 完成证据 |
| --- | --- | --- | --- |
| 按 13 模块补齐构建脚本与依赖方向 | shell / engine / learn / tools | 本 ADR | `./gradlew projects` + 构建输出 |
| 提供并验证“无 SDK 纯 JVM 单测通道” | buildsmith | TASK-102 | `/root/toolchain/REPORT.md` + 命令输出 |
| 更新 `docs/specs/module-layout.md` 与 `docs/CONTEXT.md` | architect | 本 ADR | 文档交付 |
| M2 评估 Hilt 与 Room | ARCH / shell / engine | 性能与代码规模数据 | 评估记录 + 新 ADR |
| M4 回填远程下载/插件签名前先修订 ADR | tools / SEC / ARCH | M3 完成 | 新 ADR |
