# 05 数据、内容与插件规范

## 1. 目标

统一知识树、词典、双语示例、下载包和插件的数据契约，使内容可以离线安装、独立更新，并被多个功能模块共同引用。显示名称可以调整，稳定 ID 和公开语义不能随实现随意变化。

## 2. 基本概念

| 概念 | 说明 |
| --- | --- |
| Knowledge Pack | 可下载的课程、图谱、术语和示例集合 |
| Codepack | 通过系统文件选择器导入的 ZIP 容器，扩展名为 `.codepack` |
| Plugin | 具有清单、贡献和依赖声明的 Codepack；P0 只允许声明式贡献 |
| Topic | 知识节点，不代表只能是树节点；数据层允许图关系 |
| TopicEdge | 节点间有类型、有方向的关联 |
| Term | 术语实体，包含定义、语法和关联内容 |
| SyntaxExample | 可本地化、可验证、可标注运行时的示例 |
| Contribution | 插件向内容、词典、语法或主题注册的一项贡献 |

## 3. 标识、语言与版本

### 3.1 稳定 ID

- 使用小写字母、数字、点和连字符，例如 `python.control-flow.for-loop`。
- ID 创建后不随标题翻译或显示名称修改而改变。
- 跨包引用使用完整 ID；包内短引用在导入时规范化。
- 删除 ID 时提供 `replacedBy` 或保留 tombstone，避免用户进度无解释消失。

### 3.2 本地化

- 使用 BCP 47 语言标签，首选 `zh-CN` 和 `en`。
- 缺少请求语言时按 `用户语言 → zh-CN → en → 包默认语言` 回退，并在内容审查中记录缺失。
- 代码关键字、函数名、参数名不因中文模式而翻译；解释文本单独本地化。

### 3.3 版本语义

- 内容包和插件使用 SemVer：`主版本.次版本.修订版本`。
- 主版本：不兼容的 Schema、ID 语义或贡献行为变化。
- 次版本：向后兼容的新内容、新字段或新贡献。
- 修订版本：不改变公开语义的修复。
- `schemaVersion` 和 `pluginApiVersion` 使用整数，与应用版本分开管理。

## 4. Codepack 容器布局

```text
example.codepack
├── manifest.json
├── signatures/                 信任链启用后使用
├── content/
│   ├── topics.json
│   ├── edges.json
│   ├── terms.json
│   └── examples.json
├── syntax/
│   └── python.json
├── theme/
│   └── tokens.json
├── assets/
└── LICENSE
```

P0 拒绝包含可执行入口、原生库、dex、自动执行脚本或声明外文件行为的插件。即使扩展名看似无害，也必须按内容和清单验证。

## 5. 清单契约

### 5.1 知识包示例

```json
{
  "schemaVersion": 1,
  "packId": "core.python",
  "packType": "knowledge",
  "version": "1.0.0",
  "title": {
    "zh-CN": "Python 基础",
    "en": "Python Fundamentals"
  },
  "defaultLocale": "zh-CN",
  "locales": ["zh-CN", "en"],
  "minAppVersion": "0.1.0",
  "pluginApiVersion": 1,
  "entry": "content/topics.json",
  "dependencies": [
    {
      "packId": "core.programming-basics",
      "versionRange": ">=1.0.0 <2.0.0",
      "optional": false
    }
  ],
  "files": [
    {
      "path": "content/topics.json",
      "sha256": "LOWERCASE_SHA256_HEX",
      "sizeBytes": 10240
    }
  ]
}
```

`LOWERCASE_SHA256_HEX` 仅用于结构说明，真实包必须是实际 64 位小写 SHA-256；校验器应拒绝此占位值。

### 5.2 插件附加字段

```json
{
  "schemaVersion": 1,
  "packId": "community.example",
  "packType": "plugin",
  "version": "1.0.0",
  "pluginApiVersion": 1,
  "minAppVersion": "0.1.0",
  "permissions": [],
  "contributions": [
    {
      "type": "content",
      "path": "content/topics.json"
    },
    {
      "type": "dictionary",
      "path": "content/terms.json"
    }
  ],
  "dependencies": [],
  "files": []
}
```

P0 允许的贡献：`content`、`dictionary`、`syntax`、`theme`。`runtime`、`native`、`executable` 和网络/文件权限属于后续版本，遇到时按不兼容处理，不得忽略后继续执行。

## 6. 知识图谱模型

### 6.1 Topic 字段

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `id` | string | 全局稳定且唯一 |
| `domain` | string | 如 `python`、`android`、`vibe-coding` |
| `difficulty` | enum | `beginner`、`intermediate`、`advanced` |
| `title` | localized text | 显示名称，不参与外键 |
| `summary` | localized text | 简短说明 |
| `objectives` | localized list | 可观察的学习目标 |
| `contentRef` | path/ID | 指向详情内容，不允许任意 URL 自动加载 |
| `termIds` | string list | 关联术语 |
| `exampleIds` | string list | 关联示例 |
| `estimatedMinutes` | integer | 非负估计，仅用于规划 |

### 6.2 TopicEdge 字段

| `type` | 方向语义 | 用途 |
| --- | --- | --- |
| `prerequisite` | `source` 是 `target` 的前置 | 路径生成和解锁建议 |
| `related` | 双向相关，存储时规范方向 | 跨域发现 |
| `appliedIn` | `source` 应用于 `target` | 概念到实践 |
| `confusedWith` | 双向易混淆 | 对比学习和消歧 |

前置关系必须检测循环；`related` 和 `confusedWith` 不得被误用于生成强制学习顺序。

## 7. 术语与双语示例模型

### 7.1 Term

- `id`、`termType`、`displayName`、`aliases`、`localizedDefinitions`。
- `syntaxRef`、`exampleIds`、`relatedTermIds`、`topicIds`。
- `source`、`contentVersion`、`reviewStatus`。
- `reviewStatus` 至少区分 `draft`、`aiDraft`、`reviewed`、`verifiedExample`。

别名需要语言标签和匹配类型。搜索索引保存规范化文本，但详情展示保留原始大小写，例如 `JavaScript`、`JSON`。

### 7.2 SyntaxExample

| 字段 | 说明 |
| --- | --- |
| `id`、`language`、`languageVersion` | 示例身份和适用语言版本 |
| `title`、`explanation` | 本地化标题和解释 |
| `code` | 原始代码，不做自然语言翻译 |
| `annotations` | 按 token/区间关联中英解释，避免按行号脆弱绑定 |
| `expectedOutput` | 可执行示例的预期输出或断言 |
| `runtimeId` | 可选；缺失时明确“仅阅读” |
| `verificationStatus` | 未验证、静态检查、执行验证及对应版本 |

代码修改后，原有 `annotations` 必须通过校验，防止解释指向错误区间。

## 8. 搜索索引与排序契约

排序层级：

1. 稳定 ID 或标准名称精确匹配。
2. 标准别名精确匹配。
3. 名称/别名前缀匹配。
4. FTS 正文匹配。
5. 受控模糊匹配。

同一层级可考虑语言、当前学习领域、收藏和流行度，但不得让推荐权重越过精确匹配层级。模糊结果显示“你可能想找”，并提供编辑距离或匹配字段的内部诊断。空查询、超长输入、仅标点、组合字符和中英混合均有测试。

## 9. 下载目录与安装状态

远程目录至少提供：包 ID、版本、标题、语言、领域、难度、大小、SHA-256、下载 URL、依赖、最低应用版本和发布时间。URL 只用于显式下载，不能在知识详情中静默加载远程代码。

安装状态机：

```text
NotInstalled → Queued → Downloading → Paused
                    ↓          ↓
                 Verifying → Installing → Installed
                    ↓          ↓
                   Failed → 保留旧版本/清理暂存

Installed → UpdateAvailable → Downloading
Installed → Removing → NotInstalled
```

数据库记录和文件状态启动时进行一致性检查。只有数据库提交且 `activeVersion` 原子切换后，包才对用户可见。

## 10. 插件依赖与生命周期

- 依赖解析使用包 ID、版本范围、可选标记和应用/API 兼容范围。
- 缺失、循环、版本冲突和不兼容在安装前报告，不能安装后再猜。
- 停用插件前检查已启用反向依赖，并给出影响列表。
- 卸载按「停用贡献 → 停用/处理反向依赖 → 重建索引 → 删除注册 → 清理文件」执行。
- 用户学习进度不与插件文件一起默认删除；再次安装时可恢复仍有稳定 ID 的进度。
- 主题插件只能使用设计系统允许的令牌，不提供任意远程资源或代码执行。

## 11. 校验顺序

校验必须流式、有上限且失败可清理：

1. 文件来源和用户操作有效。
2. ZIP 条目数量、单文件、总解压大小、压缩比和路径安全。
3. 清单大小、JSON Schema、必填字段和未知执行能力。
4. 应用版本、插件 API、语言和内容 Schema 兼容性。
5. 文件存在性、大小、SHA-256 和允许的贡献类型。
6. 依赖、冲突、重复 ID 和跨包引用。
7. 内容静态检查、示例检查和主题令牌范围。
8. 写入暂存区、事务导入、索引构建和原子激活。

在信任链启用后，签名验证应在内容导入前完成；具体算法和密钥轮换由 ADR 决定。

## 12. 内容质量规范

每个 P0 知识节点至少包含一个可观察目标、一个关联术语、一个示例和一个练习；若某内容不适用，需有结构化理由而非空字符串。示例标注来源、版本、验证状态和是否需要运行时。

Vibe Coding 专题必须将 AI 生成与验证分离，至少包含：需求澄清、上下文最小化、小步迭代、差异审查、自动化测试、调试证据、版本回滚、依赖和密钥安全。

## 13. 兼容性测试样例

- 旧应用读取高 `schemaVersion`：拒绝并提示升级。
- 新增可选字段：旧版本按 Schema 规则处理，不改变原字段语义。
- 插件依赖不存在：安装前失败，现有内容不变。
- 内容包更新删除节点：进度按 tombstone/replacedBy 迁移。
- 相同 ID 不同来源：报告冲突，不以后导入者静默覆盖。
- 双语缺失：按回退规则展示，并在审查工具报告。
- 卸载后重启：无幽灵术语、断链知识节点或失效主题。
