# Checks naming conventions: PascalCase classes, camelCase functions, UPPER_SNAKE_CASE constants.
import sys, os, json, re

EXTENSIONS = {".kt", ".java", ".ts"}

def find_files(root):
    found = []
    for dirpath, _, filenames in os.walk(root):
        for f in filenames:
            if os.path.splitext(f)[1] in EXTENSIONS:
                found.append(os.path.join(dirpath, f))
    return found

def is_pascal(n): return bool(re.match(r"^[A-Z][a-zA-Z0-9]*$", n))
def is_camel(n): return bool(re.match(r"^[a-z][a-zA-Z0-9]*$", n))
def is_upper_snake(n): return bool(re.match(r"^[A-Z][A-Z0-9_]*$", n)) and "__" not in n

def check_kotlin_java(filepath, content):
    violations = []
    for i, line in enumerate(content.splitlines(), 1):
        s = line.strip()
        if s.startswith("//") or s.startswith("*") or s.startswith("/*"):
            continue
        m = re.match(r"(?:.*\s)?(class|interface|enum|object)\s+(\w+)", s)
        if m and not is_pascal(m.group(2)):
            violations.append({"file": filepath, "line": i, "name": m.group(2),
                "message": f"{m.group(1).capitalize()} '{m.group(2)}' should be PascalCase"})
        m = re.match(r"(?:.*\s)?fun\s+(\w+)", s)
        if m and not is_camel(m.group(1)) and not m.group(1).startswith("_"):
            violations.append({"file": filepath, "line": i, "name": m.group(1),
                "message": f"Function '{m.group(1)}' should be camelCase"})
        m = re.match(r"\s*(?:const\s+)?val\s+([A-Z_][A-Z0-9_]*)\s*[=:]", s)
        if m and not is_upper_snake(m.group(1)) and m.group(1).upper() == m.group(1):
            violations.append({"file": filepath, "line": i, "name": m.group(1),
                "message": f"Constant '{m.group(1)}' should be UPPER_SNAKE_CASE"})
        m = re.match(r"\s*(?:public|private|protected)?\s*static\s+final\s+\w+\s+(\w+)", s)
        if m and not is_upper_snake(m.group(1)) and m.group(1) == m.group(1).upper():
            violations.append({"file": filepath, "line": i, "name": m.group(1),
                "message": f"Constant '{m.group(1)}' should be UPPER_SNAKE_CASE"})
    return violations

def check_typescript(filepath, content):
    violations = []
    for i, line in enumerate(content.splitlines(), 1):
        s = line.strip()
        if s.startswith("//") or s.startswith("*") or s.startswith("/*"):
            continue
        m = re.match(r"(?:export\s+)?(?:default\s+)?(?:function|class)\s+(\w+)", s)
        if m:
            name = m.group(1)
            if "class" in s.split(name)[0] and not is_pascal(name):
                violations.append({"file": filepath, "line": i, "name": name,
                    "message": f"Class '{name}' should be PascalCase"})
            elif "class" not in s.split(name)[0] and not is_camel(name) and not is_pascal(name):
                violations.append({"file": filepath, "line": i, "name": name,
                    "message": f"Function '{name}' should be camelCase"})
        m = re.match(r"\s*(?:export\s+)?(?:const|let)\s+(\w+)\s*=\s*(?:\([^)]*\)|[^=])*\s*=>", s)
        if m and not is_camel(m.group(1)) and not is_pascal(m.group(1)):
            violations.append({"file": filepath, "line": i, "name": m.group(1),
                "message": f"Function/component '{m.group(1)}' should be camelCase or PascalCase"})
        m = re.match(r"\s*(?:export\s+)?(?:interface|type)\s+(\w+)", s)
        if m and not is_pascal(m.group(1)):
            violations.append({"file": filepath, "line": i, "name": m.group(1),
                "message": f"Type '{m.group(1)}' should be PascalCase"})
    return violations

def main():
    if len(sys.argv) < 2:
        print(json.dumps({"status": "fail", "violations": [{"message": "No directory path provided"}], "files_checked": 0}))
        sys.exit(1)
    root = sys.argv[1]
    if not os.path.isdir(root):
        print(json.dumps({"status": "fail", "violations": [{"message": f"Directory not found: {root}"}], "files_checked": 0}))
        sys.exit(1)
    files = find_files(root)
    violations = []
    for fp in files:
        try:
            content = open(fp, "r", encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        ext = os.path.splitext(fp)[1]
        if ext in (".kt", ".java"):
            violations.extend(check_kotlin_java(fp, content))
        elif ext == ".ts":
            violations.extend(check_typescript(fp, content))
    status = "fail" if violations else "pass"
    print(json.dumps({"status": status, "violations": violations, "files_checked": len(files)}, indent=2))

if __name__ == "__main__":
    main()
