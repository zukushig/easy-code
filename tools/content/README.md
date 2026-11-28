# CodeTree 内容包格式与工具 / Content packs & tools

本目录是 CodeTree 知识内容的**唯一数据源**与工具链。权威规范见
`docs/05-DATA-CONTENT-PLUGIN.md`；本 README 只记录本工具链的实际约定与用法。

```
tools/content/
├── validate_content.py   内容包 + codepack 校验器（0 error 才通过）
├── build_dist.py         打包器：.codepack / catalog.json / sha256sums.txt / index.json
├── sync_seed.py          把 dist 产物同步进 app/src/main/assets/seed/
└── README.md

content/packs/<packId>/
├── manifest.json
├── content/topics.json   {"topics":[...]}
├── content/edges.json    {"edges":[...]}
├── content/terms.json    {"terms":[...]}
├── content/examples.json {"examples":[...]}
├── syntax/<language>.json  可选，有语言的包提供
└── LICENSE                 Apache-2.0 声明
```

## 1. 常用命令

```bash
# 全量校验（含 content/dist 产物；0 error 退出码 0）
python3 tools/content/validate_content.py

# 只校验一个包
python3 tools/content/validate_content.py --pack core.python

# JSON 汇总（CI / 机器可读；输出契约见 §5「--json 输出契约」）
python3 tools/content/validate_content.py --json

# 打包：生成 content/dist/*.codepack + catalog.json + sha256sums.txt + content/index.json
python3 tools/content/build_dist.py

# 只重打一个包（catalog 仍按 dist 全量重建）
python3 tools/content/build_dist.py --pack core.python

# 同步到 App 内置种子内容（app/src/main/assets/seed/）
python3 tools/content/sync_seed.py
python3 tools/content/sync_seed.py --only core.python,core.git
```

推荐顺序：`validate_content.py` → `build_dist.py` → `sync_seed.py`
（build 结束会调用 dist 校验器自检，sync 结束会打印复制清单与总大小）。

## 2. 稳定 ID 与本地化

- ID 正则：`^[a-z0-9]+(?:[.-][a-z0-9]+)*$`（小写字母、数字、点、连字符）。
- topic 例：`python.control-flow.for-loop`；term 例：`term.python.for`；example 例：`example.python.for-loop`。
- 全部包内 ID 全局唯一（跨包重复即 error）；跨包引用使用完整 ID。
- 本地化键固定 `zh-CN`、`en`，均必须非空；代码、关键字不翻译。
- 本地化文本形状：`{"zh-CN": "...", "en": "..."}`。
- **本地化列表形状**：`{"zh-CN": ["..."], "en": ["..."]}`（`objectives` 用这个，不是
  `[{"zh-CN":...}]` 数组）。

## 3. manifest.json

必填字段：`schemaVersion=1`（整数）、`packId`（= 目录名）、`packType="knowledge"`、
`version`（SemVer）、`title`（双语）、`defaultLocale="zh-CN"`、`locales=["zh-CN","en"]`、
`minAppVersion="0.1.0"`、`pluginApiVersion=1`（整数）、`entry="content/topics.json"`、
`dependencies`（数组）、`files`（数组）。

`dependencies` 元素：`{"packId": "...", "versionRange": ">=1.0.0 <2.0.0", "optional": false}`。

**`files[]` 策略**：源清单里必须是 `[]`。`build_dist.py` 打包时把包内除 `manifest.json`
外的所有文件（`LICENSE`、`content/*.json`、`syntax/*.json`）按
`{"path","sha256","sizeBytes"}` 注入 codepack 内的 manifest.json（自包含、自校验），
并跳过点文件。源清单写非空 `files[]` 只记 warning；写占位符 `LOWERCASE_SHA256_HEX` 是 error。

## 4. 内容实体

### topic（content/topics.json）

`id`、`domain`（如 `python`/`git`）、`difficulty`（`beginner|intermediate|advanced`）、
`title`（双语）、`summary`（双语）、`objectives`（本地化列表）、`contentRef`
（`content/topics.json#<topicId>`，禁止 URL/绝对路径/`..`，文件必须存在）、`termIds`（≥1）、
`exampleIds`（≥1）、`estimatedMinutes`（非负整数）、`exercise`（`{prompt,hint}` 双语；
不适用时用双语的 `exerciseSkipReason` 替代）。

### term（content/terms.json）

`id`、`termType`（`concept|syntax|api|tool`）、`displayName`（双语）、`aliases`
（`[{text,lang,matchType}]`，`matchType ∈ exact|pinyin|abbr`）、`definitions`（双语）、
`syntaxRef`（`syntax/python.json#anchor` 或 null，文件必须存在）、`exampleIds`、
`relatedTermIds`、`topicIds`、`source="seed"`、`contentVersion="1.0.0"`、
`reviewStatus ∈ draft|aiDraft|reviewed|verifiedExample`。未被任何 topic 引用的 term 记 warning。

### example（content/examples.json）

`id`、`language`（`python|javascript|kotlin|...|text|prompt` 等非空标识）、
`languageVersion`、`title`（双语）、`explanation`（双语）、`code`（原文、不翻译）、
`annotations`（`[{start,end,note:{zh-CN,en}}]`，字符偏移，`0 <= start < end <= len(code)`；
为空记 warning）、`expectedOutput`（可执行示例必填）、`runtimeId`（`python|javascript|null`）、
`verificationStatus`（`static-checked|execution-verified|unverified`）。

**验证状态诚实原则**：没有真正运行过的示例只能标 `static-checked` 或 `unverified`。
`execution-verified` 表示示例已由对应 runtime 实际执行且 `expectedOutput` 与真实输出一致。
`language` 为 python/javascript 但 `runtimeId=null` 时记 warning，需在 `explanation` 说明「仅阅读」。

### edge（content/edges.json）

`{source,target,type}`，`type ∈ prerequisite|related|appliedIn|confusedWith`。
`prerequisite`：source 是 target 的前置，必须无环（循环即 error）。
`related`/`confusedWith` 语义双向，存储时只保留一条规范方向即可；双向成对只记 warning。
建议每个包 4 种边类型各至少出现 1 条（缺失记 warning）。

### syntax/<language>.json

终端高亮 / 诊断 / 补全用。统一 schema：

```json
{
  "language": "python",
  "displayName": {"zh-CN": "Python", "en": "Python"},
  "version": "1.0.0",
  "keywords": ["..."], "builtins": ["..."],
  "lineComment": "#", "blockComment": ["\"\"\"", "\"\"\""],
  "stringDelims": ["'", "\""],
  "brackets": [["(", ")"], ["[", "]"], ["{", "}"]],
  "diagnostics": [{"id": "py.missing-colon", "pattern": "(...)", "message": {"zh-CN": "..."},
                   "severity": "error", "fix": {"kind": "append", "text": ":"},
                   "hint": {"zh-CN": "...", "en": "..."}}],
  "completions": [{"when": "word", "items": [{"text": "print(", "detail": "内置函数", "insertText": "print()"}]}]
}
```

- `lineComment` 可为 null（如 CSS 无行注释）；`blockComment` 为 null 或 `[开始,结束]`。
- `diagnostics[].pattern` 必须能被 Python `re.compile` 编译（单行匹配），且正则有实际意义。
- `fix.kind` 允许 `append`（需 `text`）或 `replace`；也可为 null。
- 建议数量：keywords 30~60、builtins 10~30、diagnostics 3~6（越界只 warning）。

## 5. 校验器规则摘要（error）

1. JSON 可解析；manifest 必填字段、`packId`=目录名、SemVer、整数版本字段、LICENSE 存在。
2. ID 格式与全局唯一；所有引用必须存在（termIds/exampleIds/topicIds/edge 端点）。
3. `prerequisite` 无环；`related/confusedWith` 端点存在。
4. zh-CN/en 双语非空；topic 必备关联与 exercise；exercise hint 缺失只 warning。
5. annotations 偏移合法；runtimeId 与 language 一致；可执行示例必须有 expectedOutput。
6. syntax schema 与 diagnostics 正则编译。
7. `content/dist` 存在时：zip 路径安全（无 `..`、无绝对路径、无符号链接、条目数/单文件/
   解压总量/压缩比上限），manifest `files[]` 的 sha256+sizeBytes 与实际一致，catalog 与
   sha256sums 与实际产物一致。

warnings 单独列出，不影响退出码；`--json` 输出机器可读报告，结构见下。

### --json 输出契约（供 CI / QA 快照复用）

结构（`<int>` 为占位，真实输出是整数；这是结构说明，不是可运行示例）：

```text
{
  "schemaVersion": 1,                          // 报告契约版本
  "ok": true,                                 // = counts.errors == 0
  "root": "<repo 绝对路径>",                    // 仅供定位，不用于对比
  "totals": {"packs": <int>, "topics": <int>, "terms": <int>,
             "examples": <int>, "edges": <int>},
  "packs": [
    {"packId": "core.python", "dir": "content/packs/core.python", "version": "1.0.0",
     "topics": <int>, "terms": <int>, "examples": <int>, "edges": <int>,
     "syntaxFiles": ["python.json"],
     "errorCount": <int>, "warningCount": <int>,
     "errors":   [{"pack": "...", "path": "...", "message": "..."}],
     "warnings": [{"pack": "...", "path": "...", "message": "..."}]}
  ],
  "dist": {"present": true, "codepacks": <int>, "catalog": true, "sha256sums": true},
  "errors":   [{"pack": "...", "path": "...", "message": "..."}],
  "warnings": [{"pack": "...", "path": "...", "message": "..."}],
  "counts": {"errors": <int>, "warnings": <int>}
}
```

稳定性约定：键集合固定；`packs` 顺序 = `content/packs/` 目录名排序；`errors/warnings` 顺序 =
校验执行顺序；每条 issue 为 `{"pack","path","message"}`；`path` / `dir` 均转换为**相对仓库根
的 posix 路径**，不含机器相关绝对路径，便于跨机器快照/对比。`ok = (counts.errors == 0)`，退出码
与 `ok` 一致（0/1）。QA 侧可直接解析 `--json`，断言 `counts.errors == 0` 与各包
`errorCount` / 总数。

## 6. 分发产物（content/dist/）

```
content/dist/
├── core.python-1.0.0.codepack     zip：manifest.json + content/ + syntax/ + LICENSE
├── ...
├── catalog.json                   远端目录契约
└── sha256sums.txt                 "<sha256>  <filename>"
content/index.json                 本地内容包索引（生成物）
```

`catalog.json` 契约：顶层 `{schemaVersion, generatedBy, publishedAt, packCount, packs:[...]}`，
每个 pack 含 `packId`、`version`、`title`（双语）、`locales`、`domain`（topic.domain，多域用逗号连接）、
`difficulty`（包内最高难度）、`sizeBytes`（zip 大小）、`sha256`（zip 整体）、
`downloadUrl`（**相对 content/dist/ 的文件名**，如 `core.python-1.0.0.codepack`）、
`dependencies`、`minAppVersion`、`schemaVersion`、`pluginApiVersion`、`publishedAt`（固定
`2026-01-01T00:00:00Z`，保证可复现）。

可复现性：zip 条目顺序固定、mtime 固定 1980-01-01、权限固定 0644；同样输入 → 同样 sha256。

产物生成后由 `sync_seed.py` 同步进 `app/src/main/assets/seed/`（见 §7）。

## 7. 与 App 的对接方式（assets seed）

`content/dist/` 是**分发源**；`app/src/main/assets/seed/` 是 App 首次启动时的内置副本，
由 `sync_seed.py` 生成（不要手工编辑该目录，改内容后重跑 build + sync 即可重建）：

```
app/src/main/assets/seed/
├── catalog.json                    远端目录契约（App 启动时读取）
├── sha256sums.txt                  产物哈希清单（自检 / 诊断）
└── <packId>-<version>.codepack     每个知识包一个 zip（扁平布局）
```

扁平布局与 `catalog.downloadUrl`（纯文件名、相对 seed 根）的语义一致；App 不应假定
codepack 一定在子目录里。

同步命令：

```bash
# 全量同步（默认 catalog 中全部包；会清理目标目录里过期的 *.codepack）
python3 tools/content/sync_seed.py

# 只同步指定包：写入的 catalog.json / sha256sums.txt 自动过滤为所选包
python3 tools/content/sync_seed.py --only core.python,core.git

# 同步到自定义目录（CI 或本地验证）
python3 tools/content/sync_seed.py --dest /tmp/seed-check
```

同步器先校验每个产物与 catalog 的 `sizeBytes` / `sha256` 一致，全部通过后才复制；
内容相同则跳过（`keep`）；写入使用临时文件 + `os.replace` 原子替换，中断不会留下半个
codepack。全量模式会删除目标目录中 catalog 未列出的 `*.codepack`（版本升级后的旧产物），
`--only` 模式不清理。

### catalog.json 字段用途

| 字段 | App 用途 |
| --- | --- |
| `packId` / `version` | 安装键与更新判断：`(packId, version)` 唯一确定一个包版本 |
| `title` | 知识库「下载/更新」列表的显示名（`{"zh-CN","en"}` 本地化） |
| `locales` | UI 语言能力声明，决定双语展示与回退顺序 |
| `domain` / `difficulty` | 列表筛选、学习方向与推荐 |
| `sizeBytes` / `sha256` | 下载/内置安装前的完整性校验（必须与文件实际值一致） |
| `downloadUrl` | 相对 seed 根（或远端目录）的文件名；禁止绝对路径与 `..` |
| `dependencies` | 安装前依赖解析：缺失、循环或版本冲突在安装前报错，不静默安装 |
| `minAppVersion` | 兼容性门槛：应用版本过低时拒绝安装并提示升级 |
| `publishedAt` | 目录排序与展示时间（本工具链固定为 `2026-01-01T00:00:00Z` 以保证可复现） |
| `schemaVersion` / `pluginApiVersion` | 数据契约版本；App 不兼容时拒绝读取而不是猜测 |

### SeedInstaller 建议流程（对应 docs/05 §9）

1. 启动时读 `assets/seed/catalog.json`，与本地已安装记录对比，得到待安装/待更新包。
2. 按 `dependencies` 顺序处理：依赖必须已安装或本次同时安装，否则安装前失败。
3. 校验产物 `sizeBytes` / `sha256` 后，再读取 zip 内的 `manifest.json`。
4. 校验 `manifest.files[]` 中每个 `sha256` / `sizeBytes`（zip 内自校验）。
5. 解压到暂存目录 → 内容静态检查 → 原子切换 `activeVersion` 后才对用户可见。
6. 失败时保留旧版本并清理暂存，不留下半安装状态；学习进度按稳定 ID 保留。
7. 只有数据库提交且 `activeVersion` 原子切换后，包才进入 `Installed` 状态。

注意：App 不应把 `assets/seed/` 当内容源直接读取，它只是「内置分发包」。内容更新走
`content/packs` → `build_dist.py` → `sync_seed.py` 重新生成。

## 8. 新增一个内容包

1. 复制任一现有包目录为 `content/packs/<新 packId>/`，改 `manifest.json` 的 `packId`/`title`/
   `dependencies`（依赖方与被依赖方都要与 `content/packs` 内实际存在的包一致）。
2. 写 `content/topics.json`、`edges.json`、`terms.json`、`examples.json`；确保：
   topic 至少关联 1 个 term + 1 个 example + 1 个 exercise；所有 ID 全局唯一；prerequisite 无环；
   `contentRef` 指向包内真实文件。
3. 有语言就提供 `syntax/<language>.json`；复制 `LICENSE`（Apache-2.0 声明）。
4. 跑 `python3 tools/content/validate_content.py --pack <新 packId>` 修到 0 error。
5. 跑 `python3 tools/content/build_dist.py` 生成产物并让 `content/index.json` 自动更新。
6. 需要 App 内置时，跑 `python3 tools/content/sync_seed.py` 同步进
   `app/src/main/assets/seed/`，由安装层按 docs/05 §9 的安装状态机校验安装。

## 9. 内容来源与许可

种子内容由本项目团队编写，`reviewStatus="reviewed"`；术语定义与示例为教学目的原创，
统一采用 **Apache-2.0** 许可（每个包内 `LICENSE`）。示例代码与真实输出分离：
`execution-verified` 必须来自实际运行，禁止编造输出。
