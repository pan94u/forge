# Finds public methods in Kotlin/Java source files that lack corresponding tests.
import sys
import os
import re
import json


def find_source_files(project_dir):
    """Locate .kt and .java source files outside test directories."""
    sources = []
    for root, _, files in os.walk(project_dir):
        if "test" in root.lower() or "spec" in root.lower():
            continue
        for fname in files:
            if fname.endswith((".kt", ".java")):
                sources.append(os.path.join(root, fname))
    return sources


def extract_public_methods(filepath):
    """Extract public method names from a Kotlin or Java source file."""
    methods = []
    is_kotlin = filepath.endswith(".kt")
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        for line_num, line in enumerate(f, 1):
            if is_kotlin:
                m = re.search(r"^\s*(?:override\s+)?fun\s+(\w+)\s*\(", line)
                if m and not m.group(1).startswith("_"):
                    methods.append({"name": m.group(1), "line": line_num})
            else:
                m = re.search(r"^\s*(?:public\s+)(?:\w+\s+)+(\w+)\s*\(", line)
                if m and not m.group(1).startswith("_"):
                    methods.append({"name": m.group(1), "line": line_num})
    return methods


def find_test_file(source_path, project_dir):
    """Attempt to find a test file corresponding to a source file."""
    basename = os.path.splitext(os.path.basename(source_path))[0]
    test_names = [f"{basename}Test", f"{basename}Tests", f"{basename}Spec"]
    for root, _, files in os.walk(project_dir):
        if "test" not in root.lower() and "spec" not in root.lower():
            continue
        for fname in files:
            stem = os.path.splitext(fname)[0]
            if stem in test_names:
                return os.path.join(root, fname)
    return None


def extract_test_method_names(test_path):
    """Get all test method names or test descriptions from a test file."""
    names = set()
    with open(test_path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            for m in re.finditer(r"fun\s+`?([^`(\s]+)`?\s*\(", line):
                names.add(m.group(1).lower())
            for m in re.finditer(r"void\s+(\w+)\s*\(", line):
                names.add(m.group(1).lower())
    return names


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No project directory provided"}))
        sys.exit(1)

    project_dir = sys.argv[1]
    if not os.path.isdir(project_dir):
        print(json.dumps({"error": f"Directory not found: {project_dir}"}))
        sys.exit(1)

    source_files = find_source_files(project_dir)
    untested_methods = []
    total_methods = 0
    coverage_files = 0

    for src in source_files:
        methods = extract_public_methods(src)
        if not methods:
            continue
        total_methods += len(methods)
        test_file = find_test_file(src, project_dir)
        if test_file:
            coverage_files += 1
            test_names = extract_test_method_names(test_file)
            for method in methods:
                name_lower = method["name"].lower()
                if not any(name_lower in tn for tn in test_names):
                    untested_methods.append({
                        "source_file": os.path.basename(src),
                        "method": method["name"],
                        "line": method["line"],
                    })
        else:
            for method in methods:
                untested_methods.append({
                    "source_file": os.path.basename(src),
                    "method": method["name"],
                    "line": method["line"],
                    "note": "No test file found",
                })

    result = {
        "untested_methods": untested_methods,
        "coverage_files": coverage_files,
        "total_methods": total_methods,
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
