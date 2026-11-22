#!/usr/bin/env python3
"""校验 CodeTree 视觉资产结构、令牌一致性、可访问性关键规则与 PNG 尺寸。"""

from __future__ import annotations

import argparse
import json
import struct
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / "design"
TOKENS = DESIGN / "tokens.json"
MAIN_ICON = DESIGN / "icons/codetree-icon.svg"
FOREGROUND_ICON = DESIGN / "icons/codetree-icon-foreground.svg"
MONO_ICON = DESIGN / "icons/codetree-icon-mono.svg"
CLOUD = DESIGN / "graph/knowledge-cloud.svg"
MASCOT = DESIGN / "mascot/programming-cat.svg"
MASCOT_SHEET = DESIGN / "mascot/programming-cat-sheet.svg"
PREVIEW = DESIGN / "visual-language.html"
VISUAL_DOC = ROOT / "docs/09-VISUAL-DESIGN.md"
UPDATE_DOC = ROOT / "docs/10-UPDATE-POLICY.md"
PNG_SIZES = {"icon-1024.png": 1024, "icon-192.png": 192, "icon-48.png": 48}
PNG_EXTRA = {"knowledge-cloud.png": (1200, 760), "programming-cat-sheet.png": (1600, 900)}


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def srgb_channel(value: int) -> float:
    channel = value / 255
    return channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4


def luminance(hex_color: str) -> float:
    value = hex_color.lstrip("#")
    if len(value) != 6:
        raise ValueError("仅支持六位 RGB 色值")
    red, green, blue = (int(value[index:index + 2], 16) for index in (0, 2, 4))
    return 0.2126 * srgb_channel(red) + 0.7152 * srgb_channel(green) + 0.0722 * srgb_channel(blue)


def contrast_ratio(first: str, second: str) -> float:
    light, dark = sorted((luminance(first), luminance(second)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


def parse_svg(path: Path) -> tuple[ET.Element, list[str]]:
    try:
        root = ET.fromstring(path.read_text())
    except (ET.ParseError, FileNotFoundError) as error:
        return ET.Element("svg"), [f"{path.name}: SVG 无法解析：{error}"]
    errors: list[str] = []
    if local_name(root.tag) != "svg":
        errors.append(f"{path.name}: 根元素不是 svg")
    if root.get("viewBox") != "0 0 1024 1024" and path not in (CLOUD, MASCOT_SHEET):
        errors.append(f"{path.name}: viewBox 必须为 0 0 1024 1024")
    for element in root.iter():
        name = local_name(element.tag)
        if name in {"script", "foreignObject"}:
            errors.append(f"{path.name}: 禁止元素 {name}")
        for key, value in element.attrib.items():
            if local_name(key) == "href" and not value.startswith("#"):
                errors.append(f"{path.name}: 外部资源引用 {value}")
    for required in ("title", "desc"):
        if not any(local_name(element.tag) == required for element in root):
            errors.append(f"{path.name}: 缺少 {required} 无障碍说明")
    return root, errors


def element_by_id(root: ET.Element, element_id: str) -> ET.Element | None:
    return next((element for element in root.iter() if element.get("id") == element_id), None)


def gradient_colors(root: ET.Element, gradient_id: str) -> list[str]:
    gradient = element_by_id(root, gradient_id)
    if gradient is None:
        return []
    return [
        element.get("stop-color", "")
        for element in gradient.iter()
        if local_name(element.tag) == "stop"
    ]


def cat_path(root: ET.Element) -> str:
    path = element_by_id(root, "cat-silhouette")
    if path is not None and local_name(path.tag) == "path":
        return path.get("d", "")
    return next(
        (
            element.get("d", "")
            for element in root.iter()
            if local_name(element.tag) == "path" and element.get("d", "").startswith("M512 800")
        ),
        "",
    )


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()[:24]
    if len(data) != 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("不是有效的 PNG 文件头")
    return struct.unpack(">II", data[16:24])


def validate_tokens(data: dict) -> list[str]:
    errors: list[str] = []
    if data.get("meta", {}).get("documentVersion") != "v0.7.0":
        errors.append("设计令牌 documentVersion 必须为 v0.7.0")
    brand = data.get("brand", {})
    if brand.get("nameZh") != "编程猫" or brand.get("mascot") != "小码":
        errors.append("品牌中文名或主题猫名称不符合规范")
    mascot_tokens = data.get("color", {}).get("mascot", {})
    if mascot_tokens.get("mint") != "#8CE8C6" or mascot_tokens.get("coral") != "#FF7A66":
        errors.append("主题猫薄荷/珊瑚令牌不符合规范")
    icon = data.get("color", {}).get("icon", {})
    expected = ["#2B3036", "#171A1E", "#0B0C0E"]
    actual = [icon.get("backgroundStart"), icon.get("backgroundMiddle"), icon.get("backgroundEnd")]
    if actual != expected:
        errors.append("图标底板渐变令牌不符合规范")
    cloud = data.get("knowledgeCloud", {})
    if cloud.get("minimumTouchDp", 0) < 48:
        errors.append("云图触控目标小于 48dp")
    if cloud.get("defaultVisibleLimit") != 19:
        errors.append("云图默认可见节点预算必须为 19")
    for theme in ("light", "dark"):
        colors = data.get("color", {}).get(theme, {})
        for role in ("textPrimary", "textSecondary"):
            surface = colors.get("surface", "#FFFFFF")
            if contrast_ratio(colors.get(role, "#000000"), surface) < 4.5:
                errors.append(f"{theme}.{role} 与一级表面对比度小于 4.5:1")
        if contrast_ratio(colors.get("focus", "#000000"), colors.get("surface", "#FFFFFF")) < 3:
            errors.append(f"{theme}.focus 与一级表面对比度小于 3:1")
    return errors


def validate_html(text: str) -> list[str]:
    errors: list[str] = []
    required = {
        "移动端 viewport": '<meta name="viewport"',
        "系统减少动效": "prefers-reduced-motion: reduce",
        "手动减少动效": 'id="motion-toggle"',
        "深色模式": 'id="theme-toggle"',
        "可见焦点": ":focus-visible",
        "节点键盘操作": 'event.key === \'Enter\'',
        "动态详情播报": 'aria-live="polite"',
        "等价节点列表": "等价列表",
        "云图展开状态": 'aria-pressed="false"',
    }
    for label, marker in required.items():
        if marker not in text:
            errors.append(f"视觉规范页缺少：{label}")
    if '<script src=' in text or '<img src="http' in text or '<link rel="stylesheet" href="http' in text:
        errors.append("视觉规范页包含外部运行资源")
    return errors


def self_test() -> None:
    assert round(contrast_ratio("#000000", "#FFFFFF"), 2) == 21.0
    assert round(contrast_ratio("#FFFFFF", "#FFFFFF"), 2) == 1.0
    assert contrast_ratio("#1D1D1F", "#FFFFFF") > 4.5
    header = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\rIHDR" + struct.pack(">II", 48, 48)
    temporary = ROOT / ".design-self-test.png"
    try:
        temporary.write_bytes(header)
        assert png_size(temporary) == (48, 48)
        temporary.write_bytes(b"not-a-png")
        try:
            png_size(temporary)
        except ValueError:
            pass
        else:
            raise AssertionError("损坏 PNG 未被拒绝")
    finally:
        temporary.unlink(missing_ok=True)
    minimal = '<meta name="viewport" content="width=device-width">'
    assert any("系统减少动效" in error for error in validate_html(minimal))
    assert validate_tokens({"meta": {"documentVersion": "v0.4"}})


def main() -> int:
    parser = argparse.ArgumentParser(description="校验 CodeTree 视觉设计资产")
    parser.add_argument("--self-test", action="store_true", help="只运行校验器正反例自测")
    args = parser.parse_args()
    self_test()
    print("Self-test: OK")
    if args.self_test:
        return 0

    errors: list[str] = []
    try:
        tokens = json.loads(TOKENS.read_text())
        errors.extend(validate_tokens(tokens))
    except (OSError, json.JSONDecodeError) as error:
        print("Validation: FAILED")
        print(f"- tokens.json 无法解析：{error}")
        return 1

    parsed: dict[Path, ET.Element] = {}
    for path in (MAIN_ICON, FOREGROUND_ICON, MONO_ICON, CLOUD, MASCOT, MASCOT_SHEET):
        root, svg_errors = parse_svg(path)
        parsed[path] = root
        errors.extend(svg_errors)

    icon_tokens = tokens["color"]["icon"]
    main = parsed.get(MAIN_ICON, ET.Element("svg"))
    foreground = parsed.get(FOREGROUND_ICON, ET.Element("svg"))
    mono = parsed.get(MONO_ICON, ET.Element("svg"))
    if gradient_colors(main, "ink-background") != [
        icon_tokens["backgroundStart"], icon_tokens["backgroundMiddle"], icon_tokens["backgroundEnd"]
    ]:
        errors.append("主图标底板渐变与令牌不一致")
    if gradient_colors(main, "silver-cat") != [
        icon_tokens["catStart"], icon_tokens["catMiddle"], icon_tokens["catEnd"]
    ]:
        errors.append("主图标银色渐变与令牌不一致")
    if gradient_colors(foreground, "silver-cat") != gradient_colors(main, "silver-cat"):
        errors.append("自适应前景银色渐变与主图标不一致")
    if not cat_path(main) or len({cat_path(main), cat_path(foreground), cat_path(mono)}) != 1:
        errors.append("主图标、自适应前景和单色层的猫轮廓不一致")
    for root, label in ((main, "主图标"), (foreground, "自适应前景")):
        if element_by_id(root, "inner-ear-left") is None or element_by_id(root, "inner-ear-right") is None:
            errors.append(f"{label}缺少珊瑚色耳内")
        nose = element_by_id(root, "cat-nose")
        if nose is None or nose.get("fill") != "#FF7A66":
            errors.append(f"{label}鼻尖必须使用珊瑚色")
    if element_by_id(mono, "cat-cutouts") is None or "linearGradient" in MONO_ICON.read_text():
        errors.append("单色图标必须具有透明镂空且不包含渐变")
    if "ink-background" in FOREGROUND_ICON.read_text():
        errors.append("自适应前景不应包含不透明底板")

    mascot = parsed.get(MASCOT, ET.Element("svg"))
    for element_id in ("mascot-body", "mascot-tail", "mascot-eye-left", "mascot-eye-right", "mascot-tail-tip", "inner-ear-left", "inner-ear-right"):
        if element_by_id(mascot, element_id) is None:
            errors.append(f"主题猫母版缺少结构：{element_id}")
    mascot_sheet = parsed.get(MASCOT_SHEET, ET.Element("svg"))
    for element_id in ("mascot-sheet-root", "brand-color-mint", "brand-color-coral", "brand-color-lemon", "brand-color-ink"):
        if element_by_id(mascot_sheet, element_id) is None:
            errors.append(f"主题猫设定板缺少结构：{element_id}")

    cloud = parsed.get(CLOUD, ET.Element("svg"))
    for element_id in ("cloud-root", "ring-primary", "ring-secondary", "cloud-edges"):
        if element_by_id(cloud, element_id) is None:
            errors.append(f"云图缺少结构层：{element_id}")
    cloud_text = CLOUD.read_text()
    for label in ("函数", "参数", "返回值", "调用", "作用域", "闭包", "高阶函数", "单元测试"):
        if label not in cloud_text:
            errors.append(f"云图缺少示例节点：{label}")

    errors.extend(validate_html(PREVIEW.read_text()))
    for name, expected in PNG_SIZES.items():
        path = DESIGN / "previews" / name
        try:
            actual = png_size(path)
            if actual != (expected, expected):
                errors.append(f"{name} 尺寸为 {actual}，期望 {(expected, expected)}")
        except (OSError, ValueError) as error:
            errors.append(f"{name} 校验失败：{error}")
    for name, expected in PNG_EXTRA.items():
        path = DESIGN / "previews" / name
        try:
            actual = png_size(path)
            if actual != expected:
                errors.append(f"{name} 尺寸为 {actual}，期望 {expected}")
        except (OSError, ValueError) as error:
            errors.append(f"{name} 校验失败：{error}")

    for marker in ("编程猫", "主题猫", "暖纸", "薄荷", "珊瑚", "云图扩散", "稳定布局算法", "减少动效", "美术验收清单"):
        if marker not in VISUAL_DOC.read_text():
            errors.append(f"视觉规范缺少章节要素：{marker}")
    for marker in ("每次更新必填记录", "标准更新顺序", "图标更新", "云图更新", "完成定义与回退", "validate_design.py"):
        if marker not in UPDATE_DOC.read_text():
            errors.append(f"更新条例缺少章节要素：{marker}")

    print("SVG assets: 6")
    print("PNG previews: 5")
    print("Design tokens: 1")
    print("Interactive preview: 1")
    print("Contrast checks: light/dark primary, secondary and focus")
    if errors:
        print("Validation: FAILED")
        for error in errors:
            print("- " + error)
        return 1
    print("Validation: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
