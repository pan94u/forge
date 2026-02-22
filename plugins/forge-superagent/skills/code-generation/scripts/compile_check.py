# Checks for common compilation errors: unmatched braces, missing package declarations, missing imports.
import sys
import os
import json
import re

EXTENSIONS = {".kt", ".java", ".ts"}
BRACKET_PAIRS = {"(": ")", "[": "]", "{": "}"}
CLOSING = set(BRACKET_PAIRS.values())

def find_files(root):
    found = []
    for dirpath, _, filenames in os.walk(root):
        for f in filenames:
            if os.path.splitext(f)[1] in EXTENSIONS:
                found.append(os.path.join(dirpath, f))
    return found

def check_brackets(filepath, content):
    errors = []
    stack = []
    for i, line in enumerate(content.splitlines(), 1):
        in_string = None
        j = 0
        while j < len(line):
            ch = line[j]
            if ch == "\\" and in_string:
                j += 2
                continue
            if ch in ('"', "'", "`") and (in_string is None or in_string == ch):
                in_string = None if in_string == ch else ch
            elif not in_string:
                if ch in BRACKET_PAIRS:
                    stack.append((ch, i))
                elif ch in CLOSING:
                    if stack and BRACKET_PAIRS.get(stack[-1][0]) == ch:
                        stack.pop()
                    else:
                        errors.append({"file": filepath, "line": i, "message": f"Unmatched closing '{ch}'"})
            j += 1
    for bracket, line_num in stack:
        errors.append({"file": filepath, "line": line_num, "message": f"Unmatched opening '{bracket}'"})
    return errors

def check_package(filepath, content, ext):
    warnings = []
    if ext in (".kt", ".java"):
        stripped = "\n".join(l for l in content.splitlines() if not l.strip().startswith("//"))
        if stripped.strip() and not re.search(r"^\s*package\s+", stripped, re.MULTILINE):
            warnings.append({"file": filepath, "line": 1, "message": "Missing package declaration"})
    return warnings

def check_missing_imports(filepath, content, ext):
    warnings = []
    if ext not in (".kt", ".java"):
        return warnings
    imported = set()
    for m in re.finditer(r"^\s*import\s+[\w.]+\.(\w+)", content, re.MULTILINE):
        imported.add(m.group(1))
    type_refs = set(re.findall(r"\b([A-Z][A-Za-z0-9]+)\b", content))
    body_lines = [l for l in content.splitlines() if not re.match(r"^\s*(import|package)\s", l)]
    body = "\n".join(body_lines)
    body_refs = set(re.findall(r"\b([A-Z][A-Za-z0-9]+)\b", body))
    builtin = {"String", "Int", "Long", "Boolean", "Float", "Double", "Unit", "Any", "Object",
               "List", "Map", "Set", "Array", "Void", "Integer", "Override", "Nullable",
               "NonNull", "Throws", "Deprecated", "SuppressWarnings"}
    for ref in body_refs:
        if ref not in imported and ref not in builtin and len(ref) > 1:
            decl_pattern = rf"(class|interface|enum|object|typealias|fun)\s+{re.escape(ref)}\b"
            if not re.search(decl_pattern, content):
                warnings.append({"file": filepath, "message": f"Type '{ref}' used but not imported or declared locally"})
    return warnings

def main():
    if len(sys.argv) < 2:
        print(json.dumps({"status": "fail", "errors": [{"message": "No directory path provided"}], "warnings": [], "files_checked": 0}))
        sys.exit(1)
    root = sys.argv[1]
    if not os.path.isdir(root):
        print(json.dumps({"status": "fail", "errors": [{"message": f"Directory not found: {root}"}], "warnings": [], "files_checked": 0}))
        sys.exit(1)
    files = find_files(root)
    errors, warnings = [], []
    for fp in files:
        try:
            content = open(fp, "r", encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        ext = os.path.splitext(fp)[1]
        errors.extend(check_brackets(fp, content))
        warnings.extend(check_package(fp, content, ext))
        warnings.extend(check_missing_imports(fp, content, ext))
    status = "fail" if errors else "pass"
    print(json.dumps({"status": status, "errors": errors, "warnings": warnings, "files_checked": len(files)}, indent=2))

if __name__ == "__main__":
    main()
