# Generates an OpenAPI 3.0 skeleton YAML from Kotlin controller files.
import sys
import os
import re


HTTP_METHODS = {
    "GetMapping": "get",
    "PostMapping": "post",
    "PutMapping": "put",
    "DeleteMapping": "delete",
    "PatchMapping": "patch",
}


def extract_endpoints(filepath):
    """Parse a Kotlin controller file for endpoint definitions."""
    endpoints = []
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()

    base_match = re.search(r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']', content)
    base_path = base_match.group(1) if base_match else ""

    pattern = re.compile(
        r'@(\w+Mapping)\s*\(\s*(?:value\s*=\s*)?["\']([^"\']*)["\']'
        r'.*?\)\s*(?:suspend\s+)?fun\s+(\w+)\s*\(([^)]*)\)',
        re.DOTALL,
    )
    for match in pattern.finditer(content):
        annotation, path, func_name, params_str = match.groups()
        method = HTTP_METHODS.get(annotation)
        if not method:
            continue
        full_path = (base_path.rstrip("/") + "/" + path.lstrip("/")).rstrip("/") or "/"
        path_params = re.findall(r"\{(\w+)\}", full_path)
        query_params = []
        for p in re.finditer(r"@RequestParam\s+(?:\w+\s+)?(\w+)", params_str):
            query_params.append(p.group(1))
        body_param = bool(re.search(r"@RequestBody", params_str))
        endpoints.append({
            "path": full_path, "method": method, "operation_id": func_name,
            "path_params": path_params, "query_params": query_params,
            "has_body": body_param,
        })
    return endpoints


def to_yaml(endpoints, title="Generated API"):
    """Build an OpenAPI 3.0 YAML string from endpoint data."""
    lines = [
        "openapi: '3.0.3'", "info:", f"  title: {title}", "  version: '1.0.0'",
        "paths:",
    ]
    paths = {}
    for ep in endpoints:
        paths.setdefault(ep["path"], []).append(ep)

    for path in sorted(paths):
        lines.append(f"  {path}:")
        for ep in paths[path]:
            lines.append(f"    {ep['method']}:")
            lines.append(f"      operationId: {ep['operation_id']}")
            lines.append(f"      summary: '{ep['operation_id']}'")
            if ep["path_params"] or ep["query_params"]:
                lines.append("      parameters:")
                for pp in ep["path_params"]:
                    lines.extend([
                        f"        - name: {pp}", "          in: path",
                        "          required: true",
                        "          schema:", "            type: string",
                    ])
                for qp in ep["query_params"]:
                    lines.extend([
                        f"        - name: {qp}", "          in: query",
                        "          required: false",
                        "          schema:", "            type: string",
                    ])
            if ep["has_body"]:
                lines.extend([
                    "      requestBody:", "        required: true",
                    "        content:", "          application/json:",
                    "            schema:", "              type: object",
                ])
            lines.extend([
                "      responses:", "        '200':",
                "          description: Successful response",
            ])
    return "\n".join(lines) + "\n"


def main():
    if len(sys.argv) < 2:
        print("Error: No project directory provided", file=sys.stderr)
        sys.exit(1)

    project_dir = sys.argv[1]
    if not os.path.isdir(project_dir):
        print(f"Error: Directory not found: {project_dir}", file=sys.stderr)
        sys.exit(1)

    all_endpoints = []
    for root, _, files in os.walk(project_dir):
        for fname in files:
            if fname.endswith(".kt") and "Controller" in fname:
                all_endpoints.extend(extract_endpoints(os.path.join(root, fname)))

    print(to_yaml(all_endpoints, title=os.path.basename(project_dir)))


if __name__ == "__main__":
    main()
