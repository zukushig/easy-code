# CodeTree（easy-code）项目当前状态

> 最后更新：2026-11-22（Asia/Shanghai）｜维护者：architect（文档、QA/Release）、lead（最终确认）
> 本文件是团队进入项目的“当前状态快照”；历史规划以 15 份源文档为准，落地约定以 `docs/decisions/`、`docs/specs/`、`docs/tasks/` 为准。新增文档不参与 `scripts/merge_docs.py` 的合并校验。

## 1. 项目定位

CodeTree（仓库名 `easy-code`）是一款原生 Android 编程学习应用：以知识树组织内容，以词典、双语示例、安全终端和 AI 助手形成学习闭环。首版优先离线可用与安全，**不承诺完整 Linux、账号云同步或可执行插件**。

## 2. 规划基线状态

规划基线（v0.4 文档合集）已**逐字还原入库并通过校验**：

| 项 | 状态 |
| --- | --- |
| 15 份源文档 | 已入库：`docs/00`、`README.md`、`AGENTS.md`、`docs/01`～`docs/08`、`docs/templates/*`（4 份） |
| 12 份工程文件 | 已入库：`.github/workflows/*`（4）、`.github/actions/setup-android/*`（2）、`.github/{labeler.yml,CODEOWNERS.example,PULL_REQUEST_TEMPLATE.md}`、`scripts/{merge_docs.py,validate_workflows.py,requirements-ci.txt}` |
| 生成物 | 仓库根 `CodeTree-项目文档合集.md`、`CodeTree-合并文档包.zip`，以及工作区根目录 zip 镜像；**保留，不手工编辑，不加入 `.gitignore`** |
| 校验（Lead 执行，2026-11-15） | `python3 scripts/merge_docs.py` → `Validation: OK`（Merged lines 3814 / Source docs 15 / Engineering appendix 12 / WBS tasks 90）；`--check` → `Validation: OK`；`python3 scripts/validate_workflows.py` → `Validation: OK`（Workflows 4 / Jobs 9 / Composite actions 1） |
| 保真复核 | 重生成合集与原始基线 `diff -q` → **IDENTICAL**（逐字节一致） |
| T9 复跑（Lead 执行，2026-11-22） | `python3 scripts/merge_docs.py` → `Validation: OK`（Merged lines 3860 / Archive 147852 bytes）；`--check` → `Validation: OK`；`python3 scripts/validate_workflows.py` → `Validation: OK`（Workflows 4 / Jobs 8 / Composite actions 1） |
| 校验器提示 | `validate_workflows.py` 报告 16 处 third-party `uses` 未固定 SHA，属脚本自带提示，不阻断；上线前应按 `docs/08` §4.7 固定 |

重要边界：**15 份源文档的正文与 12 份工程文件保持逐字重建，不在本阶段做 MVP 适配**；新增落地文档均为独立文件，不进入合并清单。若未来要纳入合集，须修改 `scripts/merge_docs.py` 的 `SOURCE_ORDER` 并重生成（ADR-008）。

T9 已按 easy-code 工程适配 `.github/**`（4 个工作流 + 1 个复合动作，2026-11-22）：工程文件内容变化会改变合集附录，因此需要先 `python3 scripts/merge_docs.py` 重生成，再跑 `--check` 与 `validate_workflows.py`；复跑结果与 QA 证据以 `docs/QA-REPORT.md` §4 为准。

## 3. 本期实施范围（MVP M1/M2 子集 + M3/M4 前置）

| 阶段 | 本期交付 | 明确不包含（后续回填） |
| --- | --- | --- |
| M1 应用骨架 | 13 模块工程、版本矩阵、设计系统、5 Tab 导航、三页引导、设置页、五态、数据层（内容加载/安装/DataStore） | Hilt、Room、WorkManager、真实远程目录 |
| M2 离线学习 | 内容包（12 个领域包）、内容校验/打包工具、内存索引、知识树 Canvas、节点详情、词典搜索/术语详情/三模式双语、学习进度 | Room/FTS 迁移（M2 后评估）、复杂学习路径生成（P1 项按需） |
| M3 前置 | safe-shell 白名单终端（高亮/诊断/补全/取消/资源上限）、AI OpenAI 兼容直连 + SSE + 上下文确认（Mock 验证） | Python/JavaScript 运行时、真实 AI 服务联调、后端代理 |
| M4 前置 | 声明式 `.codepack` 导入/校验/安装/启停/卸载、知识库管理（本地目录/内置包）、存储管理 | 插件签名信任链、可执行插件、真实远程下载 |
| 体验与发布 | QA 报告、CI 适配、可安装 APK、发布检查表 | 完整设备矩阵、商店发布、签名 release（需 keystore） |

非目标（与 ADR-007 一致）：完整 Linux/Termux、账号云同步、社区/付费系统、任意代码执行。

## 4. Agent 团队名册（9 人，无独立 QA 成员）

> 团队达到 8 人上限后不再新增成员；T9 QA/Release 由 architect 兼任（Lead 已于 2026-11-22 任命）。

| 名字 | 角色代码 | 职责 | 主要写范围 | 当前任务 |
| --- | --- | --- | --- | --- |
| lead | COORD | 协调、集成、发布、仓库推送（唯一 push 者） | 共享文件裁决、发布记录 | T10（TASK-110） |
| architect | ARCH + QA/RELEASE | 文档基线、ADR、模块/接口契约、CONTEXT；CI 适配、验收校验、QA 报告与发布准备 | `docs/**`、`AGENTS.md`、`README.md`、`scripts/`、`.github/`、`tools/verify/` | T1 已完成；T9（TASK-109）进行中 |
| buildsmith | RELEASE | 本机 aarch64 构建工具链、出包验证 | `/root/toolchain/**`、`tools/build/**` | T2 已完成 |
| content-a | CONTENT | 基础域内容包 + 内容校验/打包工具 | `content/packs/{基础域 6 个}`、`content/index.json`、`content/dist/**`、`tools/content/**` | T3（TASK-103） |
| content-b | CONTENT | 应用域内容包（含 Vibe Coding 专题） | `content/packs/{应用域 6 个}` | T4（TASK-104） |
| shell | DESIGN/DATA | App 壳、设计系统、数据层 | `app/`、`core/designsystem/`、`core/data/` | T5（TASK-105） |
| engine | ARCH/TERMINAL/AI/PLUGIN | 领域引擎、插件 API、运行时内核 | `core/domain/`、`plugin-api/`、`runtime/` | T6（TASK-106） |
| learn | LEARN/DICT | 知识树、词典 UI | `feature/learn/`、`feature/dictionary/` | T7（TASK-107） |
| tools | TERMINAL/AI/PLUGIN | 终端、AI 助手、知识库/插件 UI | `feature/terminal/`、`feature/assistant/`、`feature/library/` | T8（TASK-108） |

## 5. 环境约束

- **运行环境**：Android 手机上的 Ubuntu 24.04 proot（aarch64），约 1.4GB 可用内存；无 bubblewrap/Landlock 沙箱后端，DSH 的 `workspace-write` 模式不可用，文件工具走会话文件策略；部分 Agent 会话的 bash 工具不可用，命令需由 Lead 或具备执行通道的会话代跑。
- **构建工具链**：JDK 17 + Gradle 8.9 + Android SDK（compileSdk 34 / build-tools 34.0.0）；官方 aapt2 为 x86_64，本机已用 aarch64 静态 aapt2 覆盖（`-Pandroid.aapt2FromMavenOverride`，写入本机用户级 `/root/.gradle/gradle.properties`，不入仓库；CI 用 x86_64 官方 aapt2，无需覆盖）。**本机出包通道已验证**（T2 完成，见 `tools/build/README.md` 与 `/root/toolchain/REPORT.md`）：13 模块全量 `:app:assembleDebug` BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`（12,484,073 bytes；sha256 `63f88b7ef5ef944f1827bdb0d4032b25217db9cec20e75e5aef67c0c8274bd95`；191 entries；Android Debug 签名）。**GitHub Actions（ubuntu-latest，x86_64）是发布与备用出包通道**，Release 构建与首次 CI 结果未产生前不得声称“CI 已验证”（T10 回填，见 `docs/QA-REPORT.md` §2/§9）。
- **CI 构建适配（T9）**：`.github/workflows/**` 已适配 easy-code 工程（ci/android/nightly/release + setup-android 复合动作）。**仓库形态约束**：本机无 git 凭据、经 GitHub MCP 只推文本，故 `gradle/wrapper/gradle-wrapper.jar`、`content/dist/*.codepack`、`app/src/main/assets/seed/*.codepack`、`CodeTree-合并文档包.zip` 不入库（Lead 补 `.gitignore`）；CI 不使用 `./gradlew`，改用 `gradle/actions/setup-gradle@v4` 的 `gradle-version: 8.9` + `gradle` 命令，并在构建/门禁前生成内容与文档合集（`build_dist.py`、`sync_seed.py`、`merge_docs.py`）。本地开发仍用 `./gradlew`，版本矩阵一致。文件内容变化会改变文档合集附录，需按 ADR-008 重生成后校验。
- **依赖来源**：本机直连 Maven Central 不可用，`settings.gradle.kts` 已配置阿里云镜像 + 官方源兜底；CI 使用官方源。
- **外部服务**：无真实 AI API 密钥、无远程内容目录后端、无签名 keystore；AI/下载/签名相关验收只能 Mock 或标注未验证。
- **仓库写权限**：只有 Lead 执行 `git commit` / `git push`；GitHub 远端 `zukushig/easy-code` 为公开仓库，其他 Agent 不得直接推送或调用 GitHub 写接口。
- **构建参数**：`gradle.properties` 已为低内存设备设置 `-Xmx1536m`、`org.gradle.workers.max=1`、`org.gradle.parallel=false`、`org.gradle.configuration-cache=false`；不要随意上调。

## 6. 模块布局（13 模块）

```text
:app                      组合根、NavHost、AppContainer、assets 种子内容
:core:domain             纯 JVM：图/搜索/进度/AI 解析/仓库接口/AppError
:core:data               Android：内容加载与安装、内存索引、DataStore、AI 网络、密钥存储
:core:designsystem       Android：设计令牌与通用组件
:plugin-api              纯 JVM：Codepack 清单/校验/SafePath/SemVer/LanguageSyntax
:runtime:api             纯 JVM：RuntimePlugin/RuntimeSession/RunRequest/RuntimeEvent/SyntaxAnalyzer
:runtime:safe-shell      纯 JVM：白名单命令/沙箱/限制/取消
:feature:learn           知识树/节点详情/进度 UI
:feature:dictionary      搜索/术语详情/三模式双语
:feature:terminal        终端会话/高亮/诊断/补全
:feature:assistant       AI 对话/设置/上下文确认
:feature:library         知识库/导入/插件管理/存储
```

依赖方向：`feature → core:data → core:domain → plugin-api`；`:runtime:*` 独立；`:core:designsystem` 只依赖 `:core:domain` 的展示模型/枚举；feature 之间不得互相依赖（导航由 `:app` 回调完成）。细节见 `docs/specs/module-layout.md` 与 ADR-009。

## 7. 文档地图（新增落地文档）

| 文件 | 用途 |
| --- | --- |
| `docs/CONTEXT.md` | 本文件：当前状态、名册、环境、范围、风险 |
| `docs/decisions/ADR-001-android-sdk-matrix.md` | D-001 SDK/AGP/Kotlin/Compose/Gradle 矩阵 |
| `docs/decisions/ADR-002-knowledge-graph-rendering.md` | D-002 Compose Canvas 自绘图谱 |
| `docs/decisions/ADR-003-controlled-runtime.md` | D-003 P0 safe-shell；Python/JS 运行时未决 |
| `docs/decisions/ADR-004-ai-direct-vs-proxy.md` | D-004 AI 直连默认、代理 P1 |
| `docs/decisions/ADR-005-plugin-signing-trust.md` | D-005 声明式插件先行、签名推迟 |
| `docs/decisions/ADR-006-content-version-database.md` | D-006 JSON+内存索引+DataStore，Room 推迟 |
| `docs/decisions/ADR-007-termux-linux-adaptation.md` | D-007 不承诺完整 Linux |
| `docs/decisions/ADR-008-document-generation.md` | D-008 文档生成门禁 |
| `docs/decisions/ADR-009-mvp-module-layout.md` | 模块收敛、手写 DI、纯 JVM 单测 |
| `docs/specs/module-layout.md` | 模块/依赖/包名/所有权契约 |
| `docs/specs/knowledge.md` | 内容实体、JSON 格式、稳定 ID、搜索排序、安装状态机 |
| `docs/specs/terminal.md` | Runtime 契约、白名单命令、路径/资源/取消、诊断补全 |
| `docs/specs/ai-provider.md` | AiProvider、SSE 事件、上下文草稿、错误映射、密钥存储 |
| `docs/specs/plugin-api.md` | Codepack 清单、贡献类型、校验顺序、依赖解析、路径安全 |
| `docs/specs/design-system.md` | 苹果式极简令牌、动效降级、组件清单 |
| `docs/tasks/TASK-101.md` ～ `TASK-110.md` | T1～T10 任务卡（目标/只读上下文/写范围/禁止事项/验收/交接） |

## 8. 已完成 / 进行中 / 下一步（2026-11-22 快照）

| 状态 | 内容 | 证据 |
| --- | --- | --- |
| 已完成 | 15 份源文档 + 12 份工程文件逐字重建；三条校验通过；保真 diff IDENTICAL | Lead 贴回的命令输出（2026-11-15） |
| 已完成 | 13 模块骨架、版本目录、`gradle.properties`、`gradlew`；领域/插件/运行时/设计系统契约与部分实现落盘 | 仓库文件 |
| 已完成 | ADR-001～009、6 份 specs、10 张任务卡、CONTEXT | 本次交付前序 |
| 已完成 | T2 本机 aarch64 构建工具链：JDK17 + Gradle 8.9 + SDK34 + aarch64 aapt2 覆盖，本机可进入构建 | `tools/build/README.md`、`/root/toolchain/REPORT.md` |
| 已完成 | 内容包 12 个、`content/dist` 12 个 `.codepack` + `catalog.json` + `sha256sums.txt`、`app/src/main/assets/seed/` 已同步；全量校验 `errors=0` / `warnings=12` RESULT: PASS；`build_dist.py` 12 codepack 自检 OK；`sync_seed.py` synced=8 unchanged=4、seedTotalBytes=242222；QA `check_content.py` errors 0 / warnings 0；`check_seed.py` OK | Lead 回填的内容流水线与 QA 输出 |
| 已完成 | T9 交付完成：CI 适配（4 工作流 + setup-android，含“二进制不入库”生成步骤）、`tools/verify/`、`docs/QA-REPORT.md`；门禁复跑 `merge_docs.py --check` / `validate_workflows.py` OK | Lead 回填的命令输出（2026-11-28） |
| 已完成 | 全量编译与首个 APK：13 模块 `:app:assembleDebug` BUILD SUCCESSFUL；`app-debug.apk` 12,484,073 bytes / sha256 `63f8…bd95` / 191 entries / Android Debug 签名；badging 校验通过（v0.1.0(1)、SDK 26-34、INTERNET+ACCESS_NETWORK_STATE） | Lead 构建输出（2026-11-28） |
| 已完成 | 纯 JVM 单测：250 用例 / 0 失败 / 0 skipped（plugin-api 95、core:domain 63、runtime:safe-shell 92），BUILD SUCCESSFUL | Lead 回填（2026-11-28） |
| 待完成 | T10：GitHub 文本推送 → 首次 CI（android.yml）→ tag `v0.1.0` 触发 release.yml → Release APK + `SHA256SUMS` 回填；APK 真机安装冒烟；发布说明写明未验证项 | QA 报告 §9 |
| 下一步 | ① Lead 推送并触发首次 CI；② tag/Release 后回填 Release APK sha256 与下载链接；③ 真机安装冒烟（§9.8）；④ 契约收敛 `Term.displayName`；⑤ 安全集成负例与未验证项收尾 | — |

## 9. 已知风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| aarch64 aapt2 覆盖方案失效 | 本机无法出 APK，只能依赖 GitHub Actions | TASK-102 记录可复现步骤与回退条件；CI 作为备用通道并如实标注 |
| 低内存（~1.4GB）构建 OOM | 构建/测试不稳定，反馈慢 | 版本矩阵按低内存选择（ADR-001）；`workers.max=1`、关闭并行/配置缓存；纯 JVM 模块先行验证 |
| 文档与实现漂移 | 验收无依据、契约冲突 | ADR-008 生成/校验门禁；specs 与代码同轮修正；发现偏差走 `CONTRACT_CHANGE` |
| 推迟 Room/Hilt 的迁移成本 | M2 后引入时需迁移进度/设置与 DI | ADR-006/009 记录迁移触发条件与影响；稳定 ID 作为迁移锚点 |
| 内容质量与双语缺失 | 学习闭环体验差、词典排序失真 | TASK-103/104 校验器 + 审查状态；`exerciseSkipReason` 结构化理由；QA 抽查 |
| 安全负例覆盖不足 | 终端/插件越权风险 | `docs/specs/terminal.md` §8、`plugin-api.md` §9 的负例清单必须进 QA 报告 |
| 真实 AI/远程下载/设备矩阵未验证 | 发布说明可能夸大能力 | 发布门禁（`docs/07` §8）要求逐项标注未验证；入口默认关闭或明确限制 |
| 插件无签名信任链 | 内容来源不可验证 | ADR-005：P0 只做哈希/路径/依赖校验；来源展示与签名 M4 后再评估 |
| 部分 Agent 会话 bash 不可用 | 命令需代跑，证据链变慢 | Lead 统一执行命令并贴回输出；任务卡要求贴真实输出，禁止伪造 |
| 依赖镜像可用性 | 构建/CI 拉取失败 | 阿里云镜像 + 官方源兜底（`settings.gradle.kts`）；CI 使用官方源 |
| 仓库唯一推送者瓶颈 | 集成延迟 | Lead 统一 commit/push；并行任务先落盘、由 Lead 批量提交 |
| Release 构建与首次 CI 未运行 | CI 出包能力与 Release 产物未验证 | T10 推送后触发 `android.yml`/`release.yml` 并回填运行链接与 artifact |
| `feature:terminal`/`feature:library` 编译错误（已修复） | 曾阻塞 APK 产出 | tools 修复后全量编译通过（QA-001/QA-012 已关闭） |
| 内容 `Term.displayName` 双语对象 vs `:core:domain` 的 `String` 模型 | 数据层解析/显示名可能失败 | 走 `CONTRACT_CHANGE`：统一 `LocalizedText` 或数据层映射并补测试（QA-004） |
| 内容回归风险（后续内容变更可能重新引入 error/warnings） | `ci.yml` 内容门禁红、无法发版 | 内容校验已 0 error（warnings=12）并接入 CI；内容变更后保持 0 error（QA-006） |
| 本地文件误提交风险（`local.properties`、构建缓存） | 泄露本机路径或增大仓库 | Lead 已补 `.gitignore`（含 local.properties/build/.gradle）；`check_repo.py` 已通过（QA-008） |
| release 使用 debug 签名 | 不可上架、安装包可被重签 | 发布说明写明；正式发版前由 CI secrets 注入签名（QA-009） |
| Gradle Wrapper 指向华为云镜像 / wrapper jar 不入库 | CI 分发下载失败或 `./gradlew` 不可用 | CI 改用 `setup-gradle` 提供 Gradle 8.9 + `gradle` 命令；本地保留 wrapper（QA-010） |
| 二进制生成物不入库（codepack/seed/合集压缩包） | CI 检出后缺产物导致门禁/构建失败 | CI 在构建/门禁前生成：`build_dist.py`、`sync_seed.py`、`merge_docs.py`（§3.1） |

## 10. 新 Agent 上手清单

1. 读 `AGENTS.md` → `docs/00-EXECUTIVE-SUMMARY.md` → 本文件。
2. 读与任务相关的 `docs/decisions/ADR-*.md` 与 `docs/specs/*.md`。
3. 找到 `docs/tasks/TASK-10x.md`，核对目标、只读上下文、写范围、禁止事项、验收标准。
4. 在 `team_task` 中确认任务状态为 Ready/已认领；不修改他人写范围。
5. 完成后按 `docs/templates/HANDOFF.md` 提供证据与交接；不伪造、不越界、不推送。
6. 常用命令（由具备执行通道的成员运行）：`python3 scripts/merge_docs.py`、`python3 scripts/merge_docs.py --check`、`python3 scripts/validate_workflows.py`、`./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test`。

## 11. 变更记录

| 日期 | 变更 | 负责人 |
| --- | --- | --- |
| 2026-11-15 | 初版：文档基线入库并校验通过；13 模块骨架与契约落盘；ADR-001～009、specs、tasks、CONTEXT 交付 | architect |
| 2026-11-22 | T9 交付：`.github/**` 适配 easy-code（Android 构建/tag 发布）、`tools/verify/` 验收工具、`docs/QA-REPORT.md`；名册更新（architect 兼任 QA/Release）；补充出包通道、进度与风险 | architect |
| 2026-11-22 | T9 验收推进：内容 0 error（warnings=12）、`check_content`/`check_repo`/`check_seed`/`run_all` 全部通过；核心模块 4/5 编译通过（designsystem 修复中）；QA 报告与 CONTEXT 回填实测结果 | architect |
| 2026-11-28 | 代码状态更新：13 模块基本落盘（core:data 11 实现、5 feature UI/VM、app 组合根、11 个测试文件）；QA 报告按 B 口径把 APK/首 CI 标为「待发布链验证（T10）」，新增 T10 Release Runbook（QA-REPORT §9 + TASK-110） | architect |
| 2026-11-28 | 编译进展回填：app 10 个入口文件已补齐、aapt2 覆盖改为用户级属性；`assembleDebug` 中间结果 10 模块通过，`feature:terminal`/`feature:library` 修复中；QA-001 标记已修复、新增 QA-012 | architect |
| 2026-11-28 | 首个 APK 成功：13 模块全量 `assembleDebug` BUILD SUCCESSFUL（app-debug.apk 12,484,073 bytes / sha256 `63f8…bd95`，Android Debug 签名、badging 校验通过）；纯 JVM 单测 250 用例 0 失败；CI 按“二进制不入库”适配（系统 gradle 8.9 + 构建前生成内容/文档）；**T9 完成**，T10 待推送/首 CI/tag | architect |
