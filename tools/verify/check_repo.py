#!/usr/bin/env python3
"""CodeTree 仓库结构与门禁验收检查（QA 侧）。

检查范围：
  1. 规划基线文件：15 份源文档、12 份工程文件、生成物（合集 / 压缩包）存在；
  2. 工程结构：settings.gradle.kts 的 13 个模块、各模块 build 脚本、版本矩阵、
     app 的 applicationId/SDK/版本号、gradle.properties 低内存参数；
  3. 仓库卫生：.gitignore 覆盖本地文件、无明文密钥/证书特征；
  4. CI 结构：4 个工作流 + 复合动作的关键约束（权限、并发、超时、路径过滤、发布触发）；
  5. 门禁命令：调用 scripts/merge_docs.py --check 与 scripts/validate_workflows.py。

用法：
  python3 tools/verify/check_repo.py             # 完整检查（含门禁命令）
  python3 tools/verify/check_repo.py --no-gates   # 只做静态结构检查
  python3 tools/verify/check_repo.py --json       # 机器可读输出
  python3 tools/verify/check_repo.py --selftest   # 纯函数自测

退出码：0 = 无 error；1 = 有 error。
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

SOURCE_DOCS = (
    "docs/00-EXECUTIVE-SUMMARY.md",
    "README.md",
    "AGENTS.md",
    "docs/01-PRD.md",
    "docs/02-ARCHITECTURE.md",
    "docs/03-AGENT-COLLABORATION.md",
    "docs/04-WBS.md",
    "docs/05-DATA-CONTENT-PLUGIN.md",
    "docs/06-TESTING-ACCEPTANCE.md",
    "docs/07-SECURITY-RELEASE.md",
    "docs/08-GITHUB-WORKFLOW.md",
    "docs/templates/AGENT_DISPATCH.md",
    "docs/templates/TASK_CARD.md",
    "docs/templates/HANDOFF.md",
    "docs/templates/ADR.md",
)

ENGINEERING_FILES = (
    ".github/workflows/ci.yml",
    ".github/workflows/android.yml",
    ".github/workflows/nightly.yml",
    ".github/workflows/release.yml",
    ".github/actions/setup-android/action.yml",
    ".github/actions/setup-android/codetree-mirrors.gradle",
    ".github/labeler.yml",
    ".github/CODEOWNERS.example",
    ".github/PULL_REQUEST_TEMPLATE.md",
    "scripts/merge_docs.py",
    "scripts/validate_workflows.py",
    "scripts/requirements-ci.txt",
)

EXPECTED_MODULES = (
    ":app",
    ":core:domain",
    ":core:data",
    ":core:designsystem",
    ":plugin-api",
    ":runtime:api",
    ":runtime:safe-shell",
    ":feature:learn",
    ":feature:dictionary",
    ":feature:terminal",
    ":feature:assistant",
    ":feature:library",
)

EXPECTED_VERSIONS = {
    "agp": "8.6.0",
    "kotlin": "2.0.20",
    "composeBom": "2024.09.02",
}

SECRET_PATTERNS = (
    ("OpenAI 风格密钥", re.compile(r"sk-[A-Za-z0-9]{16,}")),
    ("GitHub token", re.compile(r"gh[pousr]_[A-Za-z0-9]{20,}")),
    ("Google API key", re.compile(r"AIza[0-9A-Za-z_-]{30,}")),
    ("私钥头", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
    ("Slack token", re.compile(r"xox[baprs]-[A-Za-z0-9-]{10,}")),
)

TEXT_SUFFIXES = {
    ".kt",
    ".kts",
    ".py",
    ".sh",
    ".md",
    ".yml",
    ".yaml",
    ".json",
    ".txt",
    ".properties",
    ".toml",
    ".gradle",
    ".xml",
}
SKIP_DIRS = {".git", ".gradle", "build", "__pycache__", "node_modules", "dist", ".idea"}


# ---------------------------------------------------------------------------
# 纯函数（供自测复用）
# ---------------------------------------------------------------------------
def parse_gradle_includes(text: str) -> list[str]:
    """解析 settings.gradle.kts 的 include(...) 列表。"""
    block = re.search(r"include\((.*?)\)", text, re.S)
    if not block:
        return []
    return re.findall(r'"([^"]+)"', block.group(1))


def parse_toml_versions(text: str) -> dict[str, str]:
    """解析 libs.versions.toml 的 [versions] 段（只处理简单 key = "value"）。"""
    versions: dict[str, str] = {}
    in_versions = False
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if line.startswith("["):
            in_versions = line == "[versions]"
            continue
        if not in_versions or not line or line.startswith("#"):
            continue
        match = re.match(r'([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"', line)
        if match:
            versions[match.group(1)] = match.group(2)
    return versions


def scan_secrets(text: str) -> list[str]:
    """返回命中的秘密类型列表（同一类型只报一次，避免刷屏）。"""
    hits: list[str] = []
    for label, pattern in SECRET_PATTERNS:
        if pattern.search(text):
            hits.append(label)
    return hits


def check_app_build_text(text: str) -> list[str]:
    errors: list[str] = []
    expectations = (
        ('applicationId = "com.codetree.app"', "applicationId 不是 com.codetree.app"),
        ("compileSdk = 34", "compileSdk 不是 34"),
        ("minSdk = 26", "minSdk 不是 26"),
        ("targetSdk = 34", "targetSdk 不是 34"),
    )
    for needle, message in expectations:
        if needle not in text:
            errors.append(message)
    if not re.search(r"versionName\s*=\s*\"[^\"]+\"", text):
        errors.append("缺少 versionName")
    return errors


def check_gradle_properties_text(text: str) -> list[str]:
    errors: list[str] = []
    for needle, message in (
        ("org.gradle.workers.max=1", "缺少 org.gradle.workers.max=1"),
        ("org.gradle.parallel=false", "缺少 org.gradle.parallel=false"),
    ):
        if needle not in text:
            errors.append(message)
    match = re.search(r"-Xmx(\d+)m", text)
    if not match:
        errors.append("缺少 -Xmx 堆上限")
    elif int(match.group(1)) > 1536:
        errors.append(f"-Xmx 超过低内存预算：{match.group(1)}m")
    return errors


def check_workflow_structure(data: dict, filename: str) -> list[str]:
    """工作流最小结构约束（与 validate_workflows.py 互补的 QA 视角）。"""
    errors: list[str] = []
    triggers = data.get("on", data.get(True, {}))
    if not isinstance(triggers, dict):
        errors.append("缺少触发器")
        triggers = {}
    permissions = data.get("permissions")
    if not isinstance(permissions, dict) or permissions.get("contents") != "read":
        errors.append("顶层 permissions 缺少 contents: read")
    concurrency = data.get("concurrency")
    if not isinstance(concurrency, dict) or "cancel-in-progress" not in concurrency:
        errors.append("缺少 concurrency.cancel-in-progress")
    jobs = data.get("jobs")
    if not isinstance(jobs, dict):
        return errors + ["jobs 不是映射"]
    for name, job in jobs.items():
        if not isinstance(job, dict):
            errors.append(f"job {name} 不是映射")
            continue
        timeout = job.get("timeout-minutes")
        if not isinstance(timeout, int) or isinstance(timeout, bool) or timeout <= 0:
            errors.append(f"job {name} 缺少正整数 timeout-minutes")
        if "runs-on" not in job and "uses" not in job:
            errors.append(f"job {name} 缺少 runs-on")
    return errors


def check_release_workflow_text(text: str) -> list[str]:
    errors: list[str] = []
    if "tags: ['v*']" not in text and 'tags: ["v*"]' not in text:
        errors.append("release.yml 缺少 v* tag 触发")
    if "assembleRelease" not in text:
        errors.append("release.yml 缺少 assembleRelease")
    if "sha256sum" not in text:
        errors.append("release.yml 缺少 SHA256 校验和生成")
    if "gh release" not in text:
        errors.append("release.yml 缺少 GitHub Release 上传步骤")
    return errors


def check_android_workflow_text(text: str) -> list[str]:
    errors: list[str] = []
    if "assembleDebug" not in text:
        errors.append("android.yml 缺少 assembleDebug")
    if "upload-artifact" not in text:
        errors.append("android.yml 缺少制品上传")
    if "paths:" not in text:
        errors.append("android.yml 缺少路径过滤")
    if "use-cn-mirrors" not in text:
        errors.append("android.yml 缺少镜像开关输入")
    return errors


def check_ci_workflow_text(text: str) -> list[str]:
    errors: list[str] = []
    for needle, message in (
        ("scripts/merge_docs.py --check", "ci.yml 缺少合集校验命令"),
        ("scripts/validate_workflows.py", "ci.yml 缺少工作流校验命令"),
        ("tools/content/validate_content.py", "ci.yml 缺少内容校验命令"),
    ):
        if needle not in text:
            errors.append(message)
    return errors


# ---------------------------------------------------------------------------
# 检查器
# ---------------------------------------------------------------------------
class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.stats: dict[str, object] = {}

    def error(self, message: str) -> None:
        self.errors.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)


def resolve(path_text: str) -> Path:
    return ROOT / path_text


def read_text(report: Report, path_text: str) -> str:
    path = resolve(path_text)
    if not path.is_file():
        report.error(f"缺少文件：{path_text}")
        return ""
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        report.error(f"读取失败：{path_text}（{exc}）")
        return ""


def check_baseline(report: Report) -> None:
    for path_text in SOURCE_DOCS:
        if not resolve(path_text).is_file():
            report.error(f"缺少规划基线源文档：{path_text}")
    for path_text in ENGINEERING_FILES:
        if not resolve(path_text).is_file():
            report.error(f"缺少工程文件：{path_text}")
    for path_text in ("CodeTree-项目文档合集.md", "CodeTree-合并文档包.zip"):
        if not resolve(path_text).is_file():
            report.warn(f"缺少生成物（需要运行 merge_docs.py 生成）：{path_text}")
    for path_text in ("docs/CONTEXT.md", "docs/QA-REPORT.md"):
        if not resolve(path_text).is_file():
            report.warn(f"缺少交付文档：{path_text}")


def check_modules(report: Report) -> None:
    settings_text = read_text(report, "settings.gradle.kts")
    includes = parse_gradle_includes(settings_text)
    if includes != list(EXPECTED_MODULES):
        report.error(f"模块列表与契约不一致：{includes}")
    for module in EXPECTED_MODULES:
        module_dir = ROOT / module.lstrip(":").replace(":", "/")
        build_file = module_dir / "build.gradle.kts"
        if not build_file.is_file():
            report.error(f"模块 {module} 缺少 build.gradle.kts")
    report.stats["modules"] = len(includes)

    catalog_text = read_text(report, "gradle/libs.versions.toml")
    versions = parse_toml_versions(catalog_text)
    for key, expected in EXPECTED_VERSIONS.items():
        actual = versions.get(key)
        if actual != expected:
            report.error(f"版本矩阵 {key} 期望 {expected}，实际 {actual!r}")

    wrapper_text = read_text(report, "gradle/wrapper/gradle-wrapper.properties")
    if "gradle-8.9" not in wrapper_text:
        report.error("Gradle Wrapper 不是 8.9")

    app_text = read_text(report, "app/build.gradle.kts")
    for problem in check_app_build_text(app_text):
        report.error(f"app/build.gradle.kts: {problem}")
    if "signingConfigs.getByName(\"debug\")" in app_text:
        report.warn("release 仍使用 debug 签名（MVP 允许，正式发版前须替换为 CI secrets 签名）")

    properties_text = read_text(report, "gradle.properties")
    for problem in check_gradle_properties_text(properties_text):
        report.error(f"gradle.properties: {problem}")


def check_hygiene(report: Report) -> None:
    gitignore_path = resolve(".gitignore")
    if not gitignore_path.is_file():
        report.error("缺少 .gitignore（构建缓存、local.properties、APK 等不得提交）")
    else:
        gitignore = gitignore_path.read_text(encoding="utf-8", errors="replace")
        for needle in ("local.properties", "build/", ".gradle/"):
            if needle not in gitignore:
                report.warn(f".gitignore 未覆盖 {needle}")
        if gitignore_path.with_name("local.properties").exists():
            report.warn("工作区存在 local.properties（本地 SDK 路径，确认未被提交）")

    scanned = 0
    hint_files: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.suffix not in TEXT_SUFFIXES:
            continue
        relative = path.relative_to(ROOT)
        if any(part in SKIP_DIRS for part in relative.parts):
            continue
        if relative.name in {"CodeTree-项目文档合集.md"}:
            continue
        scanned += 1
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        hits = scan_secrets(text)
        if hits:
            hint_files.append(f"{relative.as_posix()}（{', '.join(hits)}）")
    for item in hint_files:
        report.error(f"疑似明文密钥：" + item)
    report.stats["scanned_text_files"] = scanned


def check_workflows(report: Report) -> None:
    try:
        import yaml  # type: ignore
    except ImportError:
        report.error("缺少 PyYAML：按 scripts/requirements-ci.txt 安装后重试")
        return

    workflow_paths = sorted((ROOT / ".github" / "workflows").glob("*.yml"))
    if len(workflow_paths) != 4:
        report.error(f"工作流数量不符：期望 4，实际 {len(workflow_paths)}")
    names = {path.name for path in workflow_paths}
    for expected in ("ci.yml", "android.yml", "nightly.yml", "release.yml"):
        if expected not in names:
            report.error(f"缺少工作流：{expected}")

    for path in workflow_paths:
        relative = path.relative_to(ROOT).as_posix()
        try:
            data = yaml.safe_load(path.read_text(encoding="utf-8"))
        except yaml.YAMLError as exc:
            report.error(f"{relative} YAML 无法解析：{exc}")
            continue
        if not isinstance(data, dict):
            report.error(f"{relative} 顶层不是映射")
            continue
        for problem in check_workflow_structure(data, path.name):
            report.error(f"{relative}: {problem}")
        text = path.read_text(encoding="utf-8")
        if path.name == "android.yml":
            for problem in check_android_workflow_text(text):
                report.error(problem)
        elif path.name == "ci.yml":
            for problem in check_ci_workflow_text(text):
                report.error(problem)
        elif path.name == "release.yml":
            for problem in check_release_workflow_text(text):
                report.error(problem)
        if "pull_request_target" in text:
            report.error(f"{relative}: 使用了 pull_request_target")

    action_path = ROOT / ".github" / "actions" / "setup-android" / "action.yml"
    if not action_path.is_file():
        report.error("缺少 .github/actions/setup-android/action.yml")
    else:
        try:
            data = yaml.safe_load(action_path.read_text(encoding="utf-8"))
        except yaml.YAMLError as exc:
            report.error(f"setup-android action YAML 无法解析：{exc}")
        else:
            runs = data.get("runs") if isinstance(data, dict) else None
            if not isinstance(runs, dict) or runs.get("using") != "composite":
                report.error("setup-android 不是 composite action")
            elif not runs.get("steps"):
                report.error("setup-android 缺少 steps")


def run_gate(report: Report, label: str, command: list[str]) -> None:
    try:
        result = subprocess.run(
            command,
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=600,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        report.error(f"{label} 无法执行：{exc}")
        return
    if result.returncode != 0:
        report.error(f"{label} 失败（退出码 {result.returncode}）")
        tail = (result.stdout + result.stderr).strip().splitlines()[-15:]
        for line in tail:
            report.error("  " + line)
    report.stats[label] = "OK" if result.returncode == 0 else f"FAILED({result.returncode})"


def check_gates(report: Report) -> None:
    run_gate(report, "merge_docs --check", [sys.executable, "scripts/merge_docs.py", "--check"])
    run_gate(report, "validate_workflows", [sys.executable, "scripts/validate_workflows.py"])


def run_checks(run_gates_enabled: bool) -> Report:
    report = Report()
    check_baseline(report)
    check_modules(report)
    check_hygiene(report)
    check_workflows(report)
    if run_gates_enabled:
        check_gates(report)
    return report


# ---------------------------------------------------------------------------
# 自测
# ---------------------------------------------------------------------------
def self_test() -> None:
    text = 'include(\n    ":app",\n    ":core:domain",\n)\n'
    assert parse_gradle_includes(text) == [":app", ":core:domain"]
    assert parse_gradle_includes("") == []

    toml = '[versions]\nagp = "8.6.0"\nkotlin = "2.0.20"\n[libraries]\nx = "1"\n'
    versions = parse_toml_versions(toml)
    assert versions == {"agp": "8.6.0", "kotlin": "2.0.20"}

    assert scan_secrets("普通文本，没有令牌") == []
    assert "OpenAI 风格密钥" in scan_secrets("key = sk-" + "a" * 24)
    assert "GitHub token" in scan_secrets("ghp_" + "b" * 24)
    assert scan_secrets("docs 里写 sk-…abcd 这种掩码示例") == []

    good_app = (
        'android {\n  compileSdk = 34\n  defaultConfig {\n'
        '    applicationId = "com.codetree.app"\n    minSdk = 26\n    targetSdk = 34\n'
        '    versionName = "0.1.0"\n  }\n}\n'
    )
    assert check_app_build_text(good_app) == []
    assert check_app_build_text("compileSdk = 33") != []

    properties = "org.gradle.jvmargs=-Xmx1280m\norg.gradle.workers.max=1\norg.gradle.parallel=false\n"
    assert check_gradle_properties_text(properties) == []
    assert check_gradle_properties_text("org.gradle.jvmargs=-Xmx4096m") != []

    workflow = {
        "on": {"pull_request": {"branches": ["main"], "paths": ["app/**"]}},
        "permissions": {"contents": "read"},
        "concurrency": {"group": "x", "cancel-in-progress": True},
        "jobs": {"build": {"runs-on": "ubuntu-latest", "timeout-minutes": 10, "steps": []}},
    }
    assert check_workflow_structure(workflow, "android.yml") == []
    broken = dict(workflow)
    broken["permissions"] = {}
    assert check_workflow_structure(broken, "android.yml") != []

    assert check_release_workflow_text("") != []
    assert check_android_workflow_text("") != []
    assert check_ci_workflow_text("") != []
    print("self-test: OK")


def main() -> int:
    parser = argparse.ArgumentParser(description="CodeTree 仓库结构与门禁验收检查")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出结果")
    parser.add_argument("--no-gates", action="store_true", help="跳过门禁命令，只做静态结构检查")
    parser.add_argument("--selftest", action="store_true", help="只运行纯函数自测")
    args = parser.parse_args()

    if args.selftest:
        self_test()
        return 0

    report = run_checks(run_gates_enabled=not args.no_gates)
    payload = {
        "ok": not report.errors,
        "errors": report.errors,
        "warnings": report.warnings,
        "stats": report.stats,
    }
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print("== 仓库结构与门禁验收 ==")
        for key, value in report.stats.items():
            print(f"{key}: {value}")
        print(f"errors: {len(report.errors)}")
        for item in report.errors:
            print("  - " + item)
        print(f"warnings: {len(report.warnings)}")
        for item in report.warnings:
            print("  - " + item)
        print("check_repo:", "OK" if not report.errors else "FAILED")
    return 0 if not report.errors else 1


if __name__ == "__main__":
    sys.exit(main())
