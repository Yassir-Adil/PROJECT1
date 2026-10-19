"""Local engineering gate. Does not stage/install APKs or contact the phone."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

from quality_policy import (audit_backup, audit_git, audit_manifest, audit_zip,
                            junit_summary, load_policy, python_test_summary, require, sha256, source_fingerprint)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--sdk-root", type=Path)
    parser.add_argument("--allow-dependency-download", action="store_true",
                        help="Allow Gradle to fetch public dependencies; default is --offline")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    report_dir = root / "outputs/reports/quality-gate" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    report_dir.mkdir(parents=True, exist_ok=False)
    result = {"status": "FAIL", "started_utc": datetime.now(timezone.utc).isoformat(),
              "scope": "Local engineering checks, not navigation accuracy or physical safety",
              "dependency_download_allowed": args.allow_dependency_download, "stages": []}
    started = time.monotonic()

    def run(name, command, cwd=root, env=None):
        print(name, flush=True)
        before = time.monotonic()
        log = report_dir / (name + ".log")
        with log.open("x", encoding="utf-8") as stream:
            completed = subprocess.run([str(x) for x in command], cwd=cwd, env=env,
                                       stdout=stream, stderr=subprocess.STDOUT, timeout=1200, check=False)
        result["stages"].append({"name": name, "exit_code": completed.returncode,
                                 "seconds": round(time.monotonic() - before, 3), "log": log.name})
        require(completed.returncode == 0, f"{name} failed; see {log}")

    try:
        policy = load_policy(root / "scripts/test/apk-policy.json")
        result["policy_sha256"] = sha256(root / "scripts/test/apk-policy.json")
        result["git_head"] = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
        result["dirty_worktree"] = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=root))
        result["source"] = source_fingerprint(root)
        result["privacy"] = audit_git(root)
        audit_backup(root)
        result["backup_exclusions"] = "PASS_source_XML_only"
        run("policy-tests", [sys.executable, "-m", "unittest", "discover", "-s", "scripts/test/tests", "-v"])
        run("evaluation-tests", [sys.executable, "-m", "unittest", "discover", "-s", "research/tests", "-v"])
        result["python"] = {"policy": python_test_summary(report_dir / "policy-tests.log", policy["minimum_python_tests"]["policy"]),
                            "evaluation": python_test_summary(report_dir / "evaluation-tests.log", policy["minimum_python_tests"]["evaluation"])}
        pwsh = shutil.which("pwsh")
        require(pwsh, "PowerShell 7 (pwsh) is required")
        run("scene-manifest", [pwsh, "-NoProfile", "-File", root / "scripts/test/validate-scene-manifest.ps1"])
        local = Path(os.environ.get("LOCALAPPDATA", ""))
        candidates = [args.java_home, Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None,
                      *sorted((local / "Java").glob("jdk-17*"), reverse=True)]
        java = next((p for p in candidates if p and (p / "release").is_file() and
                     'JAVA_VERSION="17.' in (p / "release").read_text()), None)
        require(java, "JDK 17 missing; use --java-home (no automatic installation)")
        sdk = args.sdk_root or Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or local / "Android/Sdk")
        aapt2 = sdk / "build-tools/35.0.0/aapt2.exe"
        require(aapt2.is_file(), "Android build-tools 35.0.0 required; use --sdk-root")
        env = dict(os.environ, JAVA_HOME=str(java), ANDROID_HOME=str(sdk))
        gradle = [root / "android-app/gradlew.bat", ":app:testDebugUnitTest", "--rerun", ":sim-core:test", "--rerun",
                  ":app:assembleDebug", ":app:assembleRelease", ":app:lintDebug", ":app:lintRelease", "--no-daemon"]
        if not args.allow_dependency_download:
            gradle.append("--offline")
        run("android", gradle, cwd=root / "android-app", env=env)
        result["jvm"] = {
            "app": junit_summary(root / "android-app/app/build/test-results/testDebugUnitTest", policy["minimum_jvm_tests"]["app"]),
            "sim": junit_summary(root / "android-app/sim-core/build/test-results/test", policy["minimum_jvm_tests"]["sim"])}
        result["apks"] = {}
        for variant, filename in (("debug", "app-debug.apk"), ("release", "app-release-unsigned.apk")):
            apk = root / "android-app/app/build/outputs/apk" / variant / filename
            data = audit_zip(apk, variant, policy)
            permissions = subprocess.check_output([str(aapt2), "dump", "permissions", str(apk)], text=True)
            tree = subprocess.check_output([str(aapt2), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"], text=True)
            data["permissions"] = audit_manifest(permissions, tree, variant, policy["package"])
            (report_dir / f"{variant}-manifest.txt").write_text(tree, encoding="utf-8")
            result["apks"][variant] = data
        result["lint"] = {}
        for variant in ("debug", "release"):
            import xml.etree.ElementTree as ET
            issues = ET.parse(root / f"android-app/app/build/reports/lint-results-{variant}.xml").getroot()
            counts = {level: sum(i.get("severity") == level for i in issues.findall("issue"))
                      for level in ("Fatal", "Error", "Warning", "Information")}
            require(not (counts["Fatal"] or counts["Error"]), "Lint errors in " + variant)
            result["lint"][variant] = counts
        require(source_fingerprint(root) == result["source"], "Source changed during verification; rerun on a stable tree")
        result["status"] = "PASS"
        result["not_tested"] = ["Phone UI, real audio, TalkBack, walking and hazard accuracy",
                                "Sustained thermal/battery/depth accuracy", "Binary backup-rule resource contents",
                                "Dependency vulnerability/licensing completeness or secret/history scanning"]
    except Exception as error:
        result["error"] = f"{type(error).__name__}: {error}"
    finally:
        result["seconds"] = round(time.monotonic() - started, 3)
        (report_dir / "summary.json").write_text(json.dumps(result, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(f"{result['status']}: {report_dir / 'summary.json'}", flush=True)
    if "error" in result:
        print(result["error"], file=sys.stderr)
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
