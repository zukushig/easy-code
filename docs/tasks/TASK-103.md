# 任务卡：TASK-103 种子知识内容：基础域包 + 内容校验器（T3）

> 使用前复制到项目实际任务目录。一个任务只保留一个当前负责人；子任务分别建卡。
> 模板：`docs/templates/TASK_CARD.md`；本卡对应团队任务 **T3**。

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 任务卡编号 | TASK-103 |
| 团队任务 | T3（`team_task` id：task-3） |
| 关联 WBS | CT-010-01（JSON Schema）、CT-010-03（内容导入器契约）、CT-010-04（静态检查工具）、CT-010-05（最小内置包）、CT-010-06（Vibe Coding 专题） |
| 关联需求 | LRN-001～LRN-007、DIC-001～DIC-005、PKG-005、NFR-002 |
| 优先级 | P0 |
| 状态 | In Progress |
| 当前负责人 | content-a |
| 评审人 | architect + content-b（交叉审查） |
| 目标里程碑 | M2（离线学习） |
| 前置依赖 | TASK-101（内容契约与 `docs/specs/knowledge.md` 已冻结） |
| 后续消费者 | TASK-104（应用域包）、TASK-105（数据层加载）、TASK-106（索引/搜索）、TASK-107（学习/词典 UI）、TASK-109（验收） |

## 背景与用户价值

离线学习闭环依赖真实内容：没有主题、术语、双语示例与练习，知识树与词典就是空壳。本任务产出基础域知识包与内容校验/打包工具，是 M2 验收（飞行模式完成一条学习路径）的内容前提；同时为内容作者提供可执行的卡点（ID 唯一、边引用有效、双语缺失可发现）。

## 目标

- [ ] 产出 6 个知识包：`core.programming-basics`、`core.computing-thinking`、`core.python`、`core.javascript`、`core.git`、`core.data-structures`，每个包含 `manifest.json`、`content/{topics,edges,terms,examples}.json`、`syntax/<language>.json`、`LICENSE`，中英双语、稳定 ID、四类边。
- [ ] 产出 `content/index.json`（打包输入清单）。
- [ ] 产出 `tools/content/validate_content.py`：结构/ID/边/双语/示例标注/哈希/目录安全校验，输出统计（topics/terms/examples 数量）与 0 error 结论。
- [ ] 产出 `tools/content/build_dist.py`：生成 `content/dist/*.codepack` 与 `content/dist/catalog.json`（`ContentCatalog` 格式，真实 SHA-256/size）。
- [ ] 内容量与质量满足 `docs/specs/knowledge.md` §9：每节点至少 1 个可观察目标、1 个关联术语、1 个示例、1 个练习（或结构化 `exerciseSkipReason`）。

## 非目标

- 不产出应用域/语言域包（`core.kotlin`、`core.android`、`core.web`、`core.networking`、`core.ai-basics`、`core.vibe-coding` 由 TASK-104 负责；本任务不写其文件）。
- 不修改 App/UI/领域代码；不实现安装器（安装器由 TASK-105/106 按契约实现）。
- 不引入第三方内容库或抓取网络内容；所有内容原创/可再分发，来源与审查状态记录在包内。
- 不做机器翻译批量生成后直接标 `reviewed`；AI 草稿必须标 `aiDraft`。

## 只读上下文（必须先读，不得修改）

1. `AGENTS.md`、`docs/00-EXECUTIVE-SUMMARY.md`、`docs/01-PRD.md`（LRN/DIC 需求）。
2. `docs/05-DATA-CONTENT-PLUGIN.md`（内容规范）、`docs/07-SECURITY-RELEASE.md` §5。
3. `docs/specs/knowledge.md`（本任务的事实来源：文件布局、字段、ID、状态机、搜索契约）。
4. `docs/specs/plugin-api.md`（清单、校验顺序、路径安全）。
5. `docs/decisions/ADR-006-content-version-database.md`（预构建 JSON、内存索引）。
6. `core/domain/src/main/kotlin/com/codetree/domain/model/*.kt`、`plugin-api/src/main/kotlin/com/codetree/plugin/*.kt`（字段与枚举必须一致，不得自创字段名）。

## 输入与契约

- 输入契约：`docs/specs/knowledge.md` §4～§6 的 JSON 格式；`PackManifest`/`Topic`/`Term`/`TopicEdge`/`SyntaxExample`/`LanguageSyntax` 字段名与枚举值。
- 输出契约：上述 6 个包 + `content/index.json` + `tools/content/{validate_content.py,build_dist.py,README.md}` + `content/dist/*.codepack`、`content/dist/catalog.json`。
- 共享文件：`content/index.json`、`content/dist/**` 由内容负责人与 shell（数据层）协商；`content/packs/core.kotlin/**` 已由 content-b 使用，禁止修改。

## 允许改动文件（写范围）

```text
content/packs/{core.programming-basics,core.computing-thinking,core.python,core.javascript,core.git,core.data-structures}/**
content/index.json
content/dist/**
tools/content/**
```

只读：其他 pack 目录、`app/`、`core/**`、`feature/**`、`runtime/**`、`plugin-api/**`、`docs/**`、`.github/**`。

## 禁止事项

- 不修改 TASK-104 的 6 个包与 `content/packs/core.kotlin/**`（并行写冲突）。
- 不手填占位哈希（`LOWERCASE_SHA256_HEX` 等）；哈希必须由 `build_dist.py` 计算。
- 不引入 `.dex/.so/.sh/.jar` 等可执行文件；不写符号链接；不做路径穿越。
- 不把 AI 草稿标 `reviewed`；不伪造校验输出（必须贴真实命令结果）。
- 不执行 `git commit`/`git push`；不修改 `.gitignore` 与构建配置。

## 子任务

| 子任务 ID | 行为 | 输出 | 状态 |
| --- | --- | --- | --- |
| TASK-103-A | 6 个包 manifest + topics/edges | 可被模型反序列化的 JSON | 进行中 |
| TASK-103-B | terms/examples/syntax + 双语审查状态 | 词典与示例内容 | 进行中 |
| TASK-103-C | 内容校验器 | `tools/content/validate_content.py` | 进行中 |
| TASK-103-D | 打包与 catalog 生成 | `tools/content/build_dist.py` + dist 产物 | Backlog |
| TASK-103-E | 统计与交接 | 统计输出 + HANDOFF | Backlog |

## 可执行验收标准

1. 校验器对名下 6 个包 0 error：
   - `cd /root/workspace/easy-code && python3 tools/content/validate_content.py --packs core.programming-basics core.computing-thinking core.python core.javascript core.git core.data-structures`（参数以实现为准并在 README 说明）；
   - 输出必须含每个包的 topics/edges/terms/examples 统计与 `errors: 0`。
2. 打包与 catalog：`python3 tools/content/build_dist.py --packs <上述 6 个>` → `content/dist/` 下 6 个 `.codepack` + `catalog.json`；`catalog.json` 每项 `sha256` 为 64 位小写 hex、`sizeBytes` 与文件实际大小一致（脚本自校验）。
3. 结构一致性：用 `:core:domain` 模型反序列化每个包的 JSON 成功（可由 `validate_content.py --strict-model` 或 TASK-106 单测覆盖；至少证明字段名与枚举合法）。
4. 内容质量抽查：每条 `Topic` 有 `objectives`（≥1）、`termIds`（≥1）、`exampleIds`（≥1）、`exercise` 或 `exerciseSkipReason`；每条 `TopicEdge` 引用的节点存在且无 `prerequisite` 环。
5. 双语：每个包的 title/summary/definitions 至少 `zh-CN` 与 `en` 非空；缺失项在审查报告中列出并说明。
6. 边界：不修改 TASK-104 的包；`git status --short` 中只出现本卡写范围内的文件。

## 验证计划

| 测试 ID | 层级 | 命令/步骤 | 预期 | 证据位置 |
| --- | --- | --- | --- | --- |
| TST-103-001 | 静态与 Schema | `python3 tools/content/validate_content.py --packs ...` | errors: 0 + 统计 | 命令输出贴 HANDOFF |
| TST-103-002 | 打包 | `python3 tools/content/build_dist.py` | 6 个 codepack + catalog.json | dist 列表 + catalog 摘要 |
| TST-103-003 | 引用完整性 | 校验器边引用/环检测 | 0 broken edge / 0 cycle | 校验器输出 |
| TST-103-004 | 双语 | 校验器双语检查 | 缺失项为 0 或列明审查结论 | 报告 |
| TST-103-005 | 边界 | `git status --short` | 未越界修改 | 输出 |

## 完成核对

- [ ] 6 个包全部通过校验（0 error）并贴统计。
- [ ] `build_dist.py` 生成的 catalog 哈希/大小与实际一致。
- [ ] 内容质量抽查与双语审查状态可追踪（`reviewStatus`/来源）。
- [ ] 未触碰 TASK-104 文件与任何代码模块。
- [ ] 交接记录（HANDOFF）已填写：完成项、未完成项、证据、风险、下一步。

## 交接说明（HANDOFF 要点）

- 当前状态：进行中；已完成包列表与统计见交接消息。
- 修改文件：`content/packs/<6 个基础域包>/**`、`content/index.json`、`content/dist/**`、`tools/content/**`。
- 测试证据：校验器与打包命令输出（必须真实执行）。
- 未完成项：缺失的双语内容、待补练习、未覆盖的 Vibe Coding 交叉关联（按需补充）。
- 风险：若 `validate_content.py` 与 `docs/specs/knowledge.md` 不一致，先改规范/工具并通知 TASK-106；不得让数据层为内容错误做特殊兼容。
- 下一步：通知 TASK-104 可交叉使用术语/示例 ID；通知 TASK-105/106 内容包已可加载。

## 执行日志

- 2026-11-15：任务认领（content-a）；基础域包与校验器进行中。
