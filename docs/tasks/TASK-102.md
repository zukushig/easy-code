# 任务卡：TASK-102 本机 aarch64 构建工具链与出包验证（T2）

> 使用前复制到项目实际任务目录。一个任务只保留一个当前负责人；子任务分别建卡。
> 模板：`docs/templates/TASK_CARD.md`；本卡对应团队任务 **T2**。

## 基本信息

| 字段 | 内容 |
| --- | --- |
| 任务卡编号 | TASK-102 |
| 团队任务 | T2（`team_task` id：task-2） |
| 关联 WBS | CT-000-02（仓库初始化）、CT-000-03（Version Catalog/构建约定）、CT-000-04（CI 基线） |
| 关联需求 | NFR-001、NFR-003（低内存设备可构建）；ADR-001、ADR-009 §5 |
| 优先级 | P0 |
| 状态 | In Progress |
| 当前负责人 | buildsmith |
| 评审人 | lead + architect |
| 目标里程碑 | M1（应用骨架，构建通道先行） |
| 前置依赖 | TASK-101（版本矩阵与模块布局已冻结） |
| 后续消费者 | TASK-105、TASK-106、TASK-107、TASK-108、TASK-109、TASK-110 |

## 背景与用户价值

本机是 aarch64 手机 proot（约 1.4GB 可用内存），官方 Android SDK 的 aapt2 只有 x86_64 版本，直接构建会在资源链接阶段失败。若不能在本机产出可安装 APK，MVP 的“可构建、可安装”验收只能依赖 GitHub Actions，反馈周期长且不可离线。本任务负责搭建 **JDK17 + Gradle 8.9 + Android SDK + aarch64 aapt2/zipalign 覆盖方案** 的本机通道，并用最小 Compose Demo 与 `easy-code` 纯 JVM 模块验证。

## 目标

- [ ] 在仓库外（`/root/toolchain` 或等效目录）搭好 JDK17、Gradle 8.9、Android SDK（platforms;android-34、build-tools、platform-tools）与 aarch64 的 `aapt2`/`zipalign` 覆盖方案。
- [ ] 用最小 Compose Demo 验证 `assembleDebug` 能产出可安装 debug APK（`unzip -l` 含 `classes.dex` 与 `AndroidManifest.xml`）。
- [ ] 验证 `easy-code` 纯 JVM 模块可构建测试：`./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test`（或在工具链就绪前的等价独立 JVM 通道）。
- [ ] 产出 `tools/build/` 下的可复制脚本与说明，以及 `/root/toolchain/REPORT.md`（APK 路径、大小、耗时、内存参数、坑与绕过方案）。
- [ ] 明确“无 Android SDK 纯 JVM 单测通道”是否可行：可行则给出命令；不可行则记录原因与替代证据（ADR-009 §5 待验证项）。

## 非目标

- 不实现业务代码（app/core/feature 的 `src/**` 由 shell/engine/learn/tools 负责）。
- 不引入签名密钥、不发布到任何商店、不修改 CI 配置（`.github/**` 由 TASK-109 适配）。
- 不升级 ADR-001 锁定的版本矩阵；如必须升级，先提交 `CONTRACT_CHANGE` 并修订 ADR-001。
- 不把本机通道失败当作 MVP 阻塞：GitHub Actions 是备用出包通道，但必须如实标注“未在本机验证”。

## 只读上下文（必须先读，不得修改）

1. `AGENTS.md`、`docs/00-EXECUTIVE-SUMMARY.md`、`docs/02-ARCHITECTURE.md` §2。
2. `docs/decisions/ADR-001-android-sdk-matrix.md`、`docs/decisions/ADR-009-mvp-module-layout.md`。
3. `docs/specs/module-layout.md`（13 模块与依赖方向）。
4. `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties`。
5. 各模块 `build.gradle.kts`（了解插件与依赖，不得修改）。

## 输入与契约

- 输入：ADR-001 版本矩阵（JDK 17、AGP 8.6.0、Kotlin 2.0.20、Compose BOM 2024.09.02、Gradle 8.9、compileSdk 34）；本机网络与压缩包来源由 buildsmith 自行确认（国内镜像可用）。
- 输出契约：`tools/build/` 脚本与说明 + `/root/toolchain/REPORT.md`；报告必须包含可复制的完整命令、环境版本、内存参数、失败与绕过记录。
- 与其他任务的接口：shell/engine/learn/tools 的构建依赖本任务产出的工具链；TASK-109 的 CI 适配参考本报告的失败模式。

## 允许改动文件（写范围）

```text
/root/toolchain/**（工具链与报告）
/root/workspace/easy-code/tools/build/**（本任务脚本与说明）
```

只读：`app/`、`core/**`、`feature/**`、`runtime/**`、`plugin-api/**`、`gradle/**`、`.github/**`、`content/**`。

## 禁止事项

- 不修改版本矩阵、`gradle/libs.versions.toml`、`gradle.properties` 的既有参数；不升级 AGP/Kotlin/Compose/Gradle。
- 不修改 `.gitignore`、不提交密钥/签名文件、不执行 `git push`（仓库推送由 Lead 统一处理）。
- 不把 x86 模拟器或云端构建结果冒充“本机已出包”；报告必须区分本机验证与 GitHub Actions 备用通道。
- 不在未评审情况下引入新的构建插件/依赖；不下载来源不明的二进制。

## 子任务

| 子任务 ID | 行为 | 输出 | 状态 |
| --- | --- | --- | --- |
| TASK-102-A | SDK 组件与 aarch64 aapt2/zipalign 覆盖方案 | 可用的 `sdk` 目录 + 覆盖脚本 | 进行中 |
| TASK-102-B | 最小 Compose Demo 出包验证 | demo debug APK + 报告 | 进行中 |
| TASK-102-C | easy-code 纯 JVM 模块测试验证/无 SDK 通道 | 命令输出 + 结论 | Backlog |
| TASK-102-D | 文档与交接 | `tools/build/README.md` + `/root/toolchain/REPORT.md` | Backlog |

## 可执行验收标准

1. APK 存在且结构正确：
   - `ls -l <APK 路径>`（demo 与/或 `app/build/outputs/apk/debug/`）；
   - `unzip -l <APK>` 输出包含 `classes.dex` 与 `AndroidManifest.xml`；
   - 记录 APK 大小与构建耗时。
2. 工具链版本可核对：`java -version`（17）、`./gradlew --version`（Gradle 8.9）、SDK `platforms;android-34` 与 `build-tools` 存在。
3. 纯 JVM 测试（工具链就绪后）：`cd /root/workspace/easy-code && ./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test` 通过；若 SDK 不可用，提供独立仅 JVM 子构建/脚本的等价输出，并明确标注。
4. `/root/toolchain/REPORT.md` 包含：可复制命令、本机/备用通道区分、失败与绕过、内存参数（`-Xmx1536m` 等）与实际使用、下一步建议。
5. 不伪造：任何未实际执行的命令不得写成已验证；构建通过不等于 APK 功能通过。

## 验证计划

| 测试 ID | 层级 | 命令/步骤 | 预期 | 证据位置 |
| --- | --- | --- | --- | --- |
| TST-102-001 | 工具链 | `java -version`、`./gradlew --version` | JDK17 / Gradle 8.9 | REPORT 引用 |
| TST-102-002 | 出包 | `./gradlew :app:assembleDebug`（demo 或 easy-code） | 产出 debug APK | APK 路径 + `unzip -l` |
| TST-102-003 | 结构 | `unzip -l <APK>` | 含 classes.dex/AndroidManifest.xml | 命令输出 |
| TST-102-004 | JVM 测试 | `./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test` | 用例全部通过 | 命令输出 |
| TST-102-005 | 无 SDK 通道 | 独立子构建/脚本 | 可行则命令输出；不可行则原因 | REPORT 结论 |

## 完成核对

- [ ] APK 存在且结构检查通过（只认实际文件）。
- [ ] 三条命令（版本/出包/结构）输出已贴入报告。
- [ ] 纯 JVM 测试或明确的替代证据已记录。
- [ ] 报告含失败与绕过，未把 GitHub Actions 结果冒充本机验证。
- [ ] 未修改版本矩阵与 13 模块代码。
- [ ] 交接说明已填写。

## 交接说明（HANDOFF 要点）

- 当前状态与完成项：见 REPORT 的“结论”一节。
- 修改文件：`tools/build/**`、`/root/toolchain/**`。
- 测试证据：命令、环境、退出状态、APK 路径与大小。
- 未完成项：无法在本机验证的能力（如签名 release）必须列出。
- 风险：aarch64 覆盖方案随 SDK 版本变化可能失效；给出复现步骤与回退到 GitHub Actions 的条件。
- 下一步：通知 shell/engine 工具链可用状态；TASK-109 按报告适配 CI。

## 执行日志

- 2026-11-15：任务认领（buildsmith），工具链与 demo 出包验证进行中（见 REPORT）。
