# Checks for layered architecture violations in Kotlin/Java projects by scanning import patterns.
import sys
import os
import re
import json


def find_source_files(project_dir):
    """Recursively find all .kt and .java files."""
    source_files = []
    for root, _, files in os.walk(project_dir):
        for f in files:
            if f.endswith(".kt") or f.endswith(".java"):
                source_files.append(os.path.join(root, f))
    return source_files


def classify_file(filepath):
    """Classify a file into an architectural layer based on its path and name."""
    lower = filepath.lower()
    if "controller" in lower or "resource" in lower or "endpoint" in lower:
        return "controller"
    if "service" in lower or "usecase" in lower or "use_case" in lower:
        return "service"
    if "repository" in lower or "dao" in lower:
        return "repository"
    return None


def extract_imports(filepath):
    """Extract all import statements from a source file."""
    imports = []
    import_pattern = re.compile(r"^\s*import\s+([\w.]+)")
    with open(filepath, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            match = import_pattern.match(line)
            if match:
                imports.append(match.group(1))
    return imports


REPO_PATTERNS = re.compile(
    r"\b(Repository|Dao|CrudRepository|JpaRepository|MongoRepository)\b", re.IGNORECASE
)
HTTP_PATTERNS = re.compile(
    r"\b(HttpServletRequest|HttpServletResponse|RequestMapping|GetMapping|PostMapping|"
    r"PutMapping|DeleteMapping|PatchMapping|RestController|Controller|WebMvcConfigurer|"
    r"ResponseEntity|RequestBody|RequestParam|PathVariable|springframework\.web|"
    r"javax\.servlet|jakarta\.servlet|io\.ktor\.server|io\.ktor\.http)\b"
)


def check_violations(project_dir):
    """Scan source files for layered architecture violations."""
    source_files = find_source_files(project_dir)
    violations = []

    for filepath in source_files:
        layer = classify_file(filepath)
        if layer is None:
            continue
        imports = extract_imports(filepath)
        rel_path = os.path.relpath(filepath, project_dir)

        if layer == "controller":
            for imp in imports:
                if REPO_PATTERNS.search(imp):
                    violations.append({
                        "file": rel_path,
                        "layer": "controller",
                        "rule": "Controllers must not import Repository classes directly; use a Service instead.",
                        "import": imp,
                    })

        if layer == "service":
            for imp in imports:
                if HTTP_PATTERNS.search(imp):
                    violations.append({
                        "file": rel_path,
                        "layer": "service",
                        "rule": "Services must not import HTTP/web-related classes; keep services framework-agnostic.",
                        "import": imp,
                    })

    return {
        "status": "fail" if violations else "pass",
        "violations": violations,
        "files_checked": len(source_files),
    }


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"status": "error", "message": "Usage: layer_violation_check.py <project_dir>"}))
        sys.exit(1)

    project_dir = sys.argv[1]
    if not os.path.isdir(project_dir):
        print(json.dumps({"status": "error", "message": f"Directory not found: {project_dir}"}))
        sys.exit(1)

    result = check_violations(project_dir)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
