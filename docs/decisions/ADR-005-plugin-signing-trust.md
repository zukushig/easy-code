# ADR-005：插件签名信任链（D-005）

| 字段 | 内容 |
| --- | --- |
| 记录编号 | ADR-005（对应决策索引 D-005） |
| 状态 | Accepted（MVP 只支持声明式 `.codepack`；签名与可执行插件推迟，签名算法 ADR 未决） |
| 日期 | 2026-11-15（Asia/Shanghai） |
| 提案人 | ARCH（架构负责人） |
| 评审人 | Lead、engine（插件契约）、SEC、tools（知识库/插件管理 UI）、QA |
| 关联任务/需求 | TASK-106、TASK-108；PLG-001～PLG-006、CT-600-01/03/04/08/09 |
| 替代关系 | 无；签名信任链在 M4 后单独 ADR 决策 |

## 背景

CodeTree 的插件能力在 P0 只允许**声明式内容贡献**：内容、词典、语法、主题。`docs/05-DATA-CONTENT-PLUGIN.md` 定义了 Codepack 容器、清单、贡献类型、校验顺序与生命周期；`docs/07-SECURITY-RELEASE.md` §5 规定 P0 插件不得包含或请求 dex/so、可执行脚本、自动 shell、任意网络/文件访问、动态类加载与未声明贡献，遇到未知能力必须失败关闭。

已确认事实：

- 仓库已落盘 `:plugin-api`（纯 JVM）：`PackManifest`、`PluginContribution`（允许 `content`/`dictionary`/`syntax`/`theme`，明确拒绝 `runtime`/`native`/`executable`）、`ValidationLimits`（2000 条目 / 单文件 8MiB / 总 64MiB / 压缩比 100 / 清单 512KiB）、`CodepackValidator`、`SafePath`（拒绝绝对路径、`..`、NUL、驱动器前缀，并提供可执行文件黑名单）、`SemVer`/`VersionRange`。
- MVP 没有发行密钥基础设施，也没有商店签名与撤销列表；签名体系需要密钥轮换、撤销、来源展示和信任决策，属于独立安全设计。
- `.codepack` 通过系统文件选择器（SAF）导入，不申请全盘存储权限；导入内容默认不可信。

## 决策驱动因素

- **安全优先**：P0 不引入可执行代码路径，避免插件成为任意代码执行的入口。
- **可交付性**：签名体系依赖密钥管理与发布流程，MVP 无法在没有发行设施的情况下自称“已验证签名”。
- **可验证的替代防线**：SHA-256 完整性校验、路径安全、大小/数量上限、依赖与兼容检查已经能阻断已知的损坏包与 Zip Slip/炸弹类攻击。
- **可演进**：清单里保留 `signatures/` 目录与贡献类型扩展位，未来接入签名不破坏现有容器格式。

## 候选方案

| 方案 | 优点 | 代价/风险 | 验证结果 |
| --- | --- | --- | --- |
| A：MVP 只做声明式 `.codepack` + SHA-256/路径/依赖校验；签名与可执行插件推迟 | 无执行面；实现可测；与现有 `plugin-api` 契约一致 | 无法验证作者身份；内容可被中间人替换（但哈希清单可检测包内篡改） | 契约已落盘；校验顺序见 `docs/specs/plugin-api.md`，待 TASK-106 实现与负例验证 |
| B：MVP 立即引入 Ed25519 签名 + 内置公钥信任链 | 作者身份可验证 | 需要密钥管理、轮换、撤销、错误处理与发行流程；MVP 无发行设施 | 未验证，不采用；M4 后新 ADR |
| C：直接开放可执行插件（dex/so/脚本），仅靠权限声明 | 扩展性最强 | 与 `docs/07` P0 安全模型直接冲突；无法在无沙箱环境下保证隔离 | 不采用 |

## 决策

1. **MVP 插件范围**：只接受 `packType = "knowledge" | "plugin"` 的 `.codepack`，贡献类型只允许 `content`、`dictionary`、`syntax`、`theme`；`runtime`、`native`、`executable` 与网络/文件权限一律按不兼容拒绝，失败关闭，不得忽略字段继续安装。
2. **完整性校验先行**：安装前逐文件校验存在性、`sizeBytes` 与 SHA-256；清单 `files` 缺失或占位哈希（如 `LOWERCASE_SHA256_HEX`）即拒绝。
3. **路径安全**：ZIP 条目路径经 `SafePath.checkRelative` 规范化，禁止绝对路径、`..`、符号链接、NUL 与驱动器前缀；安装目标必须位于 `SafePath.isUnderRoot` 校验的暂存目录内；可执行扩展名与 `lib/`、`classes/` 目录按 `SafePath.isForbiddenEntry` 拒绝。
4. **大小与数量上限**：默认 `ValidationLimits(maxEntries=2000, maxFileBytes=8MiB, maxTotalBytes=64MiB, maxCompressionRatio=100, maxManifestBytes=512KiB)`；超限在解压前拒绝并清理暂存。
5. **依赖与兼容**：`SemVer` + `VersionRange` 解析依赖；缺失、循环、版本冲突、`pluginApiVersion` 不兼容在安装前报告，不安装后再猜；被依赖插件不得静默卸载。
6. **可执行插件与签名推迟**：签名信任链（算法、公钥分发、轮换、撤销、来源展示）与可执行插件隔离方案放到 M4 之后，由 SEC/ARCH 产出新 ADR 并更新 `docs/07` 威胁模型；在此之前 `signatures/` 目录可存在但不参与信任决策，安装流程必须忽略它而不是“看到签名即信任”。

## 理由

- P0 的安全边界是“没有可执行入口”。签名解决的是作者身份与分发信任，不能替代对可执行代码的沙箱；在没有隔离方案前引入签名只会制造虚假安全感。
- SHA-256、路径规范化和体积上限能直接覆盖 `docs/06-TESTING-ACCEPTANCE.md` §4.6 要求的主要负例（Zip Slip、压缩炸弹、重复清单、占位哈希、伪造路径），且完全可在纯 JVM 单测中验证。
- 清单已预留 `signatures/` 与贡献扩展位，未来接入签名属于增量演进，不需要破坏 `.codepack` 容器格式。

## 后果

### 正面

- 攻击面小：安装流程只处理数据文件，没有代码执行路径。
- 校验器为纯函数，可用恶意样例回归，证据可复现。
- 与 `docs/05` 的校验顺序一致，便于多 Agent 对同一契约实现。

### 负面与限制

- 无法证明插件作者身份与篡改来源；用户只能依赖下载渠道与包内哈希清单。
- 恶意但“格式合法”的内容（错误知识、误导性词典、越权主题令牌）仍需内容审查，不能只靠技术校验。
- 未来接入签名时，`signatures/` 的算法与语义需要重新定义，旧包需要迁移策略。

### 对其他模块的影响

- `:plugin-api` 是唯一契约模块，`:feature:library` 只调用校验与安装用例，不复制校验逻辑。
- `content/packs/**` 的打包工具（`tools/content/build_dist.py`）必须生成与清单一致的真实 SHA-256，否则安装被拒。
- 主题贡献只能使用设计系统允许的令牌（见 `docs/specs/design-system.md`），不得加载远程资源。
- QA 必须覆盖 `docs/specs/plugin-api.md` 列出的负例矩阵。

## 兼容与迁移

- `schemaVersion=1`、`pluginApiVersion=1` 冻结；新增可选字段按 `docs/05` §13 的兼容样例处理（旧版本忽略未知字段，必填缺失即失败）。
- 未来启用签名：清单增加 `signature`/`signatures/` 语义，旧未签名包默认降级为“未验证来源”，是否允许安装由新 ADR 决定；不得静默把旧包视为可信。
- 回滚条件：出现绕过路径安全或可执行内容被安装的缺陷时，暂停插件导入入口，修复并补齐负例后再开放。

## 验证计划

- 单元测试（`:plugin-api:test`）：清单必填/未知执行能力、占位哈希、路径穿越（`../`、绝对路径、Windows 驱动器、NUL）、符号链接、超限条目/文件/总量/压缩比、依赖缺失/循环/冲突、`pluginApiVersion` 不兼容。
- 集成测试（TASK-108）：SAF 导入取消无残留、安装失败回滚、启停后索引同步、卸载后无孤立引用。
- 安全测试：Zip Slip、压缩炸弹、重复清单、伪造路径、恶意主题值、安装中途失败、卸载竞态，写入 QA 报告。
- 推翻条件：出现无法用路径/大小/哈希校验阻断的合法格式攻击，或未来签名 ADR 批准后按新 ADR 更新本决策。

## 后续行动

| 动作 | 负责人 | 前置依赖 | 完成证据 |
| --- | --- | --- | --- |
| 实现 Codepack 校验器与依赖解析 | engine | 本 ADR + `docs/specs/plugin-api.md` | `:plugin-api:test` 输出 |
| 打包工具生成真实哈希与 catalog | content-a / content-b | TASK-103/104 | `tools/content/build_dist.py` 输出 |
| 插件管理 UI 与安装事务 | tools | TASK-108 | 构建输出 + 验收说明 |
| 签名信任链与可执行插件 ADR | SEC / ARCH | M4 后 | 新 ADR + 威胁模型更新 |
