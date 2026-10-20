"""Structural ground-truth support audit for Hypersim labels + world positions.

Counts NYU40-floor sample cells and synthetic periodic availability only.
Not a detector, phone confidence score, clearance claim, or navigation signal.
No network, training, Android linkage, RGB, or private fixture access from here.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path
import time
from typing import Any

import numpy as np

MAX_AREA = 1_048_576
_ALLOWED_GRIDS = frozenset((8, 16, 32))
_NYU40_FLOOR = 2
_FLOOR_EXCLUSIONS = frozenset((4, 7, 20))  # ADE floor / table / rug — never floor here


def _require_strict_int(name: str, value: Any) -> int:
    if type(value) is not int:
        raise ValueError(f"{name} must be a strict non-bool int")
    return value


def _require_labels(labels: Any) -> np.ndarray:
    array = np.asarray(labels)
    if array.dtype == np.bool_ or np.issubdtype(array.dtype, np.bool_):
        raise ValueError("labels must be a 2D integer non-bool array")
    if array.dtype.kind == "O" or array.dtype.hasobject:
        raise ValueError("labels must be a 2D integer non-bool array")
    if np.issubdtype(array.dtype, np.complexfloating) or np.iscomplexobj(array):
        raise ValueError("labels must be a 2D integer non-bool array")
    if not np.issubdtype(array.dtype, np.integer):
        raise ValueError("labels must be a 2D integer non-bool array")
    if array.ndim != 2:
        raise ValueError("labels must be a 2D integer non-bool array")
    height, width = int(array.shape[0]), int(array.shape[1])
    if height <= 0 or width <= 0:
        raise ValueError("labels dimensions must be positive")
    if height * width > MAX_AREA:
        raise ValueError(f"labels area exceeds {MAX_AREA}")
    return array


def _require_positions(positions: Any, height: int, width: int) -> np.ndarray:
    array = np.asarray(positions)
    if array.dtype == np.bool_ or np.issubdtype(array.dtype, np.bool_):
        raise ValueError("positions must be real numeric HxWx3")
    if array.dtype.kind == "O" or array.dtype.hasobject:
        raise ValueError("positions must be real numeric HxWx3")
    if np.issubdtype(array.dtype, np.complexfloating) or np.iscomplexobj(array):
        raise ValueError("positions must be real numeric HxWx3")
    if not np.issubdtype(array.dtype, np.number):
        raise ValueError("positions must be real numeric HxWx3")
    if array.ndim != 3 or int(array.shape[2]) != 3:
        raise ValueError("positions must be real numeric HxWx3")
    if int(array.shape[0]) != height or int(array.shape[1]) != width:
        raise ValueError("positions shape must match labels height and width")
    if height * width > MAX_AREA:
        raise ValueError(f"positions area exceeds {MAX_AREA}")
    return array


def _sample_centres(dimension: int, grid: int) -> list[int]:
    # floor((i + 0.5) * dimension / grid) via integer arithmetic for exactness.
    centres = [((2 * i + 1) * dimension) // (2 * grid) for i in range(grid)]
    if len(set(centres)) != grid:
        raise ValueError("sample centres are not distinct for this dimension/grid")
    if centres[0] < 0 or centres[-1] >= dimension:
        raise ValueError("sample centres fall outside the array")
    return centres


def _safe_axis_span(values: np.ndarray) -> float | None:
    """Return finite max-min, or None if empty / overflowed (never NaN/Inf JSON)."""
    if values.size == 0:
        return None
    with np.errstate(over="ignore", invalid="ignore", divide="ignore", under="ignore"):
        lo = np.min(values)
        hi = np.max(values)
        span = hi - lo
    if not np.isfinite(span):
        return None
    return float(span)


def evaluate_support(
    labels: Any,
    positions: Any,
    grid: int = 8,
    keep_every: int = 1,
) -> dict[str, Any]:
    """Aggregate structural GT floor-support counts; JSON-safe, no point arrays.

    Inputs are not mutated. Only NYU40 floor label 2 participates in the
    bottom-anchored four-connected sample component. Table/rug/ADE-floor labels
    never count as floor. Non-finite XYZ never become valid supports.
    """
    grid_i = _require_strict_int("grid", grid)
    if grid_i not in _ALLOWED_GRIDS:
        raise ValueError("grid must be one of 8, 16, 32")
    keep_i = _require_strict_int("keep_every", keep_every)
    if not 1 <= keep_i <= 64:
        raise ValueError("keep_every must be an int in 1..64")

    labels_arr = _require_labels(labels)
    height, width = int(labels_arr.shape[0]), int(labels_arr.shape[1])
    if height < grid_i or width < grid_i:
        raise ValueError("labels dimensions must be >= grid")
    positions_arr = _require_positions(positions, height, width)

    # Read-only copies so callers' arrays stay immutable even if we index heavily.
    labels_view = np.array(labels_arr, copy=True)
    positions_view = np.array(positions_arr, dtype=np.float64, copy=True)

    row_centres = _sample_centres(height, grid_i)
    col_centres = _sample_centres(width, grid_i)
    sampled_cells = grid_i * grid_i
    if sampled_cells != len(row_centres) * len(col_centres):
        raise ValueError("internal sample count mismatch")

    is_floor = np.zeros((grid_i, grid_i), dtype=bool)
    finite_xyz = np.zeros((grid_i, grid_i), dtype=bool)
    sample_xyz = np.full((grid_i, grid_i, 3), np.nan, dtype=np.float64)

    for gy, row in enumerate(row_centres):
        for gx, col in enumerate(col_centres):
            label = int(labels_view[row, col])
            # Explicit exclusions document that ADE-4 / table-7 / rug-20 are not floor.
            if label == _NYU40_FLOOR and label not in _FLOOR_EXCLUSIONS:
                is_floor[gy, gx] = True
            xyz = positions_view[row, col]
            if bool(np.all(np.isfinite(xyz))):
                finite_xyz[gy, gx] = True
                sample_xyz[gy, gx] = xyz

    # Bottom-row centre-column anchors (grid space).
    bottom = grid_i - 1
    anchor_cols = (grid_i // 2 - 1, grid_i // 2)
    stack: list[tuple[int, int]] = []
    for gx in anchor_cols:
        if is_floor[bottom, gx]:
            stack.append((bottom, gx))

    in_component = np.zeros((grid_i, grid_i), dtype=bool)
    while stack:
        gy, gx = stack.pop()
        if in_component[gy, gx] or not is_floor[gy, gx]:
            continue
        in_component[gy, gx] = True
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            ny, nx = gy + dy, gx + dx
            if 0 <= ny < grid_i and 0 <= nx < grid_i and is_floor[ny, nx] and not in_component[ny, nx]:
                stack.append((ny, nx))

    floor_component_cells = int(in_component.sum())
    finite_in_component = 0
    potential_floor_supports = 0
    support_coords: list[np.ndarray] = []
    for gy in range(grid_i):
        for gx in range(grid_i):
            if not in_component[gy, gx]:
                continue
            if not finite_xyz[gy, gx]:
                # Non-finite geometry stays missing; never imputed into supports.
                continue
            finite_in_component += 1
            flat_index = gy * grid_i + gx
            if flat_index % keep_i == 0:
                potential_floor_supports += 1
                support_coords.append(sample_xyz[gy, gx].copy())

    if support_coords:
        support_stack = np.stack(support_coords, axis=0)
        spans = {
            "x": _safe_axis_span(support_stack[:, 0]),
            "y": _safe_axis_span(support_stack[:, 1]),
            "z": _safe_axis_span(support_stack[:, 2]),
        }
    else:
        spans = {"x": None, "y": None, "z": None}

    availability = (
        "full_ground_truth"
        if keep_i == 1
        else "synthetic_periodic_drop_not_sensor_confidence"
    )

    return {
        "sampled_cells": sampled_cells,
        "floor_component_cells": floor_component_cells,
        "finite_xyz_floor_component_cells": finite_in_component,
        "potential_floor_supports": potential_floor_supports,
        "six_support_count_met": bool(potential_floor_supports >= 6),
        "finite_support_axis_spans": spans,
        "scope": "ground_truth_structural_only",
        "navigation": "none",
        "availability": availability,
    }


def evaluate_fixture_support(root: Path | str, output: Path | str) -> dict[str, Any]:
    """Run the fixed 27-case structural audit on an existing hash-verified fixture.

    Requires caller-supplied fixture and fresh output paths. Reuses the public
    fixture verifier and HDF5 reader; does not download, weaken checks, or invent
    default fixture locations.
    """
    from evaluate_hypersim_fixture import parse_dimension, read_hdf5_array
    from hypersim_fixture import PIN, SPEC, verify_fixture

    started = time.perf_counter()
    root_path, output_path = Path(root).resolve(), Path(output).resolve()
    manifest_path = root_path / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    verify_fixture(root_path, manifest)
    if manifest.get("upstream_commit") != PIN:
        raise ValueError("Unexpected dataset source pin")

    required_frames = {(scene, camera, frame) for scene, camera, frames in SPEC for frame in frames}
    actual_frames = [(item["scene"], item["camera"], item["frame"]) for item in manifest.get("frames", [])]
    if (any(type(frame) is not int for _, _, frame in actual_frames)
            or len(actual_frames) != len(required_frames) or set(actual_frames) != required_frames):
        raise ValueError("Unexpected or duplicate fixture frame selection")
    names = {item["path"] for item in manifest["files"]}

    def checked_path(name: str) -> Path:
        if name not in names:
            raise ValueError("Missing hash record: " + name)
        return root_path / name

    with checked_path("_metadata/camera_parameters.csv").open(newline="", encoding="utf-8") as stream:
        camera_rows = list(csv.DictReader(stream))
    if len({row["scene_name"] for row in camera_rows}) != len(camera_rows):
        raise ValueError("Duplicate camera calibration scene")
    cameras = {row["scene_name"]: row for row in camera_rows}

    with checked_path("_metadata/split.csv").open(newline="", encoding="utf-8") as stream:
        public_val = {
            (row["scene_name"], row["camera_name"], int(row["frame_id"]))
            for row in csv.DictReader(stream)
            if row["included_in_public_release"] == "True" and row["split_partition_name"] == "val"
        }
    if not required_frames <= public_val:
        raise ValueError("Non-public/non-validation frame")

    if output_path == root_path or output_path.is_relative_to(root_path) or root_path.is_relative_to(output_path):
        raise ValueError("Keep reports and fixture bytes in separate roots")
    if output_path.exists():
        raise FileExistsError("Report directory already exists")

    grids = (8, 16, 32)
    drops = (1, 4, 16)
    cases: list[dict[str, Any]] = []

    for scene, camera, frame in sorted(required_frames):
        row = cameras[scene]
        with checked_path(f"{scene}/_detail/metadata_scene.csv").open(newline="", encoding="utf-8") as stream:
            metadata_rows = list(csv.DictReader(stream))
        scales = [
            float(item["parameter_value"])
            for item in metadata_rows
            if item["parameter_name"] == "meters_per_asset_unit"
        ]
        if len(scales) != 1:
            raise ValueError("Missing/duplicate asset-unit scale")
        scale = scales[0]
        if not np.isfinite(scale) or scale <= 0:
            raise ValueError("Asset-unit scale must be finite and positive")
        if not np.isclose(scale, float(row["settings_units_info_meters_scale"]), rtol=1e-7, atol=0):
            raise ValueError("Inconsistent scene/camera asset scale")

        detail = f"{scene}/_detail/{camera}"
        # Keyframe integrity (same contract as the coordinate oracle); not used for nav.
        pose_positions = read_hdf5_array(checked_path(detail + "/camera_keyframe_positions.hdf5"))
        rotations = read_hdf5_array(checked_path(detail + "/camera_keyframe_orientations.hdf5"))
        indices = read_hdf5_array(checked_path(detail + "/camera_keyframe_frame_indices.hdf5"))
        if pose_positions.ndim != 2 or pose_positions.shape[1:] != (3,) or rotations.shape != (pose_positions.shape[0], 3, 3):
            raise ValueError("Malformed pose trajectory")
        if (indices.ndim != 1 or indices.shape[0] != pose_positions.shape[0] or indices.dtype.kind not in "iu"
                or np.any(indices < 0) or np.unique(indices).size != indices.size):
            raise ValueError("Malformed/duplicate pose keyframe indices")
        matches = np.flatnonzero(indices == frame)
        if matches.size != 1:
            raise ValueError("Pose keyframe absent")

        prefix = f"{scene}/images/scene_{camera}_geometry_hdf5/frame.{frame:04d}"
        # Labels + GT world positions only; no RGB and no model inference.
        semantics = read_hdf5_array(checked_path(prefix + ".semantic.hdf5"))
        reference_asset = read_hdf5_array(checked_path(prefix + ".position.hdf5"))
        expected_h = parse_dimension(row["settings_output_img_height"])
        expected_w = parse_dimension(row["settings_output_img_width"])
        if semantics.shape != (expected_h, expected_w):
            raise ValueError("Calibration/semantic dimension mismatch")
        if reference_asset.shape != (expected_h, expected_w, 3):
            raise ValueError("Calibration/position dimension mismatch")

        with np.errstate(over="ignore", invalid="ignore", divide="ignore", under="ignore"):
            world_positions = np.asarray(reference_asset, dtype=np.float64) * float(scale)

        for grid_i in grids:
            for keep_i in drops:
                report = evaluate_support(semantics, world_positions, grid=grid_i, keep_every=keep_i)
                cases.append({
                    "scene": scene,
                    "camera": camera,
                    "frame": frame,
                    "grid": grid_i,
                    "keep_every": keep_i,
                    "sampled_cells": report["sampled_cells"],
                    "floor_component_cells": report["floor_component_cells"],
                    "finite_xyz_floor_component_cells": report["finite_xyz_floor_component_cells"],
                    "potential_floor_supports": report["potential_floor_supports"],
                    "six_support_count_met": report["six_support_count_met"],
                    "finite_support_axis_spans": report["finite_support_axis_spans"],
                    "scope": report["scope"],
                    "navigation": report["navigation"],
                    "availability": report["availability"],
                })

    if len(cases) != 27:
        raise ValueError(f"Expected 27 fixed cases, got {len(cases)}")

    six_met = sum(1 for case in cases if case["six_support_count_met"])
    zero_component = sum(1 for case in cases if case["floor_component_cells"] == 0)
    zero_supports = sum(1 for case in cases if case["potential_floor_supports"] == 0)

    research_dir = Path(__file__).resolve().parent
    summary: dict[str, Any] = {
        "status": "observed",
        "scope": "ground_truth_structural_only",
        "navigation": "none",
        "provenance": {
            "label_semantic_id_space": "NYU40",
            "nyu40_floor_label": _NYU40_FLOOR,
            "not_floor_labels": sorted(_FLOOR_EXCLUSIONS),
            "synthetic_drop": "periodic_flat_grid_index_mod_keep_every_NOT_phone_sensor_confidence",
            "positions": "fixture_gt_world_metres_from_asset_scale_not_sensor_metric_claim",
        },
        "upstream_commit": PIN,
        "manifest_sha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
        "fixture_file_count": len(manifest["files"]),
        "case_counts": {
            "total": len(cases),
            "scenes": len(required_frames),
            "grids": list(grids),
            "keep_every_values": list(drops),
            "six_support_count_met": six_met,
            "zero_floor_component": zero_component,
            "zero_potential_supports": zero_supports,
        },
        "cases": cases,
        "versions": {"numpy": np.__version__},
        "source_sha256": {
            "requirements-eval.txt": hashlib.sha256((research_dir / "requirements-eval.txt").read_bytes()).hexdigest(),
            "evaluate_hypersim_support.py": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            "evaluate_hypersim_fixture.py": hashlib.sha256(
                (research_dir / "evaluate_hypersim_fixture.py").read_bytes()
            ).hexdigest(),
            "hypersim_fixture.py": hashlib.sha256(
                (research_dir / "hypersim_fixture.py").read_bytes()
            ).hexdigest(),
        },
        "seconds": round(time.perf_counter() - started, 4),
        "limits": [
            "Structural GT / synthetic drop audit only — not model confidence or recognition",
            "Does not permit navigation, CLEAR, turn, or safe-path claims",
            "Spans are GT aggregate extents; not phone/sensor metric accuracy",
            "Requires an existing hash-verified public fixture path; this CLI does not download",
            "No RGB loaded; no training; no Android linkage",
        ],
    }

    output_path.mkdir(parents=True, exist_ok=False)
    with (output_path / "summary.json").open("x", encoding="utf-8") as stream:
        json.dump(summary, stream, indent=2, allow_nan=False)
    return summary


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--fixture",
        type=Path,
        required=True,
        help="Existing hash-verified public Hypersim fixture root (caller-supplied; not invented)",
    )
    parser.add_argument(
        "--out",
        type=Path,
        required=True,
        help="Fresh output directory for bounded JSON summary (must not already exist)",
    )
    arguments = parser.parse_args(argv)
    evaluate_fixture_support(arguments.fixture, arguments.out)


if __name__ == "__main__":
    main()
