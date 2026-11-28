# 任务卡：TASK-104 种子知识内容：应用域包（T4）

> 使用前复制到项目实际任务目录。一个任务只保留一个当前负责人；子任务分别建卡。
> 模板：`docs/templates/TASK_CARD.md`；本卡对应团队任务 **T4**。

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 任务卡编号 | TASK-104 |
| 团队任务 | T4（`team_task` id：task-4） |
| 关联 WBS | CT-010-05（最小内置包）、CT-010-06（Vibe Coding 专题）、CT-010-07（内容来源与双语审查） |
| 关联需求 | LRN-003、LRN-006、DIC-004、AIA-003（AI 辅助学习的知识背景）、NFR-002 |
| 优先级 | P0 |
| 状态 | In Progress |
| 当前负责人 | content-b |
| 评审人 | architect + content-a（交叉审查） |
| 目标里程碑 | M2（离线学习） |
| 前置依赖 | TASK-101（内容契约）；TASK-103 提供术语/示例 ID 规范（可并行，不阻塞） |
| 后续消费者 | TASK-105、TASK-106、TASK-107、TASK-108、TASK-109 |

## 背景与用户价值

应用域（Kotlin、Android、Web、网络基础、AI 基础）与 Vibe Coding 专题是 CodeTree 的差异化内容：用户不只是读概念，还要理解“如何在真实工程中应用”和“如何与 AI 协作而不把生成当验证”。本任务产出 6 个应用域知识包，其中 Vibe Coding 必须覆盖需求澄清到安全边界全链路。

## 目标

- [ ] 产出 6 个知识包：`core.kotlin`（已存在基础骨架，补齐内容）、`core.android`、`core.web`、`core.networking`、`core.ai-basics`、`core.vibe-coding`，格式与 `docs/specs/knowledge.md` 一致。
- [ ] Vibe Coding 专题覆盖：需求澄清、上下文最小化、小步迭代、差异审查、自动化测试、调试证据、版本回滚、依赖与密钥安全；所有示例强调“AI 生成与验证分离”。
- [ ] 与基础域包建立跨域关联（`prerequisite`/`related`/`appliedIn`/`confusedWith`），边引用只使用已存在 ID（跨包引用需确认对方 ID，避免断链）。
- [ ] 中英双语、`reviewStatus` 可追踪；示例标注 `runtimeId`（无运行时则标“仅阅读”）。
- [ ] 用 TASK-103 的 `tools/content/validate_content.py` 对名下包校验 0 error 并贴统计。

## 非目标

- 不修改基础域 6 个包、`content/index.json`、`tools/content/**`（TASK-103 所有）。
- 不实现 UI/领域代码；不引入第三方课程内容或未授权材料。
- 不做 Vibe Coding 的“提示词玄学”内容：必须包含验证、测试、回滚与安全边界。
- 不手填哈希；打包由 TASK-103 的 `build_dist.py` 统一执行。

## 只读上下文（必须先读，不得修改）

1. `AGENTS.md`、`docs/01-PRD.md`（LRN-003/006、内容原则）。
2. `docs/05-DATA-CONTENT-PLUGIN.md`、`docs/specs/knowledge.md`（字段/ID/质量要求）。
3. `docs/specs/plugin-api.md`（清单与路径安全）。
4. `core/domain/src/main/kotlin/com/codetree/domain/model/*.kt`（字段名与枚举）。
5. TASK-103 已产出的包（只读参考 ID 与风格）与 `tools/content/validate_content.py` 的用法。

## 输入与契约

- 输入契约：`docs/specs/knowledge.md` §4～§6；既有 `core.kotlin` 基础文件只允许在**本卡写范围内**补齐（`content/packs/core.kotlin/**` 归本卡）。
- 输出契约：6 个包目录 + 每包 `manifest.json`/`content/**`/`syntax/<language>.json`/`LICENSE`；校验统计输出。
- 共享边界：跨包 `topicIds`/`termIds`/`exampleIds` 引用必须与 content-a 确认；不修改对方文件。

## 允许改动文件（写范围）

```text
content/packs/{core.kotlin,core.android,core.web,core.networking,core.ai-basics,core.vibe-coding}/**
```

只读：基础域 6 个包、`content/index.json`、`content/dist/**`、`tools/content/**`、`app/`、`core/**`、`feature/**`、`docs/**`、`.github/**`。

## 禁止事项

- 不修改 TASK-103 的包与工具；不手填占位哈希；不写可执行文件/符号链接。
- 不把 AI 生成内容直接标 `reviewed`；必须自己或交叉审查后标状态。
- 不伪造校验输出；未跑通不得写“0 error”。
- 不执行 `git commit`/`git push`；不修改构建配置与 `.gitignore`。

## 子任务

| 子任务 ID | 行为 | 输出 | 状态 |
| --- | --- | --- | --- |
| TASK-104-A | core.kotlin 内容补齐 | topics/terms/examples/syntax | 进行中 |
| TASK-104-B | core.android / core.web | 两个包 | 进行中 |
| TASK-104-C | core.networking / core.ai-basics | 两个包 | Backlog |
| TASK-104-D | core.vibe-coding 专题（含八项覆盖） | 完整专题包 | Backlog |
| TASK-104-E | 校验统计 + 交接 | 命令输出 + 统计 | Backlog |

## 可执行验收标准

1. 校验 0 error（用 TASK-103 工具）：`python3 tools/content/validate_content.py --packs core.kotlin core.android core.web core.networking core.ai-basics core.vibe-coding`，输出 0 error 与各包统计。
2. Vibe Coding 八项覆盖：在 `core.vibe-coding` 的 topics 中可逐项找到对应节点/章节（用户需求澄清、上下文最小化、小步迭代、差异审查、自动化测试、调试证据、版本回滚、依赖与密钥安全），并在交接消息中列出节点 ID 对应关系。
3. 边引用完整性：所有 `TopicEdge.source/target` 与 `termIds`/`exampleIds` 均存在；跨包引用使用完整 ID；`prerequisite` 无环。
4. 双语与审查：title/summary/definitions 中英非空；`reviewStatus` 无“AI 草稿冒充 reviewed”。
5. 边界：`git status --short` 只出现 6 个包目录的改动。

## 验证计划

| 测试 ID | 层级 | 命令/步骤 | 预期 | 证据位置 |
| --- | --- | --- | --- | --- |
| TST-104-001 | 静态与 Schema | `python3 tools/content/validate_content.py --packs ...` | errors: 0 + 统计 | HANDOFF 命令输出 |
| TST-104-002 | 结构 | 反序列化 6 包 JSON | 全部成功 | 校验器/模型测试 |
| TST-104-003 | Vibe Coding | 八项节点清单核对 | 8/8 可定位 | 交接消息 |
| TST-104-004 | 引用 | 边/术语/示例引用检查 | 0 broken | 校验器输出 |
| TST-104-005 | 边界 | `git status --short` | 未越界 | 输出 |

## 完成核对

- [ ] 6 个包校验通过并贴统计。
- [ ] Vibe Coding 八项覆盖有节点 ID 对照。
- [ ] 无断链、无环、无双语缺失或已列明审查结论。
- [ ] 未修改 TASK-103 文件。
- [ ] 交接记录完整。

## 交接说明（HANDOFF 要点）

- 当前状态与已完成包：见 HANDOFF 消息。
- 修改文件：`content/packs/<6 个应用域包>/**`。
- 测试证据：校验命令输出（真实执行）。
- 未完成项：缺失双语、待补示例、待交叉审查的内容。
- 风险：跨包引用 ID 若与 content-a 冲突，以“先登记再引用”为原则，冲突时报告而非静默改对方内容。
- 下一步：通知 TASK-103 统一打包；通知 TASK-107/108 内容可加载。

## 执行日志

- 2026-11-15：任务认领（content-b）；core.kotlin 骨架已存在，继续补齐应用域包。
