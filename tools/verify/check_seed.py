#!/usr/bin/env python3
"""CodeTree 内置种子内容一致性检查（app/src/main/assets/seed 与 content/dist 对齐）。

检查范围：
  1. seed 目录中的 catalog.json 与 content/dist/catalog.json 语义一致；
  2. seed 的 sha256sums.txt 与 seed 内 codepack 实际哈希一致；
  3. seed 内 codepack 集合与 catalog downloadUrl 集合一致，无过期产物；
  4. codepack 名称安全（无绝对路径、无目录穿越），zip 内含 manifest.json。

设计说明：MVP 允许 seed 尚未同步（app 还在开发中）。默认把“seed 缺失”记为 warning，
使用 --require-seed 时升级为 error，供发布门禁使用。

用法：
  python3 tools/verify/check_seed.py                  # 默认宽松（seed 缺失只警告）
  python3 tools/verify/check_seed.py --require-seed   # 发布门禁：seed 必须存在且一致
  python3 tools/verify/check_seed.py --json
  python3 tools/verify/check_seed.py --selftest       # 纯函数自测

退出码：0 = 无 error；1 = 有 error。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SEED = ROOT / "app" / "src" / "main" / "assets" / "seed"
DEFAULT_DIST = ROOT / "content" / "dist"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_sha256sums(text: str) -> tuple[dict[str, str], list[str]]:
    mapping: dict[str, str] = {}
    errors: list[str] = []
    for number, raw_line in enumerate(text.splitlines(), 1):
        line = raw_line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) != 2:
            errors.append(f"sha256sums 第 {number} 行格式非法")
            continue
        digest, name = parts
        if len(digest) != 64 or any(char not in "0123456789abcdef" for char in digest):
            errors.append(f"sha256sums 第 {number} 行哈希非法：{name}")
            continue
        if name in mapping:
            errors.append(f"sha256sums 文件名重复：{name}")
            continue
        mapping[name] = digest
    return mapping, errors


def codepack_names_safe(names: list[str]) -> list[str]:
    problems: list[str] = []
    for name in names:
        if "/" in name or "\\" in name or ".." in name or name.startswith("."):
            problems.append(f"codepack 名称不安全：{name}")
    return problems


def check_zip_light(path: Path) -> list[str]:
    problems: list[str] = []
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if "manifest.json" not in names:
                problems.append(f"{path.name}: zip 内缺少 manifest.json")
            damaged = archive.testzip()
            if damaged:
                problems.append(f"{path.name}: zip 成员损坏：{damaged}")
    except zipfile.BadZipFile as exc:
        problems.append(f"{path.name}: 不是合法 zip（{exc}）")
    return problems


def check_pair(seed_dir: Path, dist_dir: Path, require_seed: bool):
    errors: list[str] = []
    warnings: list[str] = []
    stats: dict[str, object] = {}

    if not seed_dir.is_dir():
        message = f"内置种子目录不存在：{seed_dir}（先运行 tools/content/sync_seed.py）"
        if require_seed:
            errors.append(message)
        else:
            warnings.append(message)
        stats["seed"] = "missing"
        return errors, warnings, stats

    catalog_path = seed_dir / "catalog.json"
    if not catalog_path.is_file():
        errors.append("seed 缺少 catalog.json")
        return errors, warnings, stats
    try:
        seed_catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(f"seed catalog.json 无法解析：{exc}")
        return errors, warnings, stats

    dist_catalog_path = dist_dir / "catalog.json"
    if dist_catalog_path.is_file():
        try:
            dist_catalog = json.loads(dist_catalog_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"dist catalog.json 无法解析：{exc}")
            dist_catalog = None
        if dist_catalog is not None and dist_catalog != seed_catalog:
            errors.append("seed/catalog.json 与 content/dist/catalog.json 内容不一致（需要重新同步）")
    else:
        warnings.append("content/dist/catalog.json 不存在，跳过目录对比")

    entries = seed_catalog.get("packs") if isinstance(seed_catalog.get("packs"), list) else []
    if seed_catalog.get("packCount") != len(entries):
        errors.append("seed catalog packCount 与 packs 长度不一致")
    declared = {
        entry.get("downloadUrl")
        for entry in entries
        if isinstance(entry, dict) and isinstance(entry.get("downloadUrl"), str)
    }
    if len(declared) != len(entries):
        errors.append("seed catalog 存在缺失或重复的 downloadUrl")

    sums_path = seed_dir / "sha256sums.txt"
    if not sums_path.is_file():
        errors.append("seed 缺少 sha256sums.txt")
        declared_sums: dict[str, str] = {}
    else:
        declared_sums, problems = parse_sha256sums(sums_path.read_text(encoding="utf-8"))
        errors.extend(problems)

    actual_files = sorted(path.name for path in seed_dir.glob("*.codepack"))
    for problem in codepack_names_safe(actual_files):
        errors.append(problem)
    actual = set(actual_files)
    missing = sorted(declared - actual)
    stale = sorted(actual - declared)
    if missing:
        errors.append("seed 缺少 catalog 声明的产物：" + ", ".join(missing))
    if stale:
        errors.append("seed 存在 catalog 未声明的过期产物：" + ", ".join(stale))

    for name in sorted(actual):
        path = seed_dir / name
        digest = sha256_file(path)
        if declared_sums.get(name) != digest:
            errors.append(f"{name}: sha256sums 与实际哈希不一致")
        for problem in check_zip_light(path):
            errors.append(problem)

    for entry in entries:
        if not isinstance(entry, dict):
            continue
        name = entry.get("downloadUrl")
        if not isinstance(name, str):
            continue
        size = entry.get("sizeBytes")
        if isinstance(size, int) and (seed_dir / name).is_file():
            if (seed_dir / name).stat().st_size != size:
                errors.append(f"{name}: catalog sizeBytes 与 seed 文件大小不一致")
        digest = entry.get("sha256")
        if isinstance(digest, str) and (seed_dir / name).is_file():
            if sha256_file(seed_dir / name) != digest:
                errors.append(f"{name}: catalog sha256 与 seed 文件不一致")

    stats["seed"] = {
        "codepacks": len(actual),
        "catalog_packs": len(entries),
        "sha256sums_entries": len(declared_sums),
    }
    return errors, warnings, stats


def self_test() -> None:
    assert parse_sha256sums("a" * 64 + "  x.codepack\n")[1] == []
    assert parse_sha256sums("bad")[1] != []
    assert codepack_names_safe(["core.python-1.0.0.codepack"]) == []
    assert codepack_names_safe(["../evil.codepack"]) != []
    assert codepack_names_safe(["/abs.codepack"]) != []

    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        dist = root / "dist"
        seed = root / "seed"
        dist.mkdir()
        seed.mkdir()
        codepack = dist / "core.demo-1.0.0.codepack"
        with zipfile.ZipFile(codepack, "w") as archive:
            archive.writestr("manifest.json", json.dumps({"packId": "core.demo"}))
            archive.writestr("content/topics.json", "{}")
        digest = sha256_file(codepack)
        size = codepack.stat().st_size
        catalog = {
            "schemaVersion": 1,
            "generatedBy": "tools/content/build_dist.py",
            "publishedAt": "2026-01-01T00:00:00Z",
            "packCount": 1,
            "packs": [
                {
                    "packId": "core.demo",
                    "version": "1.0.0",
                    "downloadUrl": codepack.name,
                    "sizeBytes": size,
                    "sha256": digest,
                }
            ],
        }
        (dist / "catalog.json").write_text(json.dumps(catalog), encoding="utf-8")
        (dist / "sha256sums.txt").write_text(f"{digest}  {codepack.name}\n", encoding="utf-8")
        (seed / "catalog.json").write_text(json.dumps(catalog), encoding="utf-8")
        (seed / "sha256sums.txt").write_text(f"{digest}  {codepack.name}\n", encoding="utf-8")
        (seed / codepack.name).write_bytes(codepack.read_bytes())

        errors, warnings, stats = check_pair(seed, dist, require_seed=True)
        assert errors == [], errors
        assert stats["seed"]["codepacks"] == 1

        (seed / "sha256sums.txt").write_text("0" * 64 + f"  {codepack.name}\n", encoding="utf-8")
        errors, _, _ = check_pair(seed, dist, require_seed=True)
        assert any("sha256sums" in error for error in errors)

        empty = root / "empty-seed"
        errors, warnings, _ = check_pair(empty, dist, require_seed=False)
        assert not errors and warnings
        errors, _, _ = check_pair(empty, dist, require_seed=True)
        assert errors
    print("self-test: OK")


def main() -> int:
    parser = argparse.ArgumentParser(description="CodeTree 内置种子内容一致性检查")
    parser.add_argument("--seed-dir", default=str(DEFAULT_SEED), help="seed 目录（默认 app/src/main/assets/seed）")
    parser.add_argument("--dist-dir", default=str(DEFAULT_DIST), help="dist 目录（默认 content/dist）")
    parser.add_argument("--require-seed", action="store_true", help="seed 缺失视为 error（发布门禁用）")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出结果")
    parser.add_argument("--selftest", action="store_true", help="只运行纯函数自测")
    args = parser.parse_args()

    if args.selftest:
        self_test()
        return 0

    errors, warnings, stats = check_pair(Path(args.seed_dir), Path(args.dist_dir), args.require_seed)
    payload = {
        "ok": not errors,
        "require_seed": args.require_seed,
        "errors": errors,
        "warnings": warnings,
        "stats": stats,
    }
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print("== 内置种子一致性 ==")
        for key, value in stats.items():
            print(f"{key}: {value}")
        print(f"errors: {len(errors)}")
        for item in errors:
            print("  - " + item)
        print(f"warnings: {len(warnings)}")
        for item in warnings:
            print("  - " + item)
        print("check_seed:", "OK" if not errors else "FAILED")
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
