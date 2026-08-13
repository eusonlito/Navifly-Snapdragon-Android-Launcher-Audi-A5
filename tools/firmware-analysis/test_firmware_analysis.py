import importlib.util
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).with_name("firmware_analysis.py")


def load_module():
    spec = importlib.util.spec_from_file_location("firmware_analysis", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class FirmwareAnalysisTest(unittest.TestCase):
    def setUp(self):
        self.module = load_module()
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.cache = self.root / "cache"
        self.docs = self.root / "docs"

    def tearDown(self):
        self.temp_dir.cleanup()

    def create_snapshot(self):
        source = self.root / "snapshot.zip"
        with zipfile.ZipFile(source, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("reports/device.json", '{"sdk":34}')
            archive.writestr("packages/com.example.core/base.apk", b"same-content")
            archive.writestr("packages/com.example.core/runtime-libraries/libx.so", b"duplicate")
            archive.writestr("packages/com.example.core/libraries/base.apk/lib/libx.so", b"duplicate")
        return source

    def test_ingest_is_content_addressed_and_idempotent(self):
        source = self.create_snapshot()

        first = self.module.ingest(source, self.cache)
        manifest = json.loads(first.manifest_path.read_text())
        object_paths = {entry["object"] for entry in manifest["entries"]}
        mtimes = {path: (self.cache / path).stat().st_mtime_ns for path in object_paths}

        second = self.module.ingest(source, self.cache)

        self.assertEqual(first.snapshot_id, second.snapshot_id)
        self.assertEqual(3, len(object_paths))
        self.assertEqual(mtimes, {
            path: (self.cache / path).stat().st_mtime_ns for path in object_paths
        })
        self.assertTrue(first.complete_marker.is_file())

    def test_ingest_repairs_snapshot_with_missing_object(self):
        source = self.create_snapshot()
        first = self.module.ingest(source, self.cache)
        manifest = json.loads(first.manifest_path.read_text())
        missing = self.cache / manifest["entries"][0]["object"]
        missing.unlink()

        repaired = self.module.ingest(source, self.cache)

        self.assertEqual(first.snapshot_id, repaired.snapshot_id)
        self.assertTrue(missing.is_file())
        self.assertIsNotNone(
            self.module.read_valid_manifest(repaired.manifest_path.parent, self.cache)
        )

    def test_corrupt_zip_does_not_create_complete_snapshot(self):
        source = self.root / "broken.zip"
        source.write_bytes(b"not-a-zip")

        with self.assertRaises(zipfile.BadZipFile):
            self.module.ingest(source, self.cache)

        self.assertEqual([], list((self.cache / "snapshots").glob("*/COMPLETE")))

    def test_safe_package_document_name_is_stable(self):
        self.assertEqual(
            "APP-com.szchoiceway.eventcenter.md",
            self.module.package_document_name("com.szchoiceway.eventcenter"),
        )

    def test_generated_identity_update_preserves_manual_analysis(self):
        package = {"package": "com.example.core"}
        path = self.docs / "APP-com.example.core.md"
        path.parent.mkdir(parents=True)
        path.write_text(
            "# core\n\n<!-- GENERATED:START -->\nold\n<!-- GENERATED:END -->\n\n"
            "## Evidencia manual\n\nNo sobrescribir.\n",
            encoding="utf-8",
        )

        self.module.replace_generated_section(path, "identity-v2", package)

        content = path.read_text(encoding="utf-8")
        self.assertIn("identity-v2", content)
        self.assertNotIn("\nold\n", content)
        self.assertIn("No sobrescribir.", content)

    def test_analysis_status_distinguishes_partial_jadx_output(self):
        apk_hash = "a" * 64
        output = self.cache / "jadx" / apk_hash
        (output / "sources").mkdir(parents=True)
        (output / "COMPLETE").write_text("ok\n", encoding="utf-8")
        (output / "JADX_EXIT_CODE").write_text("3\n", encoding="utf-8")

        self.assertEqual(
            "decompilada_parcial",
            self.module.analysis_status(self.cache, apk_hash),
        )

    def test_apk_metadata_is_reused_by_content_hash(self):
        expected = {
            "permissions": [],
            "launchable_activities": [],
            "min_sdk": "34",
            "target_sdk": "34",
            "dex_files": ["classes.dex"],
            "native_libraries": [],
        }
        with (
            mock.patch.object(
                self.module, "aapt_metadata", return_value={
                    key: expected[key]
                    for key in (
                        "permissions",
                        "launchable_activities",
                        "min_sdk",
                        "target_sdk",
                    )
                }
            ) as aapt,
            mock.patch.object(
                self.module,
                "apk_entries",
                return_value={
                    "dex_files": expected["dex_files"],
                    "native_libraries": expected["native_libraries"],
                },
            ) as entries,
        ):
            first = self.module.apk_metadata(
                self.cache, "b" * 64, self.root / "unused.apk"
            )
            second = self.module.apk_metadata(
                self.cache, "b" * 64, self.root / "unused.apk"
            )

        self.assertEqual(expected, first)
        self.assertEqual(first, second)
        aapt.assert_called_once()
        entries.assert_called_once()

    def test_external_command_failure_is_not_cached_as_empty_output(self):
        with self.assertRaisesRegex(SystemExit, r"falló \(7\)"):
            self.module.run_text([sys.executable, "-c", "raise SystemExit(7)"])

    def test_external_command_timeout_is_reported(self):
        with mock.patch.object(
            self.module.subprocess,
            "run",
            side_effect=self.module.subprocess.TimeoutExpired(["aapt"], 60),
        ):
            with self.assertRaisesRegex(SystemExit, "superó 60 segundos"):
                self.module.run_text(["aapt"])

    def test_search_reports_invalid_regular_expression(self):
        apk_hash = "c" * 64
        sources = self.cache / "jadx" / apk_hash / "sources"
        sources.mkdir(parents=True)
        (sources.parent / "COMPLETE").write_text("ok\n", encoding="utf-8")
        (sources.parent / "JADX_EXIT_CODE").write_text("0\n", encoding="utf-8")
        (sources / "Example.java").write_text("class Example {}\n", encoding="utf-8")
        self.docs.mkdir()
        (self.docs / "APPS.json").write_text(
            json.dumps({
                "packages": [{"package": "com.example", "sha256": apk_hash}]
            }),
            encoding="utf-8",
        )

        with self.assertRaisesRegex(SystemExit, "rg falló"):
            self.module.search(
                self.cache, self.docs, "com.example", "[", max_hits=2
            )

    def test_verify_docs_requires_hash_inside_generated_block(self):
        apk_hash = "d" * 64
        self.docs.mkdir()
        (self.docs / "APPS.json").write_text(
            json.dumps({
                "packages": [{"package": "com.example", "sha256": apk_hash}]
            }),
            encoding="utf-8",
        )
        (self.docs / "APP-com.example.md").write_text(
            "<!-- GENERATED:START -->\nold\n<!-- GENERATED:END -->\n\n"
            f"Manual mention: {apk_hash}\n",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(SystemExit, "desactualizados"):
            self.module.verify_docs(self.docs)


if __name__ == "__main__":
    unittest.main()
