#!/usr/bin/env python3
"""Deterministic Android versions for commits integrated into develop."""

import json
import subprocess
import sys

# Start automatic releases after the last commit using the manual 1.3.0 version.
BASE_COMMIT = "f1b1a589cc22acc0d900c6f22b08345a3817bddf"
BASE_VERSION = (1, 3, 0)
BASE_CODE = 4


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def version(commit="HEAD"):
    subprocess.run(["git", "merge-base", "--is-ancestor", BASE_COMMIT, commit], check=True)
    count = int(git("rev-list", "--first-parent", "--count", f"{BASE_COMMIT}..{commit}"))
    major, minor, patch = BASE_VERSION
    return f"{major}.{minor}.{patch + count}", BASE_CODE + count


def release_commits(before, head):
    if before == "0" * 40:
        before = BASE_COMMIT
    subprocess.run(["git", "merge-base", "--is-ancestor", before, head], check=True)
    commits = git("rev-list", "--first-parent", "--reverse", f"{before}..{head}").splitlines()
    return {"include": [
        {"commit": commit, "tag": f"v{version(commit)[0]}"} for commit in commits
    ]}


if __name__ == "__main__":
    if sys.argv[1:] == ["version"]:
        print(*version())
    elif len(sys.argv) == 4 and sys.argv[1] == "commits":
        print(json.dumps(release_commits(sys.argv[2], sys.argv[3])))
    else:
        raise SystemExit("Usage: release-version.py version | commits BEFORE HEAD")
