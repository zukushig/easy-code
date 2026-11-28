# 08 GitHub 工作流效率规范

## 1. 目标与现状

本规范把 `CT-000-04` 的 CI（持续集成）任务落实为可提交、可静态校验的 GitHub Actions 配置，核心目标是**减少每次变更消耗的运行器时间，同时保持既有发布门禁不降级**。

现状：项目处于产品与架构规划阶段，仓库内只有文档、脚本与工作流配置，没有 Android 源码、Gradle Wrapper（Gradle 包装器）和 `gradlew`。因此所有 Android 构建类作业在源码落地前自动跳过，文档与工作流校验始终运行。

效率结论来自可比测量。本文件给出的时间、命中率和运行器分钟数是**优化目标**，未在本环境实测，真实数值以 GitHub Actions 运行记录为准。

## 2. 已识别的效率瓶颈

| 编号 | 瓶颈 | 影响 | 处理 |
| --- | --- | --- | --- |
| E-01 | 无工作流，后续容易给每次提交套一个全流程 Gradle 构建 | 文档改动重复下载依赖与编译 | 用工作流级 `paths` 过滤隔离文档与源码 |
| E-02 | 同一 PR 连续推送产生多个排队运行 | 旧运行仍占用运行器与缓存写入 | `concurrency` + `cancel-in-progress: true` |
| E-03 | 为几个秒级检查分别启动运行器 | 启动与依赖安装开销大于检查本身 | 轻量检查合并成单个 `verify` 作业 |
| E-04 | 重复安装 JDK、恢复依赖、下载 Gradle 分发包 | 每次构建固定成本 | 复合动作统一环境，缓存由单一来源管理 |
| E-05 | 所有 PR 跑完整设备/版本矩阵 | 矩阵爆炸与排队 | 常规 PR 跑代表性任务，全量任务放夜间 |
| E-06 | 作业无超时上限 | 卡死任务长期占用运行器 | 每个作业显式 `timeout-minutes` |
| E-07 | 制品长期保留 | 存储成本与下载列表噪声 | 按用途设置 `retention-days` |
| E-08 | 源文档改动后忘记重生成合集 | 校验器只检查旧文件，形成假阴性 | `--check` 增加时效性比对 |
| E-09 | 公共镜像与私有仓库混用、全局换源 | 依赖解析失败或凭据外泄 | 按仓库分别设置、任务专用初始化脚本 |
| E-10 | 每次 PR 手工填标签与审查人 | 协调等待 | `labeler.yml`、CODEOWNERS 示例与 PR 模板 |

## 3. 分层工作流与触发矩阵

| 工作流 | 触发 | 运行内容 | 运行器策略 |
| --- | --- | --- | --- |
| `ci.yml` | PR、push main、手动 | 文档合集、时效性、工作流结构、策略文本、路径标签 | 常驻快速检查，单 `verify` + 条件 `label` |
| `android.yml` | PR/push 且源码或 Gradle 文件变更、手动 | 静态检查、JVM 单测、Debug 制品 | 两个作业并行，可取消旧运行 |
| `nightly.yml` | 每日定时、手动 | 全量校验、干净构建、报告 | 低频全量，报告保留 3 天 |
| `release.yml` | `v*` 标签、手动 | 发布前门禁、Release 制品、哈希清单 | 不取消在途运行，人工环境审批 |

| 事件 | ci.yml | android.yml | nightly.yml | release.yml | 自动标签 |
| --- | --- | --- | --- | --- | --- |
| 仅文档改动的 PR | 运行 | 跳过 | 跳过 | 跳过 | 运行（同仓库 PR） |
| 源码改动的 PR | 运行 | 运行 | 跳过 | 跳过 | 运行（同仓库 PR） |
| push main（文档） | 运行 | 跳过 | 跳过 | 跳过 | 跳过 |
| push main（源码） | 运行 | 运行 | 跳过 | 跳过 | 跳过 |
| 每日定时 | 跳过 | 跳过 | 运行 | 跳过 | 跳过 |
| `v*` 标签 | 跳过 | 跳过 | 跳过 | 运行 | 跳过 |
| 手动触发 | 运行 | 运行 | 运行 | 运行 | 跳过 |

`android.yml` 的 `paths` 只列源码、Gradle 与工程文件，不把自身工作流路径列入。工作流文件改动由 `ci.yml` 的 `validate_workflows.py` 校验，不会触发尚不存在源码的 Gradle 构建。

## 4. 关键效率手段

### 4.1 路径过滤

文档、脚本、模板改动只进 `ci.yml`。`android.yml` 在 `pull_request` 与 `push.branches` 上都声明同一组 `paths`，避免同一提交在两类事件上重复触发重构建。`ci.yml` 是稳定的必需检查，保留全量触发，不设置路径过滤。

### 4.2 并发取消

`ci.yml`、`android.yml`、`nightly.yml` 使用 `group: <工作流>-${{ github.ref }}` 并开启 `cancel-in-progress: true`。同一 PR 的新推送取消旧运行，最多保留一个活跃运行。`release.yml` 显式设为 `false`：发布在途状态未知时停止运行会产生重复写与半途制品，改为让当前运行完成或由发布负责人终止。

### 4.3 轻检查合并、重任务并行

`ci.yml` 把合集校验、工作流结构校验与策略文本扫描放在同一个 `verify` 作业里，共用一次检出、一次 Python 环境与一次依赖安装。`android.yml` 把静态检查/单测与 `assembleDebug` 拆成两个可并行作业，二者共用同一份恢复缓存。判断标准是"作业启动成本是否大于被拆开的检查成本"。

### 4.4 缓存单一来源

复合动作 `.github/actions/setup-android` 统一执行：

1. `actions/setup-java@v4` 安装 Temurin JDK 17，**不启用**其 `cache: gradle`。
2. `gradle/actions/setup-gradle@v4` 统一管理 Gradle User Home（依赖、Wrapper 分发包、构建缓存）与缓存清理，PR 上设置 `cache-read-only: true`。

两个动作都开启 Gradle 缓存会争用同一目录并产生重复缓存条目。缓存失效由 `gradle/actions/setup-gradle` 依据 Gradle 版本与依赖锁文件计算，不在本仓库手写第二套键。PR 只读缓存阻止不可信分支污染主干缓存。

### 4.5 按需矩阵

常规 PR 与 push 只跑 JVM 单元测试、静态检查与 Debug 构建，不启动模拟器。全量构建与报告放在 `nightly.yml`。设备矩阵、Python/JavaScript 运行时和真实 AI（人工智能）联调按 `06-TESTING-ACCEPTANCE.md` 的设备矩阵与人工门禁执行，进入默认 CI 前需要单独 ADR（架构决策记录）。

### 4.6 超时、制品与检出

- 每个作业都有正整数 `timeout-minutes`，上限从 3 分钟到 40 分钟不等。
- 测试报告保留 7 天，夜间报告 3 天，发布候选 30 天；`if-no-files-found` 对报告用 `warn`，对发布制品用 `error`。
- `actions/checkout@v4` 统一 `fetch-depth: 1`，文档与构建任务都不需要完整历史。
- 校验依赖锁定为 `PyYAML==6.0.3`，由 `actions/setup-python@v5` 的 `cache: pip` 复用。

### 4.7 权限与供应链

所有工作流顶层 `permissions: contents: read`；仅自动标签作业提升 `pull-requests: write`，且限制为同仓库 PR，避免派生仓库 PR 拿到写权限。`pull_request_target` 被校验脚本拒绝。第三方动作使用标签版本，上线前固定为 40 位提交哈希并记录来源；`validate_workflows.py` 会输出未固定的引用数量。

## 5. 时效性校验

`scripts/merge_docs.py --check` 除校验锚点、任务编号、约束文本与压缩包外，还在内存中重建一次合集并与磁盘文件逐字比对。源文档变更而合集未重生成时返回失败，堵住"旧文件自洽"这类假阴性。

`scripts/validate_workflows.py` 用 PyYAML 6.0.3 解析工作流。PyYAML 遵循 YAML 1.1，未加引号的 `on:` 会被解析成布尔 `True`；校验器同时兼容字符串键 `on` 与布尔键 `True`，避免把正常工作流误判为缺少触发器。自测包含该用例。

## 6. 国内镜像边界

`setup-android` 的 `use-cn-mirrors` 输入默认关闭。开启时只在 `$RUNNER_TEMP` 生成任务专用初始化脚本，并把路径写入 `CODETREE_GRADLE_INIT`，Gradle 命令通过 `-I "$CODETREE_GRADLE_INIT"` 显式引用。

- Central 使用 `https://maven.aliyun.com/repository/central`，Google Maven 使用 `https://maven.aliyun.com/repository/google`，两者分别设置，不相互替代。
- `pluginManagement` 与 `dependencyResolutionManagement` 分别追加镜像，同时保留项目原有 `google()`、`mavenCentral()`、`gradlePluginPortal()` 与内容过滤、校验元数据。
- 不使用通配镜像接管全部仓库，不改全局 registry、Git `insteadOf`、系统 hosts 或 shell 启动文件，不关闭 TLS（传输层安全）与主机校验。
- Maven 镜像不作用于 Gradle Wrapper 分发包与 Android SDK 组件下载；这两类由 `setup-gradle` 缓存与官方工具链处理。
- 私有包、企业代理继续走原授权渠道；镜像地址与可用性在启用时核实，锁文件、版本与哈希保持不变。

## 7. 分支保护与必需检查

`ci.yml / verify` 是每次变更都稳定出现的必需检查。`android.yml` 的两个作业使用工作流级路径过滤，在纯文档改动时整体不运行。仓库规则集按路径要求源码相关检查，避免把"因路径过滤未触发"当成失败或长期挂起。

`release.yml / build` 绑定 `environment: release`，由授权发布流程承担审批；工作流本身不读取、打印或导出签名私钥，只生成未签名 Release 制品与 SHA-256 清单。签名与分发按 `07-SECURITY-RELEASE.md` 第 9、10 节执行。

## 8. 与 Agent 协作规范的衔接

- `.github/PULL_REQUEST_TEMPLATE.md` 强制填写任务 ID（WBS 编号）、需求编号、验收证据、未验证项与回滚方式，对应 `03-AGENT-COLLABORATION.md` 的评审与交接协议。
- `.github/labeler.yml` 按 `docs`、`workflows`、`scripts`、`content`、`android`、`security` 自动打标签，供 COORD（协调者）分派。
- `.github/CODEOWNERS.example` 按模块给出审查归属模板，复制为 `CODEOWNERS` 并替换真实组织或团队后生效；校验器会拒绝仍含占位的 `CODEOWNERS`。
- 分支命名、提交说明与 PR 合并规则沿用 `03-AGENT-COLLABORATION.md` 第 14 节。

## 9. 效率目标

以下为设计目标，未在本环境实测；上线后用 GitHub Actions 运行时长、缓存命中与取消记录替换。

| 指标 | 目标 | 依据 |
| --- | --- | --- |
| 纯文档 PR 反馈 | 单个运行器、校验作业小于 1 分钟 | 只做 Python 校验与文本扫描 |
| 同一 PR 活跃运行数 | 最多 1 个 | 并发取消旧运行 |
| 文档改动触发的 Gradle 作业 | 0 个 | 工作流级路径过滤 |
| 重复构建依赖下载 | 命中缓存后不再全量下载 | `setup-gradle` 缓存 |
| 常规 PR 作业数 | 2 个轻量 + 2 个源码（条件） | 轻检查合并、重任务并行 |
| 制品保留 | 报告 3～7 天、发布候选 30 天 | `retention-days` |
| 卡死作业占用 | 单个作业上限 40 分钟 | `timeout-minutes` |

## 10. 校验与上线前动作

本地可执行：

```text
python3 scripts/validate_workflows.py --self-test
python3 scripts/validate_workflows.py
python3 scripts/merge_docs.py
python3 scripts/merge_docs.py --check
```

校验器覆盖：YAML 可解析、触发器、`permissions`、`concurrency`、作业超时、`needs` 引用、本地动作路径、脚本引用、路径过滤、复合动作 `shell`、labeler 规则、PR 模板、CODEOWNERS 占位、约束文本逃逸措辞，以及正反例自测。

单文件合集包含按原样收录的工程文件附录，附录里的工具源码含约束文本正则与测试夹具字面量。两个校验器都对生成合集只扫描正文区，附录按工程文件对待；`merge_docs.py` 另行逐字比对附录文件清单、标题和全文，因此附录不参与正文约束计数、也不会成为绕过约束的通道。源文件变更后先运行生成命令，再运行 `--check`，顺序颠倒会因为合集未重生成而失败。

上线前动作：

1. 安装 `actionlint` 并对全部工作流执行语法与表达式检查；当前环境未安装该工具，本轮校验使用 PyYAML 6.0.3 与自测脚本完成。
2. 把第三方动作标签固定为提交哈希，记录来源与版本。
3. 复制 `CODEOWNERS.example` 为 `CODEOWNERS` 并填入真实组织或团队。
4. 按 `07-SECURITY-RELEASE.md` 配置 `release` 环境保护规则与签名凭据。
5. 源码落地后首次触发 `android.yml`，记录冷缓存与热缓存时长，替换第 9 节的估算目标。
6. 把项目根目录的 `CodeTree-合并文档包.zip` 纳入版本控制，保证 CI 检出后 `--check` 能校验压缩包成员。

## 11. 未验证项

- 工作流未在真实 GitHub 仓库运行，未取得运行器时长、缓存命中或并发取消记录。
- Android 源码、Gradle Wrapper 与 `gradlew` 尚不存在，`android.yml` 的构建作业在源码落地前自动跳过。
- 模拟器设备矩阵、真实 AI（人工智能）服务联调与发布签名仍在人工门禁范围，不计入默认 CI 已验证能力。
