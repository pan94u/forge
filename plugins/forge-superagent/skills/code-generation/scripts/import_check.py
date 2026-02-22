# Checks for import issues: unused imports, wildcard imports in Kotlin/Java files.
import sys
import os
import json
import re

EXTENSIONS = {".kt", ".java", ".ts"}

def find_files(root):
    found = []
    for dirpath, _, filenames in os.walk(root):
        for f in filenames:
            if os.path.splitext(f)[1] in EXTENSIONS:
                found.append(os.path.join(dirpath, f))
    return found

def extract_imports(content, ext):
    imports = []
    for i, line in enumerate(content.splitlines(), 1):
        stripped = line.strip()
        if ext in (".kt", ".java"):
            m = re.match(r"^import\s+(static\s+)?([\w.]+(?:\.\*)?)\s*;?\s*$", stripped)
            if m:
                full_path = m.group(2)
                is_wildcard = full_path.endswith(".*")
                name = full_path.rsplit(".", 1)[-1] if not is_wildcard else "*"
                imports.append({"line": i, "path": full_path, "name": name, "wildcard": is_wildcard})
        elif ext == ".ts":
            m = re.match(r"^import\s+\{([^}]+)\}\s+from\s+['\"]", stripped)
            if m:
                names = [n.strip().split(" as ")[-1].strip() for n in m.group(1).split(",")]
                for n in names:
                    if n:
                        imports.append({"line": i, "path": stripped, "name": n, "wildcard": False})
            m2 = re.match(r"^import\s+(\w+)\s+from\s+['\"]", stripped)
            if m2:
                imports.append({"line": i, "path": stripped, "name": m2.group(1), "wildcard": False})
            m3 = re.match(r"^import\s+\*\s+as\s+(\w+)\s+from\s+['\"]", stripped)
            if m3:
                imports.append({"line": i, "path": stripped, "name": m3.group(1), "wildcard": False})
    return imports

def get_body(content, ext):
    lines = content.splitlines()
    body_lines = []
    for line in lines:
        stripped = line.strip()
        if ext in (".kt", ".java") and re.match(r"^(import|package)\s", stripped):
            continue
        if ext == ".ts" and re.match(r"^import\s", stripped):
            continue
        body_lines.append(line)
    return "\n".join(body_lines)

def check_file(filepath, content, ext):
    issues = []
    imports = extract_imports(content, ext)
    body = get_body(content, ext)
    for imp in imports:
        if imp["wildcard"] and ext in (".kt", ".java"):
            issues.append({
                "file": filepath, "line": imp["line"], "type": "wildcard_import",
                "message": f"Wildcard import: {imp['path']}"
            })
        elif not imp["wildcard"]:
            pattern = r"\b" + re.escape(imp["name"]) + r"\b"
            if not re.search(pattern, body):
                issues.append({
                    "file": filepath, "line": imp["line"], "type": "unused_import",
                    "message": f"Unused import: {imp['name']} ({imp['path']})"
                })
    return issues

def main():
    if len(sys.argv) < 2:
        print(json.dumps({"status": "fail", "issues": [{"message": "No directory path provided"}], "files_checked": 0}))
        sys.exit(1)
    root = sys.argv[1]
    if not os.path.isdir(root):
        print(json.dumps({"status": "fail", "issues": [{"message": f"Directory not found: {root}"}], "files_checked": 0}))
        sys.exit(1)
    files = find_files(root)
    all_issues = []
    for fp in files:
        try:
            content = open(fp, "r", encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        ext = os.path.splitext(fp)[1]
        all_issues.extend(check_file(fp, content, ext))
    status = "fail" if all_issues else "pass"
    print(json.dumps({"status": status, "issues": all_issues, "files_checked": len(files)}, indent=2))

if __name__ == "__main__":
    main()
