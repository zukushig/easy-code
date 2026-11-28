# 内容与知识模型契约（knowledge）

> 适用范围：MVP M1/M2 的内容包、内置种子内容、内存索引与安装状态。事实来源优先级：`docs/05-DATA-CONTENT-PLUGIN.md`（规划规范）→ 本文件（MVP 可执行细化）→ `:core:domain` / `:plugin-api` 代码契约。三者冲突时先停下核对，同轮修正并提交 `CONTRACT_CHANGE`。
>
> 契约代码：`core/domain/src/main/kotlin/com/codetree/domain/model/{Content,Term,SyntaxExample,Progress,PackCatalog}.kt`、`core/domain/src/main/kotlin/com/codetree/domain/{graph/TopicGraph,search/TermSearch,content/Repositories}.kt`、`plugin-api/src/main/kotlin/com/codetree/plugin/{PackManifest,LanguageSyntax,LocalizedText}.kt`。

## 1. 目录与文件布局

仓库根 `content/`：

```text
content/
├── index.json                     # 打包输入清单（源文件，人工/工具维护）
├── packs/
│   └── <packId>/
│       ├── manifest.json          # PackManifest（见 §4）
│       ├── content/
│       │   ├── topics.json        # Topic[]（见 §5.1）
│       │   ├── edges.json         # TopicEdge[]（见 §5.2）
│       │   ├── terms.json         # Term[]（见 §5.3）
│       │   └── examples.json      # SyntaxExample[]（见 §5.4）
│       ├── syntax/<language>.json # LanguageSyntax（见 §5.5）
│       ├── theme/tokens.json      # 可选：主题贡献（仅设计令牌白名单，见 plugin-api.md）
│       ├── assets/                # 可选：图片等静态资源（不得含可执行内容）
│       └── LICENSE
└── dist/                          # 生成物，不手工编辑
    ├── <packId>-<version>.codepack
    └── catalog.json               # ContentCatalog（见 §6）
```

规则：

- `packs/<packId>/` 是内容事实来源；`.codepack`、`catalog.json`、`index.json` 的哈希字段由 `tools/content/build_dist.py` 生成，禁止手工填写占位哈希。
- 包内文件路径必须是相对路径；不得出现绝对路径、`..`、符号链接或可执行内容（`.dex`/`.so`/`.sh`/`.jar` 等，见 `plugin-api.md` §5）。
- 应用内安装目录布局（私有存储）：`content/packs/<packId>/<version>/`，`activeVersion` 切换后旧版本保留到提交成功（见 §7）。

## 2. 编码与本地化

- 所有 JSON 为 UTF-8、无 BOM、2 空格缩进；解析使用 `ContentJson`（`ignoreUnknownKeys = true`、`isLenient = false`、`encodeDefaults = true`）。
- 本地化文本格式（`LocalizedText`）：`{"zh-CN": "...", "en": "..."}`；本地化列表（`LocalizedList`）：`{"zh-CN": ["..."], "en": ["..."]}`。
- 回退顺序固定为：请求语言 → 语言主标签 → `zh-CN` → `en` → 第一个非空值（实现见 `LocalizedText.get`）；`values` 全空视为缺失，UI 必须显示明确缺失状态而不是空白区块。
- 代码关键字、函数名、参数名不翻译；翻译只作用于解释性文本字段。

## 3. 稳定 ID 规则

- 字符集：小写字母、数字、点（`.`）与连字符（`-`）；禁止空格、下划线、大写、斜杠、中文与表情。
- 推荐格式：`<domain>.<slug>[.<slug>...]`，例如 `python.control-flow.for-loop`、`term.kotlin.coroutine`。
- 创建后不随标题/显示名修改而改变；显示名可本地化、可改，外键一律使用 ID。
- 合并所有**已启用**内容包后，`Topic.id`、`Term.id`、`SyntaxExample.id` 必须全局唯一；同一包内各实体 ID 独立命名空间（Topic 与 Term 可同名 ID，但不推荐）。
- 跨包引用必须使用完整 ID；包内短引用在打包阶段规范化为完整 ID，运行时只接受完整 ID。
- 删除实体时必须在包内提供 `replacedBy`（迁移到新 ID）或保留 tombstone 记录；不得静默删除导致用户进度无解释消失（见 `docs/05` §13）。

## 4. 清单（manifest.json）

遵循 `plugin-api` 的 `PackManifest`：

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `schemaVersion` | int | 是 | 当前 `1`；高版本由旧应用拒绝并提示升级 |
| `packId` | string | 是 | 稳定小写 ID，例如 `core.python` |
| `packType` | string | 是 | `knowledge` 或 `plugin`；P0 只接受这两种 |
| `version` | string | 是 | SemVer `主.次.修订` |
| `title` | LocalizedText | 是 | 显示名，至少一个非空语言 |
| `defaultLocale` | string | 否 | 默认 `zh-CN` |
| `locales` | string[] | 否 | 支持语言列表，`zh-CN`/`en` 优先 |
| `minAppVersion` | string | 否 | 默认 `0.1.0`；低于该版本拒绝安装 |
| `pluginApiVersion` | int | 否 | 默认 `1`；高于应用支持值时拒绝 |
| `entry` | string | 否 | 默认 `content/topics.json` |
| `permissions` | string[] | 否 | P0 必须为空；非空按不兼容拒绝 |
| `contributions` | `PluginContribution[]` | 插件必填 | 类型仅 `content`/`dictionary`/`syntax`/`theme` |
| `dependencies` | `PackDependency[]` | 否 | `packId` + `versionRange` + `optional` |
| `files` | `PackFile[]` | 分发产物必填 | `path` + 小写 64 位 SHA-256 + `sizeBytes`；源包允许空数组由打包工具注入 |

## 5. 内容实体（与 `:core:domain` 模型一致）

### 5.1 Topic（`content/topics.json`，`Topic[]`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 全局唯一稳定 ID |
| `domain` | string | 是 | 领域标识，如 `python`、`android`、`vibe-coding` |
| `difficulty` | enum | 否 | `beginner` / `intermediate` / `advanced`，默认 `beginner` |
| `title` | LocalizedText | 是 | 显示名 |
| `summary` | LocalizedText | 是 | 一句话说明 |
| `objectives` | LocalizedList | 是 | 可观察学习目标（至少 1 条中文） |
| `contentRef` | string? | 否 | M1 允许为空：节点详情由 `title`/`summary`/`objectives`/关联术语/示例/练习组合；P1 图文讲解再启用 `details/<topicId>.json`（扩展需契约变更） |
| `termIds` | string[] | 否 | 关联术语 ID |
| `exampleIds` | string[] | 否 | 关联示例 ID |
| `estimatedMinutes` | int | 否 | 非负，默认 5，仅用于规划 |
| `exercise` | object? | 否 | `{ prompt: LocalizedText, hint?: LocalizedText }` |
| `exerciseSkipReason` | string? | 否 | 无练习时的结构化理由（不得用空字符串糊弄） |

### 5.2 TopicEdge（`content/edges.json`，`TopicEdge[]`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `source` | string | 源节点 ID，必须存在 |
| `target` | string | 目标节点 ID，必须存在 |
| `type` | enum | `prerequisite` / `related` / `appliedIn` / `confusedWith` |

- `prerequisite`：`source` 是 `target` 的前置，用于路径与解锁；必须做循环检测（`TopicGraph.detectPrerequisiteCycles`），有环时拒绝安装或报告内容错误。
- `related`：双向相关，存储时保留规范方向，遍历视为双向。
- `appliedIn`：`source` 应用于 `target`（概念到实践）。
- `confusedWith`：双向易混淆，用于对比学习与消歧；不得用于生成强制学习顺序。
- 边不得引用不存在节点、自环（`prerequisite` 自环一律拒绝）；同一 `(source, target, type)` 不得重复。

### 5.3 Term（`content/terms.json`，`Term[]`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 全局唯一 |
| `termType` | enum | `concept` / `syntax` / `api` / `tool`，默认 `concept` |
| `displayName` | string | 原始大小写展示（如 `JavaScript`、`JSON`） |
| `aliases` | TermAlias[] | `{ text, lang, matchType }`；`matchType` 为 `exact` / `pinyin` / `abbr` / `alias` |
| `definitions` | LocalizedText | 中文解释 + 英文语法要点 |
| `syntaxRef` | string? | 关联语法语言 ID（指向 `syntax/*.json` 的 `language`） |
| `exampleIds` | string[] | 关联示例 |
| `relatedTermIds` | string[] | 相关术语 |
| `topicIds` | string[] | 关联知识节点 |
| `source` | string | 来源（默认 `seed`） |
| `contentVersion` | string | 内容版本（SemVer） |
| `reviewStatus` | enum | `draft` / `aiDraft` / `reviewed` / `verifiedExample`；AI 草稿不得直接标 `reviewed` |

### 5.4 SyntaxExample（`content/examples.json`，`SyntaxExample[]`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 全局唯一 |
| `language` | string | `kotlin` / `python` / `javascript` 等 |
| `languageVersion` | string | 适用语言版本，可空 |
| `title` / `explanation` | LocalizedText | 标题与解释 |
| `code` | string | 原始代码，**不做自然语言翻译** |
| `annotations` | ExampleAnnotation[] | `{ start, end, note }`，按字符区间关联中英解释，避免按行号绑定 |
| `expectedOutput` | string? | 可执行示例的预期输出或断言 |
| `runtimeId` | string? | 有值时表示可运行（`isRunnable`）；缺失时 UI 显示“仅阅读” |
| `verificationStatus` | enum | `unverified` / `static-checked` / `execution-verified` |

代码修改后必须重新校验 `annotations` 区间，越界或重叠视为内容错误。

### 5.5 LanguageSyntax（`syntax/<language>.json`）

遵循 `plugin-api` 的 `LanguageSyntax`：`language`、`displayName`、`version`、`keywords`、`builtins`、`lineComment`、`blockComment`、`stringDelims`、`brackets`、`diagnostics`（`DiagnosticRule[]`：`id`、`pattern`（Java/Kotlin 正则）、`message`、`severity`、`fix`、`hint`）、`completions`（`CompletionGroup[]`：`when`、`items`：`text`/`detail`/`insertText`）。

- 语法包由内容作者维护，终端 UI 只消费 `SyntaxAnalyzer`（见 `docs/specs/terminal.md`）。
- 正则必须在上限内（单条编译失败即拒绝安装该包，不忽略规则）；`when` 仅允许 `word`/`line-start`/`after-dot` 等白名单值。

### 5.6 主题贡献（`theme/tokens.json`）

可选主题插件贡献，只能定义设计系统中允许的令牌键（颜色/字体/间距/圆角/动效，见 `docs/specs/design-system.md` §7）。禁止远程资源、字体文件、任意键名与可执行表达式；未知键按校验错误拒绝。

## 6. 目录与 catalog 契约

### 6.1 `content/index.json`（打包输入）

```json
{
  "schemaVersion": 1,
  "packs": [
    { "packId": "core.python", "path": "packs/core.python", "defaultLocale": "zh-CN" }
  ]
}
```

`tools/content/build_dist.py` 读取本文件，逐个校验并打包为 `content/dist/<packId>-<version>.codepack`，同时生成 `content/dist/catalog.json`。

### 6.2 `content/dist/catalog.json`（安装目录）

遵循 `:core:domain` 的 `ContentCatalog` / `PackCatalogEntry`：

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-11-15T00:00:00+08:00",
  "packs": [
    {
      "packId": "core.python",
      "version": "1.0.0",
      "title": { "zh-CN": "Python 基础", "en": "Python Fundamentals" },
      "locales": ["zh-CN", "en"],
      "domain": "python",
      "difficulty": "beginner",
      "sizeBytes": 123456,
      "sha256": "<小写 64 位 SHA-256>",
      "downloadUrl": "",
      "dependencies": [],
      "minAppVersion": "0.1.0",
      "publishedAt": "2026-11-15T00:00:00+08:00"
    }
  ]
}
```

- MVP 内置包以 `BUNDLED` 来源随 APK 分发，`downloadUrl` 可为空；远程下载链路未交付前，UI 不得把空 URL 渲染成可下载按钮。
- 远程目录未来接入时，字段不变；`downloadUrl` 必须使用 HTTPS，下载后按 `sha256` 与 `sizeBytes` 复核，目录元数据不能替代文件校验。

## 7. 安装状态机与原子性

状态机（与 `docs/05` §9 一致）：

```text
NotInstalled → Queued → Downloading → Paused
                    ↓          ↓
                 Verifying → Installing → Installed
                    ↓          ↓
                   Failed → 保留旧版本 / 清理暂存

Installed → UpdateAvailable → Downloading
Installed → Removing → NotInstalled
```

实现约束（MVP 无 Room，用暂存目录 + 状态文件 + 原子重命名，见 ADR-006）：

1. 任何阶段失败都**不得删除当前可用版本**；`activeVersion` 只在数据库/状态提交且文件校验通过后原子切换。
2. 解包目标为 `staging/<packId>-<version>/`，成功后再重命名到 `content/packs/<packId>/<version>/`；失败清理暂存，旧版本保持可读。
3. 启动时做状态与文件一致性检查：状态为 `Installed` 但目录缺失、或 `activeVersion` 指向不存在目录时，回退到上一个可用版本并记录 `AppError.Internal` 供诊断。
4. 删除被依赖包前必须检查反向依赖并给出影响列表；被依赖包不能静默删除；卸载不删除用户学习进度。
5. 内容包更新：稳定 ID 保留进度；删除节点用 `replacedBy`/tombstone 迁移；迁移失败不得清空进度。

## 8. 搜索排序契约

实现：`:core:domain` 的 `TermSearchEngine`（`search(query, limit = 30)`）+ `QueryNormalizer`。

- 规范化：`trim` → 内部连续空白压缩为单空格；匹配键用 `lowercase`；展示保留原始大小写；`MAX_INPUT_LENGTH = 64`。
- 无效查询（空、超长、仅标点无字母数字）返回空结果并带原因，不抛异常。
- 排序层级（`SearchMatchKind.rank` 升序，值越小越靠前）：
  1. `EXACT_ID`（0）
  2. `EXACT_NAME`（1）
  3. `EXACT_ALIAS`（2）
  4. `PREFIX`（3）
  5. `FTS`（4，MVP 为正文子串/分词匹配的受控替代）
  6. `FUZZY`（5，受控模糊）
- 同一层级内可按语言、当前学习领域、收藏、流行度排序，但**任何推荐权重不得越过精确匹配层级**。
- 模糊候选上限：候选池 ≤ 200 条、最终建议 ≤ 5 条；编辑距离阈值 `长度 ≤ 4 时为 1，否则为 2`；低相关结果必须标记 `suggestion = true` 并显示“你可能想找”，不得冒充精确结果。
- 查询取消：UI 发起新查询时取消旧查询协程；旧结果不得覆盖新结果。搜索在主线程外执行，结果携带 `elapsedMs`。
- 契约类型：`TermSearchHit(termId, kind, matchedField, score, reason?, suggestion)`、`TermSearchResult(query, normalizedQuery, hits, suggestions, elapsedMs)`。

## 9. 校验与内容质量

- 作者侧校验：`tools/content/validate_content.py` 覆盖结构、必填字段、ID 唯一性、边引用、循环前置、双语缺失、示例 `annotations` 越界、`runtimeId` 合法性、文件哈希与目录安全；0 error 才允许进入 `dist/`。
- 安装侧校验：使用 `:plugin-api` 的 `CodepackValidator`（校验顺序与限制见 `docs/specs/plugin-api.md`）。
- 内容质量（P0）：每个知识节点至少 1 个可观察目标、1 个关联术语、1 个示例与 1 个练习；不适用时用结构化理由（`exerciseSkipReason`）而不是空字符串。
- Vibe Coding 专题必须覆盖需求澄清、上下文最小化、小步迭代、差异审查、自动化测试、调试证据、版本回滚、依赖与密钥安全，并强调 AI 生成与验证分离。
- 审查状态 `draft`/`aiDraft` 的内容不得进入发布候选；AI 草稿必须经过人工审查才能标 `reviewed`。

## 10. 兼容性测试样例（验收必测）

- 旧应用读取更高 `schemaVersion`：拒绝并提示升级，不崩溃。
- 新增可选字段：旧版本忽略未知字段，原字段语义不变。
- 依赖不存在 / 循环依赖 / 版本冲突：安装前失败，现有内容不变。
- 内容包更新删除节点：进度按 `replacedBy`/tombstone 迁移。
- 相同 ID 不同来源：报告冲突，禁止后导入者静默覆盖。
- 双语缺失：按回退规则展示并在审查工具报告。
- 卸载后重启：无幽灵术语、断链节点或失效主题。
- 飞行模式：已安装内容的知识树、词典与示例可读。
