#!/usr/bin/env python3
"""校验 GitHub Actions 工作流结构、效率约束与策略文本。"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
GITHUB = ROOT / ".github"
WORKFLOWS = GITHUB / "workflows"
ACTIONS_DIR = GITHUB / "actions"

sys.path.insert(0, str(Path(__file__).resolve().parent))
from merge_docs import APPENDIX_START, FORBIDDEN_PATTERNS  # noqa: E402

PLACEHOLDER_RE = re.compile(r"<ORG>|<TEAM-[A-Z]+>")
SCRIPT_REF_RE = re.compile(r"scripts/[A-Za-z0-9_.-]+\.(?:py|txt|md)")
LOCAL_ACTION_RE = re.compile(r"^\./\.github/actions/[A-Za-z0-9_.-]+$")
USES_RE = re.compile(r"uses:\s*([^\s'\"]+)")


def triggers(data: dict) -> dict:
    """兼容 YAML 1.1 把未加引号的 on: 解析成布尔 True 的行为。"""
    value = data.get("on", data.get(True, {}))
    return value if isinstance(value, dict) else {}


def validate_workflow(path: Path, data: dict, root: Path) -> list[str]:
    errors: list[str] = []
    if not isinstance(data, dict):
        return ["顶层结构不是映射"]

    trig = triggers(data)
    if not trig:
        errors.append("缺少触发器")
    if "pull_request_target" in trig:
        errors.append("使用 pull_request_target 会扩大凭据暴露面")

    permissions = data.get("permissions")
    if not isinstance(permissions, dict) or permissions.get("contents") != "read":
        errors.append("顶层 permissions 缺少 contents: read")

    concurrency = data.get("concurrency")
    if not isinstance(concurrency, dict) or "cancel-in-progress" not in concurrency:
        errors.append("缺少 concurrency.cancel-in-progress 显式取值")
    elif not isinstance(concurrency["cancel-in-progress"], bool):
        errors.append("concurrency.cancel-in-progress 必须是布尔值")

    for event in ("pull_request", "push"):
        config = trig.get(event)
        if not isinstance(config, dict):
            continue
        if event == "push" and "branches" not in config:
            continue
        if path.name == "ci.yml":
            continue
        if "paths" not in config and "paths-ignore" not in config:
            errors.append(f"{event} 缺少 paths 或 paths-ignore 过滤")

    jobs = data.get("jobs")
    if not isinstance(jobs, dict) or not jobs:
        errors.append("jobs 为空或不是映射")
        return errors

    for job_name, job in jobs.items():
        prefix = f"job {job_name}"
        if not isinstance(job, dict):
            errors.append(f"{prefix} 不是映射")
            continue

        if "runs-on" not in job and "uses" not in job:
            errors.append(f"{prefix} 缺少 runs-on 或 uses")

        steps = job.get("steps")
        if "uses" not in job and (not isinstance(steps, list) or not steps):
            errors.append(f"{prefix} 缺少 steps")

        timeout = job.get("timeout-minutes")
        if not isinstance(timeout, int) or isinstance(timeout, bool) or timeout <= 0:
            errors.append(f"{prefix} 缺少正整数 timeout-minutes")

        needs = job.get("needs", [])
        if isinstance(needs, str):
            needs = [needs]
        if isinstance(needs, list):
            for dependency in needs:
                if dependency not in jobs:
                    errors.append(f"{prefix} 的 needs 引用不存在：{dependency}")
        else:
            errors.append(f"{prefix} 的 needs 类型无效")

        for step in steps or []:
            if not isinstance(step, dict):
                errors.append(f"{prefix} 含非映射步骤")
                continue
            uses = str(step.get("uses", ""))
            if LOCAL_ACTION_RE.match(uses):
                action_file = root / uses[2:] / "action.yml"
                if not action_file.is_file():
                    errors.append(f"{prefix} 引用了不存在的本地动作：{uses}")
            for script_ref in SCRIPT_REF_RE.findall(str(step)):
                if not (root / script_ref).is_file():
                    errors.append(f"{prefix} 引用了不存在的脚本：{script_ref}")

    return errors


def validate_action(path: Path, data: dict) -> list[str]:
    errors: list[str] = []
    if not isinstance(data, dict):
        return ["顶层结构不是映射"]
    runs = data.get("runs")
    if not isinstance(runs, dict) or runs.get("using") != "composite":
        errors.append("runs.using 必须是 composite")
    steps = (runs or {}).get("steps")
    if not isinstance(steps, list) or not steps:
        errors.append("复合动作缺少 steps")

    for index, step in enumerate(steps or [], 1):
        if not isinstance(step, dict):
            errors.append(f"步骤 {index} 不是映射")
            continue
        if "run" not in step and "uses" not in step:
            errors.append(f"步骤 {index} 既无 run 也无 uses")
        if "run" in step and "shell" not in step:
            errors.append(f"步骤 {index} 含 run 但缺少 shell")

    for name, config in (data.get("inputs") or {}).items():
        if not isinstance(config, dict) or "description" not in config:
            errors.append(f"输入 {name} 缺少 description")

    return errors


MIRROR_REPOSITORIES = (
    "https://maven.aliyun.com/repository/central",
    "https://maven.aliyun.com/repository/google",
)


def validate_mirror_text(text: str) -> list[str]:
    """确认 Central 与 Google Maven 分别设置。"""
    return [
        f"镜像脚本缺少仓库地址：{repository}"
        for repository in MIRROR_REPOSITORIES
        if repository not in text
    ]


def policy_scan_text(text: str) -> str:
    """生成合集只返回正文区；附录按原样收录的工程文件不参与约束文本扫描。"""
    return text.split(APPENDIX_START, 1)[0]


def validate_policy(root: Path) -> list[str]:
    errors: list[str] = []
    github = root / ".github"

    labeler = github / "labeler.yml"
    if not labeler.is_file():
        errors.append("缺少 .github/labeler.yml")
    else:
        data = yaml.safe_load(labeler.read_text())
        if not isinstance(data, dict) or not data:
            errors.append("labeler.yml 为空或结构无效")
        else:
            for label, rules in data.items():
                if not isinstance(rules, list) or not rules:
                    errors.append(f"标签 {label} 缺少规则")
                    continue
                for rule in rules:
                    if not isinstance(rule, dict) or "changed-files" not in rule:
                        errors.append(f"标签 {label} 缺少 changed-files 规则")

    template = github / "PULL_REQUEST_TEMPLATE.md"
    if not template.is_file():
        errors.append("缺少 PR 模板")
    elif "任务 ID" not in template.read_text():
        errors.append("PR 模板缺少任务 ID 字段")

    mirror = github / "actions" / "setup-android" / "codetree-mirrors.gradle"
    if not mirror.is_file():
        errors.append("缺少复合动作镜像初始化脚本")
    else:
        errors.extend(validate_mirror_text(mirror.read_text()))

    example = github / "CODEOWNERS.example"
    if not example.is_file():
        errors.append("缺少 CODEOWNERS.example")
    owners = github / "CODEOWNERS"
    if owners.is_file() and PLACEHOLDER_RE.search(owners.read_text()):
        errors.append("CODEOWNERS 仍保留组织或团队占位")

    for markdown in sorted(root.rglob("*.md")):
        scan_text = policy_scan_text(markdown.read_text())
        for label, pattern in FORBIDDEN_PATTERNS:
            for match in re.finditer(pattern, scan_text):
                line = scan_text.count("\n", 0, match.start()) + 1
                errors.append(
                    f"{markdown.relative_to(root)} 第 {line} 行命中 {label}"
                )

    return errors


def self_test() -> None:
    """用固定正反例验证工作流校验器，覆盖 YAML 1.1 的 on: 陷阱。"""
    good = {
        "on": {
            "pull_request": {"branches": ["main"], "paths": ["app/**"]},
            "push": {"branches": ["main"], "paths": ["app/**"]},
        },
        "permissions": {"contents": "read"},
        "concurrency": {"group": "x-${{ github.ref }}", "cancel-in-progress": True},
        "jobs": {
            "build": {
                "runs-on": "ubuntu-latest",
                "timeout-minutes": 10,
                "steps": [{"uses": "actions/checkout@v4"}],
            }
        },
    }
    assert validate_workflow(Path("android.yml"), good, ROOT) == []

    legacy = dict(good)
    legacy[True] = legacy.pop("on")
    assert triggers(legacy) == good["on"]
    assert validate_workflow(Path("android.yml"), legacy, ROOT) == []

    missing_timeout = dict(good)
    missing_timeout["jobs"] = {
        "build": {"runs-on": "ubuntu-latest", "steps": [{"uses": "actions/checkout@v4"}]}
    }
    assert any("timeout-minutes" in error for error in validate_workflow(Path("android.yml"), missing_timeout, ROOT))

    bad_needs = dict(good)
    bad_needs["jobs"] = dict(good["jobs"])
    bad_needs["jobs"]["build"] = dict(good["jobs"]["build"], needs=["ghost"])
    assert any("needs 引用不存在" in error for error in validate_workflow(Path("android.yml"), bad_needs, ROOT))

    no_paths = {
        "on": {"push": {"branches": ["main"]}},
        "permissions": {"contents": "read"},
        "concurrency": {"group": "x", "cancel-in-progress": True},
        "jobs": {"build": {"runs-on": "ubuntu-latest", "timeout-minutes": 5, "steps": [{"uses": "actions/checkout@v4"}]}},
    }
    assert any("缺少 paths" in error for error in validate_workflow(Path("android.yml"), no_paths, ROOT))

    bad_action = dict(good)
    bad_action["jobs"] = {
        "build": {
            "runs-on": "ubuntu-latest",
            "timeout-minutes": 5,
            "steps": [{"uses": "./.github/actions/not-here"}],
        }
    }
    assert any("不存在的本地动作" in error for error in validate_workflow(Path("android.yml"), bad_action, ROOT))

    bad_script = dict(good)
    bad_script["jobs"] = {
        "build": {
            "runs-on": "ubuntu-latest",
            "timeout-minutes": 5,
            "steps": [{"run": "python3 scripts/ghost.py"}],
        }
    }
    assert any("不存在的脚本" in error for error in validate_workflow(Path("android.yml"), bad_script, ROOT))

    no_permissions = dict(good)
    no_permissions.pop("permissions")
    assert any("permissions" in error for error in validate_workflow(Path("android.yml"), no_permissions, ROOT))

    target_event = dict(good)
    target_event["on"] = {"pull_request_target": {"branches": ["main"]}}
    assert any("pull_request_target" in error for error in validate_workflow(Path("ci.yml"), target_event, ROOT))

    good_action = {
        "inputs": {"java-version": {"description": "JDK 版本", "default": "17"}},
        "runs": {
            "using": "composite",
            "steps": [
                {"uses": "actions/setup-java@v4"},
                {"run": "echo ok", "shell": "bash"},
            ],
        },
    }
    assert validate_action(Path("action.yml"), good_action) == []

    bad_action_data = {
        "runs": {"using": "composite", "steps": [{"run": "echo ok"}]},
    }
    assert any("缺少 shell" in error for error in validate_action(Path("action.yml"), bad_action_data))

    full_mirror = "\n".join(MIRROR_REPOSITORIES)
    assert validate_mirror_text(full_mirror) == []
    assert len(validate_mirror_text(MIRROR_REPOSITORIES[0])) == 1
    assert len(validate_mirror_text("")) == 2

    # 附录分区：正文区完整保留，附录区（含工具源码里的约束文本字面量）不进入扫描
    noisy = "正常说明文字" + APPENDIX_START + "不得以里程碑压力为由绕过。若需要回头再说。"
    scanned = policy_scan_text(noisy)
    assert scanned == "正常说明文字"
    assert not [
        (label, pattern)
        for label, pattern in FORBIDDEN_PATTERNS
        if re.search(pattern, scanned)
    ]
    assert [
        (label, pattern)
        for label, pattern in FORBIDDEN_PATTERNS
        if re.search(pattern, noisy)
    ]  # 全文确实含会被命中的字面量，证明分区不是空操作
    assert policy_scan_text("无标记的普通文档") == "无标记的普通文档"


def main() -> int:
    parser = argparse.ArgumentParser(description="校验 GitHub Actions 工作流")
    parser.add_argument("--self-test", action="store_true", help="只运行自测")
    args = parser.parse_args()

    self_test()
    print("Self-test: OK")
    if args.self_test:
        return 0

    workflow_files = sorted(WORKFLOWS.glob("*.yml"))
    action_files = sorted(ACTIONS_DIR.glob("*/action.yml"))
    if not workflow_files:
        print("Validation: FAILED")
        print("- 未找到任何工作流文件")
        return 1

    errors: list[str] = []
    job_count = 0
    for workflow in workflow_files:
        data = yaml.safe_load(workflow.read_text())
        for error in validate_workflow(workflow, data, ROOT):
            errors.append(f"{workflow.name}: {error}")
        if isinstance(data, dict) and isinstance(data.get("jobs"), dict):
            job_count += len(data["jobs"])

    for action in action_files:
        data = yaml.safe_load(action.read_text())
        for error in validate_action(action, data):
            errors.append(f"{action.relative_to(ROOT)}: {error}")

    for error in validate_policy(ROOT):
        errors.append(f"策略: {error}")

    unpinned = 0
    for workflow in workflow_files:
        for uses in USES_RE.findall(workflow.read_text()):
            if "@" in uses and not re.search(r"@[0-9a-f]{40}$", uses):
                unpinned += 1

    print("Workflows:", len(workflow_files))
    print("Jobs:", job_count)
    print("Composite actions:", len(action_files))
    print("Third-party uses not pinned to a commit SHA:", unpinned)
    print("Policy checks: labeler / PR template / CODEOWNERS / mirrors / forbidden phrases (body only, appendix excluded) / workflows")
    if errors:
        print("Validation: FAILED")
        for error in errors:
            print("- " + error)
        return 1

    print("Validation: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
