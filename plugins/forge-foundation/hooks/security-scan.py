#!/usr/bin/env python3
"""
Security scan hook for Forge Foundation.
Scans code for common security issues before file writes.
Exit 0 = clean, Exit 1 = issues found.
"""

import re
import sys
import json
import os

# Patterns that indicate potential security issues
SECRET_PATTERNS = [
    (r'(?i)(password|passwd|pwd)\s*=\s*["\'][^"\']+["\']', "Hardcoded password"),
    (r'(?i)(api[_-]?key|apikey)\s*=\s*["\'][^"\']+["\']', "Hardcoded API key"),
    (r'(?i)(secret|token)\s*=\s*["\'][a-zA-Z0-9+/=]{16,}["\']', "Hardcoded secret/token"),
    (r'(?i)Authorization:\s*Bearer\s+[a-zA-Z0-9._-]{20,}', "Hardcoded Bearer token"),
    (r'-----BEGIN\s+(RSA\s+)?PRIVATE\s+KEY-----', "Private key in source"),
]

SQL_INJECTION_PATTERNS = [
    (r'["\']\s*\+\s*\w+\s*\+\s*["\'].*(?:SELECT|INSERT|UPDATE|DELETE|WHERE)', "SQL string concatenation"),
    (r'String\.format\(.*(?:SELECT|INSERT|UPDATE|DELETE)', "SQL via String.format"),
]

XSS_PATTERNS = [
    (r'innerHTML\s*=\s*[^"\']*\$\{', "Potential XSS via innerHTML"),
    (r'dangerouslySetInnerHTML', "React dangerouslySetInnerHTML usage"),
]

def scan_content(content: str, filename: str) -> list:
    """Scan content for security issues. Returns list of (line, pattern_name, match)."""
    issues = []
    lines = content.split('\n')

    all_patterns = SECRET_PATTERNS + SQL_INJECTION_PATTERNS + XSS_PATTERNS

    for line_num, line in enumerate(lines, 1):
        # Skip comments
        stripped = line.strip()
        if stripped.startswith('//') or stripped.startswith('#') or stripped.startswith('*'):
            continue

        for pattern, description in all_patterns:
            if re.search(pattern, line):
                issues.append({
                    "file": filename,
                    "line": line_num,
                    "issue": description,
                    "content": line.strip()[:100]
                })

    return issues

def main():
    """Main entry point. Reads stdin for file content or checks environment."""
    issues = []

    # Read from environment (hook context)
    file_path = os.environ.get('FORGE_FILE_PATH', '')
    content = os.environ.get('FORGE_FILE_CONTENT', '')

    if content:
        issues = scan_content(content, file_path)
    elif not sys.stdin.isatty():
        content = sys.stdin.read()
        issues = scan_content(content, file_path or '<stdin>')

    if issues:
        print(json.dumps({"status": "BLOCKED", "issues": issues}, indent=2))
        sys.exit(1)
    else:
        sys.exit(0)

if __name__ == "__main__":
    main()
