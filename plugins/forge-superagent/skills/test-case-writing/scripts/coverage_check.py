# Analyzes test coverage by matching source functions to test functions.
import sys
import os
import re
import json


def find_source_functions(project_dir):
    """Find public functions/methods in source files."""
    functions = []
    src_dirs = ["src/main", "src", "lib", "app"]
    for src_dir in src_dirs:
        base = os.path.join(project_dir, src_dir)
        if not os.path.isdir(base):
            continue
        for root, _, files in os.walk(base):
            if "test" in root.lower() or "spec" in root.lower():
                continue
            for fname in files:
                fpath = os.path.join(root, fname)
                if fname.endswith((".kt", ".java")):
                    functions.extend(extract_jvm_functions(fpath, fname))
                elif fname.endswith((".py",)):
                    functions.extend(extract_python_functions(fpath, fname))
                elif fname.endswith((".ts", ".js")):
                    functions.extend(extract_js_functions(fpath, fname))
    return functions


def extract_jvm_functions(fpath, fname):
    """Extract public methods from Kotlin/Java files."""
    funcs = []
    pattern = re.compile(r"(?:public\s+)?(?:fun|void|String|Int|Long|Boolean|List|Map)\s+(\w+)\s*\(")
    with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            for m in pattern.finditer(line):
                funcs.append({"name": m.group(1), "file": fname})
    return funcs


def extract_python_functions(fpath, fname):
    funcs = []
    with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            m = re.match(r"\s{0,4}def\s+(\w+)\s*\(", line)
            if m and not m.group(1).startswith("_"):
                funcs.append({"name": m.group(1), "file": fname})
    return funcs


def extract_js_functions(fpath, fname):
    funcs = []
    pattern = re.compile(r"(?:export\s+)?(?:async\s+)?function\s+(\w+)|(\w+)\s*[:=]\s*(?:async\s+)?\(")
    with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            for m in pattern.finditer(line):
                name = m.group(1) or m.group(2)
                if name and name[0].islower():
                    funcs.append({"name": name, "file": fname})
    return funcs


def find_test_functions(project_dir):
    """Collect names of test functions across the project."""
    test_names = set()
    for root, _, files in os.walk(project_dir):
        for fname in files:
            if not any(kw in fname.lower() for kw in ("test", "spec")):
                continue
            fpath = os.path.join(root, fname)
            with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
            for m in re.finditer(r"(?:fun\s+|def\s+|it\s*\(\s*['\"]|test\s*\(\s*['\"])(\w+)", content):
                test_names.add(m.group(1).lower())
    return test_names


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No project directory provided"}))
        sys.exit(1)

    project_dir = sys.argv[1]
    if not os.path.isdir(project_dir):
        print(json.dumps({"error": f"Directory not found: {project_dir}"}))
        sys.exit(1)

    source_funcs = find_source_functions(project_dir)
    test_names = find_test_functions(project_dir)
    tested = []
    untested = []
    for func in source_funcs:
        name_lower = func["name"].lower()
        if any(name_lower in t for t in test_names):
            tested.append(func)
        else:
            untested.append(func)

    total = len(source_funcs)
    pct = round((len(tested) / total) * 100, 1) if total > 0 else 0.0
    result = {
        "total_functions": total,
        "tested_functions": len(tested),
        "coverage_pct": pct,
        "untested": [f"{u['file']}::{u['name']}" for u in untested],
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
