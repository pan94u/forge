#!/usr/bin/env python3
# Scan build files and output module dependency graph (JSON)

"""
Usage: python3 scan_modules.py [PROJECT_ROOT]

Scans for build files (build.gradle.kts, pom.xml, package.json)
and produces a JSON dependency graph of project modules.

Output: JSON to stdout
"""

import os
import re
import json
import sys


def scan_gradle_modules(root):
    """Scan Gradle multi-module project."""
    modules = []

    # Find settings.gradle.kts or settings.gradle
    settings_file = None
    for name in ["settings.gradle.kts", "settings.gradle"]:
        path = os.path.join(root, name)
        if os.path.isfile(path):
            settings_file = path
            break

    declared_modules = []
    if settings_file:
        with open(settings_file, "r") as f:
            content = f.read()
            # Match include("module") or include(":module:submodule")
            for match in re.finditer(r'include\s*\(\s*"([^"]+)"\s*\)', content):
                declared_modules.append(match.group(1).replace(":", "/").lstrip("/"))

    # Scan each module for build.gradle.kts
    for module_path in declared_modules:
        full_path = os.path.join(root, module_path)
        build_file = os.path.join(full_path, "build.gradle.kts")
        if not os.path.isfile(build_file):
            build_file = os.path.join(full_path, "build.gradle")
        if not os.path.isfile(build_file):
            continue

        with open(build_file, "r") as f:
            build_content = f.read()

        # Extract project dependencies
        deps = []
        for match in re.finditer(r'project\s*\(\s*"([^"]+)"\s*\)', build_content):
            deps.append(match.group(1).replace(":", "/").lstrip("/"))

        # Detect plugins/frameworks
        frameworks = []
        if "spring-boot" in build_content or "org.springframework.boot" in build_content:
            frameworks.append("spring-boot")
        if "kotlin" in build_content:
            frameworks.append("kotlin")
        if "ktor" in build_content:
            frameworks.append("ktor")

        modules.append({
            "name": module_path,
            "build_file": os.path.relpath(build_file, root),
            "dependencies": deps,
            "frameworks": frameworks
        })

    return modules


def scan_npm_modules(root):
    """Scan for package.json in root and subdirectories."""
    modules = []

    for dirpath, dirnames, filenames in os.walk(root):
        # Skip node_modules
        dirnames[:] = [d for d in dirnames if d != "node_modules" and d != ".git"]

        if "package.json" in filenames:
            pkg_path = os.path.join(dirpath, "package.json")
            try:
                with open(pkg_path, "r") as f:
                    pkg = json.load(f)

                deps = list((pkg.get("dependencies", {}) or {}).keys())
                dev_deps = list((pkg.get("devDependencies", {}) or {}).keys())

                frameworks = []
                all_deps = deps + dev_deps
                if any("react" in d for d in all_deps):
                    frameworks.append("react")
                if any("next" in d for d in all_deps):
                    frameworks.append("nextjs")
                if any("vue" in d for d in all_deps):
                    frameworks.append("vue")

                modules.append({
                    "name": os.path.relpath(dirpath, root) or ".",
                    "build_file": os.path.relpath(pkg_path, root),
                    "dependencies": deps[:20],  # limit for readability
                    "frameworks": frameworks
                })
            except (json.JSONDecodeError, IOError):
                pass

    return modules


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    root = os.path.abspath(root)

    result = {
        "project_root": root,
        "gradle_modules": scan_gradle_modules(root),
        "npm_modules": scan_npm_modules(root),
    }

    result["total_modules"] = len(result["gradle_modules"]) + len(result["npm_modules"])

    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
