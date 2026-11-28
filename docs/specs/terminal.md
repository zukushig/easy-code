# 终端运行时契约（terminal）

> 适用范围：MVP M1/M2 的 `:runtime:api`、`:runtime:safe-shell` 与 `:feature:terminal`。事实来源：`docs/07-SECURITY-RELEASE.md` §4、`docs/01-PRD.md` TRM-001～TRM-008、`docs/02-ARCHITECTURE.md` §4.5/§8、ADR-003；可执行契约以 `runtime/api/src/main/kotlin/com/codetree/runtime/api/RuntimeContracts.kt` 为准。
>
> 一句话：P0 终端是**受控学习沙箱**，不是系统 shell、不是完整 Linux（ADR-007）。

## 1. 类型契约（与代码一致）

| 类型 | 定义要点 |
| --- | --- |
| `RuntimeKind` | `BUILTIN_SHELL` / `PYTHON` / `JAVASCRIPT`；P0 只注册 `BUILTIN_SHELL`，其余 `available = false` |
| `RuntimeDescriptor` | `id`、`displayName`、`languages`、`kind`、`available` |
| `RuntimePlugin` | `val descriptor`；`suspend fun start(request: RunRequest): RuntimeSession` |
| `RunRequest` | `sessionId`、`input`、`cwd`（默认 `"."`）、`limits: ResourceLimits` |
| `ResourceLimits` | `maxOutputBytes = 64KiB`、`maxDurationMs = 5000`、`maxFileBytes = 1MiB`、`maxSessions = 1` |
| `RuntimeSession` | `id`、`events: Flow<RuntimeEvent>`、`suspend fun cancel()` |
| `RuntimeRegistry` | `descriptors`、`plugin(runtimeId)`、`register(plugin)` |
| `RuntimeEvent` | `Started(atEpochMs)`、`Stdout(text)`、`Stderr(text)`、`Diagnostic(CodeDiagnostic)`、`LimitReached(kind)`、`Finished(exitCode, durationMs, cancelled)` |
| `CodeDiagnostic` | `severity`（ERROR/WARNING/INFO）、`line`、`column`、`endColumn`、`message`、`hint?`、`fix?`（`FixKind` APPEND/REPLACE/NONE）、`ruleId?` |
| `SyntaxAnalyzer` | `highlight(code): List<TokenSpan>`、`diagnose(code): List<CodeDiagnostic>`、`complete(code, cursor, limit = 8): List<CompletionSuggestion>` |
| `TokenSpan` / `TokenKind` | 字符区间 + `PLAIN/KEYWORD/BUILTIN/STRING/COMMENT/NUMBER/OPERATOR/IDENTIFIER/FUNCTION/PUNCTUATION` |
| `CompletionSuggestion` | `text`、`insertText`、`detail`、`kind`（COMMAND/KEYWORD/BUILTIN/WORD/PATH）、`replaceStart`、`replaceEnd` |

契约规则：

1. `RuntimePlugin` 只负责执行，不具备 UI、网络、密钥、插件加载能力。
2. `RuntimeSession.cancel()` 必须幂等；取消后事件流必须以 `Finished(cancelled = true)` 收敛，不得再追加 `Stdout`/`Stderr`。
3. `RuntimeEvent` 的顺序保证：`Started` 最多一次且为首个事件；`Finished` 恰好一次且为最后事件；`LimitReached` 之后允许继续产生已缓冲输出，但不得无限追加。
4. `RuntimeRegistry` 的 `descriptors` 必须反映真实可用性；运行时缺失时 `available = false`，UI 显示“未安装/不可用”，**不得伪造成功输出或退出码**。

## 2. P0 白名单命令

命令解析：单条命令 + 参数列表，按空白分词；支持单/双引号包裹含空格参数；**不支持**管道 `|`、重定向 `>`/`<`/`>>`、命令串联 `;`/`&&`/`||`、命令替换 `$()`、后台 `&`、通配符展开（`*`/`?` 作为普通字符，`ls` 自身可对目录内文件名做受控前缀匹配）。遇到不支持的语法必须返回可读错误与替代建议，且不执行任何副作用。

| 命令 | 语法 | 行为与限制 |
| --- | --- | --- |
| `help` | `help [command]` | 列出白名单命令；`help <command>` 输出用法与示例；明确说明“无管道/重定向/后台任务” |
| `pwd` | `pwd` | 输出当前工作目录（相对沙箱根） |
| `ls` | `ls [-l] [path]` | 列出沙箱内目录；`-l` 输出大小与修改时间；默认当前目录；不跟随符号链接 |
| `cd` | `cd <path>` | 切换工作目录；只能进入沙箱内已存在目录；`..` 不得越出沙箱根 |
| `cat` | `cat <file>` | 输出 UTF-8 文本；超过 `maxFileBytes` 拒绝；二进制检测后拒绝 |
| `echo` | `echo <text...>` | 原样输出参数；不解释环境变量与转义执行 |
| `touch` | `touch <file>` | 创建空文件或更新修改时间；路径必须在沙箱内 |
| `mkdir` | `mkdir [-p] <path>` | 创建目录；`-p` 递归创建；不得创建可执行内容 |
| `rm` | `rm [-r] <path>` | 删除沙箱内文件/目录；`-r` 仅允许沙箱内非根目录；删除沙箱根一律拒绝 |
| `cp` | `cp <src> <dst>` | 复制文件（不做递归复制） |
| `mv` | `mv <src> <dst>` | 移动/重命名；目标存在时拒绝覆盖（需显式 `-f` 才覆盖，且仅限文件） |
| `head` | `head [-n N] <file>` | 默认前 10 行；`N` 上限 200 |
| `tail` | `tail [-n N] <file>` | 默认后 10 行；`N` 上限 200 |
| `wc` | `wc [-l] [-w] [-c] <file>` | 统计行/词/字节 |
| `grep` | `grep <pattern> <file>` | 固定字符串匹配（P0 不支持正则，避免 ReDoS）；大小写敏感；结果上限 200 行 |
| `date` | `date` | 输出设备本地时间（ISO 8601） |
| `clear` | `clear` | 清屏（UI 事件，不是文件操作） |
| `history` | `history [-c]` | 显示/清空会话内命令历史（仅内存，不落盘） |

未收录命令（如 `python`、`node`、`git`、`adb`、`su`）一律返回 `COMMAND_NOT_FOUND` 并提示 `help`；不得尝试调用系统程序。

## 3. 沙箱与路径规范化

1. 沙箱根：应用私有目录 `filesDir/terminal/workspace/`（首次创建，权限仅应用自身）。
2. 解析顺序：`cwd` 必须是沙箱内相对路径 → 参数路径先按 `cwd` 拼接 → 规范化（合并 `.`、消除 `..` 时不得越出根）→ 必须满足 `SafePath.isUnderRoot(sandboxRoot, resolved)`。
3. 拒绝规则：绝对路径（`/`、`~`、Windows 驱动器前缀）、任何越出沙箱根的 `..`、符号链接（不跟随、不创建、不删除）、NUL 字节、空路径、超长路径（单段 > 255、总长 > 1024）。
4. 文件上限：单文件 ≤ `maxFileBytes`（1MiB）；沙箱总占用 ≤ 32MiB（实现常量，超限报 `RESOURCE_LIMIT`）；单目录条目 ≤ 2000。
5. 文件内容按 UTF-8 读取；非法 UTF-8 拒绝并给出编码提示，不静默替换。
6. `rm`/`mv` 等破坏性命令必须先完成路径校验再执行；执行失败不得留下部分删除（目录删除先校验可删性）。

## 4. 资源上限、取消与生命周期

| 限制 | 默认值 | 超限行为 |
| --- | --- | --- |
| 输出字节 | 64KiB | 截断输出 + `LimitReached("output")` + `Finished(exitCode = 137)` |
| 执行时长 | 5000ms | `LimitReached("duration")` + `Finished(exitCode = 124, cancelled = false)` |
| 单文件大小 | 1MiB | 命令失败 `AppError.ResourceLimit`，不产生部分写入 |
| 并发会话 | 1 | 第二个 `start` 返回失败，提示先结束当前会话 |
| 参数数量/长度 | ≤ 32 个参数、总长 ≤ 4096 | 解析失败 `INVALID_ARGUMENTS` |

- 取消：UI 离开页面、用户点“停止”、会话超时都调用 `cancel()`；实现必须断开输出采集并设置 `Finished(cancelled = true)`；测试断言取消后无后续事件、无残留协程/任务。
- 会话 ID 由调用方生成（UUID），同一时刻一个活动 `RuntimeSession`；结束后从注册表移除。
- 所有执行在主线程外；UI 只消费事件流。

## 5. 诊断与补全

- 输入分析由 `SyntaxAnalyzer` 提供；P0 内置 `builtin-shell` 分析器（命令/路径/引号/参数数量），语言语法来自内容包 `syntax/<language>.json`（`LanguageSyntax`），`feature:terminal` 只消费，不内嵌语言规则。
- 高亮：返回 `TokenSpan` 列表；区间必须有序、不越界、不重叠；非法输入不崩溃，退化为全文 `PLAIN`。
- 诊断：P0 至少覆盖未闭合引号、未知命令、参数数量错误、路径越界尝试、不支持语法（管道/重定向）；`fix` 只提供预览，用户确认后才应用（不得静默改代码）。
- 补全：`complete(code, cursor, limit = 8)` 返回带 `replaceStart/replaceEnd` 的建议；接受建议只替换该区间，不覆盖光标后的文本；上下文感知（命令位置补命令名，参数位置补路径/内置词）；空输入/仅空白/超长输入返回空列表不抛异常。

## 6. 错误与状态映射

| 场景 | 返回 |
| --- | --- |
| 命令不存在 | 诊断 `COMMAND_NOT_FOUND` + `Stdout` 无 + 非零 `exitCode` |
| 参数错误 | 诊断 `INVALID_ARGUMENTS` + `exitCode = 2` |
| 路径越界/符号链接 | `AppError.PermissionDenied`（用户提示“路径必须在终端工作区内”）+ 诊断 `PATH_OUT_OF_SANDBOX` |
| 文件过大/沙箱满 | `AppError.ResourceLimit` + 诊断 `RESOURCE_LIMIT` |
| 运行时不可用 | `AppError.Incompatible("运行时")` 或 `NotConfigured`，UI 显示未安装状态 |
| 用户取消 | `Finished(cancelled = true)`，不显示错误弹窗 |
| 内部异常 | `AppError.Internal`（日志脱敏，不显示堆栈） |

错误文案与日志映射分离：`AppError.userHint` 面向用户且必须有下一步，`AppError.code` 稳定供日志/测试断言。

## 7. 示例运行与示例标注

- `SyntaxExample.runtimeId` 有值且运行时可用时，节点详情/词典可“在终端运行”；无运行时显示“仅阅读”，绝不伪造输出。
- 预置安全示例（如 `echo`、`ls`、`cat`）必须来自内置内容包，运行结果与 `expectedOutput` 不一致时提示“示例输出可能随环境变化”，不自动判定用户失败。
- 终端不执行 AI 建议或模型返回的命令；执行必须由用户显式点击并确认（AIA-005）。

## 8. 验收清单（对应 TASK-106/108）

- [ ] `:runtime:safe-shell:test` 覆盖白名单命中/拒绝、引号与转义、`..`/绝对路径/符号链接、超长输出、时长超限、取消后无后续事件、并发会话上限、`rm` 越界。
- [ ] `:runtime:api` 类型无 Android 依赖，`:runtime:safe-shell` 不调用系统 shell/`ProcessBuilder`。
- [ ] 终端 UI 覆盖输入/输出/错误/清屏/历史、长输出不阻塞输入、取消状态收敛、资源超限提示。
- [ ] 高亮/诊断/补全在插入/删除文本后更新；接受补全后光标与后续文本正确。
- [ ] 帮助文案明确“内置安全命令，不是完整 Linux；无管道/重定向/后台任务”。
- [ ] 失败/取消/重启路径有实际证据，不以“页面能打开”代替。
