# 02 技术架构

## 1. 架构目标

架构服务于四个约束：离线学习、多个功能共享同一知识模型、终端/插件安全隔离、多个 Agent 可并行开发。优先保持模块边界清晰，不为想象中的未来需求建立过深抽象。

## 2. 技术选型

| 领域 | 方案 | 说明 |
| --- | --- | --- |
| 语言与 UI | Kotlin、Jetpack Compose、Navigation Compose | 原生 Android；设计系统自行封装，不直接依赖系统默认视觉 |
| 状态与异步 | MVVM、StateFlow、Kotlin Coroutines | UI 使用单向状态流；长任务不得阻塞主线程 |
| 依赖注入 | Hilt | 模块边界和测试替换；版本在初始化时统一确认 |
| 本地数据 | Room、Room FTS、DataStore | 图谱、词典、进度、会话与设置；迁移必须测试 |
| 网络 | OkHttp、Retrofit、Kotlin Serialization | AI SSE 与内容包下载分别封装，避免 UI 直接调用网络 |
| 后台任务 | WorkManager | 可恢复下载、内容安装和更新 |
| 文件访问 | Storage Access Framework、应用私有存储 | 首版不申请全盘存储权限 |
| 秘密保护 | Android Keystore 及配套加密存储 | 密钥不由代码、资源或普通偏好设置保存 |
| 测试 | JUnit、Kotlinx Coroutines Test、Compose UI Test、Android Instrumentation | 真实服务使用独立人工验收，不进入默认 CI |

依赖版本集中在 Version Catalog 管理，初始化时以实际兼容矩阵和官方文档为准，禁止不同 Agent 各自升级。

## 3. 模块拆分与所有权

```text
app
├── core:designsystem       设计令牌、组件、动效、引导
├── core:common             通用结果、调度器、时间、日志约束
├── core:database           Room、迁移、数据库测试
├── core:network            网络配置、错误映射、下载基础
├── core:security           密钥、哈希、签名与沙箱基础
├── core:navigation         路由契约和跨功能导航参数
├── feature:learn           知识树、详情、路径和进度
├── feature:dictionary      搜索、术语和双语对照
├── feature:terminal        会话、编辑器、诊断和补全
├── feature:assistant       AI 对话、设置和上下文选择
├── feature:plugins         导入、校验、依赖和插件管理
├── feature:downloads       知识包目录、下载和存储管理
├── runtime:api             终端运行时 SPI
├── runtime:safe-shell      P0 安全命令和文件沙箱
├── plugin-api              声明式插件公开契约
└── content:seed            内置最小知识包和示例
```

规则：

- `feature:*` 不直接访问其他功能的数据库表或内部实现，只依赖公开 Use Case/导航契约。
- `plugin-api` 和 `runtime:api` 不依赖任何 `feature:*`，避免循环依赖。
- 数据库归 `core:database` 统一创建，DAO 可由数据模块拥有，但 Schema 与迁移必须集中登记。
- `app` 负责组合，不承载业务逻辑。
- 跨模块功能通过接口和集成测试连接，不能只交付孤立 UI 或工具类。

## 4. 核心数据流

### 4.1 内容安装与离线学习

```text
远程目录/本地 codepack
  → 清单解析与兼容检查
  → 下载/读取到临时区
  → 哈希、路径、大小和依赖校验
  → 解包到版本化暂存目录
  → 数据库事务导入与索引构建
  → 原子切换 activeVersion
  → 清理临时文件，保留旧版本直到提交成功
```

安装过程中任何失败都不得删除当前可用版本。图谱、词典和双语示例引用同一组稳定内容 ID，避免各自复制内容。

### 4.2 知识树与学习状态

`ContentRepository` 提供只读图谱；`LearningProgressRepository` 单独保存用户状态。UI 组合节点内容和进度，不把学习状态写回内容包。内容包更新时通过稳定 ID 迁移进度，并对删除节点保留可恢复的孤立记录策略。

### 4.3 词典搜索

输入经过去空白和大小写规范化，依次执行精确 ID/名称、别名、前缀、FTS 和受控模糊匹配。每类结果带内部排名原因，UI 可展示「拼写建议」，但不能把推断结果标记为精确匹配。搜索在主线程外执行，新查询取消旧查询。

### 4.4 AI 请求

```text
AiScreen → ContextDraft（用户确认） → AiRepository
       → AiProvider → 网络安全层 → SSE/响应解析
       → 流式会话状态 → 本地会话保存
```

- `ContextDraft` 明确列出节点 ID、术语 ID、代码、错误文本及其长度。
- 默认不发送文件全量内容、密钥、命令历史或设备信息。
- `AiProvider` 只返回模型回答和可验证元数据，不具备终端执行权限。
- 终端执行必须经过独立用户动作，不能由模型响应自动触发。

### 4.5 终端执行

```text
TerminalViewModel → CommandParser → PolicyCheck
                 → RuntimeRegistry → RuntimeSession
                 → 沙箱文件/受控运行时 → 输出流/诊断/退出状态
```

解析、执行、高亮、诊断和补全分离。所有会话有唯一 ID，取消时同时取消协程和底层进程。运行时不可用时返回明确状态，不能伪造成功输出。

## 5. 公开接口草案

接口签名是规划契约，编码前需在 ADR 中确认实际类型和错误模型。

```kotlin
interface AiProvider {
    suspend fun stream(request: AiRequest): Flow<AiEvent>
    suspend fun cancel(requestId: String)
}

interface RuntimePlugin {
    val descriptor: RuntimeDescriptor
    suspend fun start(request: RunRequest): RuntimeSession
}

interface CodepackPlugin {
    val manifest: PluginManifest
    fun contributions(): List<PluginContribution>
}
```

统一错误类别至少包含：未配置、无网络、超时、未授权、速率限制、不兼容、校验失败、依赖缺失、权限拒绝、资源超限、用户取消和内部错误。UI 文案与日志诊断分开映射。

## 6. 数据与存储

### 6.1 主要实体

- `Topic`、`TopicEdge`、`LearningProgress`、`LearningPath`。
- `Term`、`TermAlias`、`TermRelation`、`SyntaxExample`。
- `KnowledgePack`、`InstalledContent`、`DownloadTask`。
- `PluginRecord`、`PluginDependency`、`PluginContribution`。
- `ChatSession`、`ChatMessage`、`TerminalSession`。
- 密钥只保存引用，不以明文列存入 Room/DataStore。

### 6.2 文件布局

```text
app-private/
├── content/packs/<packId>/<version>/
├── plugins/<pluginId>/<version>/
├── runtime/<runtimeId>/
├── terminal/workspace/
├── staging/                 临时安装，启动时可恢复或清理
└── backups/                 有界、短期、迁移回滚用途
```

任何来自清单、网络或用户输入的相对路径在规范化后必须仍位于目标根目录。符号链接、绝对路径和 `..` 默认拒绝。

## 7. 图谱与搜索技术约束

- 图遍历必须维护 visited 集，遇到循环依赖时终止并报告内容问题。
- 路径计算不能假设全图是树；UI 的「知识树」是可视化称谓，数据模型允许有向图和交叉关联。
- 模糊匹配设置候选上限、最大输入长度和最低相关度，防止长输入造成高耗时。
- 大型图谱采用视口裁剪或分层加载，首版不追求无限节点同屏。
- 云图布局按「当前节点 → 第一环直接关系 → 第二环延展」分层。同一组数据按关系优先级与稳定 ID 排序，角度和半径由确定性规则计算；重复进入保持位置一致。
- 视口默认展示中心节点、最多 6 个直接关系和最多 12 个延展关系，超出部分聚合为 `+N`；筛选和展开只改变可见集合，不修改图数据。
- 节点、关系边、动效时长和触控目标遵循 `docs/09-VISUAL-DESIGN.md` 与 `design/tokens.json`；减少动效使用即时或无位移呈现。

## 8. 终端安全与资源控制

- 文件 API 只接收沙箱内规范化路径，禁止任意系统路径。
- P0 内置命令采用白名单实现，不把用户输入拼接给系统 shell。
- 输出长度、会话时长、并发会话数、文件大小和存储占用均设置上限。
- 进程或运行时终止必须可验证；页面销毁与任务取消的关系明确。
- 网络权限默认不授予声明式插件；后续可执行运行时单独评估。

## 9. UI 架构

设计系统提供颜色、字体、间距、圆角、阴影、动效和组件状态，机器可读来源为 `design/tokens.json`；品牌规则与云图参数见 `docs/09-VISUAL-DESIGN.md`。业务页面不得散落硬编码颜色或复制多套卡片样式。黑灰渐变仅用于品牌图标，普通组件使用平面语义色。毛玻璃与模糊仅作可选增强，低版本或不支持设备有不降低可读性的回退。

图谱、词典和终端采用不同密度，但共享页面标题、错误、空状态和引导组件。所有异步页面具备 Loading、Content、Empty、Error 和 Offline 状态。

## 10. 性能与兼容预算

以下是验收目标而非当前实测结果：

- 中端测试机冷启动到可交互首屏目标小于 2 秒。
- 10,000 条术语搜索 P95 小于 300ms。
- 终端常规输入不在主线程执行全量语法分析；长文档采用增量分析。
- 下载过程中其他离线学习页面保持可用。
- Android 8.0 至目标版本的设备矩阵见测试文档；特殊 API 必须有版本分支。

## 11. 需要 ADR 确认的决策

1. 最小/目标 SDK 与依赖版本矩阵。
2. 图谱渲染采用 Canvas 自绘还是成熟图形组件。
3. Python/JavaScript 运行时具体实现及发布许可。
4. AI 直连与后端代理的发行默认模式。
5. 插件签名信任链和内容包签名策略。
6. Room 数据库拆分、迁移与内容版本切换细节。
7. Termux 是集成分工、外部依赖还是独立运行时发行。

未形成 ADR 前，可以编写接口测试和内容样例，但不得把某一临时选型扩散到多个模块。
