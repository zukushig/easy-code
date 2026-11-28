#!/usr/bin/env python3
"""CodeTree 内容打包器 / Content pack builder.

为 content/packs/<packId>/ 生成可分发产物：

  content/dist/<packId>-<version>.codepack   zip 容器（manifest.json + content/ + syntax/ + LICENSE）
  content/dist/catalog.json                   远端目录契约（App「知识库」下载列表用）
  content/dist/sha256sums.txt                 dist 产物的 sha256sum 清单
  content/index.json                          本地已知内容包索引（生成物，供 App / 工具引用）

约定（与 tools/content/README.md 一致）：

1. 源 manifest.json 的 files[] 留空；打包时把包内除 manifest.json 外的**所有**文件按
   {path, sha256, sizeBytes} 写进 codepack 内的 manifest.json（自注入、自包含）。
2. zip 内条目顺序固定（manifest.json 第一，其余按路径排序），mtime 固定为 1980-01-01，
   权限固定 0644 —— 同样输入得到同样 zip（可复现）。
3. 点文件（.foo）与 content/dist 本身不参与打包。
4. catalog.downloadUrl 是相对 content/dist/ 的文件名。
5. catalog.publishedAt 使用固定值 PUBLISHED_AT，保证构建可复现。
6. 构建结束后调用 validate_content.validate_dist() 做一次自检；有 error 则退出码 1。

用法：
  python3 tools/content/build_dist.py
  python3 tools/content/build_dist.py --pack core.python
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

PUBLISHED_AT = "2026-01-01T00:00:00Z"
CATALOG_SCHEMA_VERSION = 1
INDEX_SCHEMA_VERSION = 1
ZIP_DATE_TIME = (1980, 1, 1, 0, 0, 0)
ZIP_FILE_MODE = 0o100644
REQUIRED_PACK_FILES = (
    "manifest.json",
    "LICENSE",
    "content/topics.json",
    "content/edges.json",
    "content/terms.json",
    "content/examples.json",
)
DIFFICULTY_ORDER = {"beginner": 0, "intermediate": 1, "advanced": 2}


class BuildError(Exception):
    pass


# ---------------------------------------------------------------------------
# 工具
# ---------------------------------------------------------------------------
def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def dump_json(data) -> bytes:
    return (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise BuildError(f"读取 {path} 失败 / cannot read: {exc}") from exc


def collect_pack_files(pack_dir: Path):
    """包内参与分发的文件（相对 posix 路径，排序）；排除 manifest.json 与点文件。"""
    files = []
    for path in sorted(pack_dir.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(pack_dir)
        parts = relative.parts
        if "manifest.json" in (relative.as_posix(),):
            continue
        if any(part.startswith(".") for part in parts):
            continue
        files.append(relative.as_posix())
    return files


def write_zip_entry(zf: zipfile.ZipFile, name: str, data: bytes) -> None:
    info = zipfile.ZipInfo(name, date_time=ZIP_DATE_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = ZIP_FILE_MODE << 16
    zf.writestr(info, data)


def discover_pack_dirs(packs_dir: Path, only: str | None):
    if not packs_dir.is_dir():
        raise BuildError(f"缺少 content/packs 目录 / missing: {packs_dir}")
    pack_dirs = []
    for entry in sorted(packs_dir.iterdir()):
        if not entry.is_dir() or entry.name.startswith("."):
            continue
        if only and entry.name != only:
            continue
        pack_dirs.append(entry)
    if only and not pack_dirs:
        raise BuildError(f"未找到内容包 / pack not found: {only!r}")
    return pack_dirs


# ---------------------------------------------------------------------------
# 打包
# ---------------------------------------------------------------------------
def build_pack(pack_dir: Path, dist_dir: Path) -> dict:
    manifest_path = pack_dir / "manifest.json"
    for required in REQUIRED_PACK_FILES:
        if not (pack_dir / required).is_file():
            raise BuildError(f"{pack_dir.name}: 缺少必需文件 / missing required file: {required}")
    manifest = load_json(manifest_path)
    if not isinstance(manifest, dict):
        raise BuildError(f"{pack_dir.name}: manifest.json 顶层必须是对象")
    pack_id = manifest.get("packId")
    version = manifest.get("version")
    if not isinstance(pack_id, str) or not pack_id:
        raise BuildError(f"{pack_dir.name}: manifest.packId 缺失")
    if not isinstance(version, str) or not version:
        raise BuildError(f"{pack_dir.name}: manifest.version 缺失")
    if pack_id != pack_dir.name:
        raise BuildError(f"{pack_id}: manifest.packId 与目录名 {pack_dir.name!r} 不一致")

    files = collect_pack_files(pack_dir)
    entries = []
    payloads = []
    for relative in files:
        data = (pack_dir / relative).read_bytes()
        payloads.append((relative, data))
        entries.append({"path": relative, "sha256": sha256_bytes(data), "sizeBytes": len(data)})
    if not entries:
        raise BuildError(f"{pack_dir.name}: 没有可打包的内容文件")

    embedded_manifest = dict(manifest)
    embedded_manifest["files"] = entries
    manifest_bytes = dump_json(embedded_manifest)

    dist_dir.mkdir(parents=True, exist_ok=True)
    zip_path = dist_dir / f"{pack_id}-{version}.codepack"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        write_zip_entry(zf, "manifest.json", manifest_bytes)
        for relative, data in payloads:
            write_zip_entry(zf, relative, data)

    return {
        "packId": pack_id,
        "version": version,
        "file": zip_path.name,
        "path": zip_path,
        "sizeBytes": zip_path.stat().st_size,
        "sha256": sha256_file(zip_path),
        "fileCount": len(entries) + 1,
        "manifest": manifest,
    }


def _read_zip_metadata(zip_path: Path):
    with zipfile.ZipFile(zip_path) as zf:
        manifest = json.loads(zf.read("manifest.json").decode("utf-8"))
        topics_doc = json.loads(zf.read("content/topics.json").decode("utf-8"))
    topics = topics_doc.get("topics") if isinstance(topics_doc, dict) else None
    if not isinstance(topics, list):
        raise BuildError(f"{zip_path.name}: content/topics.json 缺少 topics 列表")
    return manifest, topics


def catalog_entry(zip_path: Path) -> dict:
    manifest, topics = _read_zip_metadata(zip_path)
    domains = sorted({topic.get("domain") for topic in topics if isinstance(topic, dict) and topic.get("domain")})
    difficulties = [topic.get("difficulty") for topic in topics if isinstance(topic, dict)]
    difficulty = "beginner"
    for candidate in difficulties:
        if DIFFICULTY_ORDER.get(candidate, -1) > DIFFICULTY_ORDER.get(difficulty, -1):
            difficulty = candidate
    dependencies = manifest.get("dependencies")
    if not isinstance(dependencies, list):
        dependencies = []
    normalized_dependencies = []
    for dependency in dependencies:
        if isinstance(dependency, dict):
            normalized_dependencies.append(
                {
                    "packId": dependency.get("packId"),
                    "versionRange": dependency.get("versionRange"),
                    "optional": bool(dependency.get("optional", False)),
                }
            )
    entry = {
        "packId": manifest.get("packId"),
        "version": manifest.get("version"),
        "title": manifest.get("title"),
        "locales": manifest.get("locales"),
        "domain": domains[0] if len(domains) == 1 else ",".join(domains),
        "difficulty": difficulty,
        "sizeBytes": zip_path.stat().st_size,
        "sha256": sha256_file(zip_path),
        "downloadUrl": zip_path.name,
        "dependencies": normalized_dependencies,
        "minAppVersion": manifest.get("minAppVersion"),
        "schemaVersion": manifest.get("schemaVersion"),
        "pluginApiVersion": manifest.get("pluginApiVersion"),
        "publishedAt": PUBLISHED_AT,
    }
    return entry


def write_catalog(dist_dir: Path, zip_paths) -> Path:
    entries = [catalog_entry(path) for path in zip_paths]
    entries.sort(key=lambda entry: str(entry.get("packId")))
    catalog = {
        "schemaVersion": CATALOG_SCHEMA_VERSION,
        "generatedBy": "tools/content/build_dist.py",
        "publishedAt": PUBLISHED_AT,
        "packCount": len(entries),
        "packs": entries,
    }
    catalog_path = dist_dir / "catalog.json"
    catalog_path.write_bytes(dump_json(catalog))
    return catalog_path


def write_sha256sums(dist_dir: Path, zip_paths) -> Path:
    lines = [f"{sha256_file(path)}  {path.name}\n" for path in zip_paths]
    sums_path = dist_dir / "sha256sums.txt"
    sums_path.write_text("".join(sorted(lines)), encoding="utf-8")
    return sums_path


def write_content_index(root: Path, zip_paths) -> Path:
    packs = []
    for zip_path in zip_paths:
        manifest, _topics = _read_zip_metadata(zip_path)
        packs.append(
            {
                "packId": manifest.get("packId"),
                "version": manifest.get("version"),
                "entry": manifest.get("entry", "content/topics.json"),
                "file": f"content/dist/{zip_path.name}",
                "locales": manifest.get("locales"),
            }
        )
    packs.sort(key=lambda item: str(item.get("packId")))
    index = {
        "schemaVersion": INDEX_SCHEMA_VERSION,
        "generatedBy": "tools/content/build_dist.py",
        "publishedAt": PUBLISHED_AT,
        "packCount": len(packs),
        "packs": packs,
    }
    index_path = root / "content" / "index.json"
    index_path.parent.mkdir(parents=True, exist_ok=True)
    index_path.write_bytes(dump_json(index))
    return index_path


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def build(root: Path, only: str | None = None) -> int:
    packs_dir = root / "content" / "packs"
    dist_dir = root / "content" / "dist"
    pack_dirs = discover_pack_dirs(packs_dir, only)
    print("== CodeTree content build ==")
    print(f"root: {root}")
    print(f"packs to build: {len(pack_dirs)}")

    built = []
    for pack_dir in pack_dirs:
        info = build_pack(pack_dir, dist_dir)
        built.append(info)
        print(
            f"  built {info['file']}  sizeBytes={info['sizeBytes']}  "
            f"sha256={info['sha256'][:12]}…  files={info['fileCount']}"
        )

    if only is None:
        keep = {info["file"] for info in built}
        for stale in sorted(dist_dir.glob("*.codepack")):
            if stale.name not in keep:
                stale.unlink()
                print(f"  removed stale {stale.name}")

    zip_paths = sorted(dist_dir.glob("*.codepack"))
    catalog_path = write_catalog(dist_dir, zip_paths)
    sums_path = write_sha256sums(dist_dir, zip_paths)
    index_path = write_content_index(root, zip_paths)
    print(f"  wrote {catalog_path}  packs={len(zip_paths)}")
    print(f"  wrote {sums_path}  lines={len(zip_paths)}")
    print(f"  wrote {index_path}  packs={len(zip_paths)}")

    # 自检：用 validate_content 的 dist 校验器复核产物
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    try:
        from validate_content import Reporter, validate_dist
    except ImportError as exc:  # pragma: no cover
        print(f"  WARN: 未找到 validate_content.py，跳过产物自检: {exc}")
        return 0
    reporter = Reporter()
    validate_dist(root, reporter)
    if reporter.errors:
        print(f"  SELF-CHECK FAILED: {len(reporter.errors)} error(s)")
        for issue in reporter.errors:
            print(f"    ERROR [{issue['path']}] {issue['message']}")
        return 1
    print("  self-check: OK (0 error)")
    print(f"RESULT: BUILD OK ({len(zip_paths)} codepacks)")
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Build CodeTree .codepack distribution artifacts.")
    parser.add_argument("--root", default=None, help="仓库根目录（默认由脚本位置推断）")
    parser.add_argument("--pack", default=None, help="只构建单个 packId（catalog 仍按 dist 全量重建）")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parents[2]
    try:
        return build(root, only=args.pack)
    except BuildError as exc:
        print(f"BUILD FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
