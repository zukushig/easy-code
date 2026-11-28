# 任务卡：TASK-109 QA：CI 适配、校验脚本、整体验收与发布准备（T9）

> 使用前复制到项目实际任务目录。一个任务只保留一个当前负责人；子任务分别建卡。
> 模板：`docs/templates/TASK_CARD.md`；本卡对应团队任务 **T9**。

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 任务卡编号 | TASK-109 |
| 团队任务 | T9（`team_task` id：task-9） |
| 关联 WBS | CT-000-04（CI 基线）、CT-800-01～CT-800-06（追踪矩阵/端到端/迁移/安全/性能/设备） |
| 关联需求 | NFR-001～NFR-007、全部 P0 需求的验收证据 |
| 优先级 | P0 |
| 状态 | Done（2026-11-28：CI 适配 + 门禁复跑 + QA 报告回填完成；APK/Release/首 CI 归 T10） |
| 当前负责人 | qa |
| 评审人 | lead + architect |
| 目标里程碑 | M5（发布候选前置） |
| 前置依赖 | TASK-102（构建通道）、TASK-105～TASK-108 进入 Review、TASK-103/104 内容可打包 |
| 后续消费者 | TASK-110（发布） |

## 背景与用户价值

MVP 的“可验收”不取决于功能数量，而取决于每个 P0 需求都有可核对证据：构建产物、测试输出、安全负例、离线闭环记录。本任务把 CI 从“规划期文档校验”适配到 `easy-code` 真实工程（Android 构建、内容校验、文档校验、tag 发布），新增 `tools/verify/` 统一校验入口，并产出 QA 报告与验收清单。同时负责提醒“未验证项不得写成已验证”。

## 目标

- [ ] 适配 `.github/workflows/`：`ci.yml`（文档 + 工作流 + 内容校验）、`android.yml`（Android 构建/单测/APK 制品，路径过滤）、`nightly.yml`、`release.yml`（tag 发布）；保持 ADR-001 版本矩阵与 `docs/08` 的效率约束。
- [ ] 新增 `tools/verify/`：内容/清单/路径安全/工作流/文档一致性校验与自测（纯 Python，可本地运行，不依赖网络）。
- [ ] 重跑并通过：`python3 scripts/merge_docs.py`、`--check`、`python3 scripts/validate_workflows.py`；新增校验器自测。
- [ ] 产出 `docs/QA-REPORT.md`：P0 需求追踪矩阵、构建/单测/安全/性能证据、未验证项与已知限制。
- [ ] 更新 `docs/CONTEXT.md` 的“当前状态/已完成/风险”，供发布与后续 Agent 使用。
- [ ] 可执行验收清单：构建、校验、黄金路径 JVM 用例、内容校验全部有命令与结果。

## 非目标

- 不实现业务功能、不修复业务缺陷（发现缺陷建卡/退回，不在本任务顺手改实现）。
- 不推送 GitHub、不创建 tag（仓库写操作由 Lead 执行）；CI 配置只在本地/分支验证。
- 不伪造设备矩阵、真实 AI 联调、真实下载或签名结果。
- 不修改 15 份源文档正文、12 份工程文件的语义（需重生成时按 ADR-008 流程）。

## 只读上下文（必须先读，不得修改）

1. `AGENTS.md`、`docs/06-TESTING-ACCEPTANCE.md`（分层/追踪矩阵/证据格式）、`docs/07-SECURITY-RELEASE.md`（门禁）、`docs/08-GITHUB-WORKFLOW.md`（工作流规范）。
2. `docs/specs/*`（全部契约）、`docs/decisions/ADR-001～ADR-009`。
3. `scripts/merge_docs.py`、`scripts/validate_workflows.py`、`.github/**`（适配前理解约束）。
4. 各模块 `build.gradle.kts` 与 `docs/tasks/TASK-102～TASK-108`（证据要求）。

## 输入与契约

- 输入：其他任务交付的源码、测试、内容包、构建报告。
- 输出：`.github/**` 适配、`tools/verify/**`、`docs/QA-REPORT.md`、`docs/CONTEXT.md` 更新、重生成的合集/压缩包。
- 共享文件：`.github/**`、`docs/QA-REPORT.md`、`docs/CONTEXT.md` 归本任务；`scripts/merge_docs.py`/`validate_workflows.py` 的逻辑只允许按 ADR-008 扩展并自测，不得删改校验规则来放宽门禁。

## 允许改动文件（写范围）

```text
.github/**
tools/verify/**
docs/QA-REPORT.md
docs/CONTEXT.md
```

只读：15 份源文档正文、其余工程文件语义、`app/`、`core/**`、`feature/**`、`runtime/**`、`plugin-api/**`、`content/**`、`tools/build/**`、`tools/content/**`、`gradle/**`。

## 禁止事项

- 不改实现代码来“让验收通过”；不删除/跳过失败测试、不放宽断言、不硬编码成功。
- 不修改脚本逻辑绕过校验；不手工编辑合集；不删除压缩包。
- 不执行 `git push`/不创建 tag/不调用 GitHub 写接口（由 Lead 处理）。
- 不把 GitHub Actions 结果冒充本机验证；不把 Mock 结果写成真实服务联调。
- 不处理真实密钥/签名私钥，不在报告中贴敏感信息。

## 子任务

| 子任务 ID | 行为 | 输出 | 状态 |
| --- | --- | --- | --- |
| TASK-109-A | CI 适配（4 工作流 + 复合动作） | `.github/**` | Backlog |
| TASK-109-B | `tools/verify/` 校验器与自测 | 脚本 + 自测输出 | Backlog |
| TASK-109-C | 文档/内容/工作流校验重跑 | 三条命令输出 | Backlog |
| TASK-109-D | P0 追踪矩阵与证据汇总 | `docs/QA-REPORT.md` | Backlog |
| TASK-109-E | CONTEXT 更新与验收清单 | `docs/CONTEXT.md` 更新 | Backlog |

## 可执行验收标准

1. 文档门禁：`cd /root/workspace/easy-code && python3 scripts/merge_docs.py && python3 scripts/merge_docs.py --check && python3 scripts/validate_workflows.py` 全部通过（贴完整输出）。
2. 内容门禁：`python3 tools/content/validate_content.py`（或 TASK-103 的 CLI）对全部包 0 error；`tools/verify/` 自测通过。
3. 构建/单测：`./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test :app:assembleDebug` 通过（本机或 GitHub Actions，注明通道）。
4. CI 适配校验：`python3 scripts/validate_workflows.py` 对适配后工作流仍通过；`ci.yml` 在文档 PR 路径只跑轻量校验；`android.yml` 路径过滤不触发纯文档改动。
5. QA 报告：P0 需求追踪矩阵每项有证据位置或明确“未验证”；Blocker/Major 清单与结论；性能/设备/安全未验证项如实列出。
6. 边界：`git status --short` 只出现本卡写范围；源码目录无改动。

## 验证计划

| 测试 ID | 层级 | 命令/步骤 | 预期 | 证据位置 |
| --- | --- | --- | --- | --- |
| TST-109-001 | 文档 | `merge_docs.py` + `--check` | `Validation: OK` | 命令输出 |
| TST-109-002 | 工作流 | `validate_workflows.py` | `Validation: OK` | 命令输出 |
| TST-109-003 | 内容 | `validate_content.py` | 0 error + 统计 | 命令输出 |
| TST-109-004 | 构建/单测 | Gradle 命令 | 通过 + 用例数 | 构建日志 |
| TST-109-005 | CI 结构 | validate_workflows 对适配后配置 | 路径过滤/权限/超时通过 | 校验输出 |
| TST-109-006 | 报告 | 人工核对 QA-REPORT | 每个 P0 有证据/未验证标注 | 报告文件 |

## 完成核对

- [ ] 三条文档/工作流校验 + 内容校验 + 构建/单测全部有真实输出。
- [ ] CI 适配通过结构校验，未破坏原有文档门禁。
- [ ] QA 报告覆盖全部 P0 需求与未验证项；无“未验证却写通过”。
- [ ] 未修改实现代码与源文档正文；未推送仓库。
- [ ] `docs/CONTEXT.md` 已更新当前状态。
- [ ] 交接记录完整。

## 交接说明（HANDOFF 要点）

- 当前状态、已完成验收项、未验证项分列。
- 修改文件：`.github/**`、`tools/verify/**`、`docs/QA-REPORT.md`、`docs/CONTEXT.md`、重生成的合集/压缩包。
- 测试证据：全部命令输出与报告路径。
- 未完成项：设备矩阵、真实 AI、真实下载、签名发布等外部依赖项。
- 风险：CI 未在真实 GitHub 仓库运行前，不能宣称“CI 已验证”；本机验证与 Actions 验证必须分开标注。
- 下一步：交 Lead 做 TASK-110 集成与发布决策。

## 执行日志

- 2026-11-22：CI 适配（4 工作流 + setup-android）、`tools/verify/` 交付；`merge_docs.py`/`validate_workflows.py`/`run_all.sh` 复跑通过。
- 2026-11-28：按“二进制不入库”改造 CI（系统 `gradle` 8.9 + 构建/门禁前生成内容与文档合集）；门禁复跑 OK；纯 JVM 单测 250 用例全绿；debug APK 产出并完成 sha256/结构/badging 校验；QA-REPORT 回填，T9 完成。APK 安装冒烟、Release 产物、首次 CI 运行归 T10。

- 2026-11-15：任务 Ready，等待 qa 认领。
