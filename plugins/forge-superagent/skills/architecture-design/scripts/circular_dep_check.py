# Detects circular dependencies in Kotlin/Java projects by building a module-level dependency graph.
import sys
import os
import re
import json
from collections import defaultdict


def find_source_files(project_dir):
    """Recursively find all .kt and .java files."""
    source_files = []
    for root, _, files in os.walk(project_dir):
        for f in files:
            if f.endswith(".kt") or f.endswith(".java"):
                source_files.append(os.path.join(root, f))
    return source_files


def extract_package(filepath):
    """Extract the package declaration from a source file."""
    pkg_pattern = re.compile(r"^\s*package\s+([\w.]+)")
    with open(filepath, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            match = pkg_pattern.match(line)
            if match:
                return match.group(1)
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


def get_module(package, depth=2):
    """Reduce a full package name to a module identifier at the given depth."""
    if package is None:
        return None
    parts = package.split(".")
    return ".".join(parts[: min(depth, len(parts))])


def build_dependency_graph(project_dir):
    """Build a directed graph of module-level dependencies from source files."""
    source_files = find_source_files(project_dir)
    all_packages = set()
    graph = defaultdict(set)

    file_packages = {}
    for filepath in source_files:
        pkg = extract_package(filepath)
        if pkg:
            all_packages.add(pkg)
            file_packages[filepath] = pkg

    for filepath, pkg in file_packages.items():
        src_module = get_module(pkg)
        if src_module is None:
            continue
        imports = extract_imports(filepath)
        for imp in imports:
            imp_module = get_module(imp)
            if imp_module and imp_module != src_module:
                # Only track edges to modules that actually exist in the project
                for known_pkg in all_packages:
                    if get_module(known_pkg) == imp_module:
                        graph[src_module].add(imp_module)
                        break

    return graph, len(source_files), len(set(list(graph.keys()) + [m for deps in graph.values() for m in deps]))


def find_cycles(graph):
    """Find all cycles in a directed graph using DFS."""
    cycles = []
    visited = set()
    on_stack = set()
    stack_path = []

    def dfs(node):
        visited.add(node)
        on_stack.add(node)
        stack_path.append(node)

        for neighbor in graph.get(node, []):
            if neighbor not in visited:
                dfs(neighbor)
            elif neighbor in on_stack:
                idx = stack_path.index(neighbor)
                cycle = stack_path[idx:] + [neighbor]
                cycles.append(cycle)

        stack_path.pop()
        on_stack.remove(node)

    for node in list(graph.keys()):
        if node not in visited:
            dfs(node)

    return cycles


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"status": "error", "message": "Usage: circular_dep_check.py <project_dir>"}))
        sys.exit(1)

    project_dir = sys.argv[1]
    if not os.path.isdir(project_dir):
        print(json.dumps({"status": "error", "message": f"Directory not found: {project_dir}"}))
        sys.exit(1)

    graph, files_checked, modules_checked = build_dependency_graph(project_dir)
    cycles = find_cycles(graph)

    formatted_cycles = [" -> ".join(c) for c in cycles]

    result = {
        "status": "fail" if cycles else "pass",
        "cycles": formatted_cycles,
        "modules_checked": modules_checked,
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
