"""Read-only checks for local QA. No network, ADB, publishing or application imports."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile


class PolicyError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise PolicyError(message)


def sha256(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def load_policy(path):
    policy = json.loads(Path(path).read_text(encoding="utf-8"))
    require(policy["schema_version"] == 1, "Unknown APK policy schema")
    assets = [model["asset"] for model in policy["models"]]
    require(len(assets) == len(set(assets)), "Duplicate model in policy")
    require(policy["package"] == "com.triguide.ai", "Unexpected policy package")
    for model in policy["models"]:
        require(re.fullmatch(r"[0-9a-f]{64}", model["sha256"]), "Invalid SHA-256 pin")
        require(model["asset"].startswith("assets/") and model["asset"].endswith(".tflite"), "Invalid model asset")
        require(model["variants"] and set(model["variants"]) <= {"debug", "release"}, "Invalid build variant")
    return policy


def audit_zip(apk, variant, policy):
    require(variant in {"debug", "release"}, "Unknown build variant")
    expected = {m["asset"]: m["sha256"] for m in policy["models"] if variant in m["variants"]}
    require(expected, "No model pins for variant")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Duplicate ZIP entry")
        assets = {n for n in names if n.startswith("assets/") and not n.endswith("/")}
        require(not (assets - expected.keys() - set(policy["non_model_assets"])),
                "Unapproved APK assets (possible private fixture or unreviewed model): " +
                ", ".join(sorted(assets - expected.keys() - set(policy["non_model_assets"]))))
        require(expected.keys() <= assets, "Missing pinned model asset")
        for name, pin in expected.items():
            with archive.open(name) as stream:
                require(hashlib.file_digest(stream, "sha256").hexdigest() == pin, "Model hash mismatch: " + name)
        if variant == "release":
            require(not any("arcore" in n.lower() for n in names), "ARCore entry in release")
            for name in names:
                if re.fullmatch(r"classes\d*\.dex", name):
                    require(b"Lcom/google/ar/core/" not in archive.read(name), "ARCore SDK classes in release")
    return {"sha256": sha256(apk), "bytes": Path(apk).stat().st_size, "models": expected}


def audit_manifest(permissions, tree, variant, package):
    require(variant in {"debug", "release"}, "Unknown build variant")
    require(f"package: {package}" in permissions, "Unexpected APK package")
    actual = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", permissions))
    allowed = {"android.permission.CAMERA", "android.permission.VIBRATE",
               f"{package}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
    require({"android.permission.CAMERA", "android.permission.VIBRATE"} <= actual, "Missing expected permissions")
    require(not (actual - allowed), "Unapproved permissions: " + ", ".join(sorted(actual - allowed)))
    if variant == "release":
        require("com.google.ar.core" not in tree, "ARCore metadata in release")
        require(not re.search(r"com\.triguide\.ai\.diagnostics\.\w*DiagnosticActivity", tree),
                "Diagnostic activity in release")
    else:
        activities = [block for block in re.split(r"(?m)^\s*E: ", tree)
                      if block.startswith("activity ") and re.search(r"com\.triguide\.ai\.diagnostics\.\w*DiagnosticActivity", block)]
        require(len(activities) == 3, "Expected exactly three protected debug activities")
        for block in activities:
            require(re.search(r':permission\([^)]*\)="android.permission.DUMP"', block),
                    "Debug activity is not DUMP protected")
    return sorted(actual)


def audit_backup(root):
    resources = Path(root) / "android-app/app/src/main/res/xml"
    legacy = ET.parse(resources / "backup_rules.xml").getroot()
    modern = ET.parse(resources / "data_extraction_rules.xml").getroot()
    def excludes(node):
        return node is not None and any(e.get("domain") == "file" and e.get("path") == "guidance-memory/"
                                        for e in node.findall("exclude"))
    require(excludes(legacy), "Private memory is not excluded from legacy backup")
    for mode in ("cloud-backup", "device-transfer"):
        require(excludes(modern.find(mode)), f"Private memory is not excluded from {mode}")


CURATED = {"outputs/reports/.gitkeep", "outputs/reports/phase1-2026-10-18.txt",
           "test-data/.gitkeep", "test-data/README.md", "test-data/scene-manifest.example.tsv"}
PRIVATE_ROOTS = ("outputs/reports/", "test-data/", "android-app/app/src/debug/assets/", "research/model-candidates/")
PRIVATE_PROBES = ("outputs/reports/new/camera.png", "outputs/reports/new/log.txt",
                  "test-data/scene-manifest.tsv", "test-data/raw/scene.jpg",
                  "android-app/app/src/debug/assets/diagnostics/any.tflite",
                  "android-app/app/src/debug/assets/diagnostics/private.jpg",
                  "research/model-candidates/new.tflite")


def private_tracked(paths):
    return sorted(p for p in paths if p.startswith(PRIVATE_ROOTS) and p not in CURATED)


def audit_git(root):
    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode("utf-8").split("\0")
    require(not private_tracked(tracked), "Private/local artifacts already tracked: " + ", ".join(private_tracked(tracked)))
    probe = subprocess.run(["git", "check-ignore", "--no-index", "--stdin", "-z"], cwd=root,
                           input="\0".join(PRIVATE_PROBES).encode() + b"\0", capture_output=True, check=False)
    require(probe.returncode in (0, 1), "git check-ignore failed")
    ignored = set(probe.stdout.decode("utf-8").split("\0"))
    require(set(PRIVATE_PROBES) <= ignored, "Git privacy exclusions missing: " + ", ".join(sorted(set(PRIVATE_PROBES) - ignored)))
    return {"ignored_probes": len(PRIVATE_PROBES), "private_tracked": 0,
            "scope": "Known local artifact roots only; not a secret scanner or repository-history audit"}


def source_fingerprint(root):
    """Include dirty and untracked source, not just HEAD; ignore local/private artifacts."""
    names = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=root)
    selected = sorted({p for p in names.decode("utf-8").split("\0") if p and
                       (p.startswith(("android-app/", "scripts/")) or p in
                        {".gitignore", "research/evaluate_ade20k_segmenter.py", "research/requirements-eval.txt"} or
                        p.startswith("research/tests/"))})
    digest = hashlib.sha256()
    for name in selected:
        path = Path(root) / name
        content_hash = sha256(path) if path.is_file() else "DELETED"
        digest.update(f"{name}\0{content_hash}\n".encode("utf-8"))
    return {"sha256": digest.hexdigest(), "files": len(selected),
            "scope": "Git-listed source/config/test files including dirty/untracked; model bytes pinned separately"}


def junit_summary(directory, minimum):
    reports = sorted(Path(directory).glob("TEST-*.xml"))
    require(reports, "Missing JUnit XML results: " + str(directory))
    result = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in reports:
        suite = ET.parse(path).getroot()
        require(suite.tag == "testsuite", "Unexpected JUnit report format")
        for key in result:
            result[key] += int(suite.get(key, "0"))
    require(result["tests"] >= minimum, f"Test count {result['tests']} is below baseline {minimum}")
    require(not (result["failures"] or result["errors"] or result["skipped"]), "Failed/errored/skipped JVM tests")
    return result


def python_test_summary(log, minimum):
    text = Path(log).read_text(encoding="utf-8")
    count = re.search(r"(?m)^Ran (\d+) tests? in ", text)
    require(count and int(count[1]) >= minimum, "Missing/empty/reduced Python test suite")
    require("OK" in text.splitlines(), "Python suite did not finish without errors or skips")
    return {"tests": int(count[1]), "failures": 0, "errors": 0, "skipped": 0}
