# Generates an Architecture Decision Record (ADR) template in standard markdown format.
import sys
import os
import argparse
from datetime import date


def build_adr(title, context, adr_number=None):
    """Build a complete ADR markdown document following the standard format."""
    today = date.today().isoformat()

    number_prefix = ""
    if adr_number is not None:
        number_prefix = f"{adr_number}. "

    lines = [
        f"# {number_prefix}{title}",
        "",
        f"Date: {today}",
        "",
        "## Status",
        "",
        "Proposed",
        "",
        "## Context",
        "",
    ]

    if context:
        for paragraph in context.split("\\n"):
            lines.append(paragraph.strip())
            lines.append("")
    else:
        lines.append("<!-- Describe the issue motivating this decision and any context that influences it. -->")
        lines.append("")

    lines.extend([
        "## Decision",
        "",
        "<!-- Describe the change that is being proposed or has been agreed upon. -->",
        "",
        "## Consequences",
        "",
        "### Positive",
        "",
        "<!-- List the positive outcomes of this decision. -->",
        "",
        "- ",
        "",
        "### Negative",
        "",
        "<!-- List the negative outcomes or trade-offs of this decision. -->",
        "",
        "- ",
        "",
        "### Neutral",
        "",
        "<!-- List any neutral observations or side effects. -->",
        "",
        "- ",
        "",
        "## Notes",
        "",
        "<!-- Any additional notes, links to related ADRs, references, etc. -->",
        "",
    ])

    return "\n".join(lines)


def next_adr_number(project_dir):
    """Determine the next ADR number by scanning existing ADR files in the directory."""
    if not project_dir or not os.path.isdir(project_dir):
        return 1

    adr_dirs = ["doc/adr", "docs/adr", "adr", "doc/architecture/decisions", "docs/architecture/decisions"]
    for adr_dir in adr_dirs:
        full_path = os.path.join(project_dir, adr_dir)
        if os.path.isdir(full_path):
            existing = [f for f in os.listdir(full_path) if f.endswith(".md")]
            return len(existing) + 1

    return 1


def main():
    parser = argparse.ArgumentParser(description="Generate an Architecture Decision Record (ADR) template.")
    parser.add_argument("project_dir", nargs="?", default=None,
                        help="Project directory path (used to auto-number ADRs)")
    parser.add_argument("--title", required=True, help="Title of the ADR")
    parser.add_argument("--context", default="", help="Context paragraph(s) for the ADR (use \\\\n for line breaks)")
    parser.add_argument("--number", type=int, default=None, help="Explicit ADR number (auto-detected if omitted)")

    args = parser.parse_args()

    if args.project_dir and not os.path.isdir(args.project_dir):
        print(f"Warning: Directory not found: {args.project_dir}. Proceeding without auto-numbering.",
              file=sys.stderr)
        args.project_dir = None

    adr_number = args.number
    if adr_number is None:
        adr_number = next_adr_number(args.project_dir)

    adr_content = build_adr(args.title, args.context, adr_number)
    print(adr_content)


if __name__ == "__main__":
    main()
