"""Offline, hash-checked coordinate oracle for the small public Hypersim fixture.

Consumes ground-truth range/pose/semantic labels, NOT a detector or model score.
No network, training, Android linkage, private image access or navigation output.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path
import time

import numpy as np

from hypersim_fixture import PIN, SPEC, verify_fixture

MAX_ARRAY_ELEMENTS = 3 * 1_048_576
MAX_ARRAY_BYTES = 32 * 1024**2


def parse_dimension(value):
    if isinstance(value, (bool, np.bool_)):
        raise ValueError("Boolean camera dimension")
    number = float(value)
    if not np.isfinite(number) or not number.is_integer() or not 0 < number <= 1_048_576:
        raise ValueError("Invalid/fractional camera dimension")
    return int(number)


def read_hdf5_array(path):
    """Read one bounded local numeric dataset without external HDF5 references."""
    import h5py
    with h5py.File(path, "r") as file:
        if not isinstance(file.get("dataset", getlink=True), h5py.HardLink):
            raise ValueError("Missing or externally linked HDF5 dataset")
        data = file["dataset"]
        if (not isinstance(data, h5py.Dataset) or data.is_virtual or data.external
                or data.dtype.kind not in "iuf" or data.dtype.itemsize > 8
                or not 1 <= len(data.shape) <= 3 or not 0 < data.size <= MAX_ARRAY_ELEMENTS
                or data.size * data.dtype.itemsize > MAX_ARRAY_BYTES):
            raise ValueError("Unsupported or oversized HDF5 dataset")
        if len(data.shape) == 2 and data.size > 1_048_576:
            raise ValueError("HDF5 image/trajectory area exceeds cap")
        if len(data.shape) == 3 and (data.shape[-1] != 3 or data.shape[0] * data.shape[1] > 1_048_576):
            raise ValueError("HDF5 vector image/rotation shape exceeds cap")
        return data[:]


def evaluate_fixture(root, output):
    from hypersim_geometry import unproject_range, residual_summary
    started = time.perf_counter()
    root, output = Path(root).resolve(), Path(output).resolve()
    manifest_path = root / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    verify_fixture(root, manifest)
    if manifest.get("upstream_commit") != PIN:
        raise ValueError("Unexpected dataset source pin")
    required_frames = {(scene, camera, frame) for scene, camera, frames in SPEC for frame in frames}
    actual_frames = [(item["scene"], item["camera"], item["frame"]) for item in manifest.get("frames", [])]
    if (any(type(frame) is not int for _, _, frame in actual_frames)
            or len(actual_frames) != len(required_frames) or set(actual_frames) != required_frames):
        raise ValueError("Unexpected or duplicate fixture frame selection")
    names = {item["path"] for item in manifest["files"]}

    def checked_path(name):
        if name not in names:
            raise ValueError("Missing hash record: " + name)
        return root / name

    with checked_path("_metadata/camera_parameters.csv").open(newline="", encoding="utf-8") as stream:
        camera_rows = list(csv.DictReader(stream))
    if len({row["scene_name"] for row in camera_rows}) != len(camera_rows):
        raise ValueError("Duplicate camera calibration scene")
    cameras = {row["scene_name"]: row for row in camera_rows}
    with checked_path("_metadata/split.csv").open(newline="", encoding="utf-8") as stream:
        public_val = {(row["scene_name"], row["camera_name"], int(row["frame_id"])) for row in csv.DictReader(stream)
                      if row["included_in_public_release"] == "True" and row["split_partition_name"] == "val"}
    if not required_frames <= public_val:
        raise ValueError("Non-public/non-validation frame")
    if output == root or output.is_relative_to(root) or root.is_relative_to(output):
        raise ValueError("Keep reports and fixture bytes in separate roots")
    if output.exists():
        raise FileExistsError("Report directory already exists")
    results = []
    for scene, camera, frame in sorted(required_frames):
        before = time.perf_counter()
        row = cameras[scene]
        matrix = np.array([[float(row[f"M_cam_from_uv_{i}{j}"]) for j in range(3)] for i in range(3)])
        with checked_path(f"{scene}/_detail/metadata_scene.csv").open(newline="", encoding="utf-8") as stream:
            metadata_rows = list(csv.DictReader(stream))
        scales = [float(item["parameter_value"]) for item in metadata_rows
                  if item["parameter_name"] == "meters_per_asset_unit"]
        if len(scales) != 1:
            raise ValueError("Missing/duplicate asset-unit scale")
        scale = scales[0]
        if not np.isclose(scale, float(row["settings_units_info_meters_scale"]), rtol=1e-7, atol=0):
            raise ValueError("Inconsistent scene/camera asset scale")
        detail = f"{scene}/_detail/{camera}"
        positions = read_hdf5_array(checked_path(detail + "/camera_keyframe_positions.hdf5"))
        rotations = read_hdf5_array(checked_path(detail + "/camera_keyframe_orientations.hdf5"))
        indices = read_hdf5_array(checked_path(detail + "/camera_keyframe_frame_indices.hdf5"))
        if positions.ndim != 2 or positions.shape[1:] != (3,) or rotations.shape != (positions.shape[0], 3, 3):
            raise ValueError("Malformed pose trajectory")
        if (indices.ndim != 1 or indices.shape[0] != positions.shape[0] or indices.dtype.kind not in "iu"
                or np.any(indices < 0) or np.unique(indices).size != indices.size):
            raise ValueError("Malformed/duplicate pose keyframe indices")
        matches = np.flatnonzero(indices == frame)
        if matches.size != 1:
            raise ValueError("Pose keyframe absent")
        pose_row = int(matches[0])
        prefix = f"{scene}/images/scene_{camera}_geometry_hdf5/frame.{frame:04d}"
        depth = read_hdf5_array(checked_path(prefix + ".depth_meters.hdf5"))
        reference = read_hdf5_array(checked_path(prefix + ".position.hdf5"))
        semantics = read_hdf5_array(checked_path(prefix + ".semantic.hdf5"))
        if depth.shape != (parse_dimension(row["settings_output_img_height"]), parse_dimension(row["settings_output_img_width"])):
            raise ValueError("Calibration/depth dimension mismatch")
        world, camera_z, valid = unproject_range(depth, matrix, rotations[pose_row], positions[pose_row], scale)
        summary = residual_summary(world, reference, scale, semantics)
        finite_depth = np.isfinite(depth) & (depth > 0)
        with np.errstate(over="ignore", invalid="ignore"):
            delta = depth.astype(np.float64)[valid] - camera_z[valid]
        if not np.all(np.isfinite(delta)):
            raise ValueError("Nonfinite range-minus-camera-z diagnostic")
        result = {"scene": scene, "camera": camera, "frame": frame,
                  "shape": list(depth.shape), "pose_keyframe_row": pose_row,
                  "positive_range_pixels": int(finite_depth.sum()),
                  "valid_projected_pixels": int(valid.sum()),
                  "range_minus_camera_z_m_p50": float(np.percentile(delta, 50)) if delta.size else None,
                  "range_minus_camera_z_m_p90": float(np.percentile(delta, 90)) if delta.size else None,
                  "geometry": summary, "host_seconds": round(time.perf_counter() - before, 4)}
        results.append(result)
        print(f"{scene}: shared={summary['compared_pixels']}, coordinate residual p90={summary['error_m']['p90']}m", flush=True)
    report = {"status": "observed", "scope": "Ground-truth coordinate oracle, NOT model recognition or phone accuracy",
              "upstream_commit": PIN, "manifest_sha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
              "fixture_files": manifest["files"], "frames": results,
              "versions": {"numpy": np.__version__},
              "source_sha256": {name: hashlib.sha256((Path(__file__).parent / name).read_bytes()).hexdigest()
                                for name in ("hypersim_geometry.py", "hypersim_fixture.py", "evaluate_hypersim_fixture.py",
                                             "hypersim_transport.py", "requirements-eval.txt")},
              "seconds": round(time.perf_counter() - started, 4),
              "limits": ["Three synthetic validation frames, not a statistical recognition benchmark",
                         "Float16 depth/world positions are quantized ground truth",
                         "Labels are supplied ground truth, not discovered by this tool",
                         "Ground-truth world-Z medians are not live ground reference or walking permission"]}
    import h5py
    report["versions"]["h5py"] = h5py.__version__
    output.mkdir(parents=True, exist_ok=False)
    with (output / "summary.json").open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2, allow_nan=False)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    evaluate_fixture(arguments.fixture_root, arguments.output)
