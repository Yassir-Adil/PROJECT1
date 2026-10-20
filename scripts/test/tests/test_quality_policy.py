import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from quality_policy import (PolicyError, audit_backup, audit_git, audit_manifest, audit_zip,
                            junit_summary, load_policy, private_tracked, python_test_summary, source_fingerprint)

REPO = Path(__file__).resolve().parents[3]
PACKAGE = "com.triguide.ai"
PERMISSIONS = f"""package: {PACKAGE}
uses-permission: name='android.permission.CAMERA'
uses-permission: name='android.permission.VIBRATE'
uses-permission: name='{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
"""


class QualityPolicyTest(unittest.TestCase):
    def test_source_fingerprint_covers_all_research_helpers_and_requirements(self):
        # Existing gate covered evaluator/tests but missed new helper modules.
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            (root / "research").mkdir()
            helper = root / "research/hypersim_geometry.py"
            requirements = root / "research/requirements-rgbd.txt"
            helper.write_text("before\n", encoding="utf-8")
            requirements.write_text("h5py==3.16.0\n", encoding="utf-8")
            first = source_fingerprint(root)
            self.assertEqual(2, first["files"])
            helper.write_text("after\n", encoding="utf-8")
            self.assertNotEqual(first["sha256"], source_fingerprint(root)["sha256"])
            second = source_fingerprint(root)
            requirements.write_text("changed\n", encoding="utf-8")
            self.assertNotEqual(second["sha256"], source_fingerprint(root)["sha256"])

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "fixture.apk"
        self.asset = "assets/models/test.tflite"
        self.policy = {"models": [{"asset": self.asset, "sha256": hashlib.sha256(b"model").hexdigest(),
                                   "variants": ["debug", "release"]}], "non_model_assets": []}

    def archive(self, extras=(), payload=b"model"):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr(self.asset, payload)
            for name, value in extras:
                archive.writestr(name, value)

    def test_pinned_model_passes_and_records_actual_apk_hash(self):
        self.archive()
        result = audit_zip(self.apk, "release", self.policy)
        self.assertEqual(hashlib.sha256(self.apk.read_bytes()).hexdigest(), result["sha256"])

    def test_changed_model_is_rejected(self):
        self.archive(payload=b"changed")
        with self.assertRaisesRegex(PolicyError, "hash mismatch"):
            audit_zip(self.apk, "debug", self.policy)

    def test_private_image_or_unapproved_model_cannot_enter_either_apk(self):
        for name in ("assets/diagnostics/wall-scene.jpg", "assets/unknown.tflite"):
            for variant in ("debug", "release"):
                with self.subTest(name=name, variant=variant):
                    self.archive([(name, b"private")])
                    with self.assertRaisesRegex(PolicyError, "Unapproved APK assets"):
                        audit_zip(self.apk, variant, self.policy)

    def test_missing_model_fails(self):
        with zipfile.ZipFile(self.apk, "w"):
            pass
        with self.assertRaisesRegex(PolicyError, "Missing pinned"):
            audit_zip(self.apk, "release", self.policy)

    def test_duplicate_zip_entry_is_rejected(self):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            self.archive([(self.asset, b"model")])
        with self.assertRaisesRegex(PolicyError, "Duplicate ZIP"):
            audit_zip(self.apk, "debug", self.policy)

    def test_arcore_sdk_or_native_library_cannot_enter_release(self):
        for name, payload in (("classes.dex", b"prefix Lcom/google/ar/core/Session; suffix"),
                              ("lib/arm64-v8a/libarcore_sdk_c.so", b"sdk")):
            self.archive([(name, payload)])
            with self.assertRaisesRegex(PolicyError, "ARCore"):
                audit_zip(self.apk, "release", self.policy)

    def test_unknown_variant_fails(self):
        self.archive()
        with self.assertRaises(PolicyError):
            audit_zip(self.apk, "relase", self.policy)

    def test_added_internet_or_sensitive_permission_fails(self):
        for name in ("android.permission.INTERNET", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_NETWORK_STATE"):
            with self.assertRaisesRegex(PolicyError, "Unapproved permissions"):
                audit_manifest(PERMISSIONS + f"uses-permission: name='{name}'", "", "release", PACKAGE)

    def test_offline_release_passes_but_diagnostic_metadata_fails(self):
        self.assertEqual(3, len(audit_manifest(PERMISSIONS, "", "release", PACKAGE)))
        for tree in ("com.google.ar.core", "com.triguide.ai.diagnostics.DeepLabDiagnosticActivity"):
            with self.assertRaises(PolicyError):
                audit_manifest(PERMISSIONS, tree, "release", PACKAGE)

    def test_missing_permission_or_wrong_package_fails_closed(self):
        for permissions in ("", PERMISSIONS.replace(PACKAGE, "other.app"), PERMISSIONS.replace("CAMERA", "OTHER")):
            with self.assertRaises(PolicyError):
                audit_manifest(permissions, "", "release", PACKAGE)

    def test_every_debug_entry_requires_dump_protection(self):
        tree = "\n".join(f'''      E: activity (line=2)
        A: android:name(0x1)="com.triguide.ai.diagnostics.{name}DiagnosticActivity"
        A: android:permission(0x2)="android.permission.DUMP"
''' for name in ("DeepLab", "LiveSegmenter", "DepthCapability", "RawDepth"))
        audit_manifest(PERMISSIONS, tree, "debug", PACKAGE)
        with self.assertRaisesRegex(PolicyError, "DUMP protected"):
            audit_manifest(PERMISSIONS, tree.replace('android.permission.DUMP', 'other', 1), "debug", PACKAGE)

    def test_real_policy_is_valid_but_duplicate_model_is_not(self):
        original = json.loads((REPO / "scripts/test/apk-policy.json").read_text())
        path = self.root / "policy.json"
        path.write_text(json.dumps(original))
        self.assertEqual(4, len(load_policy(path)["models"]))
        duplicate = copy.deepcopy(original)
        duplicate["models"].append(duplicate["models"][0])
        path.write_text(json.dumps(duplicate))
        with self.assertRaisesRegex(PolicyError, "Duplicate"):
            load_policy(path)

    def test_unknown_dump_protected_activity_is_still_unreviewed(self):
        tree = "\n".join(f'''      E: activity (line=2)
        A: android:name(0x1)="com.triguide.ai.diagnostics.{name}DiagnosticActivity"
        A: android:permission(0x2)="android.permission.DUMP"
''' for name in ("DeepLab", "LiveSegmenter", "DepthCapability", "Unexpected"))
        with self.assertRaisesRegex(PolicyError, "inventory"):
            audit_manifest(PERMISSIONS, tree, "debug", PACKAGE)

    def test_backup_protects_all_three_modes_and_rejects_missing_exclusion(self):
        folder = self.root / "android-app/app/src/main/res/xml"
        folder.mkdir(parents=True)
        exclude = '<exclude domain="file" path="guidance-memory/" />'
        (folder / "backup_rules.xml").write_text(f"<full-backup-content>{exclude}</full-backup-content>")
        modern = f"<data-extraction-rules><cloud-backup>{exclude}</cloud-backup><device-transfer>{exclude}</device-transfer></data-extraction-rules>"
        (folder / "data_extraction_rules.xml").write_text(modern)
        audit_backup(self.root)
        (folder / "data_extraction_rules.xml").write_text(modern.replace(exclude, "", 1))
        with self.assertRaisesRegex(PolicyError, "cloud-backup"):
            audit_backup(self.root)

    def test_gitignore_is_exercised_by_real_git_and_tracked_capture_fails(self):
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        (self.root / ".gitignore").write_bytes((REPO / ".gitignore").read_bytes())
        self.assertEqual(0, audit_git(self.root)["private_tracked"])
        capture = self.root / "outputs/reports/camera.png"
        capture.parent.mkdir(parents=True)
        capture.write_bytes(b"private")
        subprocess.run(["git", "add", "-f", "outputs/reports/camera.png"], cwd=self.root, check=True)
        with self.assertRaisesRegex(PolicyError, "already tracked"):
            audit_git(self.root)

    def test_curated_templates_are_not_classed_as_private_captures(self):
        self.assertEqual([], private_tracked(["test-data/README.md", "test-data/scene-manifest.example.tsv"]))
        self.assertEqual(["test-data/scene-manifest.tsv"], private_tracked(["test-data/scene-manifest.tsv"]))

    def test_source_fingerprint_includes_untracked_dirty_and_deleted_source(self):
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        code = self.root / "scripts/test/example.py"
        code.parent.mkdir(parents=True)
        code.write_text("initial")
        first = source_fingerprint(self.root)
        self.assertEqual(1, first["files"])
        code.write_text("changed")
        self.assertNotEqual(first, source_fingerprint(self.root))
        subprocess.run(["git", "add", "scripts/test/example.py"], cwd=self.root, check=True)
        code.unlink()
        self.assertNotEqual(first, source_fingerprint(self.root))

    def test_missing_empty_failed_or_skipped_jvm_suite_is_not_a_pass(self):
        with self.assertRaises(PolicyError):
            junit_summary(self.root, 1)
        for tests, failures, skipped in ((0, 0, 0), (1, 1, 0), (1, 0, 1)):
            (self.root / "TEST-fixture.xml").write_text(f'<testsuite tests="{tests}" failures="{failures}" skipped="{skipped}"/>')
            with self.assertRaises(PolicyError):
                junit_summary(self.root, 1)
        (self.root / "TEST-fixture.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
        self.assertEqual(2, junit_summary(self.root, 1)["tests"])

    def test_empty_missing_failed_or_skipped_python_suite_is_not_a_pass(self):
        log = self.root / "suite.log"
        for content in ("", "Ran 0 tests in 0.0s\n\nOK\n", "Ran 1 test in 0.1s\n\nFAILED (failures=1)\n",
                        "Ran 1 test in 0.1s\n\nOK (skipped=1)\n"):
            log.write_text(content)
            with self.assertRaises(PolicyError):
                python_test_summary(log, 1)
        log.write_text("Ran 1 test in 0.1s\n\nOK\n")
        self.assertEqual(1, python_test_summary(log, 1)["tests"])


if __name__ == "__main__":
    unittest.main()
