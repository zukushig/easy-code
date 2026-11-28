#!/usr/bin/env python3
"""CodeTree 内容包校验器 / Content-pack validator.

校验 content/packs/*/ 下的知识包（与 content/dist/ 下的 .codepack 产物）：

  * JSON 可解析、manifest 必填字段、SemVer、schemaVersion / pluginApiVersion 整数
  * ID 格式与全局唯一、引用完整性（termIds / exampleIds / topicIds / edge 端点）
  * prerequisite 边无环；related / confusedWith 端点存在且语义方向规范
  * zh-CN / en 双语完整性、topic 必备关联、example annotations 字符偏移合法
  * syntax/<language>.json 统一 schema、diagnostics 正则可用 re 编译
  * 若 content/dist 存在：.codepack zip 路径安全（目录穿越 / 绝对路径 / 符号链接 /
    条目数与解压总量 / 压缩比上限），manifest files[] 的 sha256 + sizeBytes 与实际一致，
    catalog.json / sha256sums.txt 与实际产物一致

退出码：0 = 0 error（warnings 不影响退出码）；1 = 有 error；2 = 用法或不致命性错误。
用法：
  python3 tools/content/validate_content.py
  python3 tools/content/validate_content.py --pack core.python
  python3 tools/content/validate_content.py --json
  python3 tools/content/validate_content.py --skip-dist
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

# ---------------------------------------------------------------------------
# 规范常量（与 docs/05-DATA-CONTENT-PLUGIN.md 保持一致）
# ---------------------------------------------------------------------------
ID_RE = re.compile(r"^[a-z0-9]+(?:[.-][a-z0-9]+)*$")
SEMVER_RE = re.compile(
    r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)"
    r"(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$"
)
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
DOMAIN_RE = re.compile(r"^[a-z0-9]+(?:[.-][a-z0-9]+)*$")
LANG_TAG_RE = re.compile(r"^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$")
LANGUAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9+#.-]*$")
FILE_MODE_RE = re.compile(r"^[0-9a-zA-Z./_-]+$")

LOCALE_PRIMARY = "zh-CN"
LOCALE_SECONDARY = "en"
REQUIRED_LOCALES = (LOCALE_PRIMARY, LOCALE_SECONDARY)
DIFFICULTIES = ("beginner", "intermediate", "advanced")
EDGE_TYPES = ("prerequisite", "related", "appliedIn", "confusedWith")
TERM_TYPES = ("concept", "syntax", "api", "tool")
MATCH_TYPES = ("exact", "pinyin", "abbr")
REVIEW_STATUSES = ("draft", "aiDraft", "reviewed", "verifiedExample")
VERIFICATION_STATUSES = ("static-checked", "execution-verified", "unverified")
SEVERITIES = ("error", "warning")
RUNTIME_IDS = ("python", "javascript")
PACK_TYPES = ("knowledge", "plugin")
PLACEHOLDER_SHA = "LOWERCASE_SHA256_HEX"
JSON_REPORT_SCHEMA_VERSION = 1

# 建议规模（越界只记 warning）
TOPIC_COUNT_RANGE = (10, 14)
TERM_COUNT_RANGE = (20, 30)
EXAMPLE_COUNT_RANGE = (12, 18)
KEYWORD_COUNT_RANGE = (30, 60)
BUILTIN_COUNT_RANGE = (10, 30)
DIAGNOSTIC_COUNT_RANGE = (3, 6)

# dist / zip 安全上限
MAX_ZIP_ENTRIES = 2000
MAX_ZIP_TOTAL_UNCOMPRESSED = 64 * 1024 * 1024
MAX_ZIP_FILE_SIZE = 16 * 1024 * 1024
MAX_ZIP_RATIO = 100.0
MAX_MANIFEST_BYTES = 1024 * 1024

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


# ---------------------------------------------------------------------------
# 小工具
# ---------------------------------------------------------------------------
def is_int(value) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def is_nonempty_str(value) -> bool:
    return isinstance(value, str) and bool(value.strip())


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def is_safe_relative(path_value: str) -> bool:
    """相对路径安全检查：拒绝 URL、绝对路径、盘符和 .. 分段。"""
    if not is_nonempty_str(path_value):
        return False
    if "://" in path_value:
        return False
    if path_value.startswith(("/", "\\")):
        return False
    if re.match(r"^[A-Za-z]:", path_value):
        return False
    if "\\" in path_value:
        return False
    return ".." not in path_value.split("/")


def parse_semver(text: str):
    match = SEMVER_RE.match(text.strip())
    if not match:
        return None
    return tuple(int(match.group(i)) for i in (1, 2, 3))


def compare_semver(left, right) -> int:
    return (left > right) - (left < right)


def version_in_range(version: str, version_range: str) -> bool:
    """支持空格分隔的比较器，如 '>=1.0.0 <2.0.0'；无法解析时返回 True。"""
    parsed = parse_semver(version)
    if parsed is None or not is_nonempty_str(version_range):
        return True
    tokens = [tok for tok in re.split(r"[\s,]+", version_range.strip()) if tok]
    if not tokens:
        return True
    for token in tokens:
        match = re.match(r"^(>=|<=|>|<|=|==|\^|~)?\s*(\S+)$", token)
        if not match:
            return True
        operator = match.group(1) or "="
        bound = parse_semver(match.group(2))
        if bound is None:
            return True
        comp = compare_semver(parsed, bound)
        if operator in (">=",) and comp < 0:
            return False
        if operator in ("<=",) and comp > 0:
            return False
        if operator in (">",) and comp <= 0:
            return False
        if operator in ("<",) and comp >= 0:
            return False
        if operator in ("=", "==", "^", "~") and comp != 0:
            if operator in ("^", "~") and comp > 0:
                continue  # 宽松处理 ^ / ~ 的上界，只保证不低于下界
            return False
    return True


class Reporter:
    """收集 error / warning；warnings 独立列出，不影响退出码。"""

    def __init__(self) -> None:
        self.errors = []
        self.warnings = []

    def error(self, pack: str, path, message: str) -> None:
        self.errors.append({"pack": pack, "path": _display_path(path), "message": message})

    def warn(self, pack: str, path, message: str) -> None:
        self.warnings.append({"pack": pack, "path": _display_path(path), "message": message})

    def of(self, pack: str):
        return (
            [issue for issue in self.errors if issue["pack"] == pack],
            [issue for issue in self.warnings if issue["pack"] == pack],
        )

    def ok(self) -> bool:
        return not self.errors


def _display_path(path) -> str:
    if path is None:
        return "-"
    try:
        text = str(Path(path).resolve())
    except OSError:
        text = str(path)
    return text


class Pack:
    def __init__(self, pack_dir: Path) -> None:
        self.dir = pack_dir
        self.pack_id = pack_dir.name
        self.manifest = None
        self.topics = []
        self.edges = []
        self.terms = []
        self.examples = []
        self.syntax = {}


# ---------------------------------------------------------------------------
# 读取
# ---------------------------------------------------------------------------
def load_json_file(reporter: Reporter, pack_id: str, path: Path, required: bool):
    if not path.is_file():
        if required:
            reporter.error(pack_id, path, "缺少必需文件 / required file is missing")
        return None
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        reporter.error(pack_id, path, f"读取失败 / cannot read file: {exc}")
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        reporter.error(pack_id, path, f"JSON 解析失败 / invalid JSON: {exc}")
        return None


def load_packs(reporter: Reporter, root: Path):
    packs_dir = root / "content" / "packs"
    if not packs_dir.is_dir():
        reporter.error("-", packs_dir, "缺少 content/packs 目录 / missing content/packs directory")
        return {}
    packs = {}
    for entry in sorted(packs_dir.iterdir()):
        if not entry.is_dir() or entry.name.startswith("."):
            continue
        pack = Pack(entry)
        manifest = load_json_file(reporter, pack.pack_id, entry / "manifest.json", required=True)
        pack.manifest = manifest if isinstance(manifest, dict) else None
        if pack.manifest is not None and is_nonempty_str(pack.manifest.get("packId")):
            pack.pack_id = pack.manifest["packId"]
        content_dir = entry / "content"
        topics_doc = load_json_file(reporter, pack.pack_id, content_dir / "topics.json", required=True)
        edges_doc = load_json_file(reporter, pack.pack_id, content_dir / "edges.json", required=True)
        terms_doc = load_json_file(reporter, pack.pack_id, content_dir / "terms.json", required=True)
        examples_doc = load_json_file(
            reporter, pack.pack_id, content_dir / "examples.json", required=True
        )
        pack.topics = _as_list(reporter, pack.pack_id, content_dir / "topics.json", topics_doc, "topics")
        pack.edges = _as_list(reporter, pack.pack_id, content_dir / "edges.json", edges_doc, "edges")
        pack.terms = _as_list(reporter, pack.pack_id, content_dir / "terms.json", terms_doc, "terms")
        pack.examples = _as_list(
            reporter, pack.pack_id, content_dir / "examples.json", examples_doc, "examples"
        )
        syntax_dir = entry / "syntax"
        if syntax_dir.is_dir():
            for syntax_file in sorted(syntax_dir.glob("*.json")):
                doc = load_json_file(reporter, pack.pack_id, syntax_file, required=False)
                if isinstance(doc, dict):
                    pack.syntax[syntax_file.name] = doc
                elif doc is not None:
                    reporter.error(pack.pack_id, syntax_file, "syntax 文件顶层必须是对象 / must be a JSON object")
        if pack.pack_id in packs:
            reporter.error(
                pack.pack_id,
                entry / "manifest.json",
                f"packId {pack.pack_id!r} 与另一个包目录重复 / duplicated packId",
            )
            continue
        packs[pack.pack_id] = pack
    return packs


def _as_list(reporter: Reporter, pack_id: str, path: Path, doc, key: str):
    if doc is None:
        return []
    if not isinstance(doc, dict):
        reporter.error(pack_id, path, f"顶层必须是对象且包含列表字段 {key} / top level must be an object with '{key}' list")
        return []
    value = doc.get(key)
    if not isinstance(value, list):
        reporter.error(pack_id, path, f"缺少列表字段 {key} / missing list field '{key}'")
        return []
    return value


# ---------------------------------------------------------------------------
# 通用校验
# ---------------------------------------------------------------------------
def check_id(reporter: Reporter, pack_id: str, path: Path, value, field: str) -> bool:
    if not is_nonempty_str(value):
        reporter.error(pack_id, path, f"{field} 必须是非空字符串 / must be a non-empty string")
        return False
    if not ID_RE.match(value):
        reporter.error(
            pack_id,
            path,
            f"{field} 格式非法（只允许小写字母、数字、点、连字符）: {value!r}",
        )
        return False
    return True


def check_loc_text(reporter: Reporter, pack_id: str, path: Path, value, field: str) -> bool:
    if not isinstance(value, dict):
        reporter.error(pack_id, path, f"{field} 必须是本地化文本对象 {{'zh-CN','en'}} / localized text object required")
        return False
    ok = True
    for locale in REQUIRED_LOCALES:
        text = value.get(locale)
        if not is_nonempty_str(text):
            reporter.error(pack_id, path, f"{field}.{locale} 缺失或为空 / missing or empty")
            ok = False
    for locale in value:
        if locale not in REQUIRED_LOCALES:
            reporter.warn(pack_id, path, f"{field} 含额外语言 {locale!r}；当前规范只要求 zh-CN / en")
    return ok


def check_loc_list(reporter: Reporter, pack_id: str, path: Path, value, field: str) -> bool:
    if not isinstance(value, dict):
        reporter.error(
            pack_id,
            path,
            f"{field} 必须是本地化列表对象 {{\"zh-CN\":[..],\"en\":[..]}} / localized list object required",
        )
        return False
    ok = True
    for locale in REQUIRED_LOCALES:
        items = value.get(locale)
        if not isinstance(items, list) or not items:
            reporter.error(pack_id, path, f"{field}.{locale} 必须是非空列表 / must be a non-empty list")
            ok = False
            continue
        for index, item in enumerate(items):
            if not is_nonempty_str(item):
                reporter.error(pack_id, path, f"{field}.{locale}[{index}] 必须是非空字符串")
                ok = False
    return ok


def check_ref_path(reporter: Reporter, pack: Pack, path, value, field: str, fragment_kind: str | None = None) -> None:
    """校验包内相对引用（contentRef / syntaxRef）。"""
    if value is None:
        return
    if not is_nonempty_str(value):
        reporter.error(pack.pack_id, path, f"{field} 必须是非空字符串或 null / must be a non-empty string or null")
        return
    if "://" in value:
        reporter.error(pack.pack_id, path, f"{field} 不允许 URL / must not be a URL: {value!r}")
        return
    if not is_safe_relative(value):
        reporter.error(pack.pack_id, path, f"{field} 必须是包内相对路径（不允许绝对路径或 ..）: {value!r}")
        return
    file_part, _, fragment = value.partition("#")
    if not file_part:
        reporter.error(pack.pack_id, path, f"{field} 缺少文件路径部分: {value!r}")
        return
    target = pack.dir / file_part
    if not target.is_file():
        reporter.error(pack.pack_id, path, f"{field} 指向的文件不存在 / referenced file does not exist: {value!r}")
        return
    if fragment and fragment_kind and file_part.endswith("topics.json"):
        pass  # topic 片段在 topic 校验阶段检查
    if fragment and not re.match(r"^[A-Za-z0-9._:-]+$", fragment):
        reporter.error(pack.pack_id, path, f"{field} 片段格式非法: {fragment!r}")


# ---------------------------------------------------------------------------
# 各实体校验
# ---------------------------------------------------------------------------
class Context:
    def __init__(self) -> None:
        self.packs = {}
        self.topics = {}   # topic id -> pack_id
        self.terms = {}    # term id -> pack_id
        self.examples = {}  # example id -> pack_id
        self.topic_pack = {}
        self.term_pack = {}
        self.example_pack = {}

    def build_index(self, reporter: Reporter) -> None:
        for pack_id, pack in self.packs.items():
            for kind, items, index in (
                ("topic", pack.topics, self.topics),
                ("term", pack.terms, self.terms),
                ("example", pack.examples, self.examples),
            ):
                for item in items:
                    if not isinstance(item, dict):
                        continue
                    entity_id = item.get("id")
                    if not is_nonempty_str(entity_id):
                        continue
                    if entity_id in index:
                        reporter.error(
                            pack_id,
                            None,
                            f"{kind} ID {entity_id!r} 与包 {index[entity_id]!r} 重复；ID 必须全局唯一",
                        )
                    else:
                        index[entity_id] = pack_id
        self._check_cross_kind_collisions(reporter)

    def _check_cross_kind_collisions(self, reporter: Reporter) -> None:
        topic_ids = set(self.topics)
        for term_id in self.terms:
            if term_id in topic_ids:
                reporter.warn(self.terms[term_id], None, f"ID {term_id!r} 同时用作 topic 与 term；建议加前缀区分")
        example_ids = set(self.examples)
        for topic_id in topic_ids:
            if topic_id in example_ids:
                reporter.warn(self.topics[topic_id], None, f"ID {topic_id!r} 同时用作 topic 与 example；建议加前缀区分")


def _collect_ids(reporter: Reporter, pack: Pack, items, kind: str, seen: dict):
    """返回 {id: item}；重复 ID 报 error（跨包全局唯一由 seen 承担）。"""
    result = {}
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            reporter.error(pack.pack_id, pack.dir / f"content/{kind}.json", f"{kind}[{index}] 必须是对象")
            continue
        entity_id = item.get("id")
        if not check_id(reporter, pack.pack_id, pack.dir / f"content/{kind}.json", entity_id, f"{kind}[{index}].id"):
            continue
        if entity_id in result:
            reporter.error(pack.pack_id, pack.dir / f"content/{kind}.json", f"{kind} ID 重复 / duplicated ID: {entity_id}")
        if entity_id in seen and seen[entity_id] != pack.pack_id:
            reporter.error(
                pack.pack_id,
                pack.dir / f"content/{kind}.json",
                f"{kind} ID {entity_id!r} 已存在于包 {seen[entity_id]!r}；ID 必须全局唯一",
            )
        seen.setdefault(entity_id, pack.pack_id)
        result[entity_id] = item
    return result


def check_dependencies(reporter: Reporter, pack: Pack, ctx: "Context") -> None:
    manifest = pack.manifest or {}
    dependencies = manifest.get("dependencies")
    if not isinstance(dependencies, list):
        reporter.error(pack.pack_id, pack.dir / "manifest.json", "dependencies 必须是数组 / must be an array")
        return
    for index, dependency in enumerate(dependencies):
        path = pack.dir / "manifest.json"
        if not isinstance(dependency, dict):
            reporter.error(pack.pack_id, path, f"dependencies[{index}] 必须是对象")
            continue
        dep_id = dependency.get("packId")
        check_id(reporter, pack.pack_id, path, dep_id, f"dependencies[{index}].packId")
        if dep_id == pack.pack_id:
            reporter.error(pack.pack_id, path, "dependencies 不能依赖自身 / must not depend on itself")
        if not is_nonempty_str(dependency.get("versionRange")):
            reporter.error(pack.pack_id, path, f"dependencies[{index}].versionRange 必须是非空字符串")
        optional = dependency.get("optional")
        if not isinstance(optional, bool):
            reporter.error(pack.pack_id, path, f"dependencies[{index}].optional 必须是布尔值")
        if is_nonempty_str(dep_id) and dep_id not in ctx.packs:
            reporter.warn(pack.pack_id, path, f"依赖包 {dep_id!r} 不在仓库中（打包/安装时需存在）")
        elif is_nonempty_str(dep_id) and is_nonempty_str(dependency.get("versionRange")):
            dep_pack = ctx.packs[dep_id]
            dep_version = (dep_pack.manifest or {}).get("version")
            if is_nonempty_str(dep_version) and not version_in_range(dep_version, dependency["versionRange"]):
                reporter.warn(
                    pack.pack_id,
                    path,
                    f"依赖 {dep_id} 的版本 {dep_version} 不满足 {dependency['versionRange']!r}",
                )


def validate_manifest(reporter: Reporter, pack: Pack, ctx: "Context") -> None:
    manifest = pack.manifest
    path = pack.dir / "manifest.json"
    if manifest is None:
        return
    for field in REQUIRED_MANIFEST_FIELDS:
        if field not in manifest:
            reporter.error(pack.pack_id, path, f"manifest 缺少字段 / missing field: {field}")
    if not is_int(manifest.get("schemaVersion")):
        reporter.error(pack.pack_id, path, "schemaVersion 必须是整数 / must be an integer")
    elif manifest["schemaVersion"] != 1:
        reporter.error(pack.pack_id, path, f"不支持的 schemaVersion={manifest['schemaVersion']}；当前为 1")
    if not is_int(manifest.get("pluginApiVersion")):
        reporter.error(pack.pack_id, path, "pluginApiVersion 必须是整数 / must be an integer")
    elif manifest["pluginApiVersion"] < 1:
        reporter.error(pack.pack_id, path, "pluginApiVersion 必须 >= 1")

    pack_id = manifest.get("packId")
    if not check_id(reporter, pack.pack_id, path, pack_id, "manifest.packId"):
        pass
    elif pack_id != pack.dir.name:
        reporter.error(
            pack.pack_id,
            path,
            f"manifest.packId={pack_id!r} 与目录名 {pack.dir.name!r} 不一致 / packId must match directory name",
        )

    pack_type = manifest.get("packType")
    if pack_type not in PACK_TYPES:
        reporter.error(pack.pack_id, path, f"packType 必须是 {'/'.join(PACK_TYPES)} 之一: {pack_type!r}")
    elif pack_type != "knowledge":
        reporter.warn(pack.pack_id, path, f"packType={pack_type!r} 非 knowledge；内容校验按通用规则进行")

    version = manifest.get("version")
    if not is_nonempty_str(version) or not SEMVER_RE.match(version):
        reporter.error(pack.pack_id, path, f"version 必须是 SemVer（如 1.0.0）: {version!r}")

    check_loc_text(reporter, pack.pack_id, path, manifest.get("title"), "manifest.title")

    default_locale = manifest.get("defaultLocale")
    locales = manifest.get("locales")
    if not is_nonempty_str(default_locale):
        reporter.error(pack.pack_id, path, "defaultLocale 必须是非空字符串")
    if not isinstance(locales, list) or not locales or not all(is_nonempty_str(x) for x in locales):
        reporter.error(pack.pack_id, path, "locales 必须是非空字符串数组")
    else:
        for locale in locales:
            if not LANG_TAG_RE.match(locale):
                reporter.warn(pack.pack_id, path, f"locales 含非标准 BCP 47 标签: {locale!r}")
        for locale in REQUIRED_LOCALES:
            if locale not in locales:
                reporter.error(pack.pack_id, path, f"locales 必须包含 {locale}")
        if is_nonempty_str(default_locale) and default_locale not in locales:
            reporter.error(pack.pack_id, path, "defaultLocale 必须出现在 locales 中")

    min_app_version = manifest.get("minAppVersion")
    if not is_nonempty_str(min_app_version) or not SEMVER_RE.match(min_app_version):
        reporter.error(pack.pack_id, path, f"minAppVersion 必须是 SemVer: {min_app_version!r}")

    entry = manifest.get("entry")
    if not is_nonempty_str(entry):
        reporter.error(pack.pack_id, path, "entry 必须是非空字符串")
    elif not is_safe_relative(entry):
        reporter.error(pack.pack_id, path, f"entry 必须是包内相对路径: {entry!r}")
    elif not (pack.dir / entry).is_file():
        reporter.error(pack.pack_id, path, f"entry 指向的文件不存在: {entry!r}")
    elif entry != "content/topics.json":
        reporter.warn(pack.pack_id, path, "entry 建议为 content/topics.json")

    dependencies = manifest.get("dependencies")
    if not isinstance(dependencies, list):
        reporter.error(pack.pack_id, path, "dependencies 必须是数组")
    files = manifest.get("files")
    if not isinstance(files, list):
        reporter.error(pack.pack_id, path, "files 必须是数组")
    elif files:
        reporter.warn(
            pack.pack_id,
            path,
            "源清单 files[] 应为 []，由 tools/content/build_dist.py 打包时注入 sha256/sizeBytes",
        )
        for index, item in enumerate(files):
            if not isinstance(item, dict):
                reporter.error(pack.pack_id, path, f"files[{index}] 必须是对象")
                continue
            file_path = item.get("path")
            if not is_nonempty_str(file_path) or not is_safe_relative(file_path):
                reporter.error(pack.pack_id, path, f"files[{index}].path 必须是包内安全相对路径")
            digest = item.get("sha256")
            if not is_nonempty_str(digest) or digest == PLACEHOLDER_SHA or not SHA256_RE.match(str(digest)):
                reporter.error(pack.pack_id, path, f"files[{index}].sha256 必须是真实 64 位小写 SHA-256")
            if not is_int(item.get("sizeBytes")) or item.get("sizeBytes", -1) < 0:
                reporter.error(pack.pack_id, path, f"files[{index}].sizeBytes 必须是非负整数")

    license_path = pack.dir / "LICENSE"
    if not license_path.is_file() or license_path.stat().st_size == 0:
        reporter.error(pack.pack_id, license_path, "缺少非空 LICENSE 文件 / missing LICENSE")
    else:
        try:
            license_text = license_path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            license_text = ""
        if "Apache" not in license_text:
            reporter.warn(pack.pack_id, license_path, "LICENSE 建议使用 Apache-2.0 文本")


def validate_topics(reporter: Reporter, pack: Pack, ctx: "Context") -> None:
    path = pack.dir / "content" / "topics.json"
    if not pack.topics:
        reporter.error(pack.pack_id, path, "topics 为空 / topics list is empty")
        return
    collected = {}
    for index, topic in enumerate(pack.topics):
        if not isinstance(topic, dict):
            reporter.error(pack.pack_id, path, f"topics[{index}] 必须是对象")
            continue
        topic_id = topic.get("id")
        if not check_id(reporter, pack.pack_id, path, topic_id, f"topics[{index}].id"):
            continue
        if topic_id in collected:
            reporter.error(pack.pack_id, path, f"topic ID 重复: {topic_id}")
            continue
        collected[topic_id] = topic
        domain = topic.get("domain")
        if not is_nonempty_str(domain) or not DOMAIN_RE.match(domain):
            reporter.error(pack.pack_id, path, f"{topic_id}.domain 必须是合法标识（小写字母/数字/点/连字符）")
        if topic.get("difficulty") not in DIFFICULTIES:
            reporter.error(pack.pack_id, path, f"{topic_id}.difficulty 必须是 {'/'.join(DIFFICULTIES)} 之一")
        check_loc_text(reporter, pack.pack_id, path, topic.get("title"), f"{topic_id}.title")
        check_loc_text(reporter, pack.pack_id, path, topic.get("summary"), f"{topic_id}.summary")
        check_loc_list(reporter, pack.pack_id, path, topic.get("objectives"), f"{topic_id}.objectives")

        content_ref = topic.get("contentRef")
        if content_ref is not None:
            check_ref_path(reporter, pack, path, content_ref, f"{topic_id}.contentRef")
            if isinstance(content_ref, str):
                file_part, _, fragment = content_ref.partition("#")
                if fragment and file_part.endswith("topics.json") and fragment not in ctx.topics:
                    reporter.error(pack.pack_id, path, f"{topic_id}.contentRef 片段 {fragment!r} 不是已知 topic ID")

        estimated = topic.get("estimatedMinutes")
        if not is_int(estimated) or estimated < 0:
            reporter.error(pack.pack_id, path, f"{topic_id}.estimatedMinutes 必须是非负整数")

        term_ids = topic.get("termIds")
        if not isinstance(term_ids, list) or not term_ids:
            reporter.error(pack.pack_id, path, f"{topic_id}.termIds 必须是非空数组（P0 节点至少关联 1 个术语）")
        else:
            for term_id in term_ids:
                if not check_id(reporter, pack.pack_id, path, term_id, f"{topic_id}.termIds[]"):
                    continue
                if term_id not in ctx.terms:
                    reporter.error(pack.pack_id, path, f"{topic_id}.termIds 引用了不存在的 term: {term_id}")
                elif ctx.terms[term_id] != pack.pack_id and not _dependency_declared(pack, ctx.terms[term_id]):
                    reporter.warn(pack.pack_id, path, f"{topic_id} 跨包引用 term {term_id}，但未声明对包 {ctx.terms[term_id]} 的依赖")

        example_ids = topic.get("exampleIds")
        if not isinstance(example_ids, list) or not example_ids:
            reporter.error(pack.pack_id, path, f"{topic_id}.exampleIds 必须是非空数组（P0 节点至少关联 1 个示例）")
        else:
            for example_id in example_ids:
                if not check_id(reporter, pack.pack_id, path, example_id, f"{topic_id}.exampleIds[]"):
                    continue
                if example_id not in ctx.examples:
                    reporter.error(pack.pack_id, path, f"{topic_id}.exampleIds 引用了不存在的 example: {example_id}")
                elif ctx.examples[example_id] != pack.pack_id and not _dependency_declared(pack, ctx.examples[example_id]):
                    reporter.warn(pack.pack_id, path, f"{topic_id} 跨包引用 example {example_id}，但未声明对包 {ctx.examples[example_id]} 的依赖")

        exercise = topic.get("exercise")
        if exercise is None:
            skip_reason = topic.get("exerciseSkipReason")
            if skip_reason is None:
                reporter.error(pack.pack_id, path, f"{topic_id} 缺少 exercise（P0 节点必须有练习，或提供 exerciseSkipReason）")
            else:
                check_loc_text(reporter, pack.pack_id, path, skip_reason, f"{topic_id}.exerciseSkipReason")
        elif not isinstance(exercise, dict):
            reporter.error(pack.pack_id, path, f"{topic_id}.exercise 必须是对象 / must be an object")
        else:
            check_loc_text(reporter, pack.pack_id, path, exercise.get("prompt"), f"{topic_id}.exercise.prompt")
            if "hint" not in exercise:
                reporter.warn(pack.pack_id, path, f"{topic_id}.exercise 缺少 hint")
            else:
                check_loc_text(reporter, pack.pack_id, path, exercise.get("hint"), f"{topic_id}.exercise.hint")

    count = len(pack.topics)
    low, high = TOPIC_COUNT_RANGE
    if not (low <= count <= high):
        reporter.warn(pack.pack_id, path, f"topic 数量 {count} 不在建议区间 {low}~{high}")


def _dependency_declared(pack: Pack, other_pack_id: str) -> bool:
    dependencies = (pack.manifest or {}).get("dependencies")
    if not isinstance(dependencies, list):
        return False
    return any(isinstance(dep, dict) and dep.get("packId") == other_pack_id for dep in dependencies)


def validate_terms(reporter: Reporter, pack: Pack, ctx: "Context") -> None:
    path = pack.dir / "content" / "terms.json"
    if not pack.terms:
        reporter.error(pack.pack_id, path, "terms 为空 / terms list is empty")
        return
    seen = {}
    referenced_terms = set()
    for topic in pack.topics:
        if isinstance(topic, dict) and isinstance(topic.get("termIds"), list):
            referenced_terms.update(topic["termIds"])
    for index, term in enumerate(pack.terms):
        if not isinstance(term, dict):
            reporter.error(pack.pack_id, path, f"terms[{index}] 必须是对象")
            continue
        term_id = term.get("id")
        if not check_id(reporter, pack.pack_id, path, term_id, f"terms[{index}].id"):
            continue
        if term_id in seen:
            reporter.error(pack.pack_id, path, f"term ID 重复: {term_id}")
            continue
        seen[term_id] = term
        if term.get("termType") not in TERM_TYPES:
            reporter.error(pack.pack_id, path, f"{term_id}.termType 必须是 {'/'.join(TERM_TYPES)} 之一")
        check_loc_text(reporter, pack.pack_id, path, term.get("displayName"), f"{term_id}.displayName")
        definitions = term.get("definitions", term.get("localizedDefinitions"))
        check_loc_text(reporter, pack.pack_id, path, definitions, f"{term_id}.definitions")
        aliases = term.get("aliases")
        if not isinstance(aliases, list):
            reporter.error(pack.pack_id, path, f"{term_id}.aliases 必须是数组")
        else:
            for alias_index, alias in enumerate(aliases):
                if not isinstance(alias, dict):
                    reporter.error(pack.pack_id, path, f"{term_id}.aliases[{alias_index}] 必须是对象")
                    continue
                if not is_nonempty_str(alias.get("text")):
                    reporter.error(pack.pack_id, path, f"{term_id}.aliases[{alias_index}].text 必须是非空字符串")
                if not is_nonempty_str(alias.get("lang")) or not LANG_TAG_RE.match(alias["lang"]):
                    reporter.error(pack.pack_id, path, f"{term_id}.aliases[{alias_index}].lang 必须是语言标签")
                if alias.get("matchType") not in MATCH_TYPES:
                    reporter.error(pack.pack_id, path, f"{term_id}.aliases[{alias_index}].matchType 必须是 {'/'.join(MATCH_TYPES)} 之一")
        syntax_ref = term.get("syntaxRef")
        check_ref_path(reporter, pack, path, syntax_ref, f"{term_id}.syntaxRef")
        example_ids = term.get("exampleIds", [])
        if not isinstance(example_ids, list):
            reporter.error(pack.pack_id, path, f"{term_id}.exampleIds 必须是数组")
        else:
            for example_id in example_ids:
                if not check_id(reporter, pack.pack_id, path, example_id, f"{term_id}.exampleIds[]"):
                    continue
                if example_id not in ctx.examples:
                    reporter.error(pack.pack_id, path, f"{term_id}.exampleIds 引用了不存在的 example: {example_id}")
        related = term.get("relatedTermIds", [])
        if not isinstance(related, list):
            reporter.error(pack.pack_id, path, f"{term_id}.relatedTermIds 必须是数组")
        else:
            for related_id in related:
                if related_id == term_id:
                    reporter.warn(pack.pack_id, path, f"{term_id}.relatedTermIds 含自身")
                if not check_id(reporter, pack.pack_id, path, related_id, f"{term_id}.relatedTermIds[]"):
                    continue
                if related_id not in ctx.terms:
                    reporter.error(pack.pack_id, path, f"{term_id}.relatedTermIds 引用了不存在的 term: {related_id}")
        topic_ids = term.get("topicIds", [])
        if not isinstance(topic_ids, list):
            reporter.error(pack.pack_id, path, f"{term_id}.topicIds 必须是数组")
        elif not topic_ids:
            reporter.warn(pack.pack_id, path, f"{term_id}.topicIds 为空；建议关联到至少一个 topic")
        else:
            for topic_id in topic_ids:
                if not check_id(reporter, pack.pack_id, path, topic_id, f"{term_id}.topicIds[]"):
                    continue
                if topic_id not in ctx.topics:
                    reporter.error(pack.pack_id, path, f"{term_id}.topicIds 引用了不存在的 topic: {topic_id}")
        if "source" not in term:
            reporter.error(pack.pack_id, path, f"{term_id}.source 缺失 / missing source")
        elif not is_nonempty_str(term.get("source")):
            reporter.error(pack.pack_id, path, f"{term_id}.source 必须是非空字符串")
        elif term.get("source") != "seed":
            reporter.warn(pack.pack_id, path, f"{term_id}.source 建议为 'seed'（当前 {term.get('source')!r}）")
        content_version = term.get("contentVersion")
        if not is_nonempty_str(content_version) or not SEMVER_RE.match(content_version):
            reporter.error(pack.pack_id, path, f"{term_id}.contentVersion 必须是 SemVer")
        review_status = term.get("reviewStatus")
        if review_status not in REVIEW_STATUSES:
            reporter.error(pack.pack_id, path, f"{term_id}.reviewStatus 必须是 {'/'.join(REVIEW_STATUSES)} 之一")
        elif review_status in ("draft", "aiDraft"):
            reporter.warn(pack.pack_id, path, f"{term_id}.reviewStatus={review_status!r} 尚未人工审核")
        if term_id not in referenced_terms:
            reporter.warn(pack.pack_id, path, f"{term_id} 未被任何 topic.termIds 引用（orphan term）")

    count = len(pack.terms)
    low, high = TERM_COUNT_RANGE
    if not (low <= count <= high):
        reporter.warn(pack.pack_id, path, f"term 数量 {count} 不在建议区间 {low}~{high}")


def validate_examples(reporter: Reporter, pack: Pack, ctx: "Context") -> None:
    path = pack.dir / "content" / "examples.json"
    if not pack.examples:
        reporter.error(pack.pack_id, path, "examples 为空 / examples list is empty")
        return
    seen = {}
    referenced_examples = set()
    for topic in pack.topics:
        if isinstance(topic, dict) and isinstance(topic.get("exampleIds"), list):
            referenced_examples.update(topic["exampleIds"])
    for index, example in enumerate(pack.examples):
        if not isinstance(example, dict):
            reporter.error(pack.pack_id, path, f"examples[{index}] 必须是对象")
            continue
        example_id = example.get("id")
        if not check_id(reporter, pack.pack_id, path, example_id, f"examples[{index}].id"):
            continue
        if example_id in seen:
            reporter.error(pack.pack_id, path, f"example ID 重复: {example_id}")
            continue
        seen[example_id] = example
        language = example.get("language")
        if not is_nonempty_str(language) or not LANGUAGE_RE.match(language):
            reporter.error(pack.pack_id, path, f"{example_id}.language 必须是非空语言标识: {language!r}")
        if not is_nonempty_str(example.get("languageVersion")):
            reporter.error(pack.pack_id, path, f"{example_id}.languageVersion 必须是非空字符串")
        check_loc_text(reporter, pack.pack_id, path, example.get("title"), f"{example_id}.title")
        check_loc_text(reporter, pack.pack_id, path, example.get("explanation"), f"{example_id}.explanation")
        code = example.get("code")
        if not is_nonempty_str(code):
            reporter.error(pack.pack_id, path, f"{example_id}.code 必须是非空字符串")
            code = ""
        elif len(code) > 8000:
            reporter.warn(pack.pack_id, path, f"{example_id}.code 过长（{len(code)} 字符），建议拆分")
        annotations = example.get("annotations")
        if not isinstance(annotations, list) or not annotations:
            reporter.warn(pack.pack_id, path, f"{example_id}.annotations 为空；建议标注关键语法点")
            annotations = []
        for annotation_index, annotation in enumerate(annotations):
            if not isinstance(annotation, dict):
                reporter.error(pack.pack_id, path, f"{example_id}.annotations[{annotation_index}] 必须是对象")
                continue
            start = annotation.get("start")
            end = annotation.get("end")
            if not is_int(start) or not is_int(end):
                reporter.error(pack.pack_id, path, f"{example_id}.annotations[{annotation_index}] start/end 必须是整数")
                continue
            if not (0 <= start < end <= len(code)):
                reporter.error(
                    pack.pack_id,
                    path,
                    f"{example_id}.annotations[{annotation_index}] 偏移非法: 0<={start}<{end}<={len(code)} 不成立",
                )
            check_loc_text(reporter, pack.pack_id, path, annotation.get("note"), f"{example_id}.annotations[{annotation_index}].note")
        runtime_id = example.get("runtimeId")
        if runtime_id is not None and runtime_id not in RUNTIME_IDS:
            reporter.error(pack.pack_id, path, f"{example_id}.runtimeId 只能是 null 或 {'/'.join(RUNTIME_IDS)}")
        if runtime_id == "python" and language != "python":
            reporter.error(pack.pack_id, path, f"{example_id}.runtimeId=python 但 language={language!r}")
        if runtime_id == "javascript" and language != "javascript":
            reporter.error(pack.pack_id, path, f"{example_id}.runtimeId=javascript 但 language={language!r}")
        expected_output = example.get("expectedOutput", "__MISSING__")
        if runtime_id is not None:
            if expected_output == "__MISSING__" or not isinstance(expected_output, str):
                reporter.error(pack.pack_id, path, f"{example_id} 可执行示例必须提供字符串 expectedOutput")
        elif expected_output != "__MISSING__" and not isinstance(expected_output, str):
            reporter.error(pack.pack_id, path, f"{example_id}.expectedOutput 必须是字符串")
        verification = example.get("verificationStatus")
        if verification not in VERIFICATION_STATUSES:
            reporter.error(pack.pack_id, path, f"{example_id}.verificationStatus 必须是 {'/'.join(VERIFICATION_STATUSES)} 之一")
        if verification == "execution-verified" and runtime_id is None:
            reporter.warn(pack.pack_id, path, f"{example_id} 标记 execution-verified 但 runtimeId=null")
        if runtime_id is not None and verification == "unverified":
            reporter.warn(pack.pack_id, path, f"{example_id} 可执行但未验证 / runnable but unverified")
        if runtime_id is None and language in ("python", "javascript"):
            reporter.warn(pack.pack_id, path, f"{example_id}.language={language!r} 但 runtimeId=null；请说明仅阅读")
        if example_id not in referenced_examples:
            reporter.warn(pack.pack_id, path, f"{example_id} 未被任何 topic.exampleIds 引用（orphan example）")

    count = len(pack.examples)
    low, high = EXAMPLE_COUNT_RANGE
    if not (low <= count <= high):
        reporter.warn(pack.pack_id, path, f"example 数量 {count} 不在建议区间 {low}~{high}")


def validate_edges(reporter: Reporter, pack: Pack, ctx: "Context") -> None:
    path = pack.dir / "content" / "edges.json"
    pairs = set()
    type_counts = {edge_type: 0 for edge_type in EDGE_TYPES}
    for index, edge in enumerate(pack.edges):
        if not isinstance(edge, dict):
            reporter.error(pack.pack_id, path, f"edges[{index}] 必须是对象")
            continue
        source = edge.get("source")
        target = edge.get("target")
        edge_type = edge.get("type")
        if not check_id(reporter, pack.pack_id, path, source, f"edges[{index}].source"):
            continue
        if not check_id(reporter, pack.pack_id, path, target, f"edges[{index}].target"):
            continue
        if source == target:
            reporter.error(pack.pack_id, path, f"edges[{index}] 不允许自环: {source}")
        if edge_type not in EDGE_TYPES:
            reporter.error(pack.pack_id, path, f"edges[{index}].type 必须是 {'/'.join(EDGE_TYPES)} 之一")
            continue
        type_counts[edge_type] += 1
        key = (source, target, edge_type)
        if key in pairs:
            reporter.error(pack.pack_id, path, f"重复边 / duplicated edge: {key}")
        pairs.add(key)
        for endpoint, label in ((source, "source"), (target, "target")):
            if endpoint not in ctx.topics:
                reporter.error(pack.pack_id, path, f"edges[{index}].{label} 引用了不存在的 topic: {endpoint}")
            elif ctx.topics[endpoint] != pack.pack_id and not _dependency_declared(pack, ctx.topics[endpoint]):
                reporter.warn(
                    pack.pack_id,
                    path,
                    f"edges[{index}] 跨包引用 topic {endpoint}，但未声明对包 {ctx.topics[endpoint]} 的依赖",
                )
    for edge_type, count in type_counts.items():
        if count == 0:
            reporter.warn(pack.pack_id, path, f"edges 缺少类型 {edge_type!r} 的边（建议 4 种类型各至少 1 条）")
    return type_counts


def validate_prerequisite_cycles(reporter: Reporter, ctx: "Context") -> None:
    graph = {}
    for pack_id, pack in ctx.packs.items():
        for edge in pack.edges:
            if not isinstance(edge, dict):
                continue
            if edge.get("type") != "prerequisite":
                continue
            source = edge.get("source")
            target = edge.get("target")
            if not (is_nonempty_str(source) and is_nonempty_str(target)):
                continue
            graph.setdefault(source, []).append(target)
    state = {}
    stack = []

    def visit(node) -> bool:
        state[node] = 1
        stack.append(node)
        for neighbour in graph.get(node, []):
            if state.get(neighbour) == 1:
                cycle_start = stack.index(neighbour)
                cycle = stack[cycle_start:] + [neighbour]
                owner = ctx.topics.get(node, "-")
                reporter.error(owner, None, "prerequisite 边存在环 / prerequisite cycle: " + " -> ".join(cycle))
                return True
            if state.get(neighbour, 0) == 0 and visit(neighbour):
                return True
        stack.pop()
        state[node] = 2
        return False

    for node in list(graph):
        if state.get(node, 0) == 0:
            if visit(node):
                return


def validate_syntax(reporter: Reporter, pack: Pack) -> None:
    for filename, doc in sorted(pack.syntax.items()):
        path = pack.dir / "syntax" / filename
        if not isinstance(doc, dict):
            reporter.error(pack.pack_id, path, "syntax schema 顶层必须是对象")
            continue
        language = doc.get("language")
        if not is_nonempty_str(language) or not LANGUAGE_RE.match(language):
            reporter.error(pack.pack_id, path, "syntax.language 必须是非空语言标识")
        check_loc_text(reporter, pack.pack_id, path, doc.get("displayName"), "syntax.displayName")
        version = doc.get("version")
        if not is_nonempty_str(version) or not SEMVER_RE.match(version):
            reporter.error(pack.pack_id, path, "syntax.version 必须是 SemVer")
        keywords = doc.get("keywords")
        if not isinstance(keywords, list) or not keywords or not all(is_nonempty_str(x) for x in keywords):
            reporter.error(pack.pack_id, path, "syntax.keywords 必须是非空字符串数组")
        else:
            low, high = KEYWORD_COUNT_RANGE
            if not (low <= len(keywords) <= high):
                reporter.warn(pack.pack_id, path, f"keywords 数量 {len(keywords)} 不在建议区间 {low}~{high}")
            if len(set(keywords)) != len(keywords):
                reporter.error(pack.pack_id, path, "syntax.keywords 含重复项")
        builtins = doc.get("builtins")
        if not isinstance(builtins, list) or not all(is_nonempty_str(x) for x in builtins):
            reporter.error(pack.pack_id, path, "syntax.builtins 必须是字符串数组")
        else:
            low, high = BUILTIN_COUNT_RANGE
            if not (low <= len(builtins) <= high):
                reporter.warn(pack.pack_id, path, f"builtins 数量 {len(builtins)} 不在建议区间 {low}~{high}")
        line_comment = doc.get("lineComment")
        if line_comment is not None and not is_nonempty_str(line_comment):
            reporter.error(pack.pack_id, path, "syntax.lineComment 必须是非空字符串或 null（无行注释的语言用 null）")
        block_comment = doc.get("blockComment")
        if block_comment is not None:
            if (
                not isinstance(block_comment, list)
                or len(block_comment) != 2
                or not all(is_nonempty_str(x) for x in block_comment)
            ):
                reporter.error(pack.pack_id, path, "syntax.blockComment 必须是 null 或 [开始, 结束] 字符串对")
        string_delims = doc.get("stringDelims")
        if not isinstance(string_delims, list) or not string_delims or not all(is_nonempty_str(x) for x in string_delims):
            reporter.error(pack.pack_id, path, "syntax.stringDelims 必须是非空字符串数组")
        brackets = doc.get("brackets")
        if not isinstance(brackets, list):
            reporter.error(pack.pack_id, path, "syntax.brackets 必须是数组")
        else:
            for bracket_index, pair in enumerate(brackets):
                if not isinstance(pair, list) or len(pair) != 2 or not all(is_nonempty_str(x) for x in pair):
                    reporter.error(pack.pack_id, path, f"syntax.brackets[{bracket_index}] 必须是 [开始, 结束] 字符串对")
        diagnostics = doc.get("diagnostics")
        if not isinstance(diagnostics, list):
            reporter.error(pack.pack_id, path, "syntax.diagnostics 必须是数组")
            diagnostics = []
        else:
            low, high = DIAGNOSTIC_COUNT_RANGE
            if not (low <= len(diagnostics) <= high):
                reporter.warn(pack.pack_id, path, f"diagnostics 数量 {len(diagnostics)} 不在建议区间 {low}~{high}")
        for diagnostic_index, diagnostic in enumerate(diagnostics):
            if not isinstance(diagnostic, dict):
                reporter.error(pack.pack_id, path, f"syntax.diagnostics[{diagnostic_index}] 必须是对象")
                continue
            check_id(reporter, pack.pack_id, path, diagnostic.get("id"), f"syntax.diagnostics[{diagnostic_index}].id")
            pattern = diagnostic.get("pattern")
            if not is_nonempty_str(pattern):
                reporter.error(pack.pack_id, path, f"syntax.diagnostics[{diagnostic_index}].pattern 必须是非空字符串")
            else:
                try:
                    re.compile(pattern)
                except re.error as exc:
                    reporter.error(pack.pack_id, path, f"syntax.diagnostics[{diagnostic_index}].pattern 正则可编译失败: {exc}")
            check_loc_text(reporter, pack.pack_id, path, diagnostic.get("message"), f"syntax.diagnostics[{diagnostic_index}].message")
            if diagnostic.get("severity") not in SEVERITIES:
                reporter.error(pack.pack_id, path, f"syntax.diagnostics[{diagnostic_index}].severity 必须是 error/warning")
            fix = diagnostic.get("fix")
            if fix is not None:
                if not isinstance(fix, dict) or fix.get("kind") not in ("append", "replace"):
                    reporter.error(pack.pack_id, path, f"syntax.diagnostics[{diagnostic_index}].fix 必须是 null 或 {{kind: append|replace}}")
                elif fix.get("kind") == "append" and not is_nonempty_str(fix.get("text")):
                    reporter.error(pack.pack_id, path, f"syntax.diagnostics[{diagnostic_index}].fix 为 append 时必须给 text")
            if "hint" in diagnostic:
                check_loc_text(reporter, pack.pack_id, path, diagnostic.get("hint"), f"syntax.diagnostics[{diagnostic_index}].hint")
            else:
                reporter.warn(pack.pack_id, path, f"syntax.diagnostics[{diagnostic_index}] 缺少 hint")
        completions = doc.get("completions")
        if not isinstance(completions, list):
            reporter.error(pack.pack_id, path, "syntax.completions 必须是数组")
            continue
        for group_index, group in enumerate(completions):
            if not isinstance(group, dict):
                reporter.error(pack.pack_id, path, f"syntax.completions[{group_index}] 必须是对象")
                continue
            if not is_nonempty_str(group.get("when")):
                reporter.error(pack.pack_id, path, f"syntax.completions[{group_index}].when 必须是非空字符串")
            items = group.get("items")
            if not isinstance(items, list) or not items:
                reporter.error(pack.pack_id, path, f"syntax.completions[{group_index}].items 必须是非空数组")
                continue
            for item_index, item in enumerate(items):
                if not isinstance(item, dict):
                    reporter.error(pack.pack_id, path, f"syntax.completions[{group_index}].items[{item_index}] 必须是对象")
                    continue
                for field in ("text", "detail"):
                    if not is_nonempty_str(item.get(field)):
                        reporter.error(
                            pack.pack_id,
                            path,
                            f"syntax.completions[{group_index}].items[{item_index}].{field} 必须是非空字符串",
                        )
                if "insertText" in item and not is_nonempty_str(item.get("insertText")):
                    reporter.error(pack.pack_id, path, f"syntax.completions[{group_index}].items[{item_index}].insertText 必须是非空字符串")


# ---------------------------------------------------------------------------
# dist / codepack 校验
# ---------------------------------------------------------------------------
def _check_zip_entries(zf: zipfile.ZipFile, reporter: Reporter, label: str):
    infos = zf.infolist()
    names = set()
    if len(infos) > MAX_ZIP_ENTRIES:
        reporter.error("-", label, f"zip 条目数 {len(infos)} 超过上限 {MAX_ZIP_ENTRIES}")
    total_uncompressed = 0
    for info in infos:
        name = info.filename
        parts = name.split("/")
        if (
            name.startswith("/")
            or re.match(r"^[A-Za-z]:", name)
            or "\\" in name
            or ".." in parts
            or any(part == "" for part in parts[:-1])
        ):
            reporter.error("-", label, f"zip 条目路径不安全 / unsafe path: {name!r}")
            continue
        if name in names:
            reporter.error("-", label, f"zip 条目重名 / duplicated entry: {name!r}")
        names.add(name)
        mode = (info.external_attr >> 16) & 0o170000
        if mode == 0o120000:
            reporter.error("-", label, f"zip 含符号链接 / symlink entry: {name!r}")
        if info.flag_bits & 0x1:
            reporter.error("-", label, f"zip 条目被加密 / encrypted entry: {name!r}")
        if info.is_dir():
            continue
        total_uncompressed += info.file_size
        if info.file_size > MAX_ZIP_FILE_SIZE:
            reporter.error("-", label, f"zip 单文件解压过大 / file too large: {name!r} ({info.file_size} bytes)")
        if info.file_size > 0:
            if info.compress_size <= 0:
                reporter.error("-", label, f"zip 压缩尺寸异常 / invalid compress size: {name!r}")
            elif info.file_size / info.compress_size > MAX_ZIP_RATIO:
                reporter.error(
                    "-",
                    label,
                    f"zip 压缩比过高 / suspicious compression ratio: {name!r} "
                    f"({info.file_size / info.compress_size:.1f} > {MAX_ZIP_RATIO})",
                )
    if total_uncompressed > MAX_ZIP_TOTAL_UNCOMPRESSED:
        reporter.error(
            "-", label, f"zip 解压总量 {total_uncompressed} 超过上限 {MAX_ZIP_TOTAL_UNCOMPRESSED} bytes"
        )
    return names


def validate_dist(root: Path, reporter: Reporter):
    dist_dir = root / "content" / "dist"
    summary = {"present": dist_dir.is_dir(), "codepacks": 0, "catalog": False, "sha256sums": False}
    if not dist_dir.is_dir():
        return summary
    observed = {}
    for zip_path in sorted(dist_dir.glob("*.codepack")):
        label = zip_path.name
        summary["codepacks"] += 1
        try:
            with zipfile.ZipFile(zip_path) as zf:
                names = _check_zip_entries(zf, reporter, label)
                if "manifest.json" not in names:
                    reporter.error("-", label, "codepack 缺少根级 manifest.json")
                else:
                    raw = zf.read("manifest.json")
                    if len(raw) > MAX_MANIFEST_BYTES:
                        reporter.error("-", label, "codepack manifest.json 过大")
                    try:
                        manifest = json.loads(raw.decode("utf-8"))
                    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                        reporter.error("-", label, f"codepack manifest.json 解析失败: {exc}")
                        manifest = None
                    if isinstance(manifest, dict):
                        pack_id = manifest.get("packId")
                        version = manifest.get("version")
                        expected = f"{pack_id}-{version}.codepack"
                        if expected != label:
                            reporter.error("-", label, f"文件名与 manifest 不匹配（期望 {expected}）")
                        files = manifest.get("files")
                        if not isinstance(files, list) or not files:
                            reporter.error("-", label, "codepack manifest.files[] 为空；打包时必须注入内容文件哈希")
                            files = []
                        listed = set()
                        for file_index, item in enumerate(files):
                            if not isinstance(item, dict):
                                reporter.error("-", label, f"manifest.files[{file_index}] 必须是对象")
                                continue
                            file_path = item.get("path")
                            if not is_nonempty_str(file_path) or not is_safe_relative(file_path):
                                reporter.error("-", label, f"manifest.files[{file_index}].path 非法")
                                continue
                            listed.add(file_path)
                            if file_path not in names:
                                reporter.error("-", label, f"manifest.files 列出的文件不在 zip 内: {file_path!r}")
                                continue
                            try:
                                data = zf.read(file_path)
                            except (KeyError, zipfile.BadZipFile) as exc:
                                reporter.error("-", label, f"读取 zip 内文件失败: {file_path!r}: {exc}")
                                continue
                            digest = item.get("sha256")
                            size = item.get("sizeBytes")
                            if not is_nonempty_str(digest) or not SHA256_RE.match(str(digest)):
                                reporter.error("-", label, f"files[{file_index}].sha256 必须是 64 位小写十六进制")
                            elif sha256_bytes(data) != digest:
                                reporter.error("-", label, f"files[{file_index}].sha256 与实际内容不一致: {file_path!r}")
                            if not is_int(size) or size != len(data):
                                reporter.error("-", label, f"files[{file_index}].sizeBytes 与实际不一致: {file_path!r}")
                        unlisted = [
                            name
                            for name in names
                            if name != "manifest.json" and not name.endswith("/") and name not in listed
                        ]
                        if unlisted:
                            reporter.error(
                                "-", label, "zip 内存在未在 manifest.files 声明的文件: " + ", ".join(sorted(unlisted)[:5])
                            )
        except zipfile.BadZipFile as exc:
            reporter.error("-", label, f"不是合法 zip / invalid zip: {exc}")
            continue
        except OSError as exc:
            reporter.error("-", label, f"读取 zip 失败: {exc}")
            continue
        observed[zip_path.name] = {
            "sizeBytes": zip_path.stat().st_size,
            "sha256": sha256_file(zip_path),
        }

    catalog_path = dist_dir / "catalog.json"
    if catalog_path.is_file():
        summary["catalog"] = True
        try:
            catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            reporter.error("-", catalog_path, f"catalog.json 解析失败: {exc}")
            catalog = None
        if isinstance(catalog, dict):
            packs = catalog.get("packs")
            if not isinstance(packs, list) or not packs:
                reporter.error("-", catalog_path, "catalog.packs 必须是非空数组")
                packs = []
            seen_ids = set()
            for index, entry in enumerate(packs):
                if not isinstance(entry, dict):
                    reporter.error("-", catalog_path, f"catalog.packs[{index}] 必须是对象")
                    continue
                required = (
                    "packId",
                    "version",
                    "title",
                    "locales",
                    "domain",
                    "difficulty",
                    "sizeBytes",
                    "sha256",
                    "downloadUrl",
                    "dependencies",
                    "minAppVersion",
                    "publishedAt",
                )
                missing = [field for field in required if field not in entry]
                if missing:
                    reporter.error("-", catalog_path, f"catalog.packs[{index}] 缺少字段: {', '.join(missing)}")
                    continue
                check_id(reporter, "-", catalog_path, entry.get("packId"), f"catalog.packs[{index}].packId")
                if entry.get("packId") in seen_ids:
                    reporter.error("-", catalog_path, f"catalog 中 packId 重复: {entry.get('packId')}")
                seen_ids.add(entry.get("packId"))
                if not is_nonempty_str(entry.get("version")) or not SEMVER_RE.match(entry.get("version", "")):
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].version 必须是 SemVer")
                check_loc_text(reporter, "-", catalog_path, entry.get("title"), f"catalog.packs[{index}].title")
                if not isinstance(entry.get("locales"), list) or not entry.get("locales"):
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].locales 必须是非空数组")
                if not is_nonempty_str(entry.get("domain")):
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].domain 必须是非空字符串")
                if entry.get("difficulty") not in DIFFICULTIES:
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].difficulty 非法")
                if not is_int(entry.get("sizeBytes")) or entry.get("sizeBytes", -1) < 0:
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].sizeBytes 必须是非负整数")
                digest = entry.get("sha256")
                if not is_nonempty_str(digest) or not SHA256_RE.match(str(digest)):
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].sha256 必须是 64 位小写十六进制")
                download_url = entry.get("downloadUrl")
                if not is_nonempty_str(download_url) or not is_safe_relative(download_url):
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].downloadUrl 必须是安全相对路径")
                elif not (dist_dir / download_url).is_file():
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].downloadUrl 指向的文件不存在: {download_url!r}")
                else:
                    actual = observed.get(Path(download_url).name)
                    if actual is None:
                        reporter.error("-", catalog_path, f"catalog.packs[{index}].downloadUrl 未对应已校验的 codepack")
                    else:
                        if actual["sha256"] != digest:
                            reporter.error("-", catalog_path, f"catalog.sha256 与产物不符: {entry.get('packId')}")
                        if actual["sizeBytes"] != entry.get("sizeBytes"):
                            reporter.error("-", catalog_path, f"catalog.sizeBytes 与产物不符: {entry.get('packId')}")
                dependencies = entry.get("dependencies")
                if not isinstance(dependencies, list):
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].dependencies 必须是数组")
                if not is_nonempty_str(entry.get("minAppVersion")) or not SEMVER_RE.match(entry.get("minAppVersion", "")):
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].minAppVersion 必须是 SemVer")
                if "publishedAt" in entry and not is_nonempty_str(entry.get("publishedAt")):
                    reporter.error("-", catalog_path, f"catalog.packs[{index}].publishedAt 必须是非空字符串")
            catalog_download_names = set()
            for entry in packs:
                if not isinstance(entry, dict):
                    continue
                url = entry.get("downloadUrl")
                if isinstance(url, str) and url:
                    catalog_download_names.add(Path(url).name)
            missing_entries = set(observed) - catalog_download_names
            if missing_entries:
                reporter.error("-", catalog_path, "以下 codepack 未出现在 catalog 中: " + ", ".join(sorted(missing_entries)))
    else:
        if observed:
            reporter.error("-", catalog_path, "存在 .codepack 但缺少 catalog.json")

    sums_path = dist_dir / "sha256sums.txt"
    if sums_path.is_file():
        summary["sha256sums"] = True
        try:
            lines = sums_path.read_text(encoding="utf-8").splitlines()
        except OSError as exc:
            reporter.error("-", sums_path, f"读取失败: {exc}")
            lines = []
        listed = {}
        for line in lines:
            if not line.strip():
                continue
            match = re.match(r"^([0-9a-f]{64})\s+\*?(\S+)$", line.strip())
            if not match:
                reporter.error("-", sums_path, f"格式非法 / invalid line: {line!r}")
                continue
            listed[match.group(2)] = match.group(1)
        for name, digest in listed.items():
            if name not in observed:
                reporter.error("-", sums_path, f"列出未打包的文件: {name}")
            elif observed[name]["sha256"] != digest:
                reporter.error("-", sums_path, f"哈希不一致: {name}")
        missing = set(observed) - set(listed)
        if missing:
            reporter.error("-", sums_path, "以下 codepack 未出现在 sha256sums.txt: " + ", ".join(sorted(missing)))
    elif observed:
        reporter.error("-", sums_path, "存在 .codepack 但缺少 sha256sums.txt")
    return summary


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def validate(root: Path, only_pack: str | None = None, skip_dist: bool = False):
    reporter = Reporter()
    packs = load_packs(reporter, root)
    if not packs:
        reporter.error("-", root / "content" / "packs", "未找到任何内容包 / no content pack found")
    ctx = Context()
    ctx.packs = packs
    ctx.build_index(reporter)

    selected = {}
    if only_pack:
        if only_pack not in packs:
            reporter.error("-", root / "content" / "packs", f"未找到内容包 / pack not found: {only_pack!r}")
            return reporter, {}, packs, {"present": False, "codepacks": 0, "catalog": False, "sha256sums": False}
        selected = {only_pack: packs[only_pack]}
    else:
        selected = packs

    per_pack_stats = {}
    for pack_id, pack in selected.items():
        before_errors = len(reporter.errors)
        validate_manifest(reporter, pack, ctx)
        check_dependencies(reporter, pack, ctx)
        validate_topics(reporter, pack, ctx)
        validate_terms(reporter, pack, ctx)
        validate_examples(reporter, pack, ctx)
        validate_edges(reporter, pack, ctx)
        validate_syntax(reporter, pack)
        per_pack_stats[pack_id] = {
            "packId": pack_id,
            "dir": str(pack.dir),
            "version": (pack.manifest or {}).get("version"),
            "topics": len(pack.topics),
            "terms": len(pack.terms),
            "examples": len(pack.examples),
            "edges": len(pack.edges),
            "syntaxFiles": sorted(pack.syntax),
            "errors": len(reporter.errors) - before_errors,
        }

    # 全局检查
    validate_prerequisite_cycles(reporter, ctx)
    for pack_id, pack in selected.items():
        pack_errors, _ = reporter.of(pack_id)
        per_pack_stats[pack_id]["errors"] = len(pack_errors)

    dist_summary = {"present": False, "codepacks": 0, "catalog": False, "sha256sums": False}
    if not skip_dist:
        dist_summary = validate_dist(root, reporter)

    return reporter, per_pack_stats, packs, dist_summary


def _relative_label(root: Path, path_text) -> str:
    """把绝对路径转成相对仓库根的路径（--json 稳定输出用）；无法转换时原样返回。"""
    if not isinstance(path_text, str) or not path_text or path_text == "-":
        return path_text if isinstance(path_text, str) else "-"
    try:
        return Path(path_text).resolve().relative_to(root).as_posix()
    except (ValueError, OSError):
        return path_text


def _normalized_issues(root: Path, issues):
    return [{**issue, "path": _relative_label(root, issue.get("path"))} for issue in issues]


def build_json_report(root: Path, reporter: Reporter, per_pack_stats, dist_summary) -> dict:
    packs_report = []
    for pack_id, stats in per_pack_stats.items():
        errors, warnings = reporter.of(pack_id)
        packs_report.append(
            {
                **stats,
                "dir": _relative_label(root, stats.get("dir")),
                "errorCount": len(errors),
                "warningCount": len(warnings),
                "errors": _normalized_issues(root, errors),
                "warnings": _normalized_issues(root, warnings),
            }
        )
    totals = {
        "packs": len(per_pack_stats),
        "topics": sum(stats["topics"] for stats in per_pack_stats.values()),
        "terms": sum(stats["terms"] for stats in per_pack_stats.values()),
        "examples": sum(stats["examples"] for stats in per_pack_stats.values()),
        "edges": sum(stats["edges"] for stats in per_pack_stats.values()),
    }
    # 契约稳定性：键集合固定、packs 顺序 = 包目录排序、issues 顺序 = 校验阶段顺序、
    # path/dir 均为相对仓库根的 posix 路径（不含机器相关绝对路径），便于 QA 快照对比。
    return {
        "schemaVersion": JSON_REPORT_SCHEMA_VERSION,
        "ok": reporter.ok(),
        "root": str(root),
        "totals": totals,
        "packs": packs_report,
        "dist": dist_summary,
        "errors": _normalized_issues(root, reporter.errors),
        "warnings": _normalized_issues(root, reporter.warnings),
        "counts": {"errors": len(reporter.errors), "warnings": len(reporter.warnings)},
    }


def print_human(root: Path, reporter: Reporter, per_pack_stats, dist_summary) -> None:
    print("== CodeTree content validation ==")
    print(f"root: {root}")
    print(f"packs found: {len(per_pack_stats)}")
    for pack_id, stats in per_pack_stats.items():
        print(
            f"-- {pack_id}  v{stats.get('version')}  "
            f"topics={stats['topics']} terms={stats['terms']} examples={stats['examples']} "
            f"edges={stats['edges']} syntax={','.join(stats['syntaxFiles']) or '-'}"
        )
        errors, warnings = reporter.of(pack_id)
        for issue in errors:
            print(f"   ERROR  [{issue['path']}] {issue['message']}")
        for issue in warnings:
            print(f"   WARN   [{issue['path']}] {issue['message']}")
    dist_errors = [issue for issue in reporter.errors if issue["pack"] == "-"]
    dist_warnings = [issue for issue in reporter.warnings if issue["pack"] == "-"]
    if dist_summary.get("present"):
        print(
            f"-- content/dist: codepacks={dist_summary['codepacks']} "
            f"catalog={'yes' if dist_summary['catalog'] else 'no'} "
            f"sha256sums={'yes' if dist_summary['sha256sums'] else 'no'}"
        )
        for issue in dist_errors:
            print(f"   ERROR  [{issue['path']}] {issue['message']}")
        for issue in dist_warnings:
            print(f"   WARN   [{issue['path']}] {issue['message']}")
    elif dist_errors or dist_warnings:
        for issue in dist_errors:
            print(f"   ERROR  [{issue['path']}] {issue['message']}")
        for issue in dist_warnings:
            print(f"   WARN   [{issue['path']}] {issue['message']}")
    totals = {
        "topics": sum(stats["topics"] for stats in per_pack_stats.values()),
        "terms": sum(stats["terms"] for stats in per_pack_stats.values()),
        "examples": sum(stats["examples"] for stats in per_pack_stats.values()),
    }
    print(
        f"Summary: packs={len(per_pack_stats)} topics={totals['topics']} terms={totals['terms']} "
        f"examples={totals['examples']} | errors={len(reporter.errors)} warnings={len(reporter.warnings)}"
    )
    print("RESULT: " + ("PASS" if reporter.ok() else "FAIL"))


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Validate CodeTree content packs and codepack dist.")
    parser.add_argument("--root", default=None, help="仓库根目录（默认由脚本位置推断）")
    parser.add_argument("--pack", default=None, help="只校验单个 packId（仍会加载全部包用于引用检查）")
    parser.add_argument("--json", action="store_true", help="输出 JSON 汇总")
    parser.add_argument("--skip-dist", action="store_true", help="跳过 content/dist 校验")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parents[2]
    result = validate(root, only_pack=args.pack, skip_dist=args.skip_dist)
    reporter, per_pack_stats, _packs, dist_summary = result
    if args.json:
        print(json.dumps(build_json_report(root, reporter, per_pack_stats, dist_summary), ensure_ascii=False, indent=2))
    else:
        print_human(root, reporter, per_pack_stats, dist_summary)
    return 0 if reporter.ok() else 1


if __name__ == "__main__":
    sys.exit(main())
