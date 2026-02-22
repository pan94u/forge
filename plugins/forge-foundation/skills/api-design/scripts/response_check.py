# Checks API response format compliance for error handling and DTO naming conventions.
import sys
import os
import re
import json


def check_error_responses(filepath):
    """Check that error ResponseEntity blocks include correlationId and timestamp."""
    issues = []
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()
        lines = content.splitlines()

    error_pattern = re.compile(
        r"ResponseEntity\s*[.<]\s*(?:badRequest|status\s*\(\s*HttpStatus\s*\.\s*"
        r"(?:BAD_REQUEST|NOT_FOUND|INTERNAL_SERVER_ERROR|FORBIDDEN|UNAUTHORIZED|CONFLICT))",
        re.IGNORECASE,
    )
    for i, line in enumerate(lines, start=1):
        if error_pattern.search(line):
            context_start = max(0, i - 1)
            context_end = min(len(lines), i + 10)
            block = "\n".join(lines[context_start:context_end])
            if "correlationId" not in block and "correlation_id" not in block:
                issues.append({
                    "file": os.path.basename(filepath),
                    "line": i,
                    "issue": "Error response missing correlationId field",
                })
            if "timestamp" not in block and "Timestamp" not in block:
                issues.append({
                    "file": os.path.basename(filepath),
                    "line": i,
                    "issue": "Error response missing timestamp field",
                })
    return issues


def check_dto_naming(filepath):
    """Check that response DTO classes use @JsonNaming or explicit naming."""
    issues = []
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()

    dto_pattern = re.compile(r"(?:data\s+)?class\s+(\w*(?:Response|Dto|DTO)\w*)")
    json_naming = re.compile(r"@JsonNaming|@JsonProperty|@SerializedName|@Json\b")
    for i, line in enumerate(lines, start=1):
        match = dto_pattern.search(line)
        if match:
            context_start = max(0, i - 4)
            context_end = min(len(lines), i + 2)
            block = "".join(lines[context_start:context_end])
            if not json_naming.search(block):
                issues.append({
                    "file": os.path.basename(filepath),
                    "line": i,
                    "class": match.group(1),
                    "issue": "DTO class missing @JsonNaming or explicit property naming",
                })
    return issues


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"status": "fail", "error": "No project directory provided"}))
        sys.exit(1)

    project_dir = sys.argv[1]
    if not os.path.isdir(project_dir):
        print(json.dumps({"status": "fail", "error": f"Directory not found: {project_dir}"}))
        sys.exit(1)

    all_issues = []
    files_checked = 0
    for root, _, files in os.walk(project_dir):
        for fname in files:
            if fname.endswith((".kt", ".java")):
                fpath = os.path.join(root, fname)
                files_checked += 1
                all_issues.extend(check_error_responses(fpath))
                all_issues.extend(check_dto_naming(fpath))

    status = "pass" if not all_issues else "fail"
    print(json.dumps({"status": status, "issues": all_issues, "files_checked": files_checked}, indent=2))


if __name__ == "__main__":
    main()
