#!/usr/bin/env python3
"""diff_strings_xml_keys.py

对两个（或多个）Android/Compose strings.xml 进行 key（name 属性）层面的差异比较。

以 --base 指定“母版”文件，输出每个 target 文件相对母版：
- 缺少了哪些 key（base 有、target 没有）
- 多出了哪些 key（target 有、base 没有）

默认只比较 <string name="...">，如需比较所有带 name 属性的资源项可使用 --tags "*"。

示例：
  python3 diff_strings_xml_keys.py --base composeApp/src/commonMain/composeResources/values/strings.xml \
      composeApp/src/commonMain/composeResources/values-en/strings.xml

  # 比较所有带 name 的资源项（如 string/plurals/string-array 等）
  python3 diff_strings_xml_keys.py --base base.xml --tags "*" target.xml
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Optional, Sequence, Set


@dataclass(frozen=True)
class XmlKeysResult:
    file_path: str
    keys: Set[str]
    duplicate_keys: Set[str]


def parse_xml_root(file_path: str) -> ET.Element:
    """解析 XML 并返回根节点。"""
    try:
        tree = ET.parse(file_path)
        return tree.getroot()
    except FileNotFoundError:
        print(f"文件未找到: {file_path}", file=sys.stderr)
        raise
    except ET.ParseError as e:
        print(f"XML解析错误 {file_path}: {e}", file=sys.stderr)
        raise


def _normalize_tags_arg(tags_arg: str) -> Optional[Set[str]]:
    """将 --tags 参数规范化。

    Returns:
        - None: 代表匹配所有带 name 属性的元素（即传入了 "*"）
        - set[str]: 代表只匹配这些 tag
    """
    tags_arg = tags_arg.strip()
    if tags_arg == "*":
        return None

    parts = [p.strip() for p in tags_arg.split(",")]
    parts = [p for p in parts if p]
    if not parts:
        # 兜底：若用户传入空字符串，当做只比较 string
        return {"string"}
    return set(parts)


def collect_named_resource_keys(file_path: str, tags: Optional[Set[str]]) -> XmlKeysResult:
    """收集 <resources> 下所有（或指定 tag）带 name 属性的资源 key。

    Args:
        file_path: XML 文件路径
        tags:
            - None: 收集所有带 name 属性的子元素
            - set[str]: 仅收集 tag 在集合中的子元素（例如 {"string"}）

    Notes:
        Android 资源文件里，通常命名资源是 <resources> 的直接子节点。
        此处按该约定遍历 root 的一层子元素。
    """
    root = parse_xml_root(file_path)

    keys: Set[str] = set()
    dupes: Set[str] = set()

    for elem in list(root):
        if not isinstance(elem.tag, str):
            # 跳过注释等特殊节点
            continue

        if tags is not None and elem.tag not in tags:
            continue

        name = elem.get("name")
        if not name:
            continue

        if name in keys:
            dupes.add(name)
        keys.add(name)

    return XmlKeysResult(file_path=file_path, keys=keys, duplicate_keys=dupes)


def _sorted_keys(keys: Iterable[str]) -> list[str]:
    # Android key 通常是 snake_case，小写；这里做一个稳定的大小写不敏感排序
    return sorted(keys, key=lambda s: (s.lower(), s))


def _print_section(title: str, keys: Sequence[str], prefix: str) -> None:
    print(f"{title}: {len(keys)}")
    for k in keys:
        print(f"  {prefix} {k}")


def diff_against_base(base: XmlKeysResult, target: XmlKeysResult) -> tuple[Set[str], Set[str]]:
    """返回 (missing, extra)。"""
    missing = base.keys - target.keys
    extra = target.keys - base.keys
    return missing, extra


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="比较两个/多个 strings.xml 在 key(name) 上的差异：相对 --base 缺少/多出哪些 key",
    )
    parser.add_argument(
        "--base",
        required=True,
        help="母版 XML 文件路径（base 有而 target 没有的 key 会被视为 missing）",
    )
    parser.add_argument(
        "targets",
        nargs="+",
        help="要对比的目标 XML 文件路径（可传多个）",
    )
    parser.add_argument(
        "--tags",
        default="string",
        help='要比较的资源 tag，逗号分隔。默认仅 "string"。传 "*" 表示比较所有带 name 属性的资源项。',
    )
    parser.add_argument(
        "--fail-on-diff",
        action="store_true",
        help="如果发现 missing/extra，则退出码为 1（便于 CI 检查）",
    )

    args = parser.parse_args(argv)

    tags = _normalize_tags_arg(args.tags)

    base_path = str(Path(args.base))
    base_result = collect_named_resource_keys(base_path, tags)

    if base_result.duplicate_keys:
        print(
            f"警告: base 文件存在重复 key（同名 name 出现多次）: {len(base_result.duplicate_keys)}",
            file=sys.stderr,
        )
        for k in _sorted_keys(base_result.duplicate_keys):
            print(f"  ! {k}", file=sys.stderr)

    any_diff = False

    for t in args.targets:
        target_path = str(Path(t))
        target_result = collect_named_resource_keys(target_path, tags)

        if target_result.duplicate_keys:
            print(
                f"警告: target 文件存在重复 key（同名 name 出现多次）: {target_path} ({len(target_result.duplicate_keys)})",
                file=sys.stderr,
            )
            for k in _sorted_keys(target_result.duplicate_keys):
                print(f"  ! {k}", file=sys.stderr)

        missing, extra = diff_against_base(base_result, target_result)

        print("=" * 80)
        print(f"Base   : {base_result.file_path}")
        print(f"Target : {target_result.file_path}")
        print(f"Tags   : {'*' if tags is None else ','.join(sorted(tags))}")
        print(f"Base keys  : {len(base_result.keys)}")
        print(f"Target keys: {len(target_result.keys)}")

        missing_sorted = _sorted_keys(missing)
        extra_sorted = _sorted_keys(extra)

        _print_section("Missing (base 有, target 没有)", missing_sorted, "-")
        _print_section("Extra   (target 有, base 没有)", extra_sorted, "+")

        if missing or extra:
            any_diff = True

    if args.fail_on_diff and any_diff:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
