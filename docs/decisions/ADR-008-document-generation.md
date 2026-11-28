# ADR-008：文档生成方式（D-008）

| 字段 | 内容 |
| --- | --- |
| 记录编号 | ADR-008（对应决策索引 D-008） |
| 状态 | Accepted（脚本已交付并作为文档一致性门禁） |
| 日期 | 2026-11-15（Asia/Shanghai） |
| 提案人 | ARCH（架构负责人） |
| 评审人 | Lead、QA |
| 关联任务/需求 | TASK-101、TASK-109；CT-000-04、CT-800-01 |
| 替代关系 | 无 |

## 背景

规划期文档由 15 份源文档 + 12 份工程文件共同构成，评审需要一个单一入口；同时文档之间大量交叉引用，手工维护合集容易漂移。`docs/00-EXECUTIVE-SUMMARY.md` 的 E-08 与 `docs/08-GITHUB-WORKFLOW.md` §5 都要求“源文档变更后合集必须重生成，避免旧文件自洽的假阴性”。

已确认事实：

- 仓库已交付并校验通过 `scripts/merge_docs.py`：生成根目录 `CodeTree-项目文档合集.md`（正文 15 章 + 附录 A 的 12 份工程文件全文）与 `CodeTree-合并文档包.zip`，并把压缩包镜像到工作区根目录；`--check` 在内存中重建合集逐字比对，并校验锚点、链接、任务编号、约束文本、附录全文与 ZIP 完整性。
- `scripts/validate_workflows.py` 校验 4 个工作流、1 个复合动作、路径过滤、并发、超时、权限、labeler、PR 模板、CODEOWNERS 占位与策略文本。
- 本次 `python3 scripts/merge_docs.py` 输出：`Merged lines: 3814`、`Source docs: 15`、`Engineering appendix: 12`、`WBS tasks: 90`、`Validation: OK`；`--check` 与 `validate_workflows.py` 均通过；重建合集与附件基线逐字节一致。

## 决策驱动因素

- **单一事实来源**：15 份源文档与 12 份工程文件是事实来源，合集是派生产物。
- **可复核**：评审者用一个文件即可完成范围评审；CI 可自动判断“源文档改了但合集没重生成”。
- **防漂移**：禁止手工编辑合集，减少多 Agent 并行时的文档冲突。
- **可维护**：脚本本身也是仓库产物，使用标准库实现，不依赖额外服务。

## 候选方案

| 方案 | 优点 | 代价/风险 | 验证结果 |
| --- | --- | --- | --- |
| A：脚本生成合集 + `--check` 时效性校验（现状） | 确定性、可 CI、可逐字比对；压缩包可分发 | 每次源文档变更需多跑一次生成；合集与压缩包进入版本库增加体积 | 采用；已通过生成、`--check`、保真 diff 与工作流校验 |
| B：手工维护单一合集 | 无脚本依赖 | 极易漂移；无法判断遗漏；多人并行冲突 | 不采用 |
| C：文档站点生成器（MkDocs/Docusaurus） | 浏览体验好 | 引入 Node/Python 生态与构建依赖；与“单一 Markdown 交付”目标不符 | 不采用；需要站点时，可作为后续派生展示层 |
| D：只在 CI 中生成、不入库 | 仓库干净 | 评审者无法离线拿到合集；`--check` 失去比对对象 | 不采用；`docs/08` §10 明确要求压缩包纳入版本控制 |

## 决策

1. **生成唯一入口**：`python3 scripts/merge_docs.py` 生成 `CodeTree-项目文档合集.md` 与 `CodeTree-合并文档包.zip`（同时镜像到工作区根目录）；不得手工编辑这两个产物。
2. **一致性门禁**：任何源文档或工程文件变更后，必须依次运行 `python3 scripts/merge_docs.py` 与 `python3 scripts/merge_docs.py --check`；`--check` 失败视为文档门禁失败，阻断合并。
3. **工作流门禁**：`.github/**`、`scripts/validate_workflows.py` 或 CI 配置变更后运行 `python3 scripts/validate_workflows.py --self-test` 与 `python3 scripts/validate_workflows.py`。
4. **CI 接入**：`ci.yml` 的 `verify` 作业在每次 PR/push 运行两个校验器；`nightly.yml` 做全量与时效性复核（见 `docs/08-GITHUB-WORKFLOW.md`）。
5. **版本控制边界**：`CodeTree-项目文档合集.md` 与 `CodeTree-合并文档包.zip` 保留在仓库根目录（压缩包是 `--check` 的校验对象）；`.gitignore` 不忽略它们。
6. **脚本改动规则**：修改 `merge_docs.py` 必须同时更新其自测（正反例）并重跑生成与 `--check`；不允许通过删除校验规则让门禁变绿。

## 理由

- 定时性（freshness）比对是唯一能发现“源文档已改、合集未重生成”的机制，成本低、确定性高，已在本次重建中证明可把基线逐字节还原。
- 标准库实现无第三方依赖，本地与 CI 行为一致；压缩包让评审者一次拿到全量产物。
- 文档门禁纳入 CI 后，任何 Agent 的文档改动都必须通过，不需要人工提醒。

## 后果

### 正面

- 文档漂移可被自动发现；评审入口唯一。
- 合并/压缩产物可通过脚本重现，重建成本低。
- 附录 A 逐字收录 12 份工程文件，评审者不需要在多个文件间跳转。

### 负面与限制

- 仓库体积增加（合集约 250KB、压缩包约 146KB，随文档增长缓慢）。
- 每次文档改动多两条命令；忘记运行会在 CI 失败，需要习惯。
- 合集把 12 份工程文件全文收录，修改代码类工程文件后必须重生成，否则 `--check` 失败。

### 对其他模块的影响

- 所有 Agent 修改 `docs/**`、`README.md`、`AGENTS.md` 或 12 份工程文件后都要执行生成与校验；Lead 负责最终确认。
- 新增文档（如 `docs/CONTEXT.md`、ADR、specs、任务卡）不在 15 份源文档清单内，不参与合并；若未来要纳入合集，必须同步修改 `SOURCE_ORDER` 并更新预期计数。
- QA 的 CI 适配任务必须保留 `verify` 作业中的两条文档校验命令。

## 兼容与迁移

- 若未来新增源文档或工程文件：修改 `scripts/merge_docs.py` 的 `SOURCE_ORDER`/`ENGINEERING_FILES` 与 `EXPECTED_TASKS` 等常量，同步更新 `docs/00` 的执行摘要与门禁描述，然后重生成；旧合集不保留。
- 若未来改用文档站点：本 ADR 的“源文档为事实来源”原则不变，合集作为派生产物可继续生成。
- 回滚条件：脚本在干净检出上无法通过 `--check` 且无法在当轮修复时，回退到上一绿色版本并记录差异。

## 验证计划

- 生成与校验：`python3 scripts/merge_docs.py`、`python3 scripts/merge_docs.py --check`、`python3 scripts/validate_workflows.py` 三条命令全部通过。
- 保真复核：把根目录合集与规划基线合集逐字节比对，必须无差异（本次已验证 IDENTICAL）。
- CI 验证：GitHub Actions `ci.yml / verify` 在文档 PR 上运行两条校验并成功。
- 推翻条件：脚本无法在 CI 环境重现生成结果（编码/换行/时区差异）且修复成本高于收益时，改为“CI 仅校验，本地生成”。

## 后续行动

| 动作 | 负责人 | 前置依赖 | 完成证据 |
| --- | --- | --- | --- |
| 保持生成/校验命令在 CI 中运行 | qa | TASK-109 | `ci.yml` + 运行记录 |
| 文档变更后重生成并复核 | 全体 Agent / lead | 本 ADR | 命令输出 |
| 新增源文档时更新脚本常量与摘要 | architect | 变更需求 | 脚本 diff + 校验输出 |
