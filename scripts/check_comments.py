#!/usr/bin/env python3

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SKIPPED_DIRECTORIES = {
    ".git",
    ".gradle",
    ".artifacts",
    "build",
    "coverage",
    "dist",
    "node_modules",
}
COMMENT_PATTERNS = {
    ".java": re.compile(r"(?:\s*//|\s*/\*|\s*\*)\s*(.*)"),
    ".ts": re.compile(r"(?:\s*//|\s*/\*|\s*\*)\s*(.*)"),
    ".tsx": re.compile(r"(?:\s*//|\s*/\*|\s*\*)\s*(.*)"),
    ".js": re.compile(r"(?:\s*//|\s*/\*|\s*\*)\s*(.*)"),
    ".mjs": re.compile(r"(?:\s*//|\s*/\*|\s*\*)\s*(.*)"),
    ".css": re.compile(r"^\s*/\*\s*(.*)"),
    ".gradle": re.compile(r"^\s*//\s*(.*)"),
    ".sh": re.compile(r"^\s*#\s*(.*)"),
    ".yml": re.compile(r"^\s*#\s*(.*)"),
    ".yaml": re.compile(r"^\s*#\s*(.*)"),
    ".properties": re.compile(r"^\s*[#!]\s*(.*)"),
    ".sql": re.compile(r"^\s*--\s*(.*)"),
    ".xml": re.compile(r"^\s*<!--\s*(.*)"),
}
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")


def source_files() -> list[Path]:
    files: list[Path] = []
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIPPED_DIRECTORIES for part in path.relative_to(ROOT).parts):
            continue
        if path.suffix in COMMENT_PATTERNS:
            files.append(path)
    return sorted(files)


def main() -> int:
    violations: list[str] = []
    for path in source_files():
        matcher = COMMENT_PATTERNS[path.suffix]
        for line_number, line in enumerate(
            path.read_text(encoding="utf-8", errors="replace").splitlines(), start=1
        ):
            match = matcher.search(line)
            if match and CJK.search(match.group(1)):
                violations.append(
                    f"{path.relative_to(ROOT)}:{line_number}: Chinese comment text"
                )
    if violations:
        print("\n".join(violations))
        return 1
    print("No Chinese comment text found in source comments.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
