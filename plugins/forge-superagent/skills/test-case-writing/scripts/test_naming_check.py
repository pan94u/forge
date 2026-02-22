# Checks test naming conventions for JUnit5 and Jest test files.
import sys
import os
import re
import json

BAD_NAMES = re.compile(r"^test\d+$|^test$|^it\d+$|^check$|^verify$|^foo$|^bar$", re.IGNORECASE)
SHORT_THRESHOLD = 4


def check_junit_file(filepath):
    """Check JUnit5/Kotlin test method names for descriptive naming."""
    violations = []
    fname = os.path.basename(filepath)
    pattern = re.compile(r"(?:@Test|@ParameterizedTest)\s*\n\s*(?:fun|public\s+void)\s+(\w+)")
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()
    for line_num, line in enumerate(content.splitlines(), 1):
        m = re.search(r"(?:fun|void)\s+(`[^`]+`|(\w+))\s*\(", line)
        if m:
            preceding_lines = content.splitlines()[max(0, line_num - 3):line_num]
            if not any("@Test" in pl or "@ParameterizedTest" in pl for pl in preceding_lines):
                continue
            name = m.group(2) if m.group(2) else m.group(1).strip("`")
            if BAD_NAMES.match(name):
                violations.append({
                    "file": fname, "line": line_num, "method": name,
                    "reason": "Non-descriptive test name",
                })
            elif len(name) < SHORT_THRESHOLD:
                violations.append({
                    "file": fname, "line": line_num, "method": name,
                    "reason": f"Test name too short ({len(name)} chars)",
                })
    return violations


def check_jest_file(filepath):
    """Check Jest describe/it blocks for meaningful descriptions."""
    violations = []
    fname = os.path.basename(filepath)
    pattern = re.compile(r"(?:describe|it|test)\s*\(\s*['\"]([^'\"]*)['\"]")
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        for line_num, line in enumerate(f, 1):
            for m in pattern.finditer(line):
                desc = m.group(1).strip()
                if not desc or len(desc) < SHORT_THRESHOLD:
                    violations.append({
                        "file": fname, "line": line_num, "description": desc or "(empty)",
                        "reason": "Test description too short or empty",
                    })
                elif BAD_NAMES.match(desc.replace(" ", "")):
                    violations.append({
                        "file": fname, "line": line_num, "description": desc,
                        "reason": "Non-descriptive test description",
                    })
    return violations


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"status": "fail", "error": "No project directory provided"}))
        sys.exit(1)

    project_dir = sys.argv[1]
    if not os.path.isdir(project_dir):
        print(json.dumps({"status": "fail", "error": f"Directory not found: {project_dir}"}))
        sys.exit(1)

    all_violations = []
    test_files_checked = 0
    for root, _, files in os.walk(project_dir):
        for fname in files:
            fpath = os.path.join(root, fname)
            if fname.endswith((".kt", ".java")) and "test" in fname.lower():
                test_files_checked += 1
                all_violations.extend(check_junit_file(fpath))
            elif fname.endswith((".ts", ".js", ".tsx", ".jsx")) and (
                "test" in fname.lower() or "spec" in fname.lower()
            ):
                test_files_checked += 1
                all_violations.extend(check_jest_file(fpath))

    status = "pass" if not all_violations else "fail"
    result = {
        "status": status,
        "violations": all_violations,
        "test_files_checked": test_files_checked,
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
