"""Pure-numpy Hypersim Euclidean-range unprojection and residual summary.

Geometry math adapted from the Apple Hypersim (Roberts et al., 2021) toolkit
raycasting notebook terms (M_cam_from_uv, R_world_from_cam, pixel-centred u/v
with reversed vertical, Euclidean range in metres, poses in asset units) at pin:

https://github.com/apple/ml-hypersim/tree/3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254

Full licence text is added separately under third_party by Codex. This module
preserves the official ray/pose math only — not the whole toolkit, pandas,
mesh I/O, or Embree. Research/evaluator oracle only: no hardware, Redmi, or
navigation accuracy claim.
"""

from __future__ import annotations

import math
from typing import Any

import numpy as np

MAX_DEPTH_AREA = 1_048_576
_ORTHONORMAL_TOL = 1e-5

_NYU40_NAMED = {
    "wall": 1,
    "floor": 2,
    "table": 7,
    "floor_mat": 20,
}


def _require_numeric_nonbool_ndarray(name: str, value: Any) -> np.ndarray:
    array = np.asarray(value)
    if array.dtype == np.bool_ or np.issubdtype(array.dtype, np.bool_):
        raise ValueError(f"{name} must be a non-bool numeric array")
    # Complex is np.number; reject explicitly (do not cast away the imaginary part).
    if np.issubdtype(array.dtype, np.complexfloating) or np.iscomplexobj(array):
        raise ValueError(f"{name} must be real-valued; complex values are rejected")
    if not np.issubdtype(array.dtype, np.number):
        raise ValueError(f"{name} must be a non-bool numeric array")
    return array


def _require_finite_positive_scale(meters_per_asset_unit: Any) -> float:
    # bool / np.bool_ must not coerce via float(True) == 1.0.
    if isinstance(meters_per_asset_unit, (bool, np.bool_)):
        raise ValueError("meters_per_asset_unit must be a finite positive scalar")
    as_arr = np.asarray(meters_per_asset_unit)
    if as_arr.dtype == np.bool_ or np.issubdtype(as_arr.dtype, np.bool_):
        raise ValueError("meters_per_asset_unit must be a finite positive scalar")
    if np.issubdtype(as_arr.dtype, np.complexfloating) or np.iscomplexobj(as_arr):
        raise ValueError("meters_per_asset_unit must be a finite positive scalar")
    try:
        scale = float(as_arr.reshape(()))
    except (TypeError, ValueError) as exc:
        raise ValueError("meters_per_asset_unit must be a finite positive scalar") from exc
    if not math.isfinite(scale) or scale <= 0.0:
        raise ValueError("meters_per_asset_unit must be a finite positive scalar")
    return scale


def _require_shape_hw(name: str, array: np.ndarray) -> tuple[int, int]:
    if array.ndim != 2:
        raise ValueError(f"{name} must be a 2D [H,W] array")
    height, width = int(array.shape[0]), int(array.shape[1])
    if height <= 0 or width <= 0:
        raise ValueError(f"{name} height and width must be positive")
    area = height * width
    if area > MAX_DEPTH_AREA:
        raise ValueError(f"{name} area exceeds {MAX_DEPTH_AREA}")
    return height, width


def _require_hw3(name: str, array: np.ndarray) -> tuple[int, int]:
    if array.ndim != 3 or int(array.shape[2]) != 3:
        raise ValueError(f"{name} must have shape [H,W,3]")
    height, width = int(array.shape[0]), int(array.shape[1])
    if height <= 0 or width <= 0:
        raise ValueError(f"{name} height and width must be positive")
    if height * width > MAX_DEPTH_AREA:
        raise ValueError(f"{name} area exceeds {MAX_DEPTH_AREA}")
    return height, width


def _require_matrix_3x3_finite(name: str, value: Any) -> np.ndarray:
    matrix = _require_numeric_nonbool_ndarray(name, value)
    if matrix.shape != (3, 3):
        raise ValueError(f"{name} must have shape (3, 3)")
    out = np.array(matrix, dtype=np.float64, copy=True)
    if not np.all(np.isfinite(out)):
        raise ValueError(f"{name} must be finite")
    return out


def _require_invertible_camera_from_uv(camera_from_uv: Any) -> np.ndarray:
    """Invertibility is scale-invariant: positive scaling of M must not reject.

    Absolute |det| thresholds are wrong (det scales as s^3). Normalize by the
    largest coefficient (no squared-norm overflow), then slogdet.
    """
    matrix = _require_matrix_3x3_finite("camera_from_uv", camera_from_uv)
    largest = float(np.max(np.abs(matrix)))
    if largest == 0.0:
        raise ValueError("camera_from_uv must be finite and invertible")
    normalized = matrix / largest
    sign, _logabsdet = np.linalg.slogdet(normalized)
    if sign == 0:
        raise ValueError("camera_from_uv must be finite and invertible")
    return normalized


def _require_proper_world_rotation(world_from_camera: Any) -> np.ndarray:
    matrix = _require_matrix_3x3_finite("world_from_camera", world_from_camera)
    gram = matrix @ matrix.T
    if not np.allclose(gram, np.eye(3, dtype=np.float64), rtol=0.0, atol=_ORTHONORMAL_TOL):
        raise ValueError(
            "world_from_camera must be orthonormal within tolerance 1e-5"
        )
    det = float(np.linalg.det(matrix))
    if not math.isfinite(det) or abs(det - 1.0) > _ORTHONORMAL_TOL:
        raise ValueError(
            "world_from_camera must be a proper rotation (det +1, no reflection)"
        )
    return matrix


def _require_position_asset(camera_position_asset: Any) -> np.ndarray:
    position = _require_numeric_nonbool_ndarray(
        "camera_position_asset", camera_position_asset
    )
    flat = np.array(position, dtype=np.float64, copy=True).reshape(-1)
    if flat.size != 3:
        raise ValueError("camera_position_asset must have length 3")
    if not np.all(np.isfinite(flat)):
        raise ValueError("camera_position_asset must be finite")
    return flat


def _nested_hypot3(x: np.ndarray, y: np.ndarray, z: np.ndarray) -> np.ndarray:
    return np.hypot(np.hypot(x, y), z)


def _python_float_or_none(value: Any) -> float | None:
    if value is None:
        return None
    number = float(value)
    if not math.isfinite(number):
        return None
    return number


def unproject_range(
    depth_meters: Any,
    camera_from_uv: Any,
    world_from_camera: Any,
    camera_position_asset: Any,
    meters_per_asset_unit: Any,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Unproject Hypersim Euclidean range depth to world metres.

    Inputs are never mutated. Invalid depths/rays/overflows yield NaN world /
    camera_z and valid=False (no exception). Bad calibration/shapes raise
    ValueError. Not a hardware-accuracy claim.
    """
    depth = _require_numeric_nonbool_ndarray("depth_meters", depth_meters)
    height, width = _require_shape_hw("depth_meters", depth)
    cam_from_uv = _require_invertible_camera_from_uv(camera_from_uv)
    world_from_cam = _require_proper_world_rotation(world_from_camera)
    position_asset = _require_position_asset(camera_position_asset)
    scale = _require_finite_positive_scale(meters_per_asset_unit)

    depth_f = np.array(depth, dtype=np.float64, copy=True)
    ys = np.arange(height, dtype=np.float64)[:, None]
    xs = np.arange(width, dtype=np.float64)[None, :]
    # Official pixel-centred NDC with reversed vertical (toolkit notebook).
    u = 2.0 * (xs + 0.5) / float(width) - 1.0  # (1, W)
    v = 1.0 - 2.0 * (ys + 0.5) / float(height)  # (H, 1)
    ones = np.ones((height, width), dtype=np.float64)
    # np.stack does not broadcast; expand u/v to [H,W] explicitly first.
    u_b, v_b, ones_b = np.broadcast_arrays(u, v, ones)
    uvw = np.stack((u_b, v_b, ones_b), axis=-1)

    world_m = np.full((height, width, 3), np.nan, dtype=np.float64)
    camera_z_m = np.full((height, width), np.nan, dtype=np.float64)
    valid = np.zeros((height, width), dtype=bool)

    with np.errstate(invalid="ignore", divide="ignore", over="ignore", under="ignore"):
        # r = [u, v, 1] @ M_cam_from_uv.T  (full matrix; tilt-shift/shear retained)
        rays = uvw @ cam_from_uv.T
        ray_norm = _nested_hypot3(rays[..., 0], rays[..., 1], rays[..., 2])
        r_unit = rays / ray_norm[..., None]
        camera_xyz = r_unit * depth_f[..., None]
        camera_z = -camera_xyz[..., 2]
        # World metres: CameraXYZ_m @ R_world_from_cam.T + position_asset * scale
        offset_m = position_asset * scale
        world_xyz = camera_xyz @ world_from_cam.T + offset_m

        depth_ok = np.isfinite(depth_f) & (depth_f > 0.0)
        ray_ok = (
            np.isfinite(ray_norm)
            & (ray_norm > 0.0)
            & np.all(np.isfinite(r_unit), axis=-1)
        )
        forward_ok = np.isfinite(camera_z) & (camera_z > 0.0)
        camera_ok = np.all(np.isfinite(camera_xyz), axis=-1)
        world_ok = np.all(np.isfinite(world_xyz), axis=-1)
        ok = depth_ok & ray_ok & forward_ok & camera_ok & world_ok

        world_m[ok] = world_xyz[ok]
        camera_z_m[ok] = camera_z[ok]
        valid[ok] = True

    return world_m, camera_z_m, valid


def residual_summary(
    world_m: Any,
    reference_world_asset: Any,
    meters_per_asset_unit: Any,
    semantics: Any,
) -> dict[str, Any]:
    """JSON-safe world residual vs Hypersim asset reference (evaluator only).

    Shared finite pixels only. NYU40 named classes are metadata GT labels for
    diagnostics — not a live classifier, floor-plane picker, or ADE mapper.
    """
    world = _require_numeric_nonbool_ndarray("world_m", world_m)
    reference = _require_numeric_nonbool_ndarray(
        "reference_world_asset", reference_world_asset
    )
    height, width = _require_hw3("world_m", world)
    ref_h, ref_w = _require_hw3("reference_world_asset", reference)
    if (height, width) != (ref_h, ref_w):
        raise ValueError("world_m and reference_world_asset shapes must match")

    scale = _require_finite_positive_scale(meters_per_asset_unit)

    labels = np.asarray(semantics)
    if labels.dtype == np.bool_ or np.issubdtype(labels.dtype, np.bool_):
        raise ValueError("semantics must be an integral array, not bool")
    if np.issubdtype(labels.dtype, np.complexfloating) or np.iscomplexobj(labels):
        raise ValueError("semantics must be an integral array, not bool")
    if not np.issubdtype(labels.dtype, np.integer):
        raise ValueError("semantics must be an integral array, not bool")
    if labels.shape != (height, width):
        raise ValueError("semantics must match world_m height and width")
    if labels.size > 0:
        label_min = int(labels.min())
        label_max = int(labels.max())
        if label_min < -1 or label_max > 40:
            raise ValueError("semantics labels must be in -1..40 (NYU40)")

    world_f = np.array(world, dtype=np.float64, copy=True)
    ref_asset = np.array(reference, dtype=np.float64, copy=True)
    labels_i = np.array(labels, copy=True)

    with np.errstate(invalid="ignore", divide="ignore", over="ignore", under="ignore"):
        ref_m = ref_asset * scale
        world_finite = np.all(np.isfinite(world_f), axis=-1)
        ref_finite = np.all(np.isfinite(ref_m), axis=-1)
        shared = world_finite & ref_finite
        delta = world_f - ref_m
        errors = _nested_hypot3(delta[..., 0], delta[..., 1], delta[..., 2])

    # Finite inputs whose difference overflows must fail explicitly — do not
    # publish Inf/NaN, silently drop, or report a fake zero residual.
    if np.any(shared) and (not np.all(np.isfinite(errors[shared]))):
        raise ValueError(
            "residual between finite world and reference points overflowed"
        )

    compared = errors[shared]
    compared_pixels = int(compared.size)
    if compared_pixels == 0:
        error_m = {"p50": None, "p90": None, "p99": None, "max": None}
    else:
        error_m = {
            "p50": float(np.percentile(compared, 50, method="linear")),
            "p90": float(np.percentile(compared, 90, method="linear")),
            "p99": float(np.percentile(compared, 99, method="linear")),
            "max": float(np.max(compared)),
        }

    world_z = world_f[..., 2]
    by_semantic: dict[str, dict[str, Any]] = {}
    for name, class_id in _NYU40_NAMED.items():
        class_mask = shared & (labels_i == class_id)
        count = int(class_mask.sum())
        if count == 0:
            by_semantic[name] = {"pixels": 0, "median_world_z_m": None}
        else:
            by_semantic[name] = {
                "pixels": count,
                "median_world_z_m": _python_float_or_none(np.median(world_z[class_mask])),
            }

    return {
        "compared_pixels": compared_pixels,
        "error_m": error_m,
        "by_semantic": by_semantic,
    }
