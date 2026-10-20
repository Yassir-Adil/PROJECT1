"""Fetch a tiny public validation fixture using bounded Range + stdlib ZIP.

No Android dependency, image upload, training, paid mesh or whole-scene download.
Data: Hypersim, Roberts et al. ICCV2021, CC-BY-SA3.0. Toolkit terms separate.
"""
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import stat
import urllib.request
import zipfile

PIN = "3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254"
UPSTREAM = f"https://raw.githubusercontent.com/apple/ml-hypersim/{PIN}/"
SCENE_BASE = "https://docs-assets.developer.apple.com/ml-research/datasets/hypersim/v1/scenes/"
SPEC = [(scene, "cam_00", [0]) for scene in ("ai_003_010", "ai_004_003", "ai_004_004")]
MAX_MEMBER_BYTES = 32 * 1024**2
MAX_EXTRACTED_BYTES = 128 * 1024**2
METADATA = {
    "_metadata/camera_parameters.csv": "contrib/mikeroberts3000/metadata_camera_parameters.csv",
    "_metadata/split.csv": "evermotion_dataset/analysis/metadata_images_split_scene_v1.csv",
}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Redirect not permitted for pinned fixture source")


def _safe_path(name):
    if (not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9_./-]+", name)
            or name.startswith("/") or "//" in name
            or any(part in {"", ".", ".."} for part in name.split("/"))):
        raise ValueError("Unsafe fixture path")
    return PurePosixPath(name)


def _target(root, name):
    path = root.joinpath(*_safe_path(name).parts)
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError("Fixture path escaped root")
    return path


def choose_members(infos, scene, camera, frames):
    if not re.fullmatch(r"ai_[0-9]{3}_[0-9]{3}", scene) or not re.fullmatch(r"cam_[0-9]{2}", camera):
        raise ValueError("Invalid scene/camera")
    if (not frames or len(frames) > 5 or any(type(frame) is not int or not 0 <= frame <= 9999 for frame in frames)
            or len(set(frames)) != len(frames)):
        raise ValueError("Invalid frame selection")
    required = {
        f"{scene}/_detail/metadata_scene.csv",
        f"{scene}/_detail/{camera}/camera_keyframe_positions.hdf5",
        f"{scene}/_detail/{camera}/camera_keyframe_orientations.hdf5",
        f"{scene}/_detail/{camera}/camera_keyframe_frame_indices.hdf5",
        *[f"{scene}/images/scene_{camera}_geometry_hdf5/frame.{frame:04d}.{kind}.hdf5"
          for frame in frames for kind in ("depth_meters", "position", "semantic")],
    }
    by_name = {}
    for info in infos:
        if info.filename in by_name:
            raise ValueError("Duplicate ZIP member")
        by_name[info.filename] = info
    if not required <= by_name.keys():
        raise ValueError("Missing required ZIP members: " + ",".join(sorted(required - by_name.keys())))
    selected = [by_name[name] for name in sorted(required)]
    _validate_members(selected)
    return selected


def _validate_members(selected):
    total = 0
    names = set()
    for member in selected:
        _safe_path(member.filename)
        mode = stat.S_IFMT(member.external_attr >> 16)
        if (member.filename in names or member.is_dir() or mode not in (0, stat.S_IFREG)
                or member.flag_bits & 1 or member.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED)
                or not 0 <= member.file_size <= MAX_MEMBER_BYTES
                or not 0 <= member.compress_size <= MAX_MEMBER_BYTES):
            raise ValueError("Unsupported or oversized ZIP member")
        names.add(member.filename)
        total += member.file_size
    if total > MAX_EXTRACTED_BYTES:
        raise ValueError("Extraction byte budget exceeded")


def extract_selected(archive, selected, root):
    selected = list(selected)
    _validate_members(selected)
    root = Path(root)
    # Validate all targets before creating anything; never overwrite existing bytes.
    targets = [_target(root, member.filename) for member in selected]
    if any(target.exists() for target in targets):
        raise FileExistsError("A selected fixture target already exists")
    records = []
    for member, target in zip(selected, targets, strict=True):
        target.parent.mkdir(parents=True, exist_ok=True)
        digest = hashlib.sha256()
        count = 0
        with archive.open(member) as source, target.open("xb") as destination:
            while chunk := source.read(min(1024**2, member.file_size - count + 1)):
                count += len(chunk)
                if count > member.file_size:
                    raise ValueError("ZIP member exceeded advertised size")
                destination.write(chunk)
                digest.update(chunk)
        if count != member.file_size:
            raise ValueError("Truncated ZIP member")
        # ZipExtFile verifies CRC at EOF; local SHA pins the bytes actually fetched.
        records.append({"path": member.filename, "bytes": count, "sha256": digest.hexdigest()})
    return records


def verify_fixture(root, manifest):
    if manifest.get("schema_version") != 1 or manifest.get("status") != "complete" or not manifest.get("files"):
        raise ValueError("Incomplete/unsupported fixture manifest")
    names = set()
    for record in manifest["files"]:
        name = record["path"]
        target = _target(Path(root), name)
        if (name in names or type(record["bytes"]) is not int or not 0 <= record["bytes"] <= MAX_MEMBER_BYTES
                or not re.fullmatch(r"[0-9a-f]{64}", record["sha256"])):
            raise ValueError("Invalid/duplicate manifest record")
        names.add(name)
        if (not target.is_file() or target.stat().st_size != record["bytes"]
                or hashlib.sha256(target.read_bytes()).hexdigest() != record["sha256"]):
            raise ValueError("Fixture bytes/hash mismatch: " + name)


def _fetch_metadata(opener, root, name, relative):
    request = urllib.request.Request(UPSTREAM + relative, headers={"Accept-Encoding": "identity"})
    with opener.open(request, timeout=20) as response:
        if response.status != 200 or response.geturl() != request.full_url:
            raise ValueError("Unexpected pinned metadata response")
        data = response.read(4 * 1024**2 + 1)
    if len(data) > 4 * 1024**2:
        raise ValueError("Metadata response exceeded budget")
    target = _target(root, name)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("xb") as stream:
        stream.write(data)
    return {"path": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def download_fixture(root):
    from hypersim_transport import RangeReader
    root = Path(root).resolve()
    if root.is_relative_to(Path(__file__).resolve().parents[1]):
        raise ValueError("Keep public fixture bytes outside the Git checkout/APK")
    root.mkdir(parents=True, exist_ok=False)
    opener = urllib.request.build_opener(NoRedirect())
    manifest = {"schema_version": 1, "status": "incomplete", "upstream_commit": PIN,
                "created_utc": datetime.now(timezone.utc).isoformat(),
                "attribution": "Hypersim, Roberts et al. ICCV2021, Apple, CC-BY-SA3.0",
                "data_license": "https://creativecommons.org/licenses/by-sa/3.0/",
                "scope": "Coordinate oracle only, not recognition or physical safety",
                "frames": [], "files": [], "archives": []}
    manifest_path = root / "manifest.json"
    try:
        for name, relative in METADATA.items():
            manifest["files"].append(_fetch_metadata(opener, root, name, relative))
        with (root / "_metadata/split.csv").open(newline="", encoding="utf-8") as stream:
            public_val = {(row["scene_name"], row["camera_name"], int(row["frame_id"]))
                          for row in csv.DictReader(stream) if row["split_partition_name"] == "val"
                          and row["included_in_public_release"] == "True"}
        for scene, camera, frames in SPEC:
            if any((scene, camera, frame) not in public_val for frame in frames):
                raise ValueError("Fixture frame is not in the public validation split")
            url = SCENE_BASE + scene + ".zip"
            request = urllib.request.Request(url, method="HEAD", headers={"Accept-Encoding": "identity"})
            with opener.open(request, timeout=20) as response:
                if response.status != 200 or response.geturl() != url:
                    raise ValueError("Unexpected archive HEAD")
                size, etag = int(response.headers["Content-Length"]), response.headers["ETag"]
            with RangeReader(url, size, etag, opener=opener.open) as reader:
                with zipfile.ZipFile(reader) as archive:
                    selected = choose_members(archive.infolist(), scene, camera, frames)
                    manifest["files"].extend(extract_selected(archive, selected, root))
                manifest["archives"].append({"url": url, "etag": etag, "archive_bytes": size,
                                              "transferred_bytes": reader.bytes_read, "requests": reader.request_count})
            manifest["frames"].extend({"scene": scene, "camera": camera, "frame": frame} for frame in frames)
            print(f"Fetched {scene}/{camera}: {len(frames)} frame(s), {reader.bytes_read} transferred bytes", flush=True)
        manifest["status"] = "complete"
        verify_fixture(root, manifest)
    except Exception as error:
        manifest["status"] = "incomplete"
        manifest["error"] = f"{type(error).__name__}: {error}"
        raise
    finally:
        # Preserve partial evidence; never delete, silently resume or label failed downloads complete.
        with manifest_path.open("x", encoding="utf-8") as stream:
            json.dump(manifest, stream, indent=2, allow_nan=False)
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fetch", action="store_true", required=True, help="Explicit public-data network action")
    parser.add_argument("--output-root", type=Path, required=True, help="New dedicated directory outside this checkout")
    arguments = parser.parse_args()
    download_fixture(arguments.output_root)
