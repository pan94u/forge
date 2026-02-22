# Checks REST endpoint naming conventions in Kotlin and Java controller files.
import sys
import os
import re
import json


def scan_file(filepath):
    """Extract endpoint URLs from Spring mapping annotations."""
    endpoints = []
    pattern = re.compile(
        r'@(?:Get|Post|Put|Delete|Request)Mapping\s*\(\s*(?:value\s*=\s*)?'
        r'["\']([^"\']+)["\']',
        re.IGNORECASE,
    )
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        for line_num, line in enumerate(f, start=1):
            for match in pattern.finditer(line):
                url = match.group(1)
                endpoints.append({
                    "file": filepath,
                    "line": line_num,
                    "url": url,
                })
    return endpoints


def check_endpoint(entry):
    """Validate a single endpoint URL against naming conventions."""
    violations = []
    url = entry["url"]
    loc = f"{os.path.basename(entry['file'])}:{entry['line']}"

    if url != url.lower():
        violations.append({
            "location": loc,
            "url": url,
            "rule": "URLs must be lowercase",
        })

    if "_" in url:
        violations.append({
            "location": loc,
            "url": url,
            "rule": "Use hyphens instead of underscores",
        })

    segments = [s for s in url.strip("/").split("/") if s and not s.startswith("{")]
    singular_indicators = re.compile(r"^[a-z]+(?<!s)$")
    for seg in segments:
        if singular_indicators.match(seg) and seg not in (
            "api", "v1", "v2", "v3", "auth", "health", "status", "info", "login",
            "logout", "search", "me", "config", "admin", "graphql", "ws",
        ):
            violations.append({
                "location": loc,
                "url": url,
                "rule": f"Segment '{seg}' should use plural nouns for collections",
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

    all_endpoints = []
    for root, _, files in os.walk(project_dir):
        for fname in files:
            if fname.endswith((".kt", ".java")):
                fpath = os.path.join(root, fname)
                all_endpoints.extend(scan_file(fpath))

    all_violations = []
    for ep in all_endpoints:
        all_violations.extend(check_endpoint(ep))

    status = "pass" if not all_violations else "fail"
    result = {
        "status": status,
        "violations": all_violations,
        "endpoints_checked": len(all_endpoints),
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
