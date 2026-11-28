# 任务卡：TASK-110 集成、质量收口与发布（T10）

> 使用前复制到项目实际任务目录。一个任务只保留一个当前负责人；子任务分别建卡。
> 模板：`docs/templates/TASK_CARD.md`；本卡对应团队任务 **T10**（Lead 自持，无独立 `team_task`）。

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 任务卡编号 | TASK-110 |
| 团队任务 | T10（集成/发布） |
| 关联 WBS | CT-800-07/08（可重复构建/发布候选验收）、CT-700-03/04/06（上下文引导/动效/小大屏）、CT-800-09（Beta 可选） |
| 关联需求 | 全部 P0 需求的最终验收；NFR-003/005/007 |
| 优先级 | P0 |
| 状态 | Backlog（发布链：待 T9 完成 + 全量编译/单测通过 + APK 产出） |
| 当前负责人 | lead |
| 评审人 | architect + qa（发布门禁签字） |
| 目标里程碑 | M5 |
| 前置依赖 | TASK-101～TASK-109 全部通过（或明确记录的未验证项）；T9 的 QA 报告与全量编译/单测证据已回填 |
| 后续消费者 | 无（发布交付） |

## 背景与用户价值

所有并行任务最终要收敛为一个可安装、可验证、可回滚的交付：文档基线一致、构建可重复、核心闭环可走通、安全负例通过、未验证项如实记录。本任务由 Lead 执行集成与发布，避免“各自完成但拼不起来”。

## 目标

- [ ] 集成检查：13 模块构建通过；`:app:assembleDebug` 可安装；核心学习闭环（内容 → 图谱 → 词典 → 示例 → 终端 → AI/未配置 → 进度）端到端走通。
- [ ] 质量收口：TASK-109 的 QA 报告无未处理 Blocker/Major；已知限制与未验证项列明。
- [ ] 文档收口：三条文档校验通过；`docs/CONTEXT.md` 与真实状态一致；ADR/specs 与代码一致。
- [ ] 发布准备：版本号/版本代码、制品名称与哈希、Release 说明模板、回滚说明；如发布 GitHub Release，由 Lead 执行（tag/推送）。
- [ ] 决策记录：真实 AI/远程下载/设备矩阵等未验证能力按 `docs/07` 门禁处理（关闭入口或明确限制发布）。

## 非目标

- 不在此卡中重写业务实现；缺陷回退到对应功能任务。
- 不承诺未验证能力（真实 AI 联调、完整设备矩阵、签名可执行插件、完整 Linux）。
- 不修改版本矩阵（除非先修订 ADR-001）。

## 只读上下文（必须先读，不得修改）

1. `AGENTS.md`、`docs/07-SECURITY-RELEASE.md` §8～§13（发布门禁/步骤/回滚/事件分级）。
2. `docs/06-TESTING-ACCEPTANCE.md` §7/§8（门禁与证据格式）。
3. `docs/QA-REPORT.md`（TASK-109 产出）、`docs/CONTEXT.md`、`docs/decisions/*`。
4. `docs/tasks/TASK-101～TASK-109` 的交接记录。
5. `.github/workflows/release.yml`（发布工作流约束：只出未签名制品 + SHA-256 清单）。

## 输入与契约

- 输入：全部模块源码、内容包、QA 报告、构建产物与哈希、未验证项清单。
- 输出：集成后的仓库状态；发布记录（版本、提交、制品哈希、已知限制、回滚方式）；最终任务汇报。
- 共享文件：所有模块与文档（Lead 是唯一推送者；集成冲突由 Lead 裁决）。

## 允许改动文件（写范围）

```text
仓库内任意文件（仅限集成修复与发布记录，且必须先与对应 owner 说明）
```

约束：不改实现逻辑绕过验收；需要修复时退回对应任务或由 owner 确认后最小修改。

## 禁止事项

- 不在未通过门禁时发布；不把未验证项写成已验证。
- 不发布含密钥、测试后门、Mock 成功行为、内部交接记录的制品。
- 不绕过安全门禁；不删除失败测试/负例。
- 不消费用户真实密钥；不导出签名私钥。

## 子任务

| 子任务 ID | 行为 | 输出 | 状态 |
| --- | --- | --- | --- |
| TASK-110-A | 集成构建与闭环验证 | 构建日志 + 端到端记录 | Backlog |
| TASK-110-B | 质量门禁复核 | 门禁清单签字 | Backlog |
| TASK-110-C | 文档/契约一致性收口 | 三条校验输出 | Backlog |
| TASK-110-D | 发布准备与制品 | 版本/哈希/Release 说明/回滚说明 | Backlog |
| TASK-110-E | 最终汇报与归档 | 完成报告 | Backlog |

## 发布执行清单（T10 草稿，2026-11-28）

> 与本卡配套的完整 runbook 见 `docs/QA-REPORT.md` §9；本节是可执行摘要。发布由 Lead 执行（唯一推送者），本卡评审人负责核对清单。

### 前置条件（全部满足才可打 tag）

> 仓库形态（2026-11-28 确认）：本机无 git 凭据、经 GitHub MCP 只推文本，`gradle/wrapper/gradle-wrapper.jar`、`content/dist/*.codepack`、`app/src/main/assets/seed/*.codepack`、`CodeTree-合并文档包.zip` 不入库；CI 在构建/门禁前生成（`build_dist.py`、`sync_seed.py`、`merge_docs.py`），并使用 `setup-gradle` 提供的 Gradle 8.9（`gradle` 命令，不依赖 `./gradlew`）。

- [ ] 全量编译：13 模块（`:app` + `plugin-api` + `core:domain` + `core:data` + `core:designsystem` + `runtime:api` + `runtime:safe-shell` + 5 个 feature）编译成功。
- [ ] 纯 JVM 单测：11 个测试文件全绿（约 180 用例）。
- [ ] 静态门禁：`merge_docs.py --check`、`validate_workflows.py`、`tools/content/validate_content.py`、`bash tools/verify/run_all.sh` 全通过。
- [ ] `docs/QA-REPORT.md` 已回填实测数据；未验证项明确。
- [ ] 仓库无密钥/本地文件（`.gitignore` 生效），GitHub 远端已同步。

### 版本与触发

| 项 | 值 |
| --- | --- |
| versionName / versionCode | `0.1.0` / `1`（`app/build.gradle.kts`） |
| tag | `v0.1.0`（`v*` 触发 `release.yml`） |
| 主通道 | Lead 提交/推送 `main` → 创建并推送 tag（GitHub MCP，本机无 git 凭据） |
| 备用 | Actions 手动 `Run workflow` 输入 `version`，只出 artifact 不建 Release |
| 发布环境 | `release.yml` build job 使用 `environment: release`（GitHub 仓库需存在该环境或允许自动创建） |

### 产物与校验

| 项 | 预期 |
| --- | --- |
| APK | `release-out/app-release.apk`（`app/build/outputs/apk/release/app-release.apk` 复制） |
| 校验和 | `release-out/SHA256SUMS`，格式 `<sha256>  app-release.apk` |
| GitHub Release | 标题 `CodeTree v0.1.0`，资产 `app-release.apk` + `SHA256SUMS` |
| artifact | `release-apk-v0.1.0`，retention 30 天 |
| APK sha256 | 待执行后回填 `docs/QA-REPORT.md` §9.4 与交接记录 |

### 安装与回滚

- 安装：下载 APK + `SHA256SUMS` → `sha256sum -c` → 允许未知来源 → 安装（Android 8.0+）→ 首启引导 → 离线学习闭环验证。
- 回滚：删除/标记 Release 为 pre-release；用户卸载重装上一 tag 的 APK；MVP 无 Room，无数据库降级问题。

### 发布后验证（必须回填证据）

- [ ] APK 安装成功、可启动、5 Tab 可切换。
- [ ] 离线学习闭环走通（内容 → 图谱 → 词典 → 示例 → 终端 → AI 未配置态 → 进度）。
- [ ] `.codepack` 导入成功、损坏包被拒。
- [ ] Release 资产 sha256 与记录一致。
- [ ] 失败按回滚步骤执行并记录。

## 可执行验收标准

1. 构建：`cd /root/workspace/easy-code && ./gradlew :app:assembleDebug` 通过；贴 APK 路径与 `unzip -l` 结构检查。
2. 核心闭环：按 PRD §7 的 7 步在设备/模拟器（或本机可安装环境）走通并有记录；无法完成的步骤明确“未验证”。
3. 门禁：`docs/07` §8 的 9 项门禁逐项有结论（需求/构建/测试/安全/隐私/兼容/内容/制品/回滚）。
4. 文档：`python3 scripts/merge_docs.py --check` 与 `python3 scripts/validate_workflows.py` 通过；`docs/CONTEXT.md` 与真实状态一致。
5. 发布记录：版本号、版本代码、提交、制品哈希、已知限制、回滚步骤齐全；签名私钥未出现。
6. 汇报：完成项/未完成项/证据/风险/下一步，不含未验证的“已通过”。

## 验证计划

| 测试 ID | 层级 | 命令/步骤 | 预期 | 证据位置 |
| --- | --- | --- | --- | --- |
| TST-110-001 | 构建 | `./gradlew :app:assembleDebug` | 成功 + APK | 构建日志 |
| TST-110-002 | 结构 | `unzip -l <APK>` | classes.dex/Manifest | 输出 |
| TST-110-003 | 端到端 | PRD §7 七步 | 可走通/明确未验证 | 验收记录 |
| TST-110-004 | 文档 | merge/validate 命令 | OK | 输出 |
| TST-110-005 | 门禁 | `docs/07` §8 九项 | 逐项结论 | 发布检查表 |
| TST-110-006 | 制品 | 哈希/版本/回滚 | 齐全 | 发布记录 |

## 完成核对

- [ ] 构建与 APK 结构证据已贴。
- [ ] 核心闭环走通或未验证项明确记录。
- [ ] 九项发布门禁逐项有结论；Blocker/Major 清零或明确阻断发布。
- [ ] 文档校验通过，CONTEXT 与状态一致。
- [ ] 发布记录齐全，无密钥。
- [ ] 最终汇报已发送给用户/团队。

## 交接说明（HANDOFF 要点）

- 发布版本、提交、tag、制品与哈希（`app-release.apk` 的 sha256 写入 QA 报告 §9.4）。
- 已验证项与未验证项清单（真实 AI/远程下载/设备矩阵/签名）。
- 回滚方式与触发条件。
- 遗留缺陷与后续任务建议。

## 执行日志

- 2026-11-15：任务 Backlog；待 TASK-105～TASK-109 完成后由 Lead 启动。
