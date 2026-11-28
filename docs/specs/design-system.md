# 设计系统契约（design-system）

> 适用范围：MVP M1/M2 的 `:core:designsystem` 与所有 `:feature:*` UI。事实来源：`docs/01-PRD.md` UI-001～UI-006、`docs/02-ARCHITECTURE.md` §9、`docs/06-TESTING-ACCEPTANCE.md` §5；实现落点 `core/designsystem/src/main/kotlin/com/codetree/designsystem/`。
>
> 关键词：苹果式极简、内容优先、克制留白、单向状态、减少动效可降级、触控可达。

## 1. 令牌架构

`CodeTreeTheme` 通过 Compose `CompositionLocal` 提供四组令牌，业务页面**不得**硬编码颜色/圆角/阴影/时长：

```kotlin
@Immutable data class CodeTreeColors(...)
@Immutable data class CodeTreeTypography(...)
@Immutable data class CodeTreeSpacing(...)   // 间距与尺寸
@Immutable data class CodeTreeShapes(...)    // 圆角/描边
@Immutable data class CodeTreeMotion(...)    // 时长/缓动/弹簧

@Composable fun CodeTreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
)
```

约束：动态取色（Material You）关闭，保持品牌一致；`MaterialTheme` 仅作为底层实现细节，业务代码使用 `CodeTreeTheme.*`；不支持毛玻璃的设备回退为不透明表面。

## 2. 颜色令牌

### 浅色模式

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `background` | `#F5F5F7` | 页面背景 |
| `surface` | `#FFFFFF` | 卡片/列表 |
| `surfaceElevated` | `#FFFFFF` | 弹层/浮层（配合阴影） |
| `textPrimary` | `#1D1D1F` | 标题/正文 |
| `textSecondary` | `#6E6E73` | 辅助说明 |
| `textTertiary` | `#AEAEB2` | 占位/禁用 |
| `separator` | `#D2D2D7` | 分割线（0.5dp） |
| `accent` | `#0A84FF` | 主操作/选中/链接 |
| `accentSoft` | `#E5F0FF` | 强调底色 |
| `success` | `#30D158` | 掌握/成功 |
| `warning` | `#FF9F0A` | 学习中/警告 |
| `error` | `#FF453A` | 错误/删除 |
| `overlay` | `#000000` 40% | 遮罩 |

### 深色模式

| 令牌 | 值 |
| --- | --- |
| `background` | `#000000` |
| `surface` | `#1C1C1E` |
| `surfaceElevated` | `#2C2C2E` |
| `textPrimary` | `#F5F5F7` |
| `textSecondary` | `#98989D` |
| `textTertiary` | `#636366` |
| `separator` | `#38383A` |
| `accent` | `#0A84FF` |
| `accentSoft` | `#12233B` |
| `success` | `#30D158` / `warning` `#FF9F0A` / `error` `#FF453A` |

### 终端（始终深色，不随主题反色）

| 令牌 | 值 | 对应 `TokenKind` |
| --- | --- | --- |
| `terminalBg` | `#0B0B0D` | — |
| `terminalText` | `#E5E5EA` | `PLAIN` |
| `terminalKeyword` | `#FF7AB2` | `KEYWORD` |
| `terminalBuiltin` | `#7BD88F` | `BUILTIN` |
| `terminalString` | `#F2C46D` | `STRING` |
| `terminalComment` | `#7A7A80` | `COMMENT` |
| `terminalNumber` | `#C7A0FF` | `NUMBER` |
| `terminalOperator` | `#E5E5EA` | `OPERATOR` |
| `terminalFunction` | `#6FBFFF` | `FUNCTION` |
| `terminalPunctuation` | `#AEAEB2` | `PUNCTUATION` |

对比度要求：正文 ≥ 4.5:1、大号文字 ≥ 3:1；关系/状态不得只靠颜色区分（见 §6 图例）。

## 3. 字体与字号

字体族：系统默认（`FontFamily.Default`）+ 代码用 `FontFamily.Monospace`；不引入外部字体文件（离线优先、体积可控）。

| 令牌 | 字号/行高 | 字重 | 用途 |
| --- | --- | --- | --- |
| `largeTitle` | 34sp / 41sp | Bold | 页面大标题（滚动可收缩） |
| `title1` | 28sp / 34sp | SemiBold | 区块标题 |
| `title2` | 22sp / 28sp | SemiBold | 卡片标题 |
| `headline` | 17sp / 22sp | SemiBold | 列表主文本 |
| `body` | 17sp / 24sp | Regular | 正文 |
| `callout` | 16sp / 21sp | Regular | 次级正文 |
| `subhead` | 15sp / 20sp | Regular | 辅助说明 |
| `footnote` | 13sp / 18sp | Regular | 元信息 |
| `caption` | 12sp / 16sp | Regular | 标签/图例 |
| `mono` | 14sp / 20sp | Regular | 代码、终端 |

字体缩放：尊重系统 `fontScale`，`sp` 不转 `dp`；大字号下主要学习流程不得截断（可换行/滚动），终端支持水平查看。

## 4. 间距、圆角、描边与阴影

- 间距刻度（dp）：`2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64`；页面水平边距 16dp（大屏 24dp），卡片内边距 16dp，列表行最小高度 48dp。
- 圆角：`small = 8dp`（标签/输入）、`medium = 12dp`（按钮）、`large = 16dp`（卡片）、`xlarge = 24dp`（底部弹层/引导卡）、`pill = 999dp`（分段控件/胶囊）。
- 描边：分割线 0.5dp `separator`；选中态用 1.5dp `accent` 描边，不用粗重阴影。
- 阴影：`none`、`level1 = 1dp(2% 黑)`、`level2 = 4dp(6%)`、`level3 = 8dp(10%)`；卡片默认 `none` 或 `level1`，浮层用 `level3`；深色模式用 `surfaceElevated` 替代阴影。

## 5. 动效

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `durationFast` | 150ms | 按压/选中反馈 |
| `durationNormal` | 250ms | 页面内状态切换、展开/收起 |
| `durationSlow` | 400ms | 页面转场、引导页 |
| `easingStandard` | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` | 进入/位移 |
| `easingExit` | `CubicBezierEasing(0.4f, 0f, 1f, 1f)` | 退出 |
| `springPress` | `spring(dampingRatio = 0.8f, stiffness = 400f)` | 按钮/卡片按压回弹 |
| `fade` | `tween(durationNormal)` | 内容淡入淡出（默认转场） |

规则：

1. **默认动效**：淡入淡出 + 轻微位移（≤ 12dp），不使用夸张缩放/旋转/弹跳。
2. **减少动效开启**（系统 `ANIMATOR_DURATION_SCALE = 0` 或设置项 `reduceMotion = true`）：移除位移、缩放与持续装饰动画，改用 0ms 直接切换或 ≤ 100ms 的透明度变化；图谱“回到当前节点”直接跳转。
3. 动效不得阻塞输入；图谱缩放/拖动使用手势驱动而非补间动画。
4. 页面转场统一由 `:app` 的 `NavHost` 配置，feature 不自定义转场。

## 6. 组件清单（MVP 必须）

| 组件 | 要求 |
| --- | --- |
| `CodeTreeCard` | surface + `large` 圆角 + 16dp 内边距 + 可选 `level1` 阴影；点击态用按压弹簧；不支持嵌套双层卡片 |
| `LargeTitleScaffold` | 大标题随滚动收缩为导航栏标题；返回按钮 48dp；标题截断省略 |
| `BottomTabBar` | 固定 5 Tab（学习/词典/终端/AI/我的）；48dp 触控；选中色 `accent` + 文字标签；图标+文字，不只图标 |
| `SegmentedControl` | pill 容器 + 选中滑块；用于双语模式（中文解释/英文语法/双语对照）、筛选；支持 TalkBack 选中语义 |
| `CodeTreeButton` | filled（accent）/ tinted（accentSoft）/ plain 三态；最小高度 44dp、触控 48dp；禁用态降透明度并保留对比度 |
| `ListRow` | 最小高度 48dp；主文本 + 副文本 + 右侧 chevron/开关；分割线仅列表内部 |
| `EmptyState` | 图标（线性）+ 标题 + 说明 + 一个可执行操作；不得只有插图 |
| `ErrorState` | 错误原因（来自 `AppError.userHint`）+ 重试/设置入口；不显示堆栈 |
| `OfflineBanner` | 顶部细条，说明“离线可用内容”与在线能力边界 |
| `LoadingState` | 骨架屏或细进度条，超过 300ms 才显示；可取消的操作用于长任务 |
| `OnboardingPage` | 三页引导：价值说明 → 方向选择 → 权限/隐私说明；可跳过、可重看；方向选择写入 `AppSettings.activeDirection` |
| `RelationLegend` | 四类边图例：实线=前置、虚线=相关、点线=应用、双线=易混淆；配文字，不只颜色 |
| `Sparkline/ProgressRing` | 学习进度展示（`LearningStats.masteryRatio`）；数字与图形一致 |
| `TermSheet` | 底部弹层术语卡片：定义、别名、示例、跳转知识节点；支持拖拽关闭 |
| `DiagnosticRow` | 诊断行：图标 + 消息 + 位置 + “应用建议”（预览后确认） |
| `SettingsRow` / `SectionHeader` / `SwitchRow` | 设置页统一结构；分组标题用 `footnote`、行高 48dp |

组件状态：每个异步页面必须具备 `Loading / Content / Empty / Error / Offline` 五态（PRD UI-004）；五态有真实 Compose `@Preview`，QA 以预览/截图与本规范对照。

## 7. 主题插件令牌边界

插件 `theme/tokens.json` 只能覆盖以下白名单键（未知键校验失败）：

- 颜色：`accent`、`accentSoft`、`success`、`warning`、`error`（深/浅模式各一组，必须满足对比度检查）。
- 圆角：`small`、`medium`、`large`（范围 4–24dp）。
- 间距：`pageHorizontal`、`cardPadding`（范围 8–32dp）。
- 字体：`fontFamily` 仅允许 `default` / `monospace`；不允许外部字体文件与远程资源。
- 动效：`reduceMotion` 只能为 `true`（强制减少），不得加速或添加动画。

主题不得改变终端深色配色语义（`TerminalColors` 固定，避免关键字高亮失效）。

## 8. 可访问性

- 触控目标 ≥ 48dp；图标按钮必须有 `contentDescription`；装饰元素不参与语义树。
- 图谱关系不只靠颜色：线型 + 图例文字 + 详情列表；错误/成功不只靠红绿。
- TalkBack：主要按钮、输入、状态、错误可朗读；图谱提供“列表模式”等价入口。
- 字体放大（最大系统字号）下主要流程不截断；终端横向滚动不遮挡输入行。
- 键盘：输入框弹出时内容不被遮挡；返回键行为符合 `NavHost` 预期。

## 9. 验收清单（对应 TASK-105/107/108）

- [ ] `:core:designsystem` 提供 `CodeTreeTheme` 与上表组件；业务模块无硬编码颜色/圆角/时长（代码检索可验证）。
- [ ] 浅/深色、字号默认/最大、减少动效开启/关闭四组预览通过；对比度检查通过。
- [ ] 五态（Loading/Content/Empty/Error/Offline）有真实预览与文案；错误含下一步。
- [ ] 底部 5 Tab、三段引导、分段控件、卡片、空态、图例组件在真实页面接线，不是孤立组件。
- [ ] 减少动效开启后无位移/脉冲动画；“回到当前节点”直接跳转。
- [ ] 触控目标 48dp、TalkBack 主要路径可用、关系不只依赖颜色。
