#!/usr/bin/env python3
"""生成并校验 CodeTree 单文件全量合集（源文档正文 + 工程配置与脚本附录）。"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
import zipfile
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MERGED = ROOT / "CodeTree-项目文档合集.md"
ARCHIVE = ROOT / "CodeTree-合并文档包.zip"
MIRROR_ARCHIVE = ROOT.parent / "CodeTree-合并文档包.zip"

SOURCE_ORDER = [
    ("docs/00-EXECUTIVE-SUMMARY.md", "执行摘要与优化说明"),
    ("README.md", "项目总览"),
    ("AGENTS.md", "Agent 强制约束"),
    ("docs/01-PRD.md", "产品需求"),
    ("docs/02-ARCHITECTURE.md", "技术架构"),
    ("docs/03-AGENT-COLLABORATION.md", "AI Agent 协作规范"),
    ("docs/04-WBS.md", "工作分解结构"),
    ("docs/05-DATA-CONTENT-PLUGIN.md", "数据、内容与插件规范"),
    ("docs/06-TESTING-ACCEPTANCE.md", "测试与验收"),
    ("docs/07-SECURITY-RELEASE.md", "安全、隐私与发布"),
    ("docs/08-GITHUB-WORKFLOW.md", "GitHub 工作流效率规范"),
    ("docs/09-VISUAL-DESIGN.md", "视觉与美术设计规范"),
    ("docs/10-UPDATE-POLICY.md", "每次更新条例"),
    ("docs/11-PROMOTION-DESIGN.md", "宣传设计规范"),
    ("docs/templates/AGENT_DISPATCH.md", "Agent 派发模板"),
    ("docs/templates/TASK_CARD.md", "任务卡模板"),
    ("docs/templates/HANDOFF.md", "交接模板"),
    ("docs/templates/ADR.md", "架构决策模板"),
]

# 收录进附录与压缩包的工程产物：(相对路径, 代码围栏语言, 作用说明)
ENGINEERING_FILES = [
    (".github/workflows/ci.yml", "yaml", "轻量校验工作流"),
    (".github/workflows/android.yml", "yaml", "Android 源码构建工作流"),
    (".github/workflows/nightly.yml", "yaml", "夜间全量与合集时效性校验"),
    (".github/workflows/release.yml", "yaml", "发布制品与校验和工作流"),
    (".github/actions/setup-android/action.yml", "yaml", "JDK/Gradle/SDK 复合动作"),
    (".github/actions/setup-android/codetree-mirrors.gradle", "gradle", "可选国内镜像初始化脚本"),
    (".github/labeler.yml", "yaml", "PR 路径标签规则"),
    (".github/CODEOWNERS.example", "text", "代码所有者配置示例"),
    (".github/PULL_REQUEST_TEMPLATE.md", "markdown", "拉取请求模板"),
    ("scripts/merge_docs.py", "python", "合集生成与校验脚本"),
    ("scripts/validate_workflows.py", "python", "工作流结构与策略校验脚本"),
    ("scripts/validate_design.py", "python", "视觉资产与更新条例校验脚本"),
    ("scripts/requirements-ci.txt", "text", "CI Python 依赖锁定"),
    ("design/tokens.json", "json", "编程猫主题猫视觉设计令牌"),
    ("design/icons/codetree-icon.svg", "svg", "黑灰渐变猫主题主图标"),
    ("design/icons/codetree-icon-foreground.svg", "svg", "Android 自适应图标前景"),
    ("design/icons/codetree-icon-mono.svg", "svg", "Android 单色主题图标"),
    ("design/graph/knowledge-cloud.svg", "svg", "知识树云图扩散示意"),
    ("design/visual-language.html", "html", "可交互视觉规范预览页"),
    ("design/mascot/programming-cat.svg", "svg", "主题猫「小码」矢量母版"),
    ("design/mascot/programming-cat-sheet.svg", "svg", "主题猫角色设定板"),
]

# 二进制预览只进入压缩包，不嵌入 Markdown 代码围栏。
BINARY_FILES = [
    "design/previews/icon-1024.png",
    "design/previews/icon-192.png",
    "design/previews/icon-48.png",
    "design/previews/knowledge-cloud.png",
    "design/previews/programming-cat-sheet.png",
]

EXPECTED_VERSION = "v0.7.0"
EXPECTED_TASKS = 97
EXPECTED_SECTIONS = len(SOURCE_ORDER)

# 标记以拼接方式构造：附录会收录本脚本源码，完整字面量若出现在源码中，
# 会让「标记唯一」校验把源码字符串误当成第二个分隔符。
_MARK_PREFIX = "<!-- CODETREE-"
APPENDIX_START = _MARK_PREFIX + "APPENDIX:START -->"
APPENDIX_END = _MARK_PREFIX + "APPENDIX:END -->"
APPENDIX_TITLE = "附录 A. 工程与设计资产（按原样合并）"

LINK_RE = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
ANCHOR_ID_RE = re.compile(r'<a id="([^"]+)"></a>')
REF_RE = re.compile(r"\]\(#(src-[^)]+)\)")
TASK_ID_RE = re.compile(r"CT-\d{3}-\d{2}")
TASK_ROW_RE = re.compile(r"^\| (CT-\d{3}-\d{2}) \|", re.M)
RELATIVE_LINK_RE = re.compile(r"\]\((?!#|https?://|mailto:)[^)]+\)")

FORBIDDEN_PATTERNS = [
    ("动机条件式禁止", r"不得(?:为了|为).{0,20}而"),
    ("动机条件式禁止", r"不能(?:为了|为).{0,20}而"),
    ("动机条件式禁止", r"不为.{0,20}而"),
    ("理由条件式禁止", r"不得(?:以|借).{0,20}为由"),
    ("理由条件式禁止", r"不能(?:以|借).{0,20}为由"),
    ("开脱措辞", r"用户多半|省事心理|用户要求的审批闸"),
    (
        "延后转嫁措辞",
        r"若需要|如需|回头再说|这步看你|你定|待你|你告诉我|请你确认|你这边|要不要我|告诉我即可|不阻塞当前",
    ),
]


def slug(path_text: str) -> str:
    """把源文档路径转换成稳定的章节锚点。"""
    return "src-" + re.sub(r"[^a-z0-9]+", "-", path_text.lower()).strip("-")


def resolve_relative(source_path: str, target: str) -> str | None:
    """把 Markdown 链接目标解析为项目根目录内的相对路径。"""
    path_part = target.split("#")[0]
    if not path_part:
        return None
    candidate = (ROOT / Path(source_path).parent / path_part).resolve()
    try:
        return str(candidate.relative_to(ROOT))
    except ValueError:
        return None


def rewrite_links(source_path: str, text: str, anchors: dict[str, str]) -> str:
    """把跨文档相对链接重写为合集内锚点。"""
    def replace(match: re.Match[str]) -> str:
        label = match.group(1)
        target = match.group(2)
        if target.startswith(("http://", "https://", "mailto:", "#")):
            return match.group(0)
        relative = resolve_relative(source_path, target)
        if relative is None or relative not in anchors:
            return match.group(0)
        return "[" + label + "](#" + anchors[relative] + ")"

    return LINK_RE.sub(replace, text)


def lower_headings(text: str) -> str:
    """把源文档标题整体下移两级，代码块内的 # 保持原样。"""
    output: list[str] = []
    in_fence = False
    for line in text.splitlines():
        if line.startswith("```") or line.startswith("~~~"):
            in_fence = not in_fence
            output.append(line)
            continue
        match = re.match(r"^(#{1,6})(\s+)(.*)$", line) if not in_fence else None
        if match:
            level = min(len(match.group(1)) + 2, 6)
            output.append("#" * level + match.group(2) + match.group(3))
        else:
            output.append(line)
    return "\n".join(output)


def fence_for(content: str) -> str:
    """按内容中最长反引号串自适应选择围栏长度，至少四个反引号。"""
    longest = max((len(run) for run in re.findall(r"`+", content)), default=0)
    return "`" * max(4, longest + 1)


def appendix_block(rel_path: str, lang: str) -> str:
    """把单个工程文件按原样包裹成代码围栏块。"""
    content = (ROOT / rel_path).read_text()
    fence = fence_for(content)
    body = content if content.endswith("\n") else content + "\n"
    return f"{fence}{lang}\n{body}{fence}"


def build_appendix() -> str:
    """生成工程产物附录：收录清单 + 每个文件全文。"""
    parts = [
        APPENDIX_START,
        "",
        f"## {APPENDIX_TITLE}",
        "",
        "> 本节把仓库内的 GitHub 工作流、复合动作、协作配置、校验脚本、设计令牌、SVG 与 HTML 规范页按原样合并进单文件合集，便于一次评审全部文本产物。",
        "> 代码块内的示例字符串、正则表达式、占位符和约束用例属于工具或设计源码，不参与正文的锚点、任务编号与约束文本校验；事实来源仍是各源文件。PNG 预览作为二进制资产进入压缩包。", 
        "",
        "### A.1 收录清单",
        "",
        "| 序号 | 源文件 | 语言 | 作用 |",
        "| --- | --- | --- | --- |",
    ]
    for index, (rel_path, lang, desc) in enumerate(ENGINEERING_FILES, 1):
        parts.append(f"| A-{index} | `{rel_path}` | {lang} | {desc} |")
    parts += ["", "### A.2 文件全文", ""]
    for index, (rel_path, lang, desc) in enumerate(ENGINEERING_FILES, 1):
        parts += [
            f"#### A.2.{index} `{rel_path}`",
            "",
            f"> 作用：{desc}",
            "",
            appendix_block(rel_path, lang),
            "",
        ]
    parts += [APPENDIX_END, ""]
    return "\n".join(parts)


def build_document() -> str:
    """按固定顺序生成单文件全量合集。"""
    anchors = {path_text: slug(path_text) for path_text, _ in SOURCE_ORDER}
    parts = [
        "# 编程猫 CodeTree 项目文档合集",
        "",
        f"> 本合集由 {len(SOURCE_ORDER)} 份源文档、{len(ENGINEERING_FILES)} 份文本工程与设计资产完整合并而成：正文保留全部内容，跨文档链接转换为章节锚点；附录按原样收录工作流、脚本、设计令牌、主题猫 SVG 和 HTML 规范页。{len(BINARY_FILES)} 份 PNG 预览随压缩包交付。源文件是事实来源，合集由脚本生成，手工编辑会在下次生成时被覆盖。",
        "",
        "| 项目 | 内容 |",
        "| --- | --- |",
        f"| 文档版本 | {EXPECTED_VERSION} 编程猫主题猫版 |",
        f"| 源文档 | {len(SOURCE_ORDER)} 份 |",
        f"| 工程与设计附录 | {len(ENGINEERING_FILES)} 份文本文件（按原样合并） |",
        f"| 二进制视觉预览 | {len(BINARY_FILES)} 份 PNG（随压缩包交付） |",
        f"| WBS 任务 | {EXPECTED_TASKS} 个唯一编号 |",
        "| 推荐阅读 | 执行摘要 → 视觉规范 → 更新条例 → 协作规范 → WBS → 工程与设计附录 |",
        "| 生成方式 | `python3 scripts/merge_docs.py` |",
        "| 校验方式 | `python3 scripts/merge_docs.py --check` |",
        "| 使用建议 | 把本文件作为单一评审入口；修改源文档或工程文件后同步更新并重生成 |",
        "",
        "## 目录",
        "",
    ]

    for index, (path_text, title) in enumerate(SOURCE_ORDER, 1):
        parts.append(f"{index}. [{path_text} — {title}](#{anchors[path_text]})")
    parts.append(
        f"{EXPECTED_SECTIONS + 1}. {APPENDIX_TITLE}（{len(ENGINEERING_FILES)} 份工程文件，见 A.2 全文）"
    )
    parts.append("")

    for index, (path_text, title) in enumerate(SOURCE_ORDER, 1):
        source = ROOT / path_text
        body = lower_headings(rewrite_links(path_text, source.read_text(), anchors))
        parts.extend(
            [
                f'<a id="{anchors[path_text]}"></a>',
                "",
                f"## {index}. {path_text} — {title}",
                "",
                f"> 源文件：`{path_text}`",
                "",
                body.strip(),
                "",
                "---",
                "",
            ]
        )

    parts.append(build_appendix())
    return "\n".join(parts).rstrip() + "\n"


def freshness_errors(expected: str, actual: str) -> list[str]:
    """比对内存重建的合集与磁盘文件，防止源文档或工程文件漂移形成假阴性。"""
    if expected == actual:
        return []
    return ["单文件合集与源文档或工程文件不一致：运行 python3 scripts/merge_docs.py 重新生成"]


def validate_text(
    text: str,
    expected_sections: int,
    expected_tasks: int,
    valid_anchors: set[str] | None = None,
) -> list[str]:
    """校验正文部分；工程附录在标记之后，按原样收录不参与正文规则。"""
    errors: list[str] = []
    anchors = valid_anchors or {slug(path_text) for path_text, _ in SOURCE_ORDER}
    body = text.split(APPENDIX_START, 1)[0]

    id_list = ANCHOR_ID_RE.findall(body)
    ids = set(id_list)
    duplicate_ids = [key for key, count in Counter(id_list).items() if count > 1]
    if len(id_list) != expected_sections:
        errors.append(f"章节锚点数量错误：期望 {expected_sections}，实际 {len(id_list)}")
    if len(ids) != expected_sections:
        errors.append(f"唯一章节锚点数量错误：期望 {expected_sections}，实际 {len(ids)}")
    if duplicate_ids:
        errors.append("重复章节锚点：" + ", ".join(duplicate_ids))
    missing_ids = anchors - ids
    if missing_ids:
        errors.append("缺失章节锚点：" + ", ".join(sorted(missing_ids)))

    refs = set(REF_RE.findall(body))
    missing_refs = refs - ids
    if missing_refs:
        errors.append("失效内部引用：" + ", ".join(sorted(missing_refs)))

    remaining_relative = RELATIVE_LINK_RE.findall(body)
    if remaining_relative:
        errors.append("未转换相对链接：" + ", ".join(remaining_relative[:5]))

    task_ids = set(TASK_ID_RE.findall(body))
    defined_rows = TASK_ROW_RE.findall(body)
    duplicate_rows = [key for key, count in Counter(defined_rows).items() if count > 1]
    if len(task_ids) != expected_tasks:
        errors.append(f"唯一任务编号数量错误：期望 {expected_tasks}，实际 {len(task_ids)}")
    if len(defined_rows) != expected_tasks:
        errors.append(f"任务定义行数量错误：期望 {expected_tasks}，实际 {len(defined_rows)}")
    if duplicate_rows:
        errors.append("重复任务定义：" + ", ".join(duplicate_rows))

    for label, pattern in FORBIDDEN_PATTERNS:
        for match in re.finditer(pattern, body):
            line_number = body.count("\n", 0, match.start()) + 1
            errors.append(f"约束文本命中 {label}：第 {line_number} 行")

    return errors


def validate_appendix(text: str, blocks: dict[str, str] | None = None) -> list[str]:
    """校验工程附录的标记、清单、文件标题与全文一致性。"""
    errors: list[str] = []
    if text.count(APPENDIX_START) != 1:
        errors.append("附录起始标记缺失或重复")
    if text.count(APPENDIX_END) != 1:
        errors.append("附录结束标记缺失或重复")
    if (
        APPENDIX_START in text
        and APPENDIX_END in text
        and text.index(APPENDIX_START) > text.index(APPENDIX_END)
    ):
        errors.append("附录标记顺序颠倒")
    if f"## {APPENDIX_TITLE}" not in text:
        errors.append("附录标题缺失")

    appendix = (
        text.split(APPENDIX_START, 1)[1].split(APPENDIX_END, 1)[0]
        if APPENDIX_START in text
        else ""
    )
    resolved = blocks or {
        rel_path: appendix_block(rel_path, lang)
        for rel_path, lang, _ in ENGINEERING_FILES
    }
    for index, (rel_path, _lang, _desc) in enumerate(ENGINEERING_FILES, 1):
        if f"#### A.2.{index} `{rel_path}`" not in appendix:
            errors.append(f"附录缺少文件标题：{rel_path}")
        if f"| A-{index} | `{rel_path}` |" not in appendix:
            errors.append(f"附录清单缺少条目：{rel_path}")
        if resolved[rel_path] not in appendix:
            errors.append(f"附录文件全文与源文件不一致：{rel_path}")
    return errors


def self_test() -> None:
    """用固定正反例验证纯函数与校验器。"""
    assert slug("docs/01-PRD.md") == "src-docs-01-prd-md"

    sample_anchors = {"docs/01-PRD.md": "src-docs-01-prd-md"}
    assert rewrite_links(
        "README.md", "[PRD](docs/01-PRD.md)", sample_anchors
    ) == "[PRD](#src-docs-01-prd-md)"
    assert rewrite_links(
        "README.md", "[X](missing.md)", sample_anchors
    ) == "[X](missing.md)"

    heading_input = "# Title\n```\n# inside\n```\n## Sub"
    heading_expected = "### Title\n```\n# inside\n```\n#### Sub"
    assert lower_headings(heading_input) == heading_expected

    assert fence_for("plain text") == "````"
    assert fence_for("has ``` inside") == "````"
    assert len(fence_for("has ````` inside")) == 6

    good = '<a id="src-a"></a>\n\n[Link](#src-a)\n\n| CT-000-01 | 示例 |\n'
    assert validate_text(good, 1, 1, {"src-a"}) == []
    assert freshness_errors("a", "a") == []
    assert any("不一致" in error for error in freshness_errors("a", "b"))

    bad_anchor = '<a id="src-a"></a>\n\n[Link](#src-missing)\n'
    assert any("失效内部引用" in error for error in validate_text(bad_anchor, 1, 0, {"src-a"}))

    bad_relative = '<a id="src-a"></a>\n\n[Link](docs/missing.md)\n'
    assert any("未转换相对链接" in error for error in validate_text(bad_relative, 1, 0, {"src-a"}))

    bad_escape = '<a id="src-a"></a>\n\n不得以里程碑压力为由绕过。\n'
    assert any("约束文本命中" in error for error in validate_text(bad_escape, 1, 0, {"src-a"}))

    # 附录里的工具源码字面量不得污染正文校验
    polluted = (
        good
        + APPENDIX_START
        + '\n<a id="src-a"></a> | CT-000-01 | 不得以里程碑压力为由绕过。 [X](docs/x.md)\n'
        + APPENDIX_END
    )
    assert validate_text(polluted, 1, 1, {"src-a"}) == []

    fake_blocks = {
        rel_path: f"BLOCK-{index}"
        for index, (rel_path, _, _) in enumerate(ENGINEERING_FILES, 1)
    }
    good_appendix = APPENDIX_START + "\n" + f"## {APPENDIX_TITLE}\n"
    for index, (rel_path, _, _) in enumerate(ENGINEERING_FILES, 1):
        good_appendix += (
            f"\n#### A.2.{index} `{rel_path}`\n"
            f"| A-{index} | `{rel_path}` |\n"
            f"{fake_blocks[rel_path]}\n"
        )
    good_appendix += APPENDIX_END
    assert validate_appendix(good_appendix, fake_blocks) == []
    assert any(
        "起始标记" in error
        for error in validate_appendix(good_appendix.replace(APPENDIX_START, "X"), fake_blocks)
    )
    first = ENGINEERING_FILES[0][0]
    assert any(
        "全文与源文件不一致" in error
        for error in validate_appendix(
            good_appendix.replace(fake_blocks[first], "TAMPERED"), fake_blocks
        )
    )


def extra_files() -> list[tuple[Path, str]]:
    """列出压缩包中除源文档与合集之外的工程配置，与附录清单同源。"""
    items: list[tuple[Path, str]] = []
    for rel_path, _, _ in ENGINEERING_FILES:
        arcname = (
            "github/" + rel_path[len(".github/"):]
            if rel_path.startswith(".github/")
            else rel_path
        )
        items.append((ROOT / rel_path, arcname))
    items.extend((ROOT / rel_path, rel_path) for rel_path in BINARY_FILES)
    return items


def write_archive() -> None:
    """生成包含合集、源文档、工作流与校验脚本的压缩包。"""
    with zipfile.ZipFile(ARCHIVE, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.write(MERGED, MERGED.name)
        for path_text, _ in SOURCE_ORDER:
            archive.write(ROOT / path_text, "sources/" + path_text)
        for source, arcname in extra_files():
            archive.write(source, arcname)
    shutil.copy2(ARCHIVE, MIRROR_ARCHIVE)


def check_archive() -> list[str]:
    """校验压缩包内容和 ZIP 完整性。"""
    if not ARCHIVE.exists():
        return [f"压缩包不存在：{ARCHIVE}"]

    errors: list[str] = []
    with zipfile.ZipFile(ARCHIVE) as archive:
        damaged = archive.testzip()
        if damaged:
            errors.append(f"压缩包成员损坏：{damaged}")
        names = set(archive.namelist())

    if MIRROR_ARCHIVE.exists() and MIRROR_ARCHIVE.read_bytes() != ARCHIVE.read_bytes():
        errors.append("工作区副本与主压缩包内容不一致")

    expected = {MERGED.name}
    expected.update("sources/" + path_text for path_text, _ in SOURCE_ORDER)
    expected.update(arcname for _, arcname in extra_files())
    missing = expected - names
    if missing:
        errors.append("压缩包缺失成员：" + ", ".join(sorted(missing)))
    return errors


def validate_version() -> list[str]:
    """锁定本轮文档、设计令牌和变更记录的版本一致性。"""
    errors: list[str] = []
    for rel_path in ("README.md", "docs/00-EXECUTIVE-SUMMARY.md"):
        if f"文档版本：{EXPECTED_VERSION}" not in (ROOT / rel_path).read_text():
            errors.append(f"{rel_path} 文档版本不是 {EXPECTED_VERSION}")
    summary = (ROOT / "docs/00-EXECUTIVE-SUMMARY.md").read_text()
    if f"| {EXPECTED_VERSION} |" not in summary:
        errors.append(f"执行摘要缺少 {EXPECTED_VERSION} 变更记录")
    return errors


def ensure_sources_exist() -> None:
    for path_text, _ in SOURCE_ORDER:
        source = ROOT / path_text
        if not source.is_file():
            raise FileNotFoundError(f"源文档不存在：{source}")
    for rel_path, _, _ in ENGINEERING_FILES:
        if not (ROOT / rel_path).is_file():
            raise FileNotFoundError(f"工程或设计文本文件不存在：{ROOT / rel_path}")
    for rel_path in BINARY_FILES:
        if not (ROOT / rel_path).is_file():
            raise FileNotFoundError(f"视觉预览文件不存在：{ROOT / rel_path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="生成并校验 CodeTree 项目文档合集")
    parser.add_argument("--check", action="store_true", help="只校验现有合集和压缩包，不写入文件")
    parser.add_argument("--self-test", action="store_true", help="只运行脚本自测")
    args = parser.parse_args()

    self_test()
    print("Self-test: OK")
    if args.self_test:
        return 0

    ensure_sources_exist()

    if args.check:
        text = MERGED.read_text()
        errors = validate_version()
        errors.extend(validate_text(text, EXPECTED_SECTIONS, EXPECTED_TASKS))
        errors.extend(validate_appendix(text))
        errors.extend(freshness_errors(build_document(), text))
        errors.extend(check_archive())
        if errors:
            print("Validation: FAILED")
            for error in errors:
                print("- " + error)
            return 1
        print("Merged file:", MERGED)
        print("Archive:", ARCHIVE)
        print("Source docs:", EXPECTED_SECTIONS)
        print("Engineering appendix:", len(ENGINEERING_FILES))
        print("WBS tasks:", EXPECTED_TASKS)
        print("Validation: OK")
        return 0

    text = build_document()
    errors = validate_version()
    errors.extend(validate_text(text, EXPECTED_SECTIONS, EXPECTED_TASKS))
    errors.extend(validate_appendix(text))
    if errors:
        print("Validation: FAILED")
        for error in errors:
            print("- " + error)
        return 1

    MERGED.write_text(text)
    write_archive()
    errors = validate_version()
    errors.extend(validate_text(MERGED.read_text(), EXPECTED_SECTIONS, EXPECTED_TASKS))
    errors.extend(validate_appendix(MERGED.read_text()))
    errors.extend(check_archive())
    if errors:
        print("Validation: FAILED")
        for error in errors:
            print("- " + error)
        return 1

    print("Merged file:", MERGED)
    print("Merged lines:", len(text.splitlines()))
    print("Archive:", ARCHIVE, "bytes:", ARCHIVE.stat().st_size)
    print("Source docs:", EXPECTED_SECTIONS)
    print("Engineering appendix:", len(ENGINEERING_FILES))
    print("WBS tasks:", EXPECTED_TASKS)
    print("Validation: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
