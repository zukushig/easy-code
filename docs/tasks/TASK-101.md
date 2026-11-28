# 任务卡：TASK-101 文档基线重建与契约文档（T1）

> 使用前复制到项目实际任务目录。一个任务只保留一个当前负责人；子任务分别建卡。
> 模板：`docs/templates/TASK_CARD.md`；本卡对应团队任务 **T1**。

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 任务卡编号 | TASK-101 |
| 团队任务 | T1（`team_task` id：task-1） |
| 关联 WBS | CT-000-01（冻结 PRD/边界）、CT-000-02（仓库初始化）、CT-800-01（追踪矩阵基础设施） |
| 关联需求 | 无直接 PRD 需求；支撑全部 P0 |
| 优先级 | P0 |
| 状态 | In Progress（文档与契约已交付，待 Lead 最终确认后 Done） |
| 当前负责人 | architect |
| 评审人 | lead（协调者）+ qa（交叉核对者，不得只写执行者本人） |
| 目标里程碑 | M0（规划基线） |
| 前置依赖 | 无（规划基线合集已提供） |
| 后续消费者 | TASK-102～TASK-110 全部 |

## 背景与用户价值

项目规划阶段的 15 份源文档 + 12 份工程文件只存在于单一合并合集（附件）中，仓库 `easy-code` 需要可维护的源文档基线与生成脚本，否则后续所有 Agent 没有可引用的事实来源，也无法用脚本校验文档一致性。本任务把基线逐字还原到仓库，并把 MVP 落地所需的决策、模块契约、任务卡补齐，使 T2～T10 可以直接认领执行。

## 目标

- [x] 从合集重建 15 份源文档与 12 份工程文件到仓库，内容逐字（标题回退两级、锚点链接还原为相对链接）。
- [x] `python3 scripts/merge_docs.py`、`python3 scripts/merge_docs.py --check`、`python3 scripts/validate_workflows.py` 三条命令全部通过，且重生成合集与原始基线**逐字节一致**。
- [x] 新增 `docs/CONTEXT.md`、`docs/decisions/ADR-001～ADR-009`、`docs/specs/*.md`（6 份）、`docs/tasks/TASK-101～TASK-110`（10 份）。
- [ ] Lead 复核以上产物并确认任务完成。

## 非目标

- 不修改 15 份源文档正文与 12 份工程文件的内容（只做逐字重建，不做 MVP 适配；适配由 TASK-109 在独立分支/后续任务处理）。
- 不写工程代码（app/core/feature/runtime/plugin-api 的 `src/**`）。
- 不修改 `.gitignore`、不执行 `git commit`/`git push`、不运行 Gradle。
- 不新增合并源文档（新增文档不进入 `SOURCE_ORDER`，不参与 merge 校验）。

## 只读上下文（必须先读，不得修改）

1. 仓库根 `AGENTS.md`、`README.md`、`docs/00-EXECUTIVE-SUMMARY.md`。
2. `docs/01-PRD.md`（P0 需求与 MVP 完成标准）、`docs/02-ARCHITECTURE.md`（模块/接口草案）。
3. `docs/03-AGENT-COLLABORATION.md`（任务粒度、DoR/DoD、交接协议）。
4. `docs/04-WBS.md`（CT-000/010/100/200/300/400/500/600/700/800 编号）。
5. `docs/05-DATA-CONTENT-PLUGIN.md`、`docs/06-TESTING-ACCEPTANCE.md`、`docs/07-SECURITY-RELEASE.md`、`docs/08-GITHUB-WORKFLOW.md`。
6. `docs/templates/ADR.md`、`docs/templates/TASK_CARD.md`、`docs/templates/HANDOFF.md`、`docs/templates/AGENT_DISPATCH.md`。
7. 规划基线合集（附件）作为逐字比对基准。

## 输入与契约

- 输入：附件 `CodeTree-项目文档合集.md`（3814 行，15 章正文 + 附录 A 的 12 份工程文件）。
- 生成契约：`scripts/merge_docs.py`（`SOURCE_ORDER` 15 项、`ENGINEERING_FILES` 12 项、`EXPECTED_TASKS = 90`）。
- 输出契约：根目录 `CodeTree-项目文档合集.md` + `CodeTree-合并文档包.zip`（及工作区根目录镜像 zip），均保留、不手工编辑、不加入 `.gitignore`。

## 允许改动文件（写范围）

```text
docs/**（含新增 decisions/、specs/、tasks/、CONTEXT.md） 
README.md、AGENTS.md（仅逐字重建） 
scripts/**（仅逐字重建 3 份） 
.github/**（仅逐字重建 9 份）
```

## 禁止事项

- 不修改 15 份源文档正文与 12 份工程文件的内容；不删除生成物；不手工编辑合集。
- 不修改 `.gitignore`、`settings.gradle.kts`、`gradle/**`、任何模块 `src/**`。
- 不执行 `git commit`/`git push`/`git tag`；不调用 GitHub 写接口。
- 不运行 Gradle、不安装/升级 SDK、不改 CI 密钥。
- 不把“附录 A 中的工具源码字面量”当作正文约束豁免（校验器已按附录分区处理）。

## 子任务

| 子任务 | 行为 | 输出 | 状态 |
| --- | --- | --- | --- |
| TASK-101-A | 重建 15 份源文档 | 仓库 `docs/**`、`README.md`、`AGENTS.md` | Done（验证通过） |
| TASK-101-B | 重建 12 份工程文件 | `scripts/**`、`.github/**` | Done（验证通过） |
| TASK-101-C | 运行三条校验并保真比对 | 命令输出（Lead 贴回） | Done（IDENTICAL） |
| TASK-101-D | 编写 CONTEXT/ADR/specs/tasks | `docs/CONTEXT.md`、`docs/decisions/`、`docs/specs/`、`docs/tasks/` | Done（本卡交付） |
| TASK-101-E | Lead 复核与收尾 | 任务状态 Done | 待 Lead |

## 可执行验收标准

1. 文件存在性（13 模块外不新增代码文件）：
   - `ls docs/00-EXECUTIVE-SUMMARY.md README.md AGENTS.md docs/01-PRD.md ... docs/templates/ADR.md`（15 份）全部存在；
   - `ls .github/workflows/{ci,android,nightly,release}.yml .github/actions/setup-android/{action.yml,codetree-mirrors.gradle} .github/{labeler.yml,CODEOWNERS.example,PULL_REQUEST_TEMPLATE.md} scripts/{merge_docs.py,validate_workflows.py,requirements-ci.txt}` 全部存在。
2. 文档门禁（命令与预期输出）：
   - `cd /root/workspace/easy-code && python3 scripts/merge_docs.py` → `Merged lines: 3814`、`Source docs: 15`、`Engineering appendix: 12`、`WBS tasks: 90`、`Validation: OK`；
   - `python3 scripts/merge_docs.py --check` → `Validation: OK`（含压缩包完整性）；
   - `python3 scripts/validate_workflows.py` → `Validation: OK`（4 工作流 / 1 复合动作 / 策略检查通过；third-party uses 未 pin SHA 为提示不阻断）。
3. 保真：`diff -q "<附件合集>" /root/workspace/easy-code/CodeTree-项目文档合集.md` → `IDENTICAL`（本次已由 Lead 验证）。
4. 新增文档存在且结构正确：`docs/decisions/ADR-001～009*.md` 9 份、`docs/specs/{module-layout,knowledge,terminal,ai-provider,plugin-api,design-system}.md` 6 份、`docs/tasks/TASK-101～110*.md` 10 份、`docs/CONTEXT.md` 1 份；ADR 每份含背景/决策/备选/后果/状态。
5. 不改边界：`git status --short` 中不出现 `.gitignore` 与 13 个模块的 `src/**` 改动（由 Lead 检查）。

## 验证计划

| 测试 ID | 层级 | 命令/步骤 | 预期 | 证据位置 |
| --- | --- | --- | --- | --- |
| TST-101-001 | 文档生成 | `python3 scripts/merge_docs.py` | `Validation: OK`，3814 行 | Lead 贴回的命令输出 |
| TST-101-002 | 文档时效性 | `python3 scripts/merge_docs.py --check` | `Validation: OK` | 同上 |
| TST-101-003 | 工作流 | `python3 scripts/validate_workflows.py` | `Validation: OK` | 同上 |
| TST-101-004 | 保真 | `diff -q` 附件与生成合集 | 无输出（一致） | `FIDELITY: identical` |
| TST-101-005 | 结构 | `ls` 新增文档清单 | 26 份新增文件存在 | 本卡与 Lead 复核记录 |

## 完成核对

- [x] 所有验收条件有证据（Lead 已贴三条命令输出 + 保真 diff）。
- [x] 正常/失败路径核对：重建脚本结构断言、`--check` 的时效性比对已覆盖“源文档漂移”失败路径。
- [x] 无新增未解释失败；未修改脚本逻辑。
- [x] 已核对未触碰 `.gitignore`、模块 `src/**`、生成物。
- [x] 文档、ADR、specs、tasks 已同步（ADR-009 与 `settings.gradle.kts`、`plugin-api`/`runtime:api` 代码一致）。
- [ ] Lead 复核签字后置 Done。

## 交接说明（HANDOFF 要点）

- 当前状态：文档基线 + 契约文档已就绪；三条校验绿；保真 diff 一致。
- 已完成：15 份源文档、12 份工程文件、ADR-001～009、6 份 specs、10 张任务卡、CONTEXT。
- 修改文件：`docs/**`（新增 decisions/specs/tasks/CONTEXT）、重建的 15 份源文档与 12 份工程文件。
- 契约变化：无（原 15 份文档正文不变）；新增 ADR-009 记录模块布局收敛，新增 specs 作为 MVP 契约。
- 验证证据：见本卡验证计划；Lead 贴回的三条命令输出 + `FIDELITY: identical`。
- 已知风险：新增 specs 的终端白名单命令、设计令牌属于“契约先行”，实现方（engine/tools/shell）如发现不可实现必须在同轮提交 `CONTRACT_CHANGE`，不得静默偏离。
- 下一步：Lead 复核并置 Done；各实现任务按 TASK-102～TASK-110 推进。

## 执行日志

- 2026-11-15：从附件合集反向重建 15+12 份文件（脚本 `architect-reconstruct-docs.py`，结构断言 + 保真 diff 通过）；Lead 执行三条校验均 OK；补齐 ADR-001～009、specs×6、tasks×10、CONTEXT。
