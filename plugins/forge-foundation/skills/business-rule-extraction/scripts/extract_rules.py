#!/usr/bin/env python3
# Extract business rules from Service classes (JSON)

"""
Usage: python3 extract_rules.py [PROJECT_ROOT]

Scans Service classes for patterns that indicate business rules:
- Validation logic (if/throw patterns)
- State transitions (status changes)
- Calculation logic (complex expressions)
- Constraint checks (range validations, null checks)

Output: JSON to stdout
"""

import os
import re
import json
import sys
from collections import defaultdict


# Patterns that indicate business rules
RULE_PATTERNS = {
    "validation": [
        (r'(?:require|check)\s*\(.+\)\s*\{?\s*"(.+)"', "Kotlin require/check"),
        (r'if\s*\(.+\)\s*throw\s+(\w+Exception)', "Guard clause with exception"),
        (r'@NotNull|@NotBlank|@NotEmpty|@Min|@Max|@Size|@Pattern|@Valid', "Bean validation annotation"),
    ],
    "state_transition": [
        (r'\.status\s*=\s*\w+\.(\w+)', "Status assignment"),
        (r'\.state\s*=\s*\w+\.(\w+)', "State assignment"),
        (r'when\s*\(\s*\w+\.status\s*\)', "Status-based dispatch (Kotlin when)"),
        (r'switch\s*\(\s*\w+\.get(?:Status|State)\(\)\s*\)', "Status-based dispatch (Java switch)"),
    ],
    "calculation": [
        (r'\.multiply\(|\.add\(|\.subtract\(|\.divide\(', "BigDecimal arithmetic"),
        (r'\.sumOf\s*\{|\.sumBy\s*\{|\.reduce\s*\{', "Collection aggregation"),
        (r'calculateTotal|calculatePrice|calculateDiscount|calculateTax', "Named calculation method"),
    ],
    "constraint": [
        (r'>\s*\d+|<\s*\d+|>=\s*\d+|<=\s*\d+', "Numeric comparison"),
        (r'\.isAfter\(|\.isBefore\(|\.isEqual\(', "Temporal comparison"),
        (r'\.length\s*>\s*\d+|\.size\s*>\s*\d+|\.count\(\)\s*>', "Size/length constraint"),
    ],
}


def scan_file(file_path, root):
    """Scan a single file for business rules."""
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
            lines = content.split("\n")
    except (IOError, UnicodeDecodeError):
        return []

    rel_path = os.path.relpath(file_path, root)
    rules = []

    for category, patterns in RULE_PATTERNS.items():
        for pattern_regex, pattern_name in patterns:
            for i, line in enumerate(lines):
                matches = re.findall(pattern_regex, line)
                if matches:
                    # Get surrounding context (1 line before and after)
                    context_start = max(0, i - 1)
                    context_end = min(len(lines), i + 2)
                    context = "\n".join(lines[context_start:context_end]).strip()

                    rules.append({
                        "file": rel_path,
                        "line": i + 1,
                        "category": category,
                        "pattern": pattern_name,
                        "match": matches[0] if isinstance(matches[0], str) else str(matches[0]),
                        "context": context[:200]  # limit context length
                    })

    return rules


def is_service_file(file_path):
    """Check if a file is likely a service/business logic file."""
    name = os.path.basename(file_path).lower()
    return (
        name.endswith("service.kt") or name.endswith("service.java") or
        name.endswith("handler.kt") or name.endswith("handler.java") or
        name.endswith("validator.kt") or name.endswith("validator.java") or
        name.endswith("processor.kt") or name.endswith("processor.java")
    )


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    root = os.path.abspath(root)

    all_rules = []

    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in (".git", "build", "target", "node_modules", "test")]

        for filename in filenames:
            if not filename.endswith((".kt", ".java")):
                continue

            file_path = os.path.join(dirpath, filename)

            # Prioritize service files but scan all source
            file_rules = scan_file(file_path, root)
            if is_service_file(file_path):
                for rule in file_rules:
                    rule["priority"] = "high"
            else:
                for rule in file_rules:
                    rule["priority"] = "normal"

            all_rules.extend(file_rules)

    # Group by category
    by_category = defaultdict(list)
    for rule in all_rules:
        by_category[rule["category"]].append(rule)

    result = {
        "project_root": root,
        "total_rules_found": len(all_rules),
        "by_category": {
            cat: {
                "count": len(rules),
                "rules": rules[:20]  # limit per category for readability
            }
            for cat, rules in sorted(by_category.items())
        },
        "files_with_most_rules": sorted(
            Counter(r["file"] for r in all_rules).items(),
            key=lambda x: -x[1]
        )[:10]
    }

    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    from collections import Counter
    main()
