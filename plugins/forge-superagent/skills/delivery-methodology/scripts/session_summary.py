#!/usr/bin/env python3
# Generate a structured session summary from git log and file changes

"""
Usage: python3 session_summary.py [--since DAYS] [--repo-path PATH]

Generates a structured session summary including:
- Files changed (with operation type: create/modify/delete)
- Commit messages grouped by topic
- Statistics snapshot (file counts by type, line changes)

Output: JSON to stdout
"""

import subprocess
import json
import sys
import os
from collections import defaultdict
from datetime import datetime, timedelta


def run_git(args, repo_path="."):
    """Run a git command and return stdout."""
    result = subprocess.run(
        ["git"] + args,
        capture_output=True, text=True,
        cwd=repo_path
    )
    if result.returncode != 0:
        return ""
    return result.stdout.strip()


def get_file_changes(since_date, repo_path="."):
    """Get file changes since a given date."""
    log = run_git([
        "log", f"--since={since_date}",
        "--name-status", "--pretty=format:", "--diff-filter=ACDMR"
    ], repo_path)

    changes = defaultdict(lambda: {"operations": [], "last_op": None})

    for line in log.strip().split("\n"):
        line = line.strip()
        if not line:
            continue
        parts = line.split("\t", 1)
        if len(parts) != 2:
            continue

        op_code, file_path = parts
        op_map = {"A": "create", "M": "modify", "D": "delete", "C": "copy", "R": "rename"}
        operation = op_map.get(op_code[0], "modify")

        changes[file_path]["operations"].append(operation)
        changes[file_path]["last_op"] = operation

    return changes


def get_commits(since_date, repo_path="."):
    """Get commit messages since a given date."""
    log = run_git([
        "log", f"--since={since_date}",
        "--pretty=format:%H|%s|%an|%ai"
    ], repo_path)

    commits = []
    for line in log.strip().split("\n"):
        if not line:
            continue
        parts = line.split("|", 3)
        if len(parts) == 4:
            commits.append({
                "hash": parts[0][:8],
                "message": parts[1],
                "author": parts[2],
                "date": parts[3]
            })

    return commits


def get_stats(since_date, repo_path="."):
    """Get line change statistics."""
    stat = run_git([
        "log", f"--since={since_date}",
        "--pretty=format:", "--numstat"
    ], repo_path)

    additions = 0
    deletions = 0
    file_types = defaultdict(int)

    for line in stat.strip().split("\n"):
        parts = line.split("\t")
        if len(parts) != 3:
            continue
        try:
            add = int(parts[0]) if parts[0] != "-" else 0
            delete = int(parts[1]) if parts[1] != "-" else 0
            additions += add
            deletions += delete

            ext = os.path.splitext(parts[2])[1] or "no-ext"
            file_types[ext] += 1
        except ValueError:
            continue

    return {
        "lines_added": additions,
        "lines_deleted": deletions,
        "net_change": additions - deletions,
        "file_types": dict(file_types)
    }


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Generate session summary from git history")
    parser.add_argument("--since", type=int, default=1, help="Days to look back (default: 1)")
    parser.add_argument("--repo-path", default=".", help="Repository path")
    args = parser.parse_args()

    since_date = (datetime.now() - timedelta(days=args.since)).strftime("%Y-%m-%d")

    changes = get_file_changes(since_date, args.repo_path)
    commits = get_commits(since_date, args.repo_path)
    stats = get_stats(since_date, args.repo_path)

    summary = {
        "period": f"last {args.since} day(s)",
        "since": since_date,
        "commits": commits,
        "file_changes": {
            path: info["last_op"]
            for path, info in sorted(changes.items())
        },
        "statistics": stats,
        "total_files_changed": len(changes),
        "total_commits": len(commits)
    }

    print(json.dumps(summary, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
