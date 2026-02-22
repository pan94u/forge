#!/usr/bin/env python3
# Scan HTTP endpoints and output API inventory (JSON)

"""
Usage: python3 scan_endpoints.py [PROJECT_ROOT]

Scans Kotlin/Java source files for Spring Web annotations
(@GetMapping, @PostMapping, etc.) and produces an API inventory.

Output: JSON to stdout
"""

import os
import re
import json
import sys


# Spring Web mapping annotations
MAPPING_PATTERNS = [
    (r'@(Get|Post|Put|Delete|Patch)Mapping\s*\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?', None),
    (r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?.*?method\s*=\s*\[?\s*RequestMethod\.(\w+)', "request_mapping"),
]

# Controller-level RequestMapping
CONTROLLER_MAPPING = re.compile(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?')


def scan_file(file_path, root):
    """Scan a single file for HTTP endpoints."""
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
    except (IOError, UnicodeDecodeError):
        return []

    endpoints = []
    rel_path = os.path.relpath(file_path, root)

    # Find controller-level base path
    base_path = ""
    controller_match = CONTROLLER_MAPPING.search(content)
    if controller_match and controller_match.group(1):
        base_path = controller_match.group(1)

    # Find method-level mappings
    lines = content.split("\n")
    for i, line in enumerate(lines):
        stripped = line.strip()

        # Check standard mappings: @GetMapping("/path")
        for method_name in ["Get", "Post", "Put", "Delete", "Patch"]:
            pattern = rf'@{method_name}Mapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"'
            match = re.search(pattern, stripped)
            if match:
                path = base_path + match.group(1)
                func_name = find_function_name(lines, i)
                endpoints.append({
                    "method": method_name.upper(),
                    "path": path,
                    "file": rel_path,
                    "line": i + 1,
                    "function": func_name
                })
                continue

            # No-arg mapping: @GetMapping
            no_arg_pattern = rf'@{method_name}Mapping\s*$'
            if re.search(no_arg_pattern, stripped):
                func_name = find_function_name(lines, i)
                endpoints.append({
                    "method": method_name.upper(),
                    "path": base_path or "/",
                    "file": rel_path,
                    "line": i + 1,
                    "function": func_name
                })

    return endpoints


def find_function_name(lines, annotation_line):
    """Find the function name after an annotation line."""
    for j in range(annotation_line + 1, min(annotation_line + 5, len(lines))):
        # Kotlin: fun functionName(
        match = re.search(r'fun\s+(\w+)\s*\(', lines[j])
        if match:
            return match.group(1)
        # Java: public ReturnType functionName(
        match = re.search(r'(?:public|private|protected)?\s*\w+\s+(\w+)\s*\(', lines[j])
        if match:
            return match.group(1)
    return "unknown"


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    root = os.path.abspath(root)

    endpoints = []

    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in (".git", "build", "target", "node_modules")]

        for filename in filenames:
            if filename.endswith((".kt", ".java")):
                file_path = os.path.join(dirpath, filename)
                endpoints.extend(scan_file(file_path, root))

    # Sort by path then method
    endpoints.sort(key=lambda e: (e["path"], e["method"]))

    result = {
        "project_root": root,
        "total_endpoints": len(endpoints),
        "endpoints": endpoints
    }

    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
