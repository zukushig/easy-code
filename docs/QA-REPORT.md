# CodeTree MVP 验收报告（QA-REPORT）

| 项 | 内容 |
| --- | --- |
| 报告版本 | v1.1（T9 完成版，随仓库状态滚动更新） |
| 生成日期 | 2026-11-22（首版）/ 2026-11-28（T9 完成回填，Asia/Shanghai） |
| 生成者 | architect（兼任 QA/Release，团队任务 T9） |
| 评审 | lead（协调/发布）+ 各模块负责人交叉确认 |
| 仓库 | `zukushig/easy-code`（public；推送由 Lead 经 GitHub MCP 完成） |
| 对应任务 | TASK-109（CI/校验/验收）、TASK-110（集成/发布） |
| 结论口径 | 只写有证据的结论；没有证据的能力一律标“未验证”，不得把 Mock、构建通过或接口存在写成功能验收通过 |

## 1. 结论摘要

| 领域 | 状态 | 摘要 |
| --- | --- | --- |
| 文档基线（15 源文档 + 12 工程文件） | **已验证（T9 复跑 OK）** | 2026-11-15 基线重建与保真 diff（IDENTICAL）；2026-11-28 CI 改造后重生成：`Merged lines 3897`、`Archive 148703 bytes`、15 源文档/12 附录/90 WBS；`merge_docs.py --check` 与 `validate_workflows.py`（4 工作流 / 8 作业 / 1 复合动作）均 `Validation: OK` |
| 内容包（12 包 / 150 topics / 320 terms / 183 examples） | **已验证通过** | 全量校验 `errors=0`、`warnings=12`、`RESULT: PASS`；`build_dist.py` 生成 12 个 codepack 且自检 OK；`sync_seed.py` synced=8 / unchanged=4、`seedTotalBytes=242222`；QA `check_content.py` → errors 0 / warnings 0 → OK；`check_seed.py` → OK（12 codepacks / 12 catalog / 12 sha256sums） |
| QA 工具链（content / repo / seed / run_all） | **已验证通过** | 三个检查器 `--selftest` OK；`check_content` OK（errors 0 / warnings 0）；`check_repo` OK（扫描 285 个文本文件、errors 0、warnings 2）；`check_seed` OK；`run_all.sh` → 内容 PASS、验收结果“全部通过” |
| 本机构建通道 | **工具链已验证；全量编译通过、Debug APK 已产出并校验；发布链待 T10** | T2 已完成：JDK17 + Gradle 8.9 + Android SDK 34 + aarch64 aapt2 覆盖（本机 aapt2 覆盖写入 `/root/.gradle/gradle.properties` 用户级属性，不入仓库；CI 用 x86_64 官方 aapt2 无需覆盖）；**全量 `:app:assembleDebug` BUILD SUCCESSFUL（13 模块全部编译通过），`app-debug.apk` 12,484,073 bytes、sha256 `63f88b7e…4bd95`、191 entries、Debug 签名**；CI 适配为“系统 `gradle` 8.9 + 构建前生成内容产物”（§3.1）；Release 发布标注「待发布链验证（T10）」 |
| 纯 JVM 单测 | **已验证通过** | BUILD SUCCESSFUL；250 用例 / 0 失败 / 0 skipped（plugin-api 95、core:domain 63、runtime:safe-shell 92），2026-11-28 复跑 |
| 纯 JVM 单测 | **部分（运行中）** | 11 个测试文件已落盘（core:domain 4 / plugin-api 4 / runtime:safe-shell 3，约 180 用例）；实际运行结果待回填 |
| CI（4 工作流 + 复合动作） | **已适配并通过静态校验，未运行** | 本次完成 easy-code 工程适配（§3），`validate_workflows.py` 通过；尚未在 GitHub 首次运行，运行记录为空 |
| 安全与隐私门禁 | **部分** | 代码检索无系统 shell 调用（`ProcessBuilder`/`Runtime.getRuntime` 仅注释出现）；App 仅声明 INTERNET/ACCESS_NETWORK_STATE；`check_repo.py` 明文密钥扫描已运行（285 个文本文件、errors 0、无命中）；终端/插件安全负例测试与真实审查未完成 |
| 发布能力 | **未达发布标准（待发布链验证 / T10）** | 13 模块编译通过；debug APK 已产出并完成 sha256/结构/badging 校验（12,484,073 bytes、sha256 `63f8…bd95`、Android Debug 签名）；APK 真机安装、Release 构建与首次 CI 暂未验证；真实 AI、远程下载、真机矩阵、正式签名均未验证。APK 安装验证、Release 与首 CI 明确标注「待发布链验证（T10）」 |

**总体结论**：MVP 的“工程地基 + 文档门禁 + 本机出包通道 + 内容数据”基本就绪，但**产品功能实现与端到端验收未完成**，当前不具备“发布候选（RC）”条件；可以进入 CI 首次运行与功能补齐阶段。任何对外说明都不得写“MVP 已完成”。

## 2. 环境与构建证据

| 项 | 值 | 证据来源 |
| --- | --- | --- |
| 本机环境 | Android 手机 Ubuntu 24.04 proot（aarch64），root，可用内存约 1.4GB | `tools/build/README.md` |
| JDK | 17（`/usr/lib/jvm/java-17-openjdk-arm64`） | `tools/build/README.md` §0 |
| Gradle | 8.9（`/root/toolchain/gradle`，扁平解压） | 同上 |
| Android SDK | `platforms;android-34` + `build-tools;34.0.0`（`/root/toolchain/sdk`） | 同上 |
| aarch64 aapt2 | `/root/toolchain/aarch64-tools/build-tools/aapt2`，经 `-Pandroid.aapt2FromMavenOverride` 注入 | 同上 |
| 本机构建参数 | `-Xmx1280m -XX:MaxMetaspaceSize=384m -Pkotlin.compiler.execution.strategy=in-process --no-daemon`；aapt2 覆盖 `-Pandroid.aapt2FromMavenOverride=/root/toolchain/aarch64-tools/build-tools/aapt2`（本机写入 `/root/.gradle/gradle.properties` 用户级属性，不入仓库） | 同上（仓库 `gradle.properties` 为 1536m，本机脚本覆盖为 1280m） |
| Gradle Wrapper 分发源 | `mirrors.huaweicloud.com/gradle/gradle-8.9-bin.zip` | `gradle/wrapper/gradle-wrapper.properties` |
| CI 构建环境 | GitHub Actions `ubuntu-latest`（x86_64，官方 aapt2 可用，无需覆盖） | `.github/workflows/android.yml` |
| 核心模块编译（本机） | **全量编译通过**：13 模块 `:app:assembleDebug` BUILD SUCCESSFUL（含 designsystem/core:data/feature:learn/dictionary/terminal/assistant/library/app） | Lead 构建输出（2026-11-28） |
| Debug APK | **本机已验证**：`app/build/outputs/apk/debug/app-debug.apk`，12,484,073 bytes，sha256 `63f88b7ef5ef944f1827bdb0d4032b25217db9cec20e75e5aef67c0c8274bd95`；zip 191 entries（`classes.dex`…`classes11.dex`、`AndroidManifest.xml`、`resources.arsc`、`assets/seed/` catalog+12 codepack），CRC 无错；aapt2 badging：`com.codetree.app`、versionCode 1 / versionName 0.1.0、minSdk 26 / targetSdk 34 / compileSdk 34、权限 INTERNET + ACCESS_NETWORK_STATE；签名 Android Debug | Lead 构建输出（2026-11-28） |
| Release APK | 未构建（本机内存下 `assembleRelease` 预期不可行，以 CI 为准） | `tools/build/README.md` §10 |

本机出包命令（可复制，由具备执行通道的成员运行）：

```bash
source /root/toolchain/env.sh
bash tools/build/build-local.sh --test    # 纯 JVM 单测
bash tools/build/build-local.sh --apk     # :app:assembleDebug（产物 + APK 自检）
bash tools/build/build-local.sh --check   # ./gradlew projects 配置检查
```

CI 出包命令（GitHub Actions 内）：

```bash
./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

## 3. CI 适配说明（本次交付）

### 3.1 仓库形态约束（2026-11-28 确认）

本机无 git 凭据，仓库通过 GitHub MCP 以**文本文件**推送，以下二进制/生成物不入库：
`gradle/wrapper/gradle-wrapper.jar`、`content/dist/*.codepack`、`app/src/main/assets/seed/*.codepack`、`CodeTree-合并文档包.zip`（`.gitignore` 由 Lead 补充）。因此 CI 适配为：

1. **不使用 `./gradlew`**（缺 wrapper jar），改用 `gradle/actions/setup-gradle@v4` 的 `gradle-version: 8.9`，命令统一写 `gradle ...`；
2. **构建前生成内容产物**：`python3 tools/content/build_dist.py && python3 tools/content/sync_seed.py`（`assemble`/`build` 作业）；
3. **文档时效性强校验 + 压缩包生成**：CI/Nightly/Release 在生成前先用 `merge_docs.build_document()` 在内存重建合集并与已提交的 `CodeTree-项目文档合集.md` 逐字比对，不一致直接失败（因此门禁不是空转）；通过后再 `python3 scripts/merge_docs.py` 生成压缩包（二进制不入库），最后 `--check` 校验归档完整性；
4. 本地开发仍可用 `./gradlew`（本机有 wrapper jar 与生成物），两类通道命令不同但版本矩阵一致（Gradle 8.9 / JDK 17 / SDK 34 / AGP 8.6.0 / Kotlin 2.0.20）。

### 3.2 工作流变化

| 文件 | 关键变化 | 门禁约束 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | 新增“内存重建时效性校验 → 生成合集压缩包 → `--check`”；保留 `validate_workflows.py` + `tools/content/validate_content.py` + 路径标签作业 | 顶层 `contents: read`；并发取消；每作业 timeout |
| `.github/workflows/android.yml` | 双作业：`jvm-tests`（三纯 JVM 模块单测 + 报告 artifact）、`assemble`（生成 `content/dist` 与 seed → `gradle :app:assembleDebug` → APK 结构自检 + artifact）；命令改用系统 `gradle`；`paths` 增加 `tools/content/**` | 同上；APK `if-no-files-found: error` |
| `.github/workflows/nightly.yml` | `verify`：内存重建时效性校验 → 生成合集/内容产物 → `bash tools/verify/run_all.sh`；`build`：生成内容 → `gradle` 全量单测 + Debug 构建，报告保留 3 天 | 同上 |
| `.github/workflows/release.yml` | tag `v*` → `verify`（内存重建时效性校验 → 生成合集/内容 → 三项静态门禁）→ `build`（`environment: release`、job 级 `contents: write`、生成内容 → `gradle :app:assembleRelease`、`SHA256SUMS`、artifact、`gh release create/upload`）；手动 dispatch 只出 artifact | 顶层 read + job 级写；`cancel-in-progress: false` |
| `.github/actions/setup-android/action.yml` | 新增 `gradle-version` 输入（默认 `8.9`）并传给 `setup-gradle`；JDK17 + `android-actions/setup-android@v3` + 安装 `platforms;android-34 / build-tools;34.0.0 / platform-tools` + 缓存 + 可选国内镜像 | 与 4 个工作流引用一致 |

**CI 静态校验结果（2026-11-28，Lead 执行）**：`merge_docs.py` 重生成 OK（Merged lines 3897 / Archive 148703 bytes / 15 源文档 / 12 附录 / 90 WBS）、`--check` → `Validation: OK`；`validate_workflows.py` → `Validation: OK`（Workflows 4 / Jobs 8 / Composite actions 1）。

**CI 未验证项（状态：未运行）**：截至本报告更新，没有任何一次真实 GitHub Actions 运行记录；T10 需先由用户提供 PAT（本机无 git 凭据、MCP 无法批量传文件）完成 GitHub 推送，再核对 `ci.yml / verify`、`android.yml / jvm-tests`、`android.yml / assemble` 三个作业结果，并把运行链接与 artifact 回填本报告。CI 不能替代真机与安全审查。

## 4. 可执行验收清单（命令与状态）

| # | 门禁 | 命令 | 期望 | 当前状态 |
| --- | --- | --- | --- | --- |
| 1 | 文档合集时效性 | `python3 scripts/merge_docs.py --check` | `Validation: OK` | **已验证 OK**（2026-11-28 重生成后：Merged lines 3897 / Archive 148703 bytes / 15 源文档 / 12 附录 / 90 WBS） |
| 2 | 工作流结构与策略 | `python3 scripts/validate_workflows.py` | `Validation: OK` | **已验证 OK**（Workflows 4 / Jobs 8 / Composite actions 1） |
| 3 | 内容包与产物 | `python3 tools/content/validate_content.py` | `errors: 0` | **已验证 OK**：errors=0 / warnings=12 / RESULT: PASS；`build_dist.py` 12 codepack 自检 OK；`sync_seed.py` synced=8 / unchanged=4、seedTotalBytes=242222 |
| 4 | QA 内容检查 | `python3 tools/verify/check_content.py` | `OK` | **已验证 OK**：errors 0 / warnings 0 |
| 5 | QA 仓库检查 | `python3 tools/verify/check_repo.py` | `OK` | **已验证 OK**：scanned_text_files 285；merge_docs `--check` OK；validate_workflows OK；errors 0；warnings 2（release debug 签名、local.properties 未提交确认） |
| 6 | QA 种子一致性 | `python3 tools/verify/check_seed.py` | `OK` | **已验证 OK**（codepacks 12 / catalog 12 / sha256sums 12，errors 0） |
| 7 | 一键验收 | `bash tools/verify/run_all.sh` | 全部 PASS | **已验证 OK**：内容 PASS（errors=0 / warnings=12），验收结果“全部通过” |
| 8 | 纯 JVM 单测 | `gradle :core:domain:test :plugin-api:test :runtime:safe-shell:test` | BUILD SUCCESSFUL + 用例数 | **已验证 OK**：250 用例 / 0 失败 / 0 skipped（plugin-api 95、core:domain 63、runtime:safe-shell 92） |
| 9 | Debug 出包 | `gradle :app:assembleDebug`（CI）/ `./gradlew`（本机） | APK 生成 + 结构检查 | **已验证 OK（本机）**：BUILD SUCCESSFUL；`app-debug.apk` 12,484,073 bytes；sha256 `63f8…bd95`；191 entries、CRC 无错；badging `com.codetree.app` / v0.1.0(1) / SDK 26-34 / 权限符合；Android Debug 签名；CI 侧与 Release 待 T10 |
| 10 | CI 首次运行 | GitHub Actions | 三个作业绿 | **待发布链验证（T10）**：推送后首次运行 |

> 说明：本报告不预填命令输出。评审时以实际命令输出、artifact 与运行链接为准；命令失败时按 `docs/03-AGENT-COLLABORATION.md` 的失败纪律处理，不删除测试、不放宽断言。

## 5. P0 需求追踪矩阵

状态口径：**已验证** = 有实际证据；**部分** = 代码/契约落盘但未达验收；**未实现** = 无对应实现；**未验证** = 实现或条件存在但缺证据/外部依赖。

> 2026-11-28 更新：13 模块代码基本落盘（`core:data` 11 个实现文件、5 个 feature 的 Tab/VM/UI、`app` 10 个入口文件），下列多数状态由「未实现」调整为「部分（代码已落盘，编译/运行/设备证据待回填）」。**全量 `:app:assembleDebug` 已 BUILD SUCCESSFUL（13 模块全部编译通过，产出 `app-debug.apk` 12.48MB，sha256/结构/badging 已校验）**；纯 JVM 单测 250 用例全绿；矩阵仍标“部分”是因为真机 UI、集成与安全负例证据尚未回填；P1/P2 项按 ADR 明确不做。

### 5.1 学习知识树（LRN）

| 需求 | 摘要 | 当前实现位置 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- |
| LRN-001 | 缩放/拖动/选中/回到当前 | `feature/learn/ui/KnowledgeGraphCanvas.kt`、`GraphDrawing.kt`、`core/domain/graph/GraphLayout.kt` | `GraphLayoutTest` + 人工 UI 操作 | 部分（交互真机未验收） |
| LRN-002 | 四类边 + 图例、非颜色区分 | `core/domain/model/Content.kt`（`EdgeType`）、`feature/learn/ui/GraphPanels.kt` | 单测 + 界面检查 | 部分 |
| LRN-003 | 节点详情六要素与缺失状态 | `feature/learn/ui/TopicDetailScreen.kt`、`TopicDetailSections.kt` | 人工核对内容包 | 部分 |
| LRN-004 | 未学/学习中/已掌握 + 持久化 | `core/domain/model/Progress.kt`、`core/data/progress/ProgressRepositoryImpl.kt`、`feature/learn/LearnViewModel.kt` | 重启恢复测试 | 部分（持久化实现落盘，重启恢复验收待回填） |
| LRN-005 | 从词典/终端/AI 返回保留位置 | feature 内返回状态已实现；`app` 导航与 surface 已接线（`CodeTreeApp`/`FeatureSurfaces` 已落盘） | 端到端人工验收 | 部分（端到端验收待回填） |
| LRN-006/007 | 路径生成与筛选（P1） | `core/domain/graph/TopicGraph.kt` 提供路径/推荐能力 | 单测 | 部分（P1） |

### 5.2 词典与双语（DIC）

| 需求 | 摘要 | 当前实现位置 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- |
| DIC-001/002 | 中英/缩写/别名检索、离线排序 | `core/domain/search/InMemoryTermSearchEngine.kt`、`TermSearch.kt` + `InMemoryTermSearchEngineTest`；`feature/dictionary/DictionaryViewModel.kt`、`ui/SearchScreen.kt` | 单测 + 真实内容抽查 | 部分（引擎+测试+UI 均落盘，编译/UI 验收待回填） |
| DIC-003 | 模糊建议与原因 | `feature/terminal/analysis/FuzzyMatch.kt`、`feature/dictionary/ui/SearchSections.kt`、`core/domain/search` | 单测 | 部分 |
| DIC-004/005 | 详情四要素、三模式且代码不翻译 | `feature/dictionary/ui/TermDetailScreen.kt`、`TermDetailSections.kt`、`DictExampleCard.kt`、`TermDetailViewModel.kt`、`DictionaryTab.kt` | 编译 + UI 验收 | 部分（编译/UI 验收待回填） |
| DIC-006/007 | 历史/收藏、拼音（P1） | 未实现 | — | 未实现 |

### 5.3 终端（TRM）

| 需求 | 摘要 | 当前实现位置 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- |
| TRM-001 | 会话输入/输出/错误/清屏/历史 | `feature/terminal/ui/{TerminalTab,TerminalOutput,TerminalInput,CompletionBar,DiagnosticSheet,HistorySheet,TerminalToolbar,TerminalSettingsSheet}.kt`、`terminal/TerminalViewModel.kt` | 编译 + 会话 UI 手工验收 | 部分（UI 落盘，编译/真机验收待回填） |
| TRM-002 | 内置安全命令 + 私有沙箱 | `runtime/safe-shell/**`（`CommandParser`/`CommandPolicy`/`CommandInterpreter`）+ 3 个测试文件 | 安全负例单测 | 部分（单测已落盘：`CommandParserTest`/`CommandInterpreterTest`/`SafeShellRuntimeTest`，运行结果待回填） |
| TRM-003/004 | 高亮、括号匹配、诊断与修复 | `feature/terminal/analysis/**`（`ShellHighlighter`、`BracketMatcher`、`ShellDiagnostics`、`DefaultSyntaxAnalyzer`、`AnsiParser`）、`ui/DiagnosticSheet.kt` | 分析函数单测 + UI | 部分（实现落盘，单测运行结果待回填） |
| TRM-005 | 上下文模糊补全 | `analysis/FuzzyMatch.kt`、`DefaultSyntaxAnalyzer` | 单测/UI | 部分 |
| TRM-006 | 超时/输出/资源限制 | `runtime/safe-shell/CommandInterpreter.kt`（纯 Kotlin 文件操作，无系统 shell 调用） | 资源上限单测 | 部分 |
| TRM-007/008 | Python/JS 运行时、Termux/Linux（P1/P2） | ADR-003/ADR-007 明确不承诺 | — | 未实现（按计划） |

### 5.4 AI 助手（AIA）

| 需求 | 摘要 | 当前实现位置 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- |
| AIA-001 | 可配置 Base URL/模型/密钥、抽象 Provider | `core/domain/ai/AiContracts.kt`、`core/data/ai/OpenAiCompatibleProvider.kt`、`feature/assistant/ui/AiConfigSheet.kt` + `data/SessionStore.kt` | 单测 + 设置页验收 | 部分（实现落盘，编译/UI 验收待回填） |
| AIA-002 | 流式/取消/重试/错误状态 | `core/domain/ai/OpenAiSseParser.kt` + `OpenAiSseParserTest`；`feature/assistant/assistant/AssistantViewModel.kt`、`ui/AssistantTab.kt` | 单测 + Mock | 部分 |
| AIA-003 | 上下文引用与发送前确认 | `core/domain/ai/AiContracts.kt`（`AiContextDraft`）、`feature/assistant/ui/ContextConfirmCard.kt`、`model/AssistantModels.kt` | UI 验收 | 部分（确认 UI 已落盘，验收待回填） |
| AIA-004 | 本地会话保存/删除、密钥不出日志 | `feature/assistant/data/SessionStore.kt`、`core/data/settings/SecretStore.kt`（Keystore） | 代码审查 + 密钥扫描 | 部分（实现落盘；密钥扫描已通过，运行验证待回填） |
| AIA-005 | AI 建议与实际执行分离 | `feature/assistant/ui/{ChatMessages,Composer}.kt` 与终端独立执行入口 | 评审 | 部分（待评审确认） |
| AIA-006/007 | 限流提示、后端代理（P1） | 未实现 | — | 未实现 |

### 5.5 插件（PLG）

| 需求 | 摘要 | 当前实现位置 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- |
| PLG-001 | SAF 导入 `.codepack` | `feature/library/ui/ImportSection.kt`、`library/LibraryViewModel.kt`、`core/data/content/CodepackInstaller.kt` | 真机导入 | 部分（实现落盘，编译/真机验收待回填） |
| PLG-002 | 清单/版本/哈希/路径校验 | `plugin-api/DefaultCodepackValidator.kt`、`DependencyChecker.kt` + 4 个测试文件 | 恶意样例单测 | 部分（校验器与单测落盘，运行结果待回填） |
| PLG-003/004 | 声明式贡献、依赖/启停/卸载 | `plugin-api` 校验 + 依赖合约；`core/data/library/LibraryRepositoryImpl.kt`、`feature/library/ui/{Catalog,Installed,Storage}Section.kt` | 集成测试 | 部分（实现落盘，集成验收待回填） |
| PLG-005/006 | API 版本、签名可执行插件（P1/P2） | ADR-005 明确推迟 | — | 未实现（按计划） |

### 5.6 知识包（PKG）

| 需求 | 摘要 | 当前实现位置 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- |
| PKG-001/002 | 目录/选择/暂停/恢复/取消 | `content/dist/catalog.json`、`core/data/content/CatalogRepository.kt`、`core/data/library/LibraryRepositoryImpl.kt`、`feature/library/ui/{Catalog,Installed}Section.kt` | 下载流程验收 | 部分（目录/安装实现落盘；远程下载链路未实现） |
| PKG-003 | 哈希校验、原子安装、版本迁移 | `content/dist/sha256sums.txt`、`tools/content/build_dist.py`、`core/data/content/{SeedInstaller,CodepackInstaller}.kt` | 失败回滚测试 | 部分（实现落盘；失败回滚验收待回填） |
| PKG-004/005 | 更新/删除/依赖提示、离线可用 | `app/src/main/assets/seed/**`（12 产物）、`core/data/content/ContentRepositoryImpl.kt`、`core/data/library/LibraryRepositoryImpl.kt` | 飞行模式验收 | 部分（seed 验证通过；离线读取实现落盘，飞行模式验收待回填） |
| PKG-006 | 仅 Wi-Fi/存储阈值（P1） | 未实现 | — | 未实现 |

### 5.7 UI 与引导（UI）

| 需求 | 摘要 | 当前实现位置 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- |
| UI-001 | 苹果式简约、统一令牌与终端深色主题 | `core/designsystem/theme/{Tokens,CodeTreeTheme}.kt`、`components/**`、`motion/CTMotion.kt`、`app` UI 已接线 | 代码检索 + 预览 | 部分（组件与令牌落盘、app 已接线；UI 验收待回填） |
| UI-002 | 三页首启引导 + 方向选择 | `app/ui/Directions.kt`（12 方向）、`app` 引导页（shell 已落盘） | 真机 | 部分（引导 UI 已落盘，真机验收待回填） |
| UI-003 | 知识树/终端一次性上下文引导 | 待 shell 落盘 | 真机 | 部分（未落盘） |
| UI-004 | Loading/Empty/Error/Offline | `core/designsystem/components/CTStates.kt` | 预览 + 页面接线 | 部分 |
| UI-005 | 深色/字号/减少动效/48dp | `Tokens.kt`（深浅色令牌）、`CTMotion.kt`；`app` 未接线验证 | 预览 + 可访问性检查 | 部分 |
| UI-006 | 页面转场与路径高亮动效（P1） | `CTMotion.kt` 提供动效令牌 | 预览 | 部分（P1） |

### 5.8 非功能（NFR）

| 需求 | 摘要 | 当前状态 |
| --- | --- | --- |
| NFR-001 | API 26 最低支持 | 已验证：ADR-001 + `app/build.gradle.kts`（minSdk 26 / compileSdk 34 / targetSdk 34） |
| NFR-002 | 10,000 术语搜索 P95 < 300ms | 未验证：无基准数据 |
| NFR-003 | 下载/安装失败不破坏既有内容、数据库可迁移 | 部分：`SeedInstaller`/`CodepackInstaller` 实现失败回滚与暂存目录；迁移策略仅文档化（MVP 无 Room） |
| NFR-004 | 最小权限 | 部分：`app/src/main/AndroidManifest.xml` 仅 INTERNET/ACCESS_NETWORK_STATE；SAF 导入实现已落盘（`ImportSection`） |
| NFR-005 | 密钥保护、日志不泄密 | 部分：`core/data/settings/SecretStore.kt`（Keystore）实现落盘；`check_repo.py` 明文密钥扫描已通过；运行期验证待回填 |
| NFR-006 | 核心学习流程离线可用 | 部分：内容与 seed 就绪；`SeedInstaller`/`ContentRepositoryImpl` 落盘；飞行模式验收待回填 |
| NFR-007 | 错误原因 + 下一步 | 部分：`core/domain/common/AppError.kt` 统一错误模型已落盘 |

## 6. 安全与隐私检查

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 终端不调用系统 shell | 通过（代码级 + 引擎自检）：全仓库 `.kt` 检索 `ProcessBuilder`/`Runtime.getRuntime`/`/system/bin/sh` 仅命中一句注释；引擎对纯 JVM 模块自检为 **0 命中，且无 android import** | 本次检索 + engine 自检输出 |
| 终端白名单 | 已实现：`CommandPolicy` 白名单 11 条命令，危险命令显式拒绝；`help` 文案说明“非完整 Linux、无管道/重定向/后台任务” | `runtime/safe-shell/.../CommandPolicy.kt` |
| 插件拒绝可执行内容 | 已实现（代码级）：`DefaultCodepackValidator` 未知能力/路径/压缩比/哈希等错误码；`SafePath` 拒绝绝对路径、`..`、符号链接、可执行扩展名；负例单测已落盘（`DefaultCodepackValidatorTest`/`SafePathTest`/`SemVerTest`/`DependencyCheckerTest`），运行结果待回填 | `plugin-api/**` |
| 纯 JVM 模块无系统 shell / Android 依赖 | 通过（引擎自检）：`ProcessBuilder`/`Runtime.getRuntime`/`/system/bin/sh` 0 命中；无 android import | engine 自检输出（2026-11-28） |
| 权限最小化 | 通过（清单级）：App 仅 INTERNET + ACCESS_NETWORK_STATE | `app/src/main/AndroidManifest.xml` + aapt2 badging |
| 明文密钥扫描 | 已验证：`check_repo.py` 扫描 285 个文本文件，errors 0（无明文密钥命中），warnings 2 与本项无关 | `tools/verify/check_repo.py` 运行输出（2026-11-22） |
| 真实密钥/签名私钥 | 未发现：仓库无签名文件；发布使用 debug 签名（见 §8 风险 QA-009） | 代码检索 + `app/build.gradle.kts` |
| 安全负例测试 | 部分：`plugin-api` 4 个、`runtime:safe-shell` 3 个测试文件已落盘（覆盖路径/参数/取消/资源/校验负例），运行结果待回填；真机导入与恶意包集成负例仍待做 | 缺陷 QA-005；`**/src/test/**` |

## 7. 未验证项与已知限制

1. **真实 AI 服务**：无 API 密钥与后端；仅 Mock/SSE 解析单测，未做真实 Provider 联调（发布说明必须写明）。
2. **远程内容下载**：无远程目录后端；当前知识库以内置 seed/本地 catalog 为准，下载链路未实现。
3. **真机与设备矩阵**：未做 Android 8.0/中间版本/目标版本、屏幕尺寸、键盘、深色、字体、减少动效矩阵。
4. **正式签名**：无 release keystore；CI 发布的 Release APK 使用 debug 签名（可安装，不可上架）。
5. **CI 首次运行**：4 个工作流尚未在真实 GitHub 仓库运行，运行器时长/缓存命中/artifact 均无记录。
6. **性能**：启动/搜索/图谱/终端无基准数据（NFR-002 等目标未测）。
7. **Compose/仪器测试**：无 UI 测试与 Android instrumentation 测试。
8. **Room/FTS、Hilt**：按 ADR-006/009 推迟，迁移与注入方案未验证。
9. **插件签名信任链**：按 ADR-005 推迟，内容来源不可验证。
10. **Gradle Wrapper**：wrapper jar 不入库、wrapper 指向华为云镜像；CI 已改用 `setup-gradle` 提供的 Gradle 8.9（不依赖 wrapper），本地首次下载依赖镜像（首次 CI 会最终确认）。
11. **全量编译**：**已验证**——13 模块 `:app:assembleDebug` BUILD SUCCESSFUL，`app-debug.apk` 12,484,073 bytes、sha256 与结构已校验。
12. **纯 JVM 单测**：**已验证**——250 用例 / 0 失败 / 0 skipped（2026-11-28 复跑）。
13. **Release 构建与首次 CI**：标注「待发布链验证（T10）」；Release APK sha256 以 CI 产物为准，本报告不预填。

## 8. 缺陷与风险登记

| ID | 级别 | 描述 | 影响 | 建议 |
| --- | --- | --- | --- | --- |
| QA-001 | 已修复（已验证） | `app` 入口曾缺 `CodeTreeApp`/`FeatureSurfaces`；shell 已补齐 10 个 app 文件，app 的 `processDebugResources` 也已修好；全量 `assembleDebug` 已通过 | app 可编译并产出 APK | 无（APK 安装验证归 T10） |
| QA-012 | 已修复（已验证） | 曾报 `feature:terminal` 5 处（`BracketMatcher` 未闭合注释等）与 `feature:library` 1 处类型不匹配；tools 修复后全量 `assembleDebug` BUILD SUCCESSFUL | 不再阻塞 APK | 无（回归由 `android.yml` 覆盖） |
| QA-002 | 已修复（代码级，待编译验证） | 曾报「`:core:data` 无实现」；现有 11 个实现文件（内容加载/安装/DataStore/密钥/AI Provider/知识库） | 离线学习、进度持久化、AI 设置代码路径已具备 | 全量编译 + 集成验收回填 |
| QA-003 | 已修复（代码级，待编译验证） | 曾报「dictionary/library 无实现、assistant/terminal 无 Screen」；现 5 个 feature 均有 Tab/VM/UI 落盘 | 词典/AI/知识库/终端页面代码已具备 | 全量编译 + UI 验收回填 |
| QA-004 | Major | 内容 `Term.displayName` 为 `{"zh-CN","en"}` 双语对象，而 `:core:domain Term.displayName` 为 `String` | 数据层解析可能失败或丢显示名 | 走 `CONTRACT_CHANGE`：统一为 LocalizedText 或数据层映射并加测试 |
| QA-005 | 已修复（代码级，待运行验证） | 曾报「plugin-api 与 runtime:safe-shell 无测试源」；现 11 个测试文件（core:domain 4 / plugin-api 4 / runtime:safe-shell 3） | 安全结论将有自动化证据 | 单测运行结果回填 §4 #8 |
| QA-006 | 已修复（已验证） | 内容校验 errors=0 / warnings=12 RESULT: PASS；`check_content` errors 0 / warnings 0；`build_dist`/`sync_seed` 终检 PASS | 内容门禁已绿 | 后续内容变更保持 0 error |
| QA-007 | Minor | `docs/specs/module-layout.md` 写 `com.codetree.runtime.safe`，实际实现包名为 `com.codetree.runtime.safeshell` | 文档与代码漂移 | 统一包名或修订 spec |
| QA-008 | 已修复（已验证） | 工作目录曾缺 `.gitignore`；Lead 已补（含 `local.properties`、`build/`、`.gradle/`、签名与 APK 忽略） | `check_repo.py` 已转绿（errors 0），local.properties 确认未提交 | 推送前复核 `.gitignore` 生效 |
| QA-009 | Minor（发布说明项） | release 构建使用 debug 签名（本机 debug APK 已确认 Android Debug 证书） | 不可上架；安装包可被替换/重签 | 发布说明写明；正式发版前由 CI secrets 注入正式签名 |
| QA-010 | 已缓解（CI 已绕开 wrapper） | wrapper jar 不入库、wrapper 指向华为云镜像 | 本地首次下载依赖镜像；CI 不依赖 wrapper | CI 用 `setup-gradle` 提供 Gradle 8.9（§3.1）；首次 CI 观察记录 |
| QA-011 | 已修复（已验证） | `tools/content/README.md` 的策略措辞已由内容侧修改；`validate_workflows.py` 通过 | 门禁不再命中 | 无 |

## 9. 发布清单（Release Runbook，T10 草稿）

> 状态：草稿（2026-11-28）。本节只定义流程与命令草案；版本号/tag/APK 实际值在 T10 执行时回填。APK 与首次 CI 运行标注「待发布链验证（T10）」。

### 9.1 发布前置条件（全部满足才可打 tag）

1. **全量编译通过**：13 模块（`:app` + `plugin-api` + `core:domain` + `core:data` + `core:designsystem` + `runtime:api` + `runtime:safe-shell` + 5 个 `feature:*`）编译成功。
2. **纯 JVM 单测**：11 个测试文件全绿（core:domain 4 / plugin-api 4 / runtime:safe-shell 3，约 180 用例）。
3. **静态门禁**：`merge_docs.py --check`、`validate_workflows.py`、`tools/content/validate_content.py`、`bash tools/verify/run_all.sh` 全部通过。
4. **QA 报告**：本文件 §1/§2/§4 回填实测数据，未验证项列清。
5. **仓库状态与凭据**：Lead 确认工作树无密钥、无本地文件，GitHub 远端 `zukushig/easy-code` 已同步；**GitHub 推送需要用户提供 PAT**（本机无 git 凭据、MCP 无法批量传文件，用户已同意提供）。

### 9.2 版本与 tag

| 项 | 值 |
| --- | --- |
| versionName | `0.1.0`（`app/build.gradle.kts`） |
| versionCode | `1` |
| tag | `v0.1.0`（`v*` 触发 `release.yml`） |
| 变更规则 | 每次发布递增 `versionCode`；tag 与 versionName 一致；不删除/移动已发布 tag |

### 9.3 触发方式

- **主通道（推荐，依赖 PAT）**：用户提供 PAT 后，Lead 通过 GitHub MCP/API 提交并推送 `main`，再创建/推送 tag `v0.1.0`；`release.yml` 的 `on.push.tags: ['v*']` 自动触发。本机无 git 凭据，MCP 无法批量传文件，因此 PAT 是当前唯一阻塞项。
- **备用**：Actions 页面手动 `Run workflow`，输入 `version`（如 `v0.1.0`）；该模式只产出 artifact（`release-apk-v0.1.0`），不创建 GitHub Release（`if: github.ref_type == 'tag'` 保护）。
- **`release.yml` 行为**：`verify`（生成合集/内容产物 → 发布前门禁，timeout 12）→ `build`（`environment: release`、生成内容产物 → 用 `setup-gradle` 提供的 **Gradle 8.9**（`gradle`，不依赖 `./gradlew`）执行 `:app:assembleRelease`、timeout 40）→ 生成 `release-out/SHA256SUMS` → 上传 artifact（retention 30 天）→ tag 触发时 `gh release create/upload`。
- **仓库形态**：二进制不入库（wrapper jar / codepack / seed / 合集 zip），因此 release 的两个作业都必须先跑生成步骤（见 §3.1）；本地预演可用 `./gradlew :app:assembleRelease`。

### 9.4 产物与校验

| 项 | 预期值 |
| --- | --- |
| APK 文件名 | `app-release.apk`（AGP 输出 `app/build/outputs/apk/release/app-release.apk`，`release.yml` 复制到 `release-out/`） |
| 校验和文件 | `SHA256SUMS`，格式 `<64 位小写 sha256>  app-release.apk` |
| Release 资产 | `app-release.apk` + `SHA256SUMS`；标题 `CodeTree v0.1.0`；notes 由 `--generate-notes` 生成并人工补充已知限制 |
| 下载后校验 | `sha256sum -c SHA256SUMS`（Windows 可用 `certutil -hashfile app-release.apk SHA256`） |
| 本机构建参考（debug） | `app-debug.apk` 12,484,073 bytes；sha256 `63f88b7ef5ef944f1827bdb0d4032b25217db9cec20e75e5aef67c0c8274bd95`；191 entries；Android Debug 签名（本机 aarch64 通道，2026-11-28） |
| Release APK sha256 | **待 T10 回填**：以 CI `release-out/SHA256SUMS` 为准；Release 构建可复现性未保证，不能用 debug 构建的 sha256 代替 |

### 9.5 安装步骤（用户手机）

1. 从 GitHub Release 下载 `app-release.apk` 与 `SHA256SUMS`，核对哈希一致。
2. 手机允许“安装未知来源应用”（设置 → 应用 → 特殊权限）。
3. 安装（Android 8.0 / API 26 及以上；首次安装触发内容解压与索引）。
4. 首启走引导：选择学习方向 → 进入学习 Tab；离线状态应可读取内置内容。
5. 已知：Release 为 debug 签名，不是商店包；覆盖安装升级保留应用数据（MVP 无 Room 迁移）。
6. 本地已有一份 debug 构建副本：`/sdcard/Download/DSH-Go/CodeTree-v0.1.0-debug.apk`（sha256 见 §9.4），仅用于本机安装冒烟，不是 Release 产物。

### 9.6 回滚

1. **GitHub**：删除或标记该 Release 为 pre-release；tag 由 Lead 决定是否重建（不移动已发布 tag）。
2. **应用**：引导用户卸载当前版本，安装上一个 tag 的 APK；MVP 无 Room，无数据库降级问题，内容 seed 随包重置。
3. **内容**：内容包版本为 1.0.0，回滚只涉及 APK 内 seed，无需服务端操作。

### 9.7 发布说明必含项

- 真实 AI 未联调（仅 Mock/SSE 单测）；远程内容下载未实现（知识库以内置内容为准）；完整设备矩阵未做；无正式签名；QA-REPORT 中的未验证项（QA-001 app 入口补齐后复核、QA-004 契约、QA-007 包名等）。

### 9.8 发布后验证（T10 执行清单）

- [ ] APK 安装成功、可启动、5 Tab 可切换。
- [ ] 离线学习闭环走通（内容 → 图谱 → 词典 → 示例 → 终端 → AI 未配置态 → 进度）。
- [ ] 终端 safe-shell 无系统 shell 调用（`help` 说明正确）、超限/取消行为符合规格。
- [ ] `.codepack` 导入成功、损坏包被拒。
- [ ] Release 资产 sha256 与 §9.4 记录一致。
- [ ] 失败则按 §9.6 回滚并记录。

## 10. 证据索引

| 证据 | 位置 | 状态 |
| --- | --- | --- |
| 文档门禁输出（merge/validate） | Lead 会话记录（2026-11-28 CI 改造后复跑：Merged lines 3897 / Archive 148703 bytes / `--check` OK / validate 4-8-1 OK） | 已验证 |
| 内容包清单（12 包） | `content/index.json`、`content/dist/catalog.json`、`content/dist/sha256sums.txt` | 已验证（errors=0 / warnings=12；`build_dist` 12 codepack、`sync_seed` synced=8 unchanged=4） |
| 内置种子 | `app/src/main/assets/seed/**`（12 codepack + catalog + sha256sums） | 已验证（`check_seed.py` errors 0；APK 内 `assets/seed/` 191 entries 已含） |
| 本机工具链 | `tools/build/README.md`、`/root/toolchain/REPORT.md` | 工具链已验证；13 模块全量编译通过（`assembleDebug` BUILD SUCCESSFUL） |
| 单元测试 | `core/domain/src/test/kotlin/**`、`plugin-api/src/test/**`、`runtime/safe-shell/src/test/**`（11 个测试文件） | 已验证：250 用例 / 0 失败 / 0 skipped |
| CI 定义 | `.github/workflows/**`、`.github/actions/setup-android/action.yml` | 已适配（系统 gradle + 内容/文档生成步骤）且 `validate_workflows.py` OK；首次运行待 T10 |
| 发布链（APK / Release） | `release.yml`、`app/build.gradle.kts`、`app/build/outputs/apk/debug/app-debug.apk` | debug APK 已产出并校验（sha256 `63f8…bd95`）；Release 资产与首次 CI 未产生——**待发布链验证（T10）** |
| QA 检查工具 | `tools/verify/{check_content,check_repo,check_seed}.py`、`run_all.sh`、`README.md` | 已验证：三个 `--selftest` OK；`check_content` errors 0 / warnings 0；`check_repo` errors 0 / warnings 2；`check_seed` OK；`run_all.sh` 全部通过 |
| 安全自检（纯 JVM） | `core/domain`、`plugin-api`、`runtime/api`、`runtime/safe-shell` | 已验证：`ProcessBuilder`/`Runtime.getRuntime`/`/system/bin/sh` 0 命中；无 android import |

## 11. 下一步建议（按优先级）

1. **T10 推送与首次 CI**：**当前阻塞项 = GitHub PAT**（用户已同意提供）；拿到后 Lead 经 GitHub API 推送 `main`（合集 md 必须入库，四类二进制按 `.gitignore` 排除）→ 触发 `android.yml` 首次 CI（`jvm-tests` + `assemble`）→ 回填运行链接与 artifact；如失败按 §3.1 排查（wrapper 缺失/生成步骤/SDK）。
2. **T10 tag 与 Release**：`main` 上 `ci.yml`/`android.yml` 绿后打 tag `v0.1.0` → `release.yml` 产出 `release-out/app-release.apk` + `SHA256SUMS` → 回填 §9.4 的 Release APK sha256 与下载链接。
3. **APK 安装冒烟**：用 Release APK 在目标手机安装，按 §9.8 验证首启、5 Tab、离线学习、终端、AI 未配置态、知识库导入。
4. **安全集成负例**：真机导入 `codepack`、恶意包拒绝、终端取消/资源超限集成验证（QA-005 的运行证据）。
5. **契约收敛**：处理 QA-004（`Term.displayName`）与 QA-007（包名）的 `CONTRACT_CHANGE`，同步 specs。
6. **发布说明**：真实 AI、远程下载、完整设备矩阵、正式签名均未验证，发布前由 Lead 在 Release notes 写明（§7）。
