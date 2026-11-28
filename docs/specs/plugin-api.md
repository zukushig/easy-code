# 插件与 Codepack 契约（plugin-api）

> 适用范围：MVP M1/M2 的 `.codepack` 导入、校验与安装。事实来源：`docs/05-DATA-CONTENT-PLUGIN.md`、`docs/07-SECURITY-RELEASE.md` §5、ADR-005；可执行契约以 `plugin-api/src/main/kotlin/com/codetree/plugin/*.kt` 为准。
>
> P0 只支持**声明式** `.codepack`（content / dictionary / syntax / theme）：没有可执行代码路径，签名信任链推迟（ADR-005）。

## 1. 类型契约（与代码一致）

| 类型 | 定义要点 |
| --- | --- |
| `PackType` | `KNOWLEDGE`（`"knowledge"`）/ `PLUGIN`（`"plugin"`） |
| `PackManifest` | `schemaVersion = 1`、`packId`、`packType`、`version`、`title: LocalizedText`、`defaultLocale = "zh-CN"`、`locales`、`minAppVersion = "0.1.0"`、`pluginApiVersion = 1`、`entry = "content/topics.json"`、`permissions`、`contributions: List<PluginContribution>`、`dependencies: List<PackDependency>`、`files: List<PackFile>`；`type` 便捷属性把非 `"plugin"` 视为 `KNOWLEDGE` |
| `PackDependency` | `packId`、`versionRange = ">=0.1.0"`、`optional = false` |
| `PackFile` | `path`、`sha256`（小写 64 位 hex）、`sizeBytes` |
| `PluginContribution` | `type`、`path`；`ALLOWED_TYPES = [content, dictionary, syntax, theme]`，`REJECTED_TYPES = [runtime, native, executable]` |
| `ValidationSeverity` | `ERROR`（阻断）/ `WARNING`（记录不阻断） |
| `ValidationIssue` | `path`、`code`、`severity`、`message` |
| `CodepackValidationResult` | `manifest?`、`issues`；便捷属性 `errors`、`warnings`、`ok` |
| `ValidationLimits` | `maxEntries = 2000`、`maxFileBytes = 8MiB`、`maxTotalBytes = 64MiB`、`maxCompressionRatio = 100`、`maxManifestBytes = 512KiB` |
| `ZipEntryInfo` | `path`、`sizeBytes`、`compressedSizeBytes`、`isDirectory`、`isSymbolicLink` |
| `CodepackValidator` | `parseAndValidateManifest(manifestJson, limits)`、`validateEntries(manifest, entries, limits)`、`validateCompatibility(manifest, appVersion)` |
| `SemVer` / `VersionRange` | `主.次.修订`（可带 `v` 前缀）；范围由空格分隔的比较条件组成（`>=`/`<=`/`>`/`<`/`=`），全部满足才匹配，如 `">=1.0.0 <2.0.0"`；非法版本抛 `IllegalArgumentException`（由调用方映射为校验错误） |
| `LocalizedText` / `LocalizedList` | 见 `docs/specs/knowledge.md` §2；`ContentJson` 配置：未知字段容忍、非宽松模式、写出默认值 |
| `LanguageSyntax` | 见 `docs/specs/knowledge.md` §5.5 |
| `SafePath.Check` | `Ok(normalized)` / `Rejected(reason)` |

## 2. Codepack 容器布局

```text
example.codepack (ZIP, deflate)
├── manifest.json
├── signatures/                  # 可选目录；P0 不参与信任决策（ADR-005）
├── content/
│   ├── topics.json
│   ├── edges.json
│   ├── terms.json
│   └── examples.json
├── syntax/<language>.json
├── theme/tokens.json
├── assets/                      # 可选静态资源
└── LICENSE
```

规则：

- 只接受 ZIP；非 ZIP、截断 ZIP、空清单、未知 `packType`、未知贡献类型一律拒绝。
- 不允许可执行内容：`.dex`、`.so`、`.jar`、`.apk`、`.bin`、`.elf`、`.wasm`、`.sh`、`.bat`、`.exe`，以及 `lib/`、`classes/` 目录（`SafePath.isForbiddenEntry`）。
- 不允许符号链接条目；目录条目以 `/` 结尾。
- 不得包含清单声明之外的可执行能力字段（如 `runtime`、`native`、`executable`、任意网络/文件权限）。

## 3. 校验顺序（失败即停，失败可清理）

与 `docs/05` §11 对齐，实现为纯函数流水线：

1. **来源与操作**：SAF 返回的 URI 可读、文件大小 ≤ 64MiB、扩展名/内容类型为 ZIP（`application/zip` 或 `application/octet-stream`）；用户取消则无残留。
2. **ZIP 结构**：条目数 ≤ 2000；单文件 ≤ 8MiB；总解压 ≤ 64MiB；压缩比 ≤ 100；路径按 `SafePath.checkRelative` 规范化；拒绝绝对路径、`..`、NUL、驱动器前缀、符号链接；`isForbiddenEntry` 拒绝可执行内容。
3. **清单**：大小 ≤ 512KiB；JSON 可解析；`PackManifest` 必填字段（`packId`、`packType`、`version`、`title`）完整；`schemaVersion` 已知；未知执行能力字段/权限拒绝。
4. **兼容性**：`pluginApiVersion` ≤ 应用支持版本（当前 1）；`minAppVersion` ≤ 应用版本；`packType` 合法；贡献类型在 `ALLOWED_TYPES` 内。
5. **文件完整性**：清单 `files` 非空（分发产物）；逐项校验路径存在、`sizeBytes` 一致、SHA-256 匹配（小写 hex，拒绝 `LOWERCASE_SHA256_HEX` 等占位值）；清单未列出的内容文件拒绝或警告（按安全默认拒绝）。
6. **依赖与冲突**：`PackDependency` 的 `packId` 存在性、版本范围可解析、循环依赖、重复安装冲突、与已安装包的 ID 冲突（Topic/Term/Example 合并后全局唯一）。
7. **内容静态检查**：Topic/Edge/Term/Example 的 ID、引用、边类型、enums、双语缺失、`annotations` 区间、示例 `runtimeId` 合法性（与 `docs/specs/knowledge.md` §9 一致）。
8. **暂存与激活**：写入 `staging/` → 事务导入/索引构建 → 原子切换 `activeVersion` → 清理暂存；任一步失败回滚且保留旧版本。

## 4. 依赖解析

- 输入：待安装包 `P` 的 `dependencies` + 已安装包集合 + 目录中可选版本。
- 算法：先校验每个依赖是否可满足（已安装或可安装），再做拓扑排序（Kahn），有环或缺失即失败并报告完整链（不安装任何包）。
- 版本选择：已安装版本满足范围则复用；否则要求用户先安装依赖；`optional = true` 的依赖缺失不阻断，但在 UI 标注“可选能力缺失”。
- 冲突：同一 `packId` 已有不同 `version` 且范围不兼容 → 报告冲突；同一 ID 来自两个启用包 → 拒绝后安装者，不静默覆盖。
- 卸载：被依赖包不能静默卸载；先给出反向依赖列表，用户确认后按“停用贡献 → 处理反向依赖 → 重建索引 → 删除注册 → 清理文件”执行。
- 禁用/启用：启停后必须重建索引，确保无孤立引用；主题插件只允许使用设计令牌白名单键。

## 5. 路径安全（`SafePath`）

`checkRelative(raw)` 依次拒绝：

1. 空白/空路径；
2. 以 `/` 开头的绝对路径；
3. 匹配 `^[A-Za-z]:` 的驱动器路径；
4. 含 NUL（`\u0000`）；
5. 任一 `..` 段（不做“容忍式”归一化，直接拒绝）；
6. 单段长度 > 255；
7. 归一化后为空（如 `./`）。

允许：普通相对段、`.` 与空段（被忽略）、反斜杠统一转 `/` 后处理；返回 `Ok(归一化路径)`，段之间用 `/` 连接。

`isUnderRoot(root, target)`：规范化后 `target == root` 或以 `root + "/"` 开头才为真；调用方负责先解析真实父目录（防符号链接逃逸）。

`isForbiddenEntry(path)`：命中禁止扩展名或 `lib/`、`classes/` 前缀返回 `true`。

## 6. 导入流程（SAF）

1. `:feature:library` 调用 SAF `OpenDocument`（MIME：`application/zip`、`application/octet-stream`、`*/*` 兜底），不申请全盘存储权限。
2. 读取 URI 到临时文件（流式、带上限）；用户取消或读取失败时清理临时文件。
3. 调用 `CodepackValidator` 流水线；`errors` 非空则展示每个 `path + code + message`，不安装。
4. 依赖/冲突检查通过后安装；展示来源（导入/内置/下载）、版本、贡献、依赖与权限说明。
5. 安装失败展示可执行下一步（重试/查看原因/删除暂存），不得留下半安装状态。

## 7. 错误码与严重级别

| code（示例） | severity | 含义 |
| --- | --- | --- |
| `manifest.missing` / `manifest.too_large` / `manifest.invalid_json` | ERROR | 清单缺失/过大/不可解析 |
| `manifest.required_field` | ERROR | 必填字段缺失 |
| `manifest.unknown_capability` | ERROR | 出现可执行/未知能力 |
| `entry.absolute_path` / `entry.parent_traversal` / `entry.symlink` / `entry.forbidden_type` | ERROR | 路径安全违规 |
| `entry.too_many` / `entry.too_large` / `archive.too_large` / `archive.compression_ratio` | ERROR | 体积/数量超限 |
| `file.missing` / `file.size_mismatch` / `file.sha256_mismatch` / `file.placeholder_hash` | ERROR | 完整性失败 |
| `compat.plugin_api` / `compat.min_app_version` / `compat.schema_version` | ERROR | 版本不兼容 |
| `contrib.type_not_allowed` | ERROR | 贡献类型不允许 |
| `dependency.missing` / `dependency.cycle` / `dependency.version_conflict` | ERROR | 依赖问题 |
| `content.duplicate_id` / `content.broken_edge` / `content.bilingual_missing` | ERROR/WARNING | 内容质量 |
| `signature.present_but_ignored` | WARNING | P0 忽略签名（不构成信任） |

WARNING 不阻断安装，但必须显示在安装确认页；ERROR 一律阻断。校验器必须保证失败后无残留：暂存目录被清理、旧版本可读（与 `docs/specs/knowledge.md` §7 一致）。

## 8. 示例：最小知识包清单

```json
{
  "schemaVersion": 1,
  "packId": "core.python",
  "packType": "knowledge",
  "version": "1.0.0",
  "title": { "zh-CN": "Python 基础", "en": "Python Fundamentals" },
  "defaultLocale": "zh-CN",
  "locales": ["zh-CN", "en"],
  "minAppVersion": "0.1.0",
  "pluginApiVersion": 1,
  "entry": "content/topics.json",
  "dependencies": [
    { "packId": "core.programming-basics", "versionRange": ">=1.0.0 <2.0.0", "optional": false }
  ],
  "files": [
    { "path": "content/topics.json", "sha256": "<64 位小写 hex>", "sizeBytes": 10240 }
  ]
}
```

## 9. 验收清单（对应 TASK-106/108）

- [ ] `:plugin-api:test` 覆盖清单必填/未知能力、占位哈希、路径穿越、绝对路径、驱动器前缀、NUL、符号链接、条目/文件/总量/压缩比超限、依赖缺失/循环/冲突、API 版本不兼容、贡献类型拒绝。
- [ ] 恶意样例矩阵（Zip Slip、压缩炸弹、重复清单、伪造路径、安装中途失败、卸载竞态）全部有负例结果。
- [ ] 导入取消无残留；安装失败回滚且旧版本可读；启停后索引无孤立引用。
- [ ] P0 遇到 dex/so/脚本/可执行贡献/越权权限明确拒绝，不忽略字段继续执行。
- [ ] 错误码与 `ValidationIssue` 一致，UI 展示可执行下一步。
