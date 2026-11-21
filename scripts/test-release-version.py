#!/usr/bin/env python3
"""Exercise release numbering and multi-commit pushes against real Git history."""

import importlib.util
import json
import os
import subprocess
import tempfile
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

    def test_ci_only_runs_on_manual_dispatch(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertEqual("workflow_dispatch:", workflow.split("\non:\n")[1].split("\n\n")[0].strip())
        self.assertNotIn("contents: write", workflow)

    def test_local_publication_and_retry(self):
        origin = Path("origin.git").resolve()
        RELEASE.git("init", "--bare", "-q", str(origin))
        RELEASE.git("remote", "add", "origin", str(origin))
        Path("bin").mkdir()
        gh = Path("bin/gh")
        gh.write_text("""#!/usr/bin/env python3
import shutil, sys
from pathlib import Path
args = sys.argv[1:]
state = Path('draft')
assets = Path('remote-assets')
if args[:2] == ['repo', 'view']:
    print('owner/repo')
elif args[:2] == ['release', 'view']:
    if not state.exists(): sys.exit(1)
    print(state.read_text() if '.isDraft' in args else 'release-url')
elif args[:2] == ['release', 'create']:
    assert not state.exists() and '--verify-tag' in args and '--draft' in args
    state.write_text('true')
    assets.mkdir()
elif args[:2] == ['release', 'upload']:
    assert state.read_text() == 'true'
    for f in args[3:5]: shutil.copy(f, assets)
elif args[:2] == ['release', 'download']:
    for f in assets.iterdir(): shutil.copy(f, args[args.index('--dir')+1])
elif args[:2] == ['release', 'edit']:
    assert '--draft=false' in args
    state.write_text('false')
else:
    raise AssertionError(args)
""")
        gh.chmod(0o700)
        Path("dist").mkdir()
        Path("download").mkdir()
        for name in ("A5Cockpit.apk", "A5Cockpit.apk.sha256"):
            Path("dist", name).write_text(name)
        cmd = ["bash", "-c", 'source "$1"; publish "$2" v1.3.0 dist download',
               "_", str(ROOT / "scripts/release-local.sh"), self.base]
        env = {**os.environ, "PATH": f"{Path('bin').resolve()}:{os.environ['PATH']}"}

        def publish():
            return subprocess.run(cmd, env=env, capture_output=True, text=True)

        result = publish()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("false", Path("draft").read_text())
        for ref in ("refs/heads/master", "refs/tags/v1.3.0"):
            self.assertEqual(self.base, RELEASE.git("--git-dir", str(origin), "rev-parse", ref))

        Path("draft").write_text("true")
        Path("remote-assets/A5Cockpit.apk").unlink()
        self.assertEqual(0, publish().returncode)
        self.assertEqual("false", Path("draft").read_text())
        self.assertEqual(0, publish().returncode)
        Path("remote-assets/A5Cockpit.apk").write_text("different")
        self.assertNotEqual(0, publish().returncode)
        self.assertEqual("different", Path("remote-assets/A5Cockpit.apk").read_text())

        RELEASE.git("commit", "--allow-empty", "-qm", "Other commit")
        cmd[-1] = RELEASE.git("rev-parse", "HEAD")
        self.assertNotEqual(0, publish().returncode)
        self.assertEqual(self.base, RELEASE.git("--git-dir", str(origin), "rev-parse", "master"))


if __name__ == "__main__":
    unittest.main()
