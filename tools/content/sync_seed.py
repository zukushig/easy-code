#!/usr/bin/env python3
"""CodeTree 内置内容同步器 / Seed asset sync.

把 `content/dist/` 的分发产物同步到 App 的内置种子目录
`app/src/main/assets/seed/`，供 App 首次启动时由 SeedInstaller 安装。

同步后的布局（扁平，与 catalog.downloadUrl 的相对路径语义一致）：

  app/src/main/assets/seed/
  ├── catalog.json                        远端目录契约（App 启动读取）
  ├── sha256sums.txt                      产物哈希清单（诊断/校验）
  └── <packId>-<version>.codepack         每个知识包一个 zip

行为：
  * 默认同步 catalog 中全部包；`--only` 可只同步指定包（逗号分隔或重复传参），
    此时写入目标目录的 catalog.json / sha256sums.txt 会被过滤为只含所选包，
    保证内置目录自洽，不会出现“catalog 列了但文件不存在”。
  * 复制前校验 dist 中每个产物的 sizeBytes / sha256 与 catalog 一致，先全部
    校验再复制，避免只同步一半。
  * 已存在且 sha256 相同的文件跳过（keep）。
  * 全量同步会清理目标目录里过期的 *.codepack（--only 模式不清理）。
  * 写入使用临时文件 + os.replace，避免中断留下半个文件。

退出码：0 成功；1 同步失败；2 用法/参数错误。
用法：
  python3 tools/content/sync_seed.py
  python3 tools/content/sync_seed.py --only core.python,core.git
  python3 tools/content/sync_seed.py --dest /tmp/seed-check
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
from pathlib import Path

DEFAULT_DEST = "app/src/main/assets/seed"
CATALOG_NAME = "catalog.json"
SUMS_NAME = "sha256sums.txt"
REQUIRED_ENTRY_FIELDS = ("packId", "version", "downloadUrl", "sha256", "sizeBytes")


class SyncError(Exception):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_catalog(dist_dir: Path):
    catalog_path = dist_dir / CATALOG_NAME
    if not catalog_path.is_file():
        raise SyncError(f"缺少 {catalog_path}；请先运行 python3 tools/content/build_dist.py")
    try:
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SyncError(f"读取 catalog 失败: {exc}") from exc
    if not isinstance(catalog, dict):
        raise SyncError("catalog.json 顶层必须是对象")
    entries = catalog.get("packs")
    if not isinstance(entries, list) or not entries:
        raise SyncError("catalog.packs 必须是非空数组")
    clean_entries = []
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise SyncError(f"catalog.packs[{index}] 必须是对象")
        missing = [field for field in REQUIRED_ENTRY_FIELDS if field not in entry]
        if missing:
            raise SyncError(f"catalog.packs[{index}] 缺少字段: {', '.join(missing)}")
        clean_entries.append(entry)
    return catalog, clean_entries


def selected_entries(entries, only):
    if not only:
        return entries
    requested = []
    for chunk in only:
        for part in str(chunk).split(","):
            part = part.strip()
            if part and part not in requested:
                requested.append(part)
    by_id = {entry["packId"]: entry for entry in entries}
    unknown = [pack_id for pack_id in requested if pack_id not in by_id]
    if unknown:
        available = ", ".join(sorted(by_id))
        raise SyncError(f"--only 指定了未知 packId: {', '.join(unknown)}；可用: {available}")
    return [by_id[pack_id] for pack_id in requested]


def verify_sources(dist_dir: Path, entries):
    """先校验全部产物，再开始复制；返回 {filename: source_path}。"""
    verified = {}
    for entry in entries:
        pack_id = entry["packId"]
        url = entry["downloadUrl"]
        if (
            not isinstance(url, str)
            or not url
            or url.startswith(("/", "\\"))
            or "\\" in url
            or "/" in url
            or ".." in url
        ):
            raise SyncError(f"{pack_id}: catalog.downloadUrl 必须是纯文件名: {url!r}")
        source = dist_dir / url
        if not source.is_file():
            raise SyncError(f"{pack_id}: catalog 指向的产物不存在: {source}")
        actual_size = source.stat().st_size
        if actual_size != entry["sizeBytes"]:
            raise SyncError(
                f"{pack_id}: sizeBytes 不一致（catalog={entry['sizeBytes']} actual={actual_size}）: {source}"
            )
        actual_sha = sha256_file(source)
        if actual_sha != entry["sha256"]:
            raise SyncError(f"{pack_id}: sha256 不一致（catalog={entry['sha256']} actual={actual_sha}）: {source}")
        verified[url] = source
    return verified


def atomic_copy_file(source: Path, destination: Path) -> None:
    temp_path = destination.with_name(destination.name + ".tmp")
    try:
        shutil.copyfile(source, temp_path)
        os.replace(temp_path, destination)
    finally:
        if temp_path.exists():
            temp_path.unlink()


def atomic_write_bytes(destination: Path, data: bytes) -> None:
    temp_path = destination.with_name(destination.name + ".tmp")
    try:
        temp_path.write_bytes(data)
        os.replace(temp_path, destination)
    finally:
        if temp_path.exists():
            temp_path.unlink()


def dump_json(data) -> bytes:
    return (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def write_filtered_catalog(destination: Path, catalog, entries) -> None:
    filtered = dict(catalog)
    filtered["packs"] = entries
    filtered["packCount"] = len(entries)
    atomic_write_bytes(destination / CATALOG_NAME, dump_json(filtered))


def write_filtered_sums(destination: Path, entries) -> None:
    lines = [f"{entry['sha256']}  {entry['downloadUrl']}\n" for entry in entries]
    atomic_write_bytes(destination / SUMS_NAME, "".join(sorted(lines)).encode("utf-8"))


def sync(root: Path, only, dest_arg: str) -> int:
    dist_dir = root / "content" / "dist"
    if not dist_dir.is_dir():
        raise SyncError(f"缺少 {dist_dir}；请先运行 python3 tools/content/build_dist.py")

    dest_dir = Path(dest_arg)
    if not dest_dir.is_absolute():
        dest_dir = root / dest_dir
    if dest_dir.exists() and not dest_dir.is_dir():
        raise SyncError(f"目标已存在但不是目录: {dest_dir}")
    dest_dir.mkdir(parents=True, exist_ok=True)

    catalog, all_entries = load_catalog(dist_dir)
    entries = selected_entries(all_entries, only)
    sources = verify_sources(dist_dir, entries)

    print("== CodeTree seed sync ==")
    print(f"root: {root}")
    print(f"dist: {dist_dir}")
    print(f"dest: {dest_dir}")
    print(f"catalog packs: {len(all_entries)}  selected: {len(entries)}")

    copied = 0
    unchanged = 0
    copied_bytes = 0
    for entry in entries:
        filename = entry["downloadUrl"]
        source = sources[filename]
        destination = dest_dir / filename
        already_same = destination.is_file() and destination.stat().st_size == entry["sizeBytes"] \
            and sha256_file(destination) == entry["sha256"]
        if already_same:
            unchanged += 1
            print(f"  keep  {filename}  sizeBytes={entry['sizeBytes']}  sha256={entry['sha256'][:12]}…")
            continue
        atomic_copy_file(source, destination)
        copied += 1
        copied_bytes += entry["sizeBytes"]
        print(f"  sync  {filename}  sizeBytes={entry['sizeBytes']}  sha256={entry['sha256'][:12]}…")

    write_filtered_catalog(dest_dir, catalog, entries)
    write_filtered_sums(dest_dir, entries)
    print(f"  wrote {CATALOG_NAME} (packs={len(entries)})  {SUMS_NAME} (lines={len(entries)})")

    removed = 0
    if only is None:
        expected = {entry["downloadUrl"] for entry in entries}
        for stale in sorted(dest_dir.glob("*.codepack")):
            if stale.name not in expected:
                stale.unlink()
                removed += 1
                print(f"  removed stale {stale.name}")
    for leftover in sorted(dest_dir.glob("*.tmp")):
        leftover.unlink()

    total_bytes = sum(entry["sizeBytes"] for entry in entries)
    print(
        f"Summary: synced={copied} unchanged={unchanged} removed={removed} "
        f"copiedBytes={copied_bytes} seedTotalBytes={total_bytes}"
    )
    print(f"DEST: {dest_dir}")
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Sync content/dist artifacts into app seed assets.")
    parser.add_argument("--root", default=None, help="仓库根目录（默认由脚本位置推断）")
    parser.add_argument(
        "--only",
        action="append",
        default=None,
        help="只同步指定 packId（逗号分隔或重复传参，如 --only core.python,core.git）",
    )
    parser.add_argument("--dest", default=DEFAULT_DEST, help=f"目标目录（默认 {DEFAULT_DEST}）")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve() if args.root else Path(__file__).resolve().parents[2]
    try:
        return sync(root, args.only, args.dest)
    except SyncError as exc:
        print(f"SYNC FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
