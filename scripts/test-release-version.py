#!/usr/bin/env python3
"""Exercise release numbering and multi-commit pushes against real Git history."""

import importlib.util
import json
import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("release_version", ROOT / "scripts/release-version.py")
RELEASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RELEASE)


class ReleaseVersionTest(unittest.TestCase):
    def setUp(self):
        original = Path.cwd()
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.addCleanup(os.chdir, original)
        os.chdir(temporary.name)
        subprocess.run(["git", "init", "-q", "-b", "master"], check=True)
        RELEASE.git("config", "user.name", "Release test")
        RELEASE.git("config", "user.email", "test@example.com")
        RELEASE.git("commit", "--allow-empty", "-qm", "Base")
        self.base = RELEASE.git("rev-parse", "HEAD")
        base_patch = patch.object(RELEASE, "BASE_COMMIT", self.base)
        base_patch.start()
        self.addCleanup(base_patch.stop)

    def test_each_commit_gets_a_stable_patch_and_android_code(self):
        self.assertEqual(("1.3.0", 4), RELEASE.version())
        RELEASE.git("commit", "--allow-empty", "-qm", "First")
        first = RELEASE.git("rev-parse", "HEAD")
        RELEASE.git("commit", "--allow-empty", "-qm", "Second")
        second = RELEASE.git("rev-parse", "HEAD")
        self.assertEqual(("1.3.1", 5), RELEASE.version(first))
        self.assertEqual(("1.3.2", 6), RELEASE.version(second))
        expected = {"include": [
            {"commit": first, "tag": "v1.3.1"},
            {"commit": second, "tag": "v1.3.2"},
        ]}
        self.assertEqual(expected, RELEASE.release_commits(self.base, second))
        RELEASE.git("tag", "v1.3.1", first)
        self.assertEqual(expected, RELEASE.release_commits(self.base, second))
        self.assertEqual(expected, RELEASE.release_commits("0" * 40, second))
        self.assertEqual({"include": []}, RELEASE.release_commits(second, second))
        with self.assertRaises(subprocess.CalledProcessError):
            RELEASE.release_commits(second, first)

        RELEASE.git("checkout", "-qb", "feature")
        RELEASE.git("commit", "--allow-empty", "-qm", "Side one")
        RELEASE.git("commit", "--allow-empty", "-qm", "Side two")
        RELEASE.git("checkout", "-q", "master")
        RELEASE.git("merge", "--no-ff", "-qm", "Merge", "feature")
        merged = RELEASE.git("rev-parse", "HEAD")
        self.assertEqual(("1.3.3", 7), RELEASE.version(merged))
        self.assertEqual({"include": [{"commit": merged, "tag": "v1.3.3"}]},
                         RELEASE.release_commits(second, merged))

    def test_actual_publication_preserves_repairs_creates_and_rejects_wrong_tags(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        stanza = workflow.split("      - name: Publish GitHub Release\n", 1)[1]
        stanza = stanza.split("      - name: Remove temporary signing key\n", 1)[0]
        publication = textwrap.dedent(stanza.split("        run: |\n", 1)[1])
        RELEASE.git("init", "--bare", "-q", "origin.git")
        RELEASE.git("remote", "add", "origin", str(Path("origin.git").resolve()))
        Path("bin").mkdir()
        fake_gh = Path("bin/gh")
        fake_gh.write_text('''#!/usr/bin/env python3
import json, sys
from pathlib import Path
args = sys.argv[1:]
p = Path("state.json")
state = json.loads(p.read_text()) if p.exists() else None
if args[0] == "api" and "--method" not in args:
    if state is None: sys.exit(1)
    print(state["id"] if "--jq" in args else json.dumps(state))
    sys.exit(0)
if args[:2] == ["release", "create"]:
    assert state is None and "--draft" in args and "--verify-tag" in args
    state = {"id": 42, "draft": True, "assets": []}
if args[:2] in (["release", "create"], ["release", "upload"]):
    state["assets"] = [{"name": f.name, "state": "uploaded", "size": f.stat().st_size,
                        "content": f.read_text()} for f in Path("dist").iterdir()]
elif args[0] == "api" and "--method" in args:
    assert "draft=false" in args and "make_latest=legacy" in args
    state["draft"] = False
else:
    raise AssertionError(args)
p.write_text(json.dumps(state))
''')
        fake_gh.chmod(0o700)
        Path("dist").mkdir()
        Path("dist/A5Cockpit.apk").write_text("new APK")
        Path("dist/A5Cockpit.apk.sha256").write_text("new checksum")
        assets = [{"name": name, "state": "uploaded", "size": 7, "content": "old"}
                  for name in ("A5Cockpit.apk", "A5Cockpit.apk.sha256")]
        complete = {"id": 42, "draft": False, "assets": assets}
        env = {**os.environ, "PATH": f"{Path('bin').resolve()}:{os.environ['PATH']}",
               "RELEASE_COMMIT": self.base, "GITHUB_REPOSITORY": "test/repo"}
        for case, initial in (("complete", complete), ("incomplete", {"id": 42, "draft": True, "assets": []}),
                              ("absent", None), ("wrong-tag", complete)):
            with self.subTest(case=case):
                tag = f"v-test-{case}"
                env["RELEASE_TAG"] = tag
                if initial is None:
                    Path("state.json").unlink(missing_ok=True)
                else:
                    Path("state.json").write_text(json.dumps(initial))
                if case == "wrong-tag":
                    RELEASE.git("commit", "--allow-empty", "-qm", "Other commit")
                    RELEASE.git("tag", tag, "HEAD")
                result = subprocess.run(["bash", "-e", "-o", "pipefail", "-c", publication],
                                        env=env, capture_output=True, text=True)
                if case == "wrong-tag":
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual(initial, json.loads(Path("state.json").read_text()))
                    continue
                self.assertEqual(0, result.returncode, result.stderr)
                final = json.loads(Path("state.json").read_text())
                self.assertFalse(final["draft"])
                self.assertEqual(self.base, RELEASE.git("rev-parse", f"{tag}^{{commit}}"))
                self.assertEqual(["old", "old"] if case == "complete" else ["new APK", "new checksum"],
                                 [asset["content"] for asset in sorted(final["assets"], key=lambda a: a["name"])])


if __name__ == "__main__":
    unittest.main()
