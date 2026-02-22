#!/usr/bin/env python3
# Detect drift between two baseline documents

"""
Usage: python3 baseline_drift.py <file_a> <file_b>

Compares two baseline documents (Markdown) and reports:
- Sections present in A but missing in B
- Sections present in B but missing in A
- Sections with differing content (by heading)

Output: JSON to stdout
"""

import sys
import json
import re
from collections import OrderedDict


def parse_sections(content):
    """Parse a Markdown file into sections keyed by heading."""
    sections = OrderedDict()
    current_heading = "(preamble)"
    current_lines = []

    for line in content.split("\n"):
        heading_match = re.match(r'^(#{1,4})\s+(.+)$', line)
        if heading_match:
            # Save previous section
            if current_lines:
                sections[current_heading] = "\n".join(current_lines).strip()
            current_heading = heading_match.group(2).strip()
            current_lines = []
        else:
            current_lines.append(line)

    # Save last section
    if current_lines:
        sections[current_heading] = "\n".join(current_lines).strip()

    return sections


def compare_sections(sections_a, sections_b):
    """Compare two section dictionaries and find drift."""
    keys_a = set(sections_a.keys())
    keys_b = set(sections_b.keys())

    only_in_a = sorted(keys_a - keys_b)
    only_in_b = sorted(keys_b - keys_a)
    common = sorted(keys_a & keys_b)

    diffs = []
    for key in common:
        content_a = sections_a[key].strip()
        content_b = sections_b[key].strip()
        if content_a != content_b:
            # Count line differences
            lines_a = content_a.split("\n")
            lines_b = content_b.split("\n")
            diffs.append({
                "section": key,
                "lines_in_a": len(lines_a),
                "lines_in_b": len(lines_b),
                "delta": abs(len(lines_a) - len(lines_b))
            })

    return {
        "only_in_a": only_in_a,
        "only_in_b": only_in_b,
        "content_differs": diffs,
        "total_sections_a": len(sections_a),
        "total_sections_b": len(sections_b),
        "drift_score": len(only_in_a) + len(only_in_b) + len(diffs)
    }


def main():
    if len(sys.argv) != 3:
        print("Usage: python3 baseline_drift.py <file_a> <file_b>", file=sys.stderr)
        sys.exit(1)

    file_a, file_b = sys.argv[1], sys.argv[2]

    try:
        with open(file_a, "r") as f:
            content_a = f.read()
        with open(file_b, "r") as f:
            content_b = f.read()
    except FileNotFoundError as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)

    sections_a = parse_sections(content_a)
    sections_b = parse_sections(content_b)

    result = compare_sections(sections_a, sections_b)
    result["file_a"] = file_a
    result["file_b"] = file_b

    # Summary
    if result["drift_score"] == 0:
        result["summary"] = "No drift detected. Baselines are aligned."
    else:
        result["summary"] = (
            f"Drift detected (score: {result['drift_score']}): "
            f"{len(result['only_in_a'])} sections only in A, "
            f"{len(result['only_in_b'])} sections only in B, "
            f"{len(result['content_differs'])} sections with content differences."
        )

    print(json.dumps(result, indent=2, ensure_ascii=False))

    # Exit code: 0 if no drift, 1 if drift detected
    sys.exit(0 if result["drift_score"] == 0 else 1)


if __name__ == "__main__":
    main()
