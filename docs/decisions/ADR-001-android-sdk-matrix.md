# ADR-001：Android SDK 版本矩阵（D-001）

| 字段 | 内容 |
| --- | --- |
| 记录编号 | ADR-001（对应 `docs/00-EXECUTIVE-SUMMARY.md` 决策索引 D-001） |
| 状态 | Accepted（已接受，MVP M1/M2 生效） |
| 日期 | 2026-11-15（Asia/Shanghai） |
| 提案人 | ARCH（架构负责人） |
| 评审人 | Lead（协调者）、QA（质量保障）、shell（App/设计系统）、buildsmith（构建工具链） |
| 关联任务/需求 | TASK-102、TASK-105、TASK-106；NFR-001、NFR-003、CT-000-03 |
| 替代关系 | 无 |

## 背景

CodeTree 需要一个在 **aarch64 手机 proot 低内存环境**里可复现构建、且能在 Android 8.0（API 26）到当前目标版本上运行的版本矩阵。当前仓库骨架（`settings.gradle.kts`、`gradle/libs.versions.toml`、`gradle.properties`）已经按该矩阵落盘，本 ADR 将其从“工程默认值”升格为受控决策，避免各模块各自升级依赖。

已确认事实：

- 本机为 Android 手机上的 Ubuntu 24.04 proot（aarch64），可用内存约 1.4GB，Gradle 并发必须关闭（`gradle.properties` 已配置 `-Xmx1536m`、`org.gradle.workers.max=1`、`org.gradle.parallel=false`）。
- 本机 Gradle 分发包、Android SDK Build-Tools 的 aapt2 需要 aarch64 覆盖方案，正在由 T2（buildsmith）验证；验证报告落 `/root/toolchain/REPORT.md`。
- 依赖仓库通过阿里云镜像 + 官方源兜底（`settings.gradle.kts`），本机直连 Maven Central 不可用。
- 版本目录唯一来源是 `gradle/libs.versions.toml`，当前已写入下表矩阵。

## 决策驱动因素

- **用户与产品约束**：NFR-001 要求最低支持 Android 8.0（API 26）；首版只交付内部可验收 MVP，不以应用商店最新 targetSdk 强制要求为前提。
- **低内存设备构建约束**：compileSdk 越低，构建时下载与解析的 SDK 平台越少；Kotlin 2.0.20 + AGP 8.6.0 的常驻内存开销已在本机预算内。
- **多 Agent 并行**：`libs.versions.toml` 是共享高冲突文件，必须一次锁定，禁止各模块自行升级。
- **离线优先**：版本升级不应改变公开数据契约；内容与插件兼容性用 `minAppVersion`、`pluginApiVersion` 单独管理。

## 候选方案

| 方案 | 优点 | 代价/风险 | 验证结果 |
| --- | --- | --- | --- |
| A：minSdk 26 / compileSdk 34 / targetSdk 34 / AGP 8.6.0 / Kotlin 2.0.20 / Compose BOM 2024.09.02 / Gradle 8.9 / JDK 17 | 与当前骨架一致；AGP 8.6.0 官方要求 Gradle 8.7+、JDK 17；Compose BOM 2024.09.02 与 Kotlin 2.0.20 的 Compose 编译器插件匹配；构建内存与 SDK 体积可控 | 不能使用 Android 15/16 新 API；未来升级需修订 ADR | 骨架已落盘；构建验证由 TASK-102 给出（本机 APK 或 GitHub Actions 备用通道） |
| B：minSdk 24 / compileSdk 35 / targetSdk 35 / AGP 8.7+ / Kotlin 2.0.21+ | 覆盖设备更多；更接近最新商店要求 | API 24 无部分现代行为保证；SDK 35 构建产物体积与解析开销更高，低内存设备更易 OOM | 未验证，不采用 |
| C：跟随最新 AGP 8.9+/compileSdk 36 | 最新工具链 | aarch64 aapt2 覆盖方案与 Gradle 8.9 兼容性未验证；工具链升级成本高，与 MVP 目标无关 | 未验证，不采用 |

## 决策

MVP M1/M2 锁定以下矩阵，所有模块统一遵守：

| 项 | 值 |
| --- | --- |
| minSdk | 26（Android 8.0） |
| compileSdk | 34 |
| targetSdk | 34 |
| JDK / `compileOptions` / `jvmTarget` | 17 |
| Android Gradle Plugin（AGP） | 8.6.0 |
| Kotlin | 2.0.20（含 `org.jetbrains.kotlin.plugin.compose`） |
| Compose BOM | 2024.09.02 |
| Gradle Wrapper | 8.9 |
| 测试 | JUnit 5（Jupiter）；Android 侧 Compose UI Test 仅在需要设备验证时引入 |

约束：

1. 版本只在 `gradle/libs.versions.toml` 声明；模块禁止写死版本号，禁止 `+`/`latest.release` 动态版本。
2. 纯 JVM 模块（`:core:domain`、`:plugin-api`、`:runtime:api`、`:runtime:safe-shell`）使用 `kotlin-jvm` 插件，Java/Kotlin 目标均为 17，保证在无 Android SDK 的低内存设备上可直接跑单测。
3. 所有 Android 模块 `compileSdk = 34`、`targetSdk = 34`、`minSdk = 26`，不得单独覆盖。
4. 依赖升级必须修订本 ADR 并同步 `libs.versions.toml` 与所有消费端；一轮只升一个域（AGP / Kotlin / Compose / 网络与序列化）。

## 理由

- AGP 8.6.0 要求 Gradle 8.7+ 与 JDK 17，选 Gradle 8.9 留出小版本余量；Kotlin 2.0.20 自带 Compose 编译器插件机制，避免额外 `composeOptions` 版本对齐问题。
- Compose BOM 2024.09.02 覆盖 Material3 1.3.x 与 `ui-tooling-preview`，满足设计系统与预览需求；再高的 BOM 会要求更高的 compileSdk 或 AGP。
- compileSdk 34 足以支撑 M1/M2 全部 API（DataStore 1.1.1、Navigation 2.8.0、OkHttp 4.12.0、kotlinx-serialization 1.7.3），同时减少低内存设备的 SDK 下载与 dex 解析压力。
- targetSdk 34 避开 Android 15/16 的强制边到边与预测式返回等行为变更，MVP 阶段不引入未验证行为；API 26 下限符合 NFR-001 与 PRD 设备假说。

## 后果

### 正面

- 一套矩阵覆盖本机构建与 GitHub Actions 备用通道，CI 与本地版本一致，避免“本机能构建、CI 不通过”。
- 纯 JVM 模块可脱离 Android SDK 单测，低内存设备上仍能获得验证证据。
- 版本唯一来源，减少多 Agent 并行时的依赖冲突。

### 负面与限制

- 不能使用 Android 15+ 新行为与新 API；未来应用商店合规需要一次工具链升级窗口。
- aarch64 aapt2 覆盖方案失败时，本机 `assembleDebug` 不可用，出包只能走 GitHub Actions（备用通道，见 `docs/CONTEXT.md`）。
- Kotlin 2.0.20 与部分第三方库（如未来引入的 Room/KSP）存在版本耦合，ADR-006 评估 Room 时需同步复核。

### 对其他模块的影响

- 所有 `build.gradle.kts` 的 `plugins`/`targetSdk` 必须与本矩阵一致；`:app` 的 `applicationId` 使用 `com.codetree.app`。
- `.github/workflows/android.yml` 的 JDK/SDK 与 Gradle 版本需与本 ADR 对齐（由 TASK-109 适配）。
- `docs/02-ARCHITECTURE.md` §2 的技术选型表已由本 ADR 落实具体版本；该文档无需重写正文，版本矩阵以本 ADR 与版本目录为准。

## 兼容与迁移

- 数据/内容契约不受影响：`schemaVersion=1`、`pluginApiVersion=1` 由 `:plugin-api` 独立管理，与本 ADR 解耦。
- 升级路径：先修订本 ADR → 更新 `libs.versions.toml` → 跑 `./gradlew :core:domain:test :plugin-api:test :runtime:safe-shell:test` 与 `:app:assembleDebug` → 更新 `docs/CONTEXT.md` 记录。
- 回滚条件：升级后低内存设备构建 OOM 或 CI 失败无法在 1 个工作日内修复时，回退到本矩阵并记录证据。

## 验证计划

1. TASK-102 在本机用最小 Compose Demo 验证 `assembleDebug` 产出 APK，并在 `/root/toolchain/REPORT.md` 记录 APK 路径、大小、耗时、内存参数与坑。
2. 本机通道不可用或结果不稳定时，以 GitHub Actions `android.yml` 产出 debug APK 作为备用证据，并明确标注“未在本机验证”。
3. 验收命令（由具备执行环境的成员运行，输出贴回）：
   - `cd /root/workspace/easy-code && ./gradlew --version`
   - `cd /root/workspace/easy-code && ./gradlew :app:assembleDebug`
4. 推翻条件：AGP 8.6.0 与 Gradle 8.9 在本机 aarch64 无法完成配置阶段，或 Compose BOM 2024.09.02 与 Kotlin 2.0.20 组合编译失败且无小版本修复。

## 后续行动

| 动作 | 负责人 | 前置依赖 | 完成证据 |
| --- | --- | --- | --- |
| 按矩阵补齐各模块 `build.gradle.kts` | shell / engine | 本 ADR | 模块文件 + `./gradlew projects` 输出 |
| 本机 aarch64 工具链验证与 APK 出包 | buildsmith | 本 ADR | `/root/toolchain/REPORT.md` + APK 路径 |
| CI 适配 AGP/Gradle/JDK 版本 | qa | 本 ADR | `.github/workflows/android.yml` + 运行记录 |
| 版本升级时修订本 ADR | ARCH | 升级需求 | 修订记录 |
