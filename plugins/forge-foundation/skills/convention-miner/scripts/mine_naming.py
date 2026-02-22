#!/usr/bin/env python3
# Analyze naming patterns in the codebase

"""
Usage: python3 mine_naming.py [PROJECT_ROOT]

Scans source files and analyzes naming conventions:
- Class naming patterns (suffixes, prefixes)
- Method naming patterns
- Package/directory structure
- Test naming conventions

Output: JSON to stdout
"""

import os
import re
import json
import sys
from collections import Counter, defaultdict


def scan_kotlin_java(root):
    """Scan Kotlin and Java files for naming patterns."""
    class_names = []
    method_names = []
    test_names = []
    packages = []

    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in (".git", "build", "target", "node_modules")]

        for filename in filenames:
            if not filename.endswith((".kt", ".java")):
                continue

            file_path = os.path.join(dirpath, filename)
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
            except (IOError, UnicodeDecodeError):
                continue

            is_test = "test" in dirpath.lower() or filename.endswith("Test.kt") or filename.endswith("Test.java")

            # Extract package
            pkg_match = re.search(r'package\s+([\w.]+)', content)
            if pkg_match:
                packages.append(pkg_match.group(1))

            # Extract class names
            for match in re.finditer(r'(?:class|interface|enum\s+class|object)\s+(\w+)', content):
                class_names.append(match.group(1))

            # Extract method/function names
            if filename.endswith(".kt"):
                for match in re.finditer(r'fun\s+`?([^`(]+)`?\s*\(', content):
                    name = match.group(1).strip()
                    if is_test:
                        test_names.append(name)
                    else:
                        method_names.append(name)
            else:  # Java
                for match in re.finditer(r'(?:public|private|protected)\s+\w+\s+(\w+)\s*\(', content):
                    name = match.group(1)
                    if is_test:
                        test_names.append(name)
                    else:
                        method_names.append(name)

    return class_names, method_names, test_names, packages


def analyze_suffixes(names, min_count=2):
    """Find common suffixes in a list of names."""
    suffix_counter = Counter()
    common_suffixes = [
        "Controller", "Service", "Repository", "Entity", "Dto",
        "Request", "Response", "Config", "Exception", "Test",
        "Mapper", "Validator", "Factory", "Builder", "Handler",
        "Listener", "Event", "Provider", "Client", "Adapter"
    ]

    for name in names:
        for suffix in common_suffixes:
            if name.endswith(suffix) and name != suffix:
                suffix_counter[suffix] += 1

    return {k: v for k, v in suffix_counter.most_common() if v >= min_count}


def analyze_test_patterns(test_names):
    """Analyze test naming conventions."""
    patterns = Counter()

    for name in test_names:
        if name.startswith("should_"):
            patterns["should_X_when_Y"] += 1
        elif name.startswith("should "):
            patterns["should X when Y (backtick)"] += 1
        elif name.startswith("test"):
            patterns["testMethodName"] += 1
        elif "_" in name and "when" in name.lower():
            patterns["X_when_Y"] += 1
        else:
            patterns["other"] += 1

    return dict(patterns.most_common())


def analyze_packages(packages):
    """Analyze package structure patterns."""
    layer_counter = Counter()
    common_layers = ["controller", "service", "repository", "model",
                     "dto", "config", "exception", "util", "entity",
                     "websocket", "security"]

    for pkg in packages:
        parts = pkg.split(".")
        for part in parts:
            if part in common_layers:
                layer_counter[part] += 1

    return dict(layer_counter.most_common())


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    root = os.path.abspath(root)

    class_names, method_names, test_names, packages = scan_kotlin_java(root)

    result = {
        "project_root": root,
        "class_count": len(class_names),
        "method_count": len(method_names),
        "test_count": len(test_names),
        "class_suffixes": analyze_suffixes(class_names),
        "test_naming_patterns": analyze_test_patterns(test_names),
        "package_layers": analyze_packages(packages),
        "sample_classes": sorted(set(class_names))[:30],
        "sample_methods": sorted(set(method_names))[:30]
    }

    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
