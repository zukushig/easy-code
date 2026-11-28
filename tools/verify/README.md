# CodeTree 验收检查工具 / Verification tools

本目录是 QA 侧的可执行验收入口，配合 `docs/QA-REPORT.md` 使用。所有脚本只依赖 Python 3.11 标准库与
`PyYAML`（见 `scripts/requirements-ci.txt`），不联网、不调用 Gradle，可在 CI 与本机重复执行。

## 1. 目录

| 文件 | 作用 |
| --- | --- |
| `check_content.py` | 内容包与分发产物：12 包结构、ID 全局唯一、边引用与 prerequisite 环、双语、annotation 偏移；`content/dist` 的 sha256sums / catalog / zip 安全 / manifest.files 一致性；`content/index.json` |
| `check_repo.py` | 仓库结构与门禁：15 份源文档 + 12 份工程文件、13 个模块与 build 脚本、版本矩阵、app 的 SDK/applicationId/versionName、`gradle.properties` 低内存参数、`.gitignore`、明文密钥扫描、4 个工作流与复合动作结构；末尾调用 `merge_docs.py --check` 与 `validate_workflows.py` |
| `check_seed.py` | `app/src/main/assets/seed/` 与 `content/dist/` 的一致性：catalog、sha256sums、codepack 集合、zip 完整性；默认“seed 缺失只告警”，`--require-seed` 升级为发布门禁 |
| `run_all.sh` | 一键入口：依次运行上述三个检查器 + `merge_docs.py --check` + `validate_workflows.py` + `tools/content/validate_content.py`，最后打印汇总退出码 |

## 2. 常用命令

```bash
# 一键完整验收（推荐给 QA / 发布前）
bash tools/verify/run_all.sh

# 先跑自测再完整验收
bash tools/verify/run_all.sh --selftest

# 单项检查（--json 便于机器读取）
python3 tools/verify/check_content.py --json
python3 tools/verify/check_repo.py --json
python3 tools/verify/check_seed.py --require-seed

# 只做纯函数自测（不读取仓库状态，适合改脚本后快速回归）
python3 tools/verify/check_content.py --selftest
python3 tools/verify/check_repo.py --selftest
python3 tools/verify/check_seed.py --selftest
```

## 3. 退出码

| 退出码 | 含义 |
| --- | --- |
| 0 | 无 error（warning 不阻断） |
| 1 | 存在 error，需要修复后重跑 |
| 2 | 参数错误（`run_all.sh`） |

`--json` 输出结构：`{ok, errors[], warnings[], stats{}}`；`check_seed.py` 额外带 `require_seed`。

## 4. 覆盖范围与边界

覆盖：

- 内容数据契约（12 包、稳定 ID、引用完整性、双语、示例标注、哈希与 zip 安全）；
- 分发产物与内置种子的一致性（可复现构建的哈希清单）；
- 仓库结构、版本矩阵、模块布局、工作流关键约束、仓库卫生与明文密钥扫描；
- 文档门禁（合集时效性、工作流策略文本）与内容工具校验器。

不覆盖（必须在 QA 报告或 CI 中单独取证）：

- Gradle 构建与 APK 产物：由 `.github/workflows/android.yml` / `release.yml` 的 artifact 提供证据；
- 纯 JVM 单元测试：由 `android.yml` 的 `jvm-tests` 作业执行并上传报告；
- Compose UI、真机/模拟器、性能与可访问性：需要设备矩阵与人工验收；
- 真实 AI 服务联调、远程内容下载、签名发布：当前环境无凭据/后端，只能标注未验证。

## 5. 与 CI 的关系

- `ci.yml`：`merge_docs.py --check` + `validate_workflows.py` + `tools/content/validate_content.py`（快速门禁）；
- `android.yml`：纯 JVM 单测 + `:app:assembleDebug` + APK artifact；
- `nightly.yml`：`bash tools/verify/run_all.sh` 全量验收 + 全量构建；
- `release.yml`：发布前静态门禁 + `assembleRelease` + SHA256SUMS + GitHub Release 上传。

## 6. 维护约定

1. 修改本目录脚本后，先跑对应 `--selftest`，再跑 `bash tools/verify/run_all.sh --selftest`。
2. 检查项与 `docs/QA-REPORT.md` 的“验证方式/证据”列一一对应；新增检查项时同步更新报告。
3. 本目录脚本不改仓库内容（只读），不执行 `git` 写操作，不安装依赖。
