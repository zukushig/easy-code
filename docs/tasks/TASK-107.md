# 任务卡：TASK-107 学习知识树 + 词典模块（T7）

> 使用前复制到项目实际任务目录。一个任务只保留一个当前负责人；子任务分别建卡。
> 模板：`docs/templates/TASK_CARD.md`；本卡对应团队任务 **T7**。

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 任务卡编号 | TASK-107 |
| 团队任务 | T7（`team_task` id：task-7） |
| 关联 WBS | CT-100-02/03/04/05/06（图谱/详情/关系/进度/返回位置）、CT-200-03/04/05（搜索页/术语详情/三模式） |
| 关联需求 | LRN-001～LRN-006、DIC-001～DIC-005、UI-001/003/004/006 |
| 优先级 | P0 |
| 状态 | Ready |
| 当前负责人 | learn |
| 评审人 | lead + qa |
| 目标里程碑 | M2（离线学习闭环） |
| 前置依赖 | TASK-105（设计系统/容器）、TASK-106（领域逻辑契约） |
| 后续消费者 | TASK-108（跨模块跳转）、TASK-109（端到端验收） |

## 背景与用户价值

知识树与词典是学习的两个主入口：用户从图谱进入节点，从术语进入解释与双语示例，再回到学习位置。这两个页面的“顺畅程度”决定 M2 学习闭环是否成立。本任务交付可缩放/拖动/选中的 Canvas 图谱、节点详情、学习路径与进度，以及搜索/术语详情/三模式双语。

## 目标

- [ ] `:feature:learn`：Canvas 知识树（缩放/拖动/节点选中/回到当前，四类边线型+图例，视口裁剪，减少动效降级）；节点详情（目标/讲解素材/双语示例/关联术语/练习/关联节点，内容缺失有明确状态）；学习路径与进度（未学/学习中/已掌握，本地持久化，重启保持）；从词典/终端/AI 返回保留位置。
- [ ] `:feature:dictionary`：搜索页（规范化、精确/别名/前缀/FTS 替代/模糊建议与原因、历史/收藏 P1 可选）、术语详情（定义/语法/示例/常见错误/相关术语/知识节点）、中文解释/英文语法/双语对照三模式且记忆选择、代码不被翻译。
- [ ] 只消费 `:core:domain`/`:core:data` 接口与 `:core:designsystem` 组件；无硬编码颜色/圆角/时长。
- [ ] 构建通过并贴输出；关键交互有截图/说明，纯逻辑复用 TASK-106 单测。

## 非目标

- 不修改领域引擎与数据层（发现契约不足走 `CONTRACT_CHANGE`）。
- 不实现终端/AI/知识库页面（TASK-108）。
- 不实现图谱的预计算布局分发、跨节点动画编排（后续版本）。
- 不新增第三方图库/图表库；不修改内容包与 `.github/**`。

## 只读上下文（必须先读，不得修改）

1. `AGENTS.md`、`docs/01-PRD.md` §4.1/§4.2/§4.7、`docs/06-TESTING-ACCEPTANCE.md` §3/§4.1/§4.2。
2. `docs/specs/{module-layout,knowledge,design-system}.md`；`docs/decisions/ADR-002`（图谱渲染）。
3. `docs/02-ARCHITECTURE.md` §4.1/§4.2/§7/§9。
4. `core/domain/**` 契约（`TopicGraph`、`TermSearchEngine`、`ContentRepository`、`ProgressRepository`）。
5. 现有模块骨架与 `:core:designsystem` 组件 API。

## 输入与契约

- 输入：`ContentRepository.graph()/topicsOfDomain()/term()/example()`、`ContentRepository.searchEngine()`、`ProgressRepository`/`SettingsRepository`、`AppError`。
- 输出：`:feature:learn` 与 `:feature:dictionary` 的路由入口 + ViewModel（构造注入）+ stateless 屏幕组件；`:app` 组装导航。
- 共享：`:app` 的 `NavHost`/路由表由 shell 所有，跨 feature 跳转通过回调参数；不得自行新增全局导航单例。

## 允许改动文件（写范围）

```text
feature/learn/**
feature/dictionary/**
```

只读：`app/**`、`core/**`、`runtime/**`、`plugin-api/**`、其他 feature、`content/**`、`tools/**`、`docs/**`、`gradle/**`。

## 禁止事项

- 不修改 `core/domain`/`core/data` 契约；不在 feature 内复制图算法/搜索排序逻辑（必须复用领域层）。
- 不引入第三方图库/图表库/浏览器组件；不硬编码颜色/字体/圆角/动效时长。
- 不把 AI 内容或伪造数据写进内容包/知识库；不修改内容文件。
- 不执行 `git commit`/`git push`；不改 `.gitignore`、构建配置与共享文件。
- 不只在空数据下截图充当验收；必须验证空图、单节点、循环/断链内容、无结果搜索等边界。

## 子任务

| 子任务 ID | 行为 | 输出 | 状态 |
| --- | --- | --- | --- |
| TASK-107-A | 图谱画布与手势 | Canvas + 图例 + 视口裁剪 | Backlog |
| TASK-107-B | 节点详情/练习/关联跳转 | 详情页 + 返回位置 | Backlog |
| TASK-107-C | 学习路径/进度 | 路径、下一节点、进度状态 | Backlog |
| TASK-107-D | 词典搜索页 | 搜索/建议/历史与收藏（P1 可裁剪） | Backlog |
| TASK-107-E | 术语详情与三模式双语 | 详情 + 模式记忆 | Backlog |
| TASK-107-F | 构建与交互证据 | 构建输出 + 截图/说明 | Backlog |

## 可执行验收标准

1. 构建：`cd /root/workspace/easy-code && ./gradlew :feature:learn:assembleDebug :feature:dictionary:assembleDebug`（或 `:app:assembleDebug`）通过，贴输出。
2. 图谱：空图/单节点/多根/交叉关联/循环/断链内容不崩溃且有明确内容状态；缩放/拖动/选中/回到当前状态一致；低缩放有 LOD；减少动效开启后无位移。
3. 节点详情：目标/解释素材/示例/术语/练习/关联节点按内容显示；缺失显示“内容缺失”而不是空白区块；从词典/终端跳转返回后保留选中节点与阅读位置。
4. 词典：精确/别名/前缀/模糊层级正确；无精确结果显示“你可能想找”与原因；快速连续输入旧查询不覆盖新结果；代码 token 不被翻译；三模式选择重启后保持。
5. 设计系统：页面无硬编码颜色/圆角/时长（检索验证）；五态覆盖 Loading/Content/Empty/Error/Offline。
6. 边界：`git status --short` 只出现两个 feature 目录。

## 验证计划

| 测试 ID | 层级 | 命令/步骤 | 预期 | 证据位置 |
| --- | --- | --- | --- | --- |
| TST-107-001 | 构建 | `./gradlew :feature:learn:assembleDebug` | 通过 | 构建输出 |
| TST-107-002 | 构建 | `./gradlew :feature:dictionary:assembleDebug` | 通过 | 构建输出 |
| TST-107-003 | 交互 | 真机/预览操作 | 缩放/拖动/选中/返回位置 | 截图 + 说明 |
| TST-107-004 | 边界 | 空图/循环/断链/无结果 | 明确状态、不崩溃 | 测试记录 |
| TST-107-005 | 搜索 | 连续输入/层级排序 | 旧查询取消、精确优先 | 测试记录 |
| TST-107-006 | 规范 | 代码检索硬编码颜色 | 0 命中 | 命令输出 |

## 完成核对

- [ ] 构建通过并贴输出；两个模块均接线到 `:app` 可进入。
- [ ] 图谱/详情/路径/搜索/三模式验收有真实操作证据。
- [ ] 边界（空/循环/断链/无结果）覆盖。
- [ ] 无硬编码令牌、无第三方图库、无领域逻辑复制。
- [ ] 交接记录完整。

## 交接说明（HANDOFF 要点）

- 当前状态、完成项、未完成项分列。
- 修改文件：`feature/learn/**`、`feature/dictionary/**`。
- 契约变化：无 / 附 `CONTRACT_CHANGE`。
- 测试证据：构建输出、操作截图/说明、边界测试记录。
- 风险：图谱性能在真实内容包上需 QA 复测；大图降级为“聚焦邻域”的体验需产品确认。
- 下一步：通知 tools（TASK-108）跨 feature 跳转接口；通知 qa 开始 UI/端到端验收。

## 执行日志

- 2026-11-15：任务 Ready，等待 learn 认领。
