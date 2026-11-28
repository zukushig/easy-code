#!/usr/bin/env python3
"""CodeTree 内容与分发产物验收检查（QA 侧独立检查，补充 tools/content 校验器）。

检查范围：
  1. content/packs：目录结构、manifest 必填字段、ID 规则与全局唯一、边引用、prerequisite 环、
     双语完整性、topic 关联数量、示例 annotation 偏移；
  2. content/dist：codepack 数量与命名、sha256sums.txt 与实际产物一致、catalog.json 字段与
     产物一致、zip 路径安全、manifest.files[] 与 zip 内容一致；
  3. content/index.json：packCount 与产物存在性。

用法：
  python3 tools/verify/check_content.py            # 完整检查
  python3 tools/verify/check_content.py --json     # 机器可读输出
  python3 tools/verify/check_content.py --selftest # 纯函数自测（不读取仓库状态）

退出码：0 = 无 error；1 = 有 error。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKS_DIR = ROOT / "content" / "packs"
DIST_DIR = ROOT / "content" / "dist"
INDEX_FILE = ROOT / "content" / "index.json"

EXPECTED_PACK_COUNT = 12
REQUIRED_PACK_FILES = (
    "manifest.json",
    "LICENSE",
    "content/topics.json",
    "content/edges.json",
    "content/terms.json",
    "content/examples.json",
)
REQUIRED_MANIFEST_FIELDS = (
    "schemaVersion",
    "packId",
    "packType",
    "version",
    "title",
    "defaultLocale",
    "locales",
    "minAppVersion",
    "pluginApiVersion",
    "entry",
    "dependencies",
    "files",
)
EDGE_TYPES = ("prerequisite", "related", "appliedIn", "confusedWith")
LOCALES = ("zh-CN", "en")
ID_RE = re.compile(r"^[a-z0-9]+(?:[.-][a-z0-9]+)*$")
SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

ZIP_MAX_ENTRIES = 2000
ZIP_MAX_FILE_BYTES = 16 * 1024 * 1024
ZIP_MAX_TOTAL_BYTES = 64 * 1024 * 1024
ZIP_MAX_RATIO = 100.0


# ---------------------------------------------------------------------------
# 纯函数（供自测复用）
# ---------------------------------------------------------------------------
def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def localized_ok(value) -> bool:
    """本地化文本/列表：zh-CN 与 en 都必须非空。"""
    if not isinstance(value, dict):
        return False
    for locale in LOCALES:
        item = value.get(locale)
        if isinstance(item, str):
            if not item.strip():
                return False
        elif isinstance(item, list):
            if not item or any(not isinstance(entry, str) or not entry.strip() for entry in item):
                return False
        else:
            return False
    return True


def find_prerequisite_cycles(nodes: list[str], edges: list[dict]) -> list[list[str]]:
    """在 prerequisite 边上做 DFS 找环；返回环的节点序列（不含重复首节点）。"""
    graph: dict[str, list[str]] = {node: [] for node in nodes}
    for edge in edges:
        if edge.get("type") == "prerequisite":
            source, target = edge.get("source"), edge.get("target")
            if source in graph and target is not None:
                graph[source].append(target)
    state: dict[str, int] = {}  # 0 未访问 / 1 访问中 / 2 完成
    stack: list[str] = []
    cycles: list[list[str]] = []

    def dfs(node: str) -> None:
        state[node] = 1
        stack.append(node)
        for nxt in graph.get(node, []):
            if nxt not in state or state[nxt] == 0:
                dfs(nxt)
            elif state[nxt] == 1:
                start = stack.index(nxt)
                cycles.append(stack[start:])
        stack.pop()
        state[node] = 2

    sys.setrecursionlimit(max(2000, len(nodes) * 4 + 100))
    for node in nodes:
        if state.get(node, 0) == 0:
            dfs(node)
    return cycles


def parse_sha256sums(text: str) -> tuple[dict[str, str], list[str]]:
    """解析 `sha256  filename` 清单；返回 (映射, 错误)。"""
    mapping: dict[str, str] = {}
    errors: list[str] = []
    for number, raw_line in enumerate(text.splitlines(), 1):
        line = raw_line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) != 2:
            errors.append(f"sha256sums 第 {number} 行格式非法：{raw_line!r}")
            continue
        digest, name = parts
        if not SHA256_RE.match(digest):
            errors.append(f"sha256sums 第 {number} 行哈希非法：{name}")
            continue
        if name in mapping:
            errors.append(f"sha256sums 文件名重复：{name}")
            continue
        mapping[name] = digest
    return mapping, errors


def zip_safety_violations(archive: zipfile.ZipFile) -> list[str]:
    """检查 zip 条目：数量/大小/压缩比/路径穿越/绝对路径/符号链接。"""
    problems: list[str] = []
    infos = archive.infolist()
    if len(infos) > ZIP_MAX_ENTRIES:
        problems.append(f"条目数超限：{len(infos)} > {ZIP_MAX_ENTRIES}")
    total = 0
    for info in infos:
        name = info.filename
        if name.startswith("/") or re.match(r"^[A-Za-z]:", name):
            problems.append(f"绝对路径：{name}")
        parts = [part for part in name.replace("\\", "/").split("/") if part]
        if ".." in parts:
            problems.append(f"路径穿越：{name}")
        if "\\" in name:
            problems.append(f"反斜杠路径：{name}")
        mode = (info.external_attr >> 16) & 0o170000
        if mode == 0o120000:
            problems.append(f"符号链接条目：{name}")
        if info.file_size > ZIP_MAX_FILE_BYTES:
            problems.append(f"单文件超限：{name}")
        total += info.file_size
        if info.compress_size > 0 and info.file_size / info.compress_size > ZIP_MAX_RATIO:
            problems.append(f"压缩比超限：{name}")
    if total > ZIP_MAX_TOTAL_BYTES:
        problems.append(f"解压总量超限：{total} > {ZIP_MAX_TOTAL_BYTES}")
    return problems


def verify_pack_manifest(manifest: dict, pack_dir_name: str) -> list[str]:
    errors: list[str] = []
    for field in REQUIRED_MANIFEST_FIELDS:
        if field not in manifest:
            errors.append(f"manifest 缺少字段 {field}")
    if manifest.get("packId") != pack_dir_name:
        errors.append(f"packId 与目录名不一致：{manifest.get('packId')!r} != {pack_dir_name!r}")
    if not SEMVER_RE.match(str(manifest.get("version", ""))):
        errors.append(f"version 不是合法 SemVer：{manifest.get('version')!r}")
    if not localized_ok(manifest.get("title")):
        errors.append("title 双语不完整")
    if not isinstance(manifest.get("schemaVersion"), int) or isinstance(manifest.get("schemaVersion"), bool):
        errors.append("schemaVersion 必须是整数")
    if not isinstance(manifest.get("pluginApiVersion"), int) or isinstance(manifest.get("pluginApiVersion"), bool):
        errors.append("pluginApiVersion 必须是整数")
    return errors


# ---------------------------------------------------------------------------
# 仓库检查
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


def load_json(report: Report, path: Path, label: str):
    if not path.is_file():
        report.error(f"{label} 缺少文件：{path.relative_to(ROOT)}")
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        report.error(f"{label} JSON 无法解析：{exc}")
        return None


def collect_packs(report: Report):
    """读取所有内容包，返回 {packId: {manifest, topics, edges, terms, examples}}。"""
    packs: dict[str, dict] = {}
    if not PACKS_DIR.is_dir():
        report.error("缺少 content/packs 目录")
        return packs
    for pack_dir in sorted(PACKS_DIR.iterdir()):
        if not pack_dir.is_dir() or pack_dir.name.startswith("."):
            continue
        for relative in REQUIRED_PACK_FILES:
            if not (pack_dir / relative).is_file():
                report.error(f"{pack_dir.name} 缺少必需文件 {relative}")
        manifest = load_json(report, pack_dir / "manifest.json", f"{pack_dir.name}/manifest.json")
        manifest = manifest if isinstance(manifest, dict) else {}
        for problem in verify_pack_manifest(manifest, pack_dir.name):
            report.error(f"{pack_dir.name}: {problem}")
        parsed: dict[str, object] = {"manifest": manifest}
        for key, filename in (
            ("topics", "topics.json"),
            ("edges", "edges.json"),
            ("terms", "terms.json"),
            ("examples", "examples.json"),
        ):
            document = load_json(report, pack_dir / "content" / filename, f"{pack_dir.name}/{filename}")
            items = document.get(key) if isinstance(document, dict) else None
            if not isinstance(items, list):
                report.error(f"{pack_dir.name}/{filename} 缺少列表字段 {key}")
                items = []
            parsed[key] = items
        packs[str(manifest.get("packId") or pack_dir.name)] = parsed
    return packs


def check_pack_content(report: Report, packs: dict[str, dict]) -> None:
    if len(packs) != EXPECTED_PACK_COUNT:
        report.error(f"内容包数量不符：期望 {EXPECTED_PACK_COUNT}，实际 {len(packs)}")

    topic_ids: dict[str, str] = {}
    term_ids: dict[str, str] = {}
    example_ids: dict[str, str] = {}
    all_edges: list[dict] = []
    totals = {"topics": 0, "terms": 0, "examples": 0, "edges": 0}

    for pack_id, pack in packs.items():
        for topic in pack["topics"]:
            topic_id = topic.get("id")
            if not isinstance(topic_id, str) or not ID_RE.match(topic_id):
                report.error(f"{pack_id}: topic id 非法 {topic_id!r}")
                continue
            if topic_id in topic_ids:
                report.error(f"topic id 全局重复：{topic_id}（{topic_ids[topic_id]} / {pack_id}）")
            topic_ids[topic_id] = pack_id
            if not localized_ok(topic.get("title")) or not localized_ok(topic.get("summary")):
                report.error(f"{topic_id}: title/summary 双语不完整")
            if not localized_ok(topic.get("objectives")):
                report.error(f"{topic_id}: objectives 双语不完整")
            if not topic.get("termIds"):
                report.warn(f"{topic_id}: 没有关联术语")
            if not topic.get("exampleIds"):
                report.warn(f"{topic_id}: 没有关联示例")
            if not topic.get("exercise") and not topic.get("exerciseSkipReason"):
                report.warn(f"{topic_id}: 既无 exercise 也无 exerciseSkipReason")
        for term in pack["terms"]:
            term_id = term.get("id")
            if not isinstance(term_id, str) or not ID_RE.match(term_id):
                report.error(f"{pack_id}: term id 非法 {term_id!r}")
                continue
            if term_id in term_ids:
                report.error(f"term id 全局重复：{term_id}")
            term_ids[term_id] = pack_id
            if not localized_ok(term.get("definitions")):
                report.error(f"{term_id}: definitions 双语不完整")
            display = term.get("displayName")
            if not (isinstance(display, str) and display.strip()) and not localized_ok(display):
                report.error(f"{term_id}: displayName 既不是非空字符串也不是完整双语对象")
        for example in pack["examples"]:
            example_id = example.get("id")
            if not isinstance(example_id, str) or not ID_RE.match(example_id):
                report.error(f"{pack_id}: example id 非法 {example_id!r}")
                continue
            if example_id in example_ids:
                report.error(f"example id 全局重复：{example_id}")
            example_ids[example_id] = pack_id
            code = example.get("code")
            code = code if isinstance(code, str) else ""
            for annotation in example.get("annotations") or []:
                start, end = annotation.get("start"), annotation.get("end")
                if not isinstance(start, int) or not isinstance(end, int) or not (0 <= start < end <= len(code)):
                    report.error(f"{example_id}: annotation 偏移非法（{start}, {end}）")
            if example.get("runtimeId") and not example.get("expectedOutput"):
                report.error(f"{example_id}: 可执行示例缺少 expectedOutput")
        for edge in pack["edges"]:
            all_edges.append(edge)
            if edge.get("type") not in EDGE_TYPES:
                report.error(f"{pack_id}: 边类型非法 {edge.get('type')!r}")
            if not isinstance(edge.get("source"), str) or not isinstance(edge.get("target"), str):
                report.error(f"{pack_id}: 边端点不是字符串")
            if edge.get("type") == "prerequisite" and edge.get("source") == edge.get("target"):
                report.error(f"{pack_id}: prerequisite 自环 {edge.get('source')}")
        totals["topics"] += len(pack["topics"])
        totals["terms"] += len(pack["terms"])
        totals["examples"] += len(pack["examples"])
        totals["edges"] += len(pack["edges"])

    for pack_id, pack in packs.items():
        for topic in pack["topics"]:
            for term_id in topic.get("termIds") or []:
                if term_id not in term_ids:
                    report.error(f"{topic.get('id')}: 引用不存在的 term {term_id}")
            for example_id in topic.get("exampleIds") or []:
                if example_id not in example_ids:
                    report.error(f"{topic.get('id')}: 引用不存在的 example {example_id}")
    for edge in all_edges:
        if edge.get("source") not in topic_ids:
            report.error(f"边引用不存在的 source：{edge.get('source')}")
        if edge.get("target") not in topic_ids:
            report.error(f"边引用不存在的 target：{edge.get('target')}")

    cycles = find_prerequisite_cycles(sorted(topic_ids), all_edges)
    for cycle in cycles:
        report.error("prerequisite 存在环：" + " -> ".join(cycle + [cycle[0]]))

    report.stats["content"] = totals
    report.stats["packs"] = sorted(packs)
    report.stats["unique_ids"] = {
        "topics": len(topic_ids),
        "terms": len(term_ids),
        "examples": len(example_ids),
    }


def check_dist(report: Report, packs: dict[str, dict]) -> None:
    if not DIST_DIR.is_dir():
        report.error("缺少 content/dist 目录")
        return
    codepacks = sorted(DIST_DIR.glob("*.codepack"))
    if len(codepacks) != EXPECTED_PACK_COUNT:
        report.error(f"codepack 数量不符：期望 {EXPECTED_PACK_COUNT}，实际 {len(codepacks)}")

    sums_path = DIST_DIR / "sha256sums.txt"
    if not sums_path.is_file():
        report.error("缺少 content/dist/sha256sums.txt")
        return
    declared, problems = parse_sha256sums(sums_path.read_text(encoding="utf-8"))
    for problem in problems:
        report.error(problem)
    actual: dict[str, str] = {}
    for codepack in codepacks:
        digest = sha256_file(codepack)
        actual[codepack.name] = digest
        if declared.get(codepack.name) != digest:
            report.error(f"{codepack.name}: sha256sums 与实际哈希不一致")
    for name in declared:
        if name not in actual:
            report.error(f"sha256sums 声明了不存在的产物：{name}")

    catalog = load_json(report, DIST_DIR / "catalog.json", "content/dist/catalog.json")
    catalog = catalog if isinstance(catalog, dict) else {}
    entries = catalog.get("packs") if isinstance(catalog.get("packs"), list) else []
    if catalog.get("packCount") != len(entries):
        report.error(f"catalog packCount 与 packs 长度不一致：{catalog.get('packCount')} vs {len(entries)}")
    if len(entries) != EXPECTED_PACK_COUNT:
        report.error(f"catalog 条目数不符：期望 {EXPECTED_PACK_COUNT}，实际 {len(entries)}")
    for entry in entries:
        if not isinstance(entry, dict):
            report.error("catalog 条目不是对象")
            continue
        name = entry.get("downloadUrl")
        if not isinstance(name, str) or "/" in name or ".." in name or not name.endswith(".codepack"):
            report.error(f"catalog downloadUrl 非法：{name!r}")
            continue
        path = DIST_DIR / name
        if not path.is_file():
            report.error(f"catalog 指向不存在的产物：{name}")
            continue
        if entry.get("sha256") != sha256_file(path):
            report.error(f"{name}: catalog sha256 与实际不一致")
        if entry.get("sizeBytes") != path.stat().st_size:
            report.error(f"{name}: catalog sizeBytes 与实际不一致")
        pack_id = entry.get("packId")
        if pack_id not in packs:
            report.error(f"catalog 引用了不存在的内容包：{pack_id}")

    for codepack in codepacks:
        with zipfile.ZipFile(codepack) as archive:
            for problem in zip_safety_violations(archive):
                report.error(f"{codepack.name}: {problem}")
            if "manifest.json" not in archive.namelist():
                report.error(f"{codepack.name}: 缺少 manifest.json")
                continue
            manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
            files = manifest.get("files") if isinstance(manifest.get("files"), list) else []
            declared_paths = set()
            for item in files:
                item_path = item.get("path")
                declared_paths.add(item_path)
                try:
                    data = archive.read(item_path)
                except KeyError:
                    report.error(f"{codepack.name}: manifest.files 指向不存在的条目 {item_path}")
                    continue
                if sha256_bytes(data) != item.get("sha256"):
                    report.error(f"{codepack.name}: {item_path} sha256 不一致")
                if len(data) != item.get("sizeBytes"):
                    report.error(f"{codepack.name}: {item_path} sizeBytes 不一致")
            actual_paths = {info.filename for info in archive.infolist() if info.filename != "manifest.json"}
            if declared_paths != actual_paths:
                report.error(f"{codepack.name}: manifest.files 与 zip 内容不一致")
    report.stats["dist"] = {
        "codepacks": len(codepacks),
        "sha256sums_entries": len(declared),
        "catalog_packs": len(entries),
    }


def check_index(report: Report) -> None:
    document = load_json(report, INDEX_FILE, "content/index.json")
    if not isinstance(document, dict):
        return
    packs = document.get("packs") if isinstance(document.get("packs"), list) else []
    if document.get("packCount") != len(packs):
        report.error("content/index.json packCount 与 packs 长度不一致")
    if len(packs) != EXPECTED_PACK_COUNT:
        report.error(f"content/index.json 条目数不符：期望 {EXPECTED_PACK_COUNT}，实际 {len(packs)}")
    for entry in packs:
        file_value = entry.get("file") if isinstance(entry, dict) else None
        if not isinstance(file_value, str) or not (ROOT / file_value).is_file():
            report.error(f"content/index.json 引用不存在的产物：{file_value!r}")


def run_checks() -> Report:
    report = Report()
    packs = collect_packs(report)
    check_pack_content(report, packs)
    check_dist(report, packs)
    check_index(report)
    return report


# ---------------------------------------------------------------------------
# 自测
# ---------------------------------------------------------------------------
def self_test() -> None:
    assert ID_RE.match("python.control-flow.for-loop")
    assert not ID_RE.match("Python.Control")
    assert not ID_RE.match("a b")
    assert localized_ok({"zh-CN": "中文", "en": "English"})
    assert localized_ok({"zh-CN": ["甲"], "en": ["A"]})
    assert not localized_ok({"zh-CN": "中文", "en": "  "})
    assert not localized_ok({"zh-CN": "中文"})

    assert find_prerequisite_cycles(["a", "b"], [{"source": "a", "target": "b", "type": "prerequisite"}]) == []
    cycle = find_prerequisite_cycles(
        ["a", "b"],
        [
            {"source": "a", "target": "b", "type": "prerequisite"},
            {"source": "b", "target": "a", "type": "prerequisite"},
        ],
    )
    assert len(cycle) == 1 and set(cycle[0]) == {"a", "b"}

    mapping, errors = parse_sha256sums("a" * 64 + "  x.codepack\n")
    assert not errors and mapping["x.codepack"] == "a" * 64
    _, errors = parse_sha256sums("bad-line")
    assert errors
    _, errors = parse_sha256sums("XYZ  x.codepack")
    assert errors

    assert verify_pack_manifest(
        {
            "schemaVersion": 1,
            "packId": "core.demo",
            "packType": "knowledge",
            "version": "1.0.0",
            "title": {"zh-CN": "示例", "en": "Demo"},
            "defaultLocale": "zh-CN",
            "locales": ["zh-CN", "en"],
            "minAppVersion": "0.1.0",
            "pluginApiVersion": 1,
            "entry": "content/topics.json",
            "dependencies": [],
            "files": [],
        },
        "core.demo",
    ) == []

    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        good = root / "good.codepack"
        with zipfile.ZipFile(good, "w") as archive:
            archive.writestr("manifest.json", "{}")
            archive.writestr("content/topics.json", "[]")
        with zipfile.ZipFile(good) as archive:
            assert zip_safety_violations(archive) == []
        bad = root / "bad.codepack"
        with zipfile.ZipFile(bad, "w") as archive:
            archive.writestr("../evil.txt", "x")
            archive.writestr("/abs.txt", "x")
        with zipfile.ZipFile(bad) as archive:
            problems = zip_safety_violations(archive)
        assert any("路径穿越" in problem for problem in problems)
        assert any("绝对路径" in problem for problem in problems)
    print("self-test: OK")


def main() -> int:
    parser = argparse.ArgumentParser(description="CodeTree 内容与产物验收检查")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出结果")
    parser.add_argument("--selftest", action="store_true", help="只运行纯函数自测")
    args = parser.parse_args()

    if args.selftest:
        self_test()
        return 0

    report = run_checks()
    payload = {
        "ok": not report.errors,
        "errors": report.errors,
        "warnings": report.warnings,
        "stats": report.stats,
    }
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print("== 内容与产物验收 ==")
        for key, value in report.stats.items():
            print(f"{key}: {value}")
        print(f"errors: {len(report.errors)}")
        for item in report.errors:
            print("  - " + item)
        print(f"warnings: {len(report.warnings)}")
        for item in report.warnings[:30]:
            print("  - " + item)
        if len(report.warnings) > 30:
            print(f"  ... 其余 {len(report.warnings) - 30} 条省略")
        print("check_content:", "OK" if not report.errors else "FAILED")
    return 0 if not report.errors else 1


if __name__ == "__main__":
    sys.exit(main())
