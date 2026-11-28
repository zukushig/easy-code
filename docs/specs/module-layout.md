# 模块布局与依赖契约（module-layout）

> 本文件是 ADR-009 的可执行细化，描述 MVP 的 13 个 Gradle 模块、依赖方向、包名、公开入口与文件所有权。**与代码骨架（`settings.gradle.kts`、各模块 `build.gradle.kts`）不一致时，先停下核对**：以代码实际状态为准，同轮修正本文件或提交 `CONTRACT_CHANGE`。`docs/02-ARCHITECTURE.md` 是规划基线，本文件与 ADR-009 记录 MVP 收敛后的实际结构。

## 1. 模块清单（13 个）

| Gradle 路径 | 类型 / 插件 | 命名空间（包名） | 职责 | 负责人 |
| --- | --- | --- | --- | --- |
| `:app` | Android application（`android.application` + `kotlin.android` + `kotlin.compose` + `kotlin.serialization`） | `com.codetree.app` | 组合根：`Application`、`MainActivity`、唯一 `NavHost`、`AppContainer`、assets 种子内容、权限声明 | shell |
| `:core:domain` | **纯 JVM**（`kotlin.jvm` + `kotlin.serialization`） | `com.codetree.domain` | 图模型与遍历、词典搜索排序、进度模型、AI 契约与 SSE 解析、内容仓库接口、统一 `AppError`/`AppResult` | engine |
| `:core:data` | Android library（`android.library` + `kotlin.android` + `kotlin.serialization`） | `com.codetree.data` | 内容包加载/安装/索引、DataStore 进度与设置、AI Provider 网络实现、密钥加密存储、知识库下载 | shell |
| `:core:designsystem` | Android library（`android.library` + `kotlin.android` + `kotlin.compose` + `kotlin.serialization`） | `com.codetree.designsystem` | 设计令牌、通用组件、动效、减少动效降级、图例/空态/错误态组件 | shell |
| `:plugin-api` | **纯 JVM**（`kotlin.jvm` + `kotlin.serialization`） | `com.codetree.plugin` | Codepack 清单、贡献类型、校验器契约、`SafePath`、`SemVer`、`LanguageSyntax`、`LocalizedText` | engine |
| `:runtime:api` | **纯 JVM**（`kotlin.jvm`） | `com.codetree.runtime.api` | `RuntimePlugin`/`RuntimeSession`/`RunRequest`/`RuntimeEvent`/`SyntaxAnalyzer` 契约 | engine |
| `:runtime:safe-shell` | **纯 JVM**（`kotlin.jvm` + `kotlin.serialization`） | `com.codetree.runtime.safe` | P0 白名单命令、沙箱文件系统、资源上限、取消语义 | engine |
| `:feature:learn` | Android library | `com.codetree.feature.learn` | 知识树 Canvas、节点详情、学习路径与进度 UI | learn |
| `:feature:dictionary` | Android library | `com.codetree.feature.dictionary` | 搜索页、术语详情、三模式双语对照 | learn |
| `:feature:terminal` | Android library | `com.codetree.feature.terminal` | 终端会话 UI、高亮/诊断/补全、运行状态 | tools |
| `:feature:assistant` | Android library | `com.codetree.feature.assistant` | AI 对话、AI 设置、上下文确认 | tools |
| `:feature:library` | Android library | `com.codetree.feature.library` | 知识库目录/安装/删除、`.codepack` 导入、插件管理、存储管理 | tools |

四个纯 JVM 模块（`:core:domain`、`:plugin-api`、`:runtime:api`、`:runtime:safe-shell`）不使用 Android 插件、不依赖 Android SDK 类型，是“低内存设备可跑核心单测”的基础（无 SDK 单测通道见 ADR-009 §5 与 TASK-102）。

## 2. 依赖方向（强制）

```text
:app
 ├─→ :feature:learn / :feature:dictionary / :feature:terminal / :feature:assistant / :feature:library
 │     ├─→ :core:data ──→ :core:domain ──→ :plugin-api
 │     │                  └──────────────→ (仅 LocalizedText / PackManifest 等契约类型)
 │     ├─→ :core:designsystem ──→ :core:domain（仅展示模型/枚举）
 │     └─→ :runtime:api + :runtime:safe-shell（仅 :feature:terminal）
 └─→ :core:data、:core:designsystem、:core:domain、:plugin-api、:runtime:*（组合根可直连）
```

允许的边（白名单）：

| 消费方 | 允许依赖 |
| --- | --- |
| `:app` | 全部模块（组合根） |
| `:feature:learn` | `:core:domain`、`:core:data`、`:core:designsystem` |
| `:feature:dictionary` | `:core:domain`、`:core:data`、`:core:designsystem` |
| `:feature:terminal` | `:core:domain`、`:core:data`、`:core:designsystem`、`:runtime:api`、`:runtime:safe-shell` |
| `:feature:assistant` | `:core:domain`、`:core:data`、`:core:designsystem` |
| `:feature:library` | `:core:domain`、`:core:data`、`:core:designsystem`、`:plugin-api` |
| `:core:data` | `:core:domain`（`api`）、`:plugin-api`（`api`） |
| `:core:domain` | `:plugin-api` |
| `:core:designsystem` | `:core:domain`（仅展示模型/枚举） |
| `:runtime:safe-shell` | `:runtime:api` |
| `:plugin-api`、`:runtime:api` | 无项目依赖 |

禁止的边（评审必须拒绝）：

1. `feature:*` → `feature:*`（跨功能直接依赖）；跨功能跳转由 `:app` 导航回调完成。
2. `:core:domain` → `:core:data` / Android 类型 / `feature:*`；`:core:domain` 必须保持纯 JVM 可编译。
3. `:plugin-api` / `:runtime:api` → 任何 Android 模块或 `feature:*`。
4. `:core:designsystem` → `:core:data` / `runtime:*` / `feature:*`。
5. 任何模块 → `:app`（组合根只被依赖，不反向依赖）。

## 3. 公开入口与注入约定（M1 手写 DI）

- `:app` 持有 `AppContainer`：创建 `:core:data` 的仓库实现（`ContentRepository`、`ProgressRepository`、`SettingsRepository`、`LibraryRepository`）、`:core:domain` 用例、`RuntimeRegistry`、`AiProvider` 与密钥存储，并暴露给导航层。
- `:feature:*` 对外只暴露：路由常量/参数类型 + `@Composable` 入口 + 构造注入依赖的 `ViewModel`。**不暴露内部实现类**给其他 feature。
- `:app` 的 `NavHost` 负责把 `AppContainer` 的接口实例传给 feature 的 ViewModel 工厂；feature 不持有全局单例，不使用业务 Service Locator。
- 共享状态（选中节点、双语模式、进度）通过仓库接口与 `StateFlow` 暴露，feature 不复制业务状态。
- M2 评估 Hilt 的触发条件见 ADR-009 §4；评估前不得在 feature 内引入自定义注入框架。

## 4. 构建配置（与 ADR-001 一致）

| 项 | 值 | 落点 |
| --- | --- | --- |
| `compileSdk` / `targetSdk` | 34 / 34 | 所有 Android 模块 |
| `minSdk` | 26 | 所有 Android 模块 |
| `applicationId` / `versionName` / `versionCode` | `com.codetree.app` / `0.1.0` / 1 | `:app` |
| JDK 与 Kotlin 目标 | 17 | 所有模块 |
| AGP / Kotlin / Compose BOM / Gradle | 8.6.0 / 2.0.20 / 2024.09.02 / 8.9 | `gradle/libs.versions.toml` |
| 低内存参数 | `-Xmx1536m`、`workers.max=1`、`parallel=false`、`configuration-cache=false` | `gradle.properties` |
| Release 签名 | MVP 阶段无正式 keystore，release 使用 debug 签名，正式签名由 CI secrets 注入 | `:app`（发布前必须替换，见 ADR/`docs/07`） |

共享文件（只有 Lead 可改，其他 Agent 提交修改请求）：`settings.gradle.kts`、`build.gradle.kts`（根）、`gradle.properties`、`gradle/libs.versions.toml`、`gradle/wrapper/**`、`/root/workspace/easy-code` 之外的构建工具链。

模块内文件所有权：每个模块的 `build.gradle.kts`、`src/**` 由该模块负责人修改；`app/src/main/assets` 的内容清单与 `content/**` 由内容负责人与 shell 协商登记。

## 5. 内容与资源接线

- 仓库根 `content/packs/<packId>/` 是内容事实来源；`content/index.json` 是打包输入清单；`content/dist/*.codepack` 与 `content/dist/catalog.json` 由 `tools/content/build_dist.py` 生成。
- `:app` 通过 assets 或构建拷贝把种子包（`BUNDLED` 来源）随 APK 分发；`LibraryRepository` 负责安装到应用私有目录并构建内存索引（ADR-006）。
- 内容文件格式见 `docs/specs/knowledge.md`；任何内容 Schema 变化必须先改规范与 `:core:domain` 模型，再改内容与打包工具。

## 6. 验证与门禁

- 模块完整性：`./gradlew projects` 输出应包含本文件列出的 13 个模块。
- 构建证据：`./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test`（纯 JVM 核心逻辑）；`:app:assembleDebug` 出包由 TASK-102/105 负责。
- 依赖方向抽查：对新增依赖执行 `./gradlew :<module>:dependencies`，与上表白名单比对；违反白名单的依赖不得合并。
- 文档一致性：本文件、ADR-009、`settings.gradle.kts`、各模块 `build.gradle.kts` 必须一致；新增模块或调整依赖方向必须先修订 ADR-009。
