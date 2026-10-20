"""Contract tests for research/hypersim_geometry.py (tests first).

Pure-numpy Hypersim raycasting oracle: Euclidean range != camera-z, official
pixel-centered u/v, full camera_from_uv (tilt-shift/shear retained). No h5py,
network, Android, navigation, or hardware-accuracy claims. Implementation must
land only after Codex records RED and an explicit source release.
"""

from __future__ import annotations

import importlib.util
import json
import math
import unittest
from pathlib import Path

import numpy as np

_MODULE_PATH = Path(__file__).resolve().parents[1] / "hypersim_geometry.py"
_SPEC = importlib.util.spec_from_file_location("hypersim_geometry", _MODULE_PATH)
hypersim_geometry = importlib.util.module_from_spec(_SPEC)
assert _SPEC.loader is not None
_SPEC.loader.exec_module(hypersim_geometry)

MAX_DEPTH_AREA = 1_048_576
ORTHONORMAL_TOL = 1e-5
NYU40_WALL = 1
NYU40_FLOOR = 2
NYU40_TABLE = 7
NYU40_FLOOR_MAT = 20


def _nested_hypot3(x: float, y: float, z: float) -> float:
    return math.hypot(math.hypot(x, y), z)


def _unit_ray(u: float, v: float, camera_from_uv: np.ndarray) -> np.ndarray:
    r = np.asarray([u, v, 1.0], dtype=np.float64) @ camera_from_uv.T
    n = _nested_hypot3(float(r[0]), float(r[1]), float(r[2]))
    return r / n


def _pixel_uv(x: int, y: int, width: int, height: int) -> tuple[float, float]:
    u = 2.0 * (x + 0.5) / width - 1.0
    v = 1.0 - 2.0 * (y + 0.5) / height
    return u, v


def _opengl_looking_camera_from_uv() -> np.ndarray:
    """Finite invertible 3x3: optical axis maps to -Z (positive camera_z)."""
    return np.array(
        [
            [1.0, 0.0, 0.0],
            [0.0, 1.0, 0.0],
            [0.0, 0.0, -1.0],
        ],
        dtype=np.float64,
    )


def _tilt_shift_camera_from_uv() -> np.ndarray:
    """Non-default tilt/shear; still finite, invertible, forward-looking."""
    m = np.array(
        [
            [1.15, 0.08, 0.04],
            [-0.05, 0.92, -0.06],
            [0.02, 0.03, -1.05],
        ],
        dtype=np.float64,
    )
    assert abs(float(np.linalg.det(m))) > 1e-6
    return m


def _identity_world() -> np.ndarray:
    return np.eye(3, dtype=np.float64)


def _rotz90_world() -> np.ndarray:
    # Proper rotation: det +1, orthonormal.
    return np.array(
        [
            [0.0, -1.0, 0.0],
            [1.0, 0.0, 0.0],
            [0.0, 0.0, 1.0],
        ],
        dtype=np.float64,
    )


def _call_unproject(
    depth_meters,
    camera_from_uv=None,
    world_from_camera=None,
    camera_position_asset=None,
    meters_per_asset_unit=1.0,
):
    if camera_from_uv is None:
        camera_from_uv = _opengl_looking_camera_from_uv()
    if world_from_camera is None:
        world_from_camera = _identity_world()
    if camera_position_asset is None:
        camera_position_asset = np.zeros(3, dtype=np.float64)
    return hypersim_geometry.unproject_range(
        depth_meters,
        camera_from_uv,
        world_from_camera,
        camera_position_asset,
        meters_per_asset_unit,
    )


class HypersimGeometryUnprojectContractTest(unittest.TestCase):
    def test_center_pixel_camera_z_equals_euclidean_range(self):
        # Odd size so pixel (1,1) is exactly image centre: u=v=0.
        depth = np.full((3, 3), np.nan, dtype=np.float64)
        depth[1, 1] = 2.5
        world_m, camera_z_m, valid = _call_unproject(depth)
        self.assertTrue(bool(valid[1, 1]))
        self.assertAlmostEqual(2.5, float(camera_z_m[1, 1]), places=12)
        # On-axis: world at identity pose is (0, 0, -range) in camera frame → world.
        np.testing.assert_allclose(world_m[1, 1], [0.0, 0.0, -2.5], atol=1e-12, rtol=0.0)
        # Off-centre pixels stay invalid (NaN depth).
        self.assertFalse(bool(valid[0, 0]))
        self.assertTrue(math.isnan(float(camera_z_m[0, 0])))
        self.assertTrue(np.all(np.isnan(world_m[0, 0])))

    def test_corner_euclidean_range_differs_from_camera_z(self):
        # 2x2 corner (0,0) forces |u|,|v| != 0 so range != camera_z.
        depth = np.full((2, 2), np.nan, dtype=np.float64)
        depth[0, 0] = 3.0
        world_m, camera_z_m, valid = _call_unproject(depth)
        self.assertTrue(bool(valid[0, 0]))
        u, v = _pixel_uv(0, 0, 2, 2)
        r_unit = _unit_ray(u, v, _opengl_looking_camera_from_uv())
        expected_z = -float(r_unit[2]) * 3.0
        self.assertAlmostEqual(expected_z, float(camera_z_m[0, 0]), places=10)
        self.assertNotAlmostEqual(3.0, float(camera_z_m[0, 0]), places=6)
        # Euclidean length of CameraXYZ equals range (identity world, origin).
        cam_xyz = np.array(world_m[0, 0], dtype=np.float64)  # identity world == camera
        length = _nested_hypot3(float(cam_xyz[0]), float(cam_xyz[1]), float(cam_xyz[2]))
        self.assertAlmostEqual(3.0, length, places=10)

    def test_tilt_shift_matrix_changes_unprojection_vs_diagonal(self):
        depth = np.full((2, 2), np.nan, dtype=np.float64)
        depth[0, 1] = 1.75
        diag = _opengl_looking_camera_from_uv()
        tilt = _tilt_shift_camera_from_uv()
        _, z_diag, v_diag = _call_unproject(depth, camera_from_uv=diag)
        world_tilt, z_tilt, v_tilt = _call_unproject(depth, camera_from_uv=tilt)
        self.assertTrue(bool(v_diag[0, 1]) and bool(v_tilt[0, 1]))
        self.assertGreater(abs(float(z_diag[0, 1]) - float(z_tilt[0, 1])), 1e-4)
        u, v = _pixel_uv(1, 0, 2, 2)
        r_unit = _unit_ray(u, v, tilt)
        expected = r_unit * 1.75
        np.testing.assert_allclose(world_tilt[0, 1], expected, atol=1e-10, rtol=0.0)
        self.assertAlmostEqual(-float(expected[2]), float(z_tilt[0, 1]), places=10)

    def test_world_rotation_and_asset_unit_translation(self):
        depth = np.full((3, 3), np.nan, dtype=np.float64)
        depth[1, 1] = 4.0
        pos_asset = np.array([1.0, -2.0, 0.5], dtype=np.float64)
        scale = 0.025  # typical asset→metre scale shape; value is fixture-only
        world_m, camera_z_m, valid = _call_unproject(
            depth,
            world_from_camera=_rotz90_world(),
            camera_position_asset=pos_asset,
            meters_per_asset_unit=scale,
        )
        self.assertTrue(bool(valid[1, 1]))
        self.assertAlmostEqual(4.0, float(camera_z_m[1, 1]), places=12)
        # CameraXYZ on axis = (0,0,-4); World = Camera @ R.T + pos*scale
        cam = np.array([0.0, 0.0, -4.0], dtype=np.float64)
        expected = cam @ _rotz90_world().T + pos_asset * scale
        np.testing.assert_allclose(world_m[1, 1], expected, atol=1e-12, rtol=0.0)

    def test_zero_negative_nan_inf_depths_are_invalid_not_exception(self):
        depth = np.array(
            [
                [0.0, -1.0, np.nan],
                [np.inf, -np.inf, 1.0],
            ],
            dtype=np.float64,
        )
        world_m, camera_z_m, valid = _call_unproject(depth)
        self.assertEqual((2, 3, 3), world_m.shape)
        self.assertEqual(np.float64, world_m.dtype)
        self.assertEqual(np.float64, camera_z_m.dtype)
        self.assertEqual(np.bool_, valid.dtype)
        for y, x in ((0, 0), (0, 1), (0, 2), (1, 0), (1, 1)):
            self.assertFalse(bool(valid[y, x]))
            self.assertTrue(math.isnan(float(camera_z_m[y, x])))
            self.assertTrue(np.all(np.isnan(world_m[y, x])))
        self.assertTrue(bool(valid[1, 2]))
        self.assertFalse(math.isnan(float(camera_z_m[1, 2])))
        # No fabricated origin points for invalid pixels.
        self.assertFalse(np.any(world_m[0, 0] == 0.0))

    def test_forward_bad_ray_with_positive_camera_z_component_is_invalid(self):
        # Identity camera_from_uv keeps +Z; camera_z = -CameraXYZ.z would be <= 0.
        depth = np.array([[2.0]], dtype=np.float64)
        identity = np.eye(3, dtype=np.float64)
        world_m, camera_z_m, valid = _call_unproject(depth, camera_from_uv=identity)
        self.assertFalse(bool(valid[0, 0]))
        self.assertTrue(math.isnan(float(camera_z_m[0, 0])))
        self.assertTrue(np.all(np.isnan(world_m[0, 0])))

    def test_nonfinite_or_overflowed_points_are_invalid(self):
        depth = np.array([[1.0e200, 2.0]], dtype=np.float64)
        # Huge translation can overflow world even when camera point is finite.
        pos = np.array([1.0e250, 0.0, 0.0], dtype=np.float64)
        world_m, camera_z_m, valid = _call_unproject(
            depth,
            camera_position_asset=pos,
            meters_per_asset_unit=1.0e100,
        )
        self.assertFalse(bool(valid[0, 0]))
        self.assertTrue(math.isnan(float(camera_z_m[0, 0])))
        self.assertTrue(np.all(np.isnan(world_m[0, 0])))
        # Neighbour with modest depth still subject to same overflowed offset.
        self.assertFalse(bool(valid[0, 1]))
        self.assertTrue(np.all(np.isnan(world_m[0, 1])))

    def test_inputs_are_not_mutated_and_accept_readonly(self):
        depth = np.array([[1.5, 2.5], [3.5, 4.5]], dtype=np.float64)
        cam = _opengl_looking_camera_from_uv().copy()
        world_r = _identity_world().copy()
        pos = np.array([0.1, 0.2, 0.3], dtype=np.float64)
        depth_before = depth.copy()
        cam_before = cam.copy()
        world_before = world_r.copy()
        pos_before = pos.copy()
        for arr in (depth, cam, world_r, pos):
            arr.setflags(write=False)
        world_m, camera_z_m, valid = hypersim_geometry.unproject_range(
            depth, cam, world_r, pos, 1.0
        )
        self.assertEqual(4, int(valid.sum()))
        np.testing.assert_array_equal(depth_before, np.asarray(depth))
        np.testing.assert_array_equal(cam_before, np.asarray(cam))
        np.testing.assert_array_equal(world_before, np.asarray(world_r))
        np.testing.assert_array_equal(pos_before, np.asarray(pos))
        self.assertEqual(np.float64, world_m.dtype)
        self.assertEqual((2, 2, 3), world_m.shape)

    def test_bad_world_rotation_raises(self):
        depth = np.array([[1.0]], dtype=np.float64)
        reflected = np.diag([1.0, 1.0, -1.0]).astype(np.float64)  # det -1
        with self.assertRaises(ValueError):
            _call_unproject(depth, world_from_camera=reflected)
        scaled = np.eye(3, dtype=np.float64) * 1.01
        with self.assertRaises(ValueError):
            _call_unproject(depth, world_from_camera=scaled)
        non_ortho = np.array(
            [[1.0, 0.2, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]],
            dtype=np.float64,
        )
        with self.assertRaises(ValueError):
            _call_unproject(depth, world_from_camera=non_ortho)

    def test_invalid_calibration_shapes_and_scale_raise(self):
        depth = np.array([[1.0]], dtype=np.float64)
        with self.assertRaises(ValueError):
            _call_unproject(depth, camera_from_uv=np.eye(2))
        with self.assertRaises(ValueError):
            _call_unproject(depth, world_from_camera=np.eye(4))
        with self.assertRaises(ValueError):
            _call_unproject(
                depth,
                camera_position_asset=np.array([0.0, 0.0], dtype=np.float64),
            )
        with self.assertRaises(ValueError):
            _call_unproject(depth, meters_per_asset_unit=0.0)
        with self.assertRaises(ValueError):
            _call_unproject(depth, meters_per_asset_unit=-2.0)
        with self.assertRaises(ValueError):
            _call_unproject(depth, meters_per_asset_unit=float("nan"))
        singular = np.array(
            [[1.0, 0.0, 0.0], [2.0, 0.0, 0.0], [0.0, 0.0, -1.0]],
            dtype=np.float64,
        )
        with self.assertRaises(ValueError):
            _call_unproject(depth, camera_from_uv=singular)
        nonfinite_cam = _opengl_looking_camera_from_uv().copy()
        nonfinite_cam[0, 0] = np.inf
        with self.assertRaises(ValueError):
            _call_unproject(depth, camera_from_uv=nonfinite_cam)

    def test_depth_area_cap_and_shape_gates(self):
        # Exactly at cap is allowed; one more row over area rejects.
        ok = np.ones((1024, 1024), dtype=np.float64)
        world_m, camera_z_m, valid = _call_unproject(ok)
        self.assertEqual((1024, 1024), valid.shape)
        self.assertEqual(MAX_DEPTH_AREA, int(valid.size))
        over = np.ones((1025, 1024), dtype=np.float64)
        with self.assertRaises(ValueError):
            _call_unproject(over)
        with self.assertRaises(ValueError):
            _call_unproject(np.ones((0, 4), dtype=np.float64))
        with self.assertRaises(ValueError):
            _call_unproject(np.ones((4,), dtype=np.float64))
        # Bool depth is rejected (must be non-bool numeric).
        with self.assertRaises(ValueError):
            _call_unproject(np.ones((2, 2), dtype=np.bool_))

    def test_api_does_not_require_normals(self):
        # Smoke: only the documented five inputs; normals are not part of the API.
        depth = np.array([[1.0, 1.0]], dtype=np.float32)
        world_m, camera_z_m, valid = _call_unproject(depth)
        self.assertEqual(2, int(valid.sum()))
        self.assertEqual(np.float64, world_m.dtype)
        self.assertTrue(callable(hypersim_geometry.unproject_range))
        self.assertTrue(callable(hypersim_geometry.residual_summary))

    def test_integer_depth_accepted_as_nonbool_numeric(self):
        depth = np.array([[2, 0], [3, 4]], dtype=np.int32)
        world_m, camera_z_m, valid = _call_unproject(depth)
        self.assertFalse(bool(valid[0, 1]))  # zero → invalid
        self.assertTrue(bool(valid[0, 0]))
        self.assertTrue(bool(valid[1, 0]))
        self.assertTrue(bool(valid[1, 1]))
        self.assertEqual(np.float64, world_m.dtype)
        # This is an off-axis corner in a2x2 image: range2m != camera-z.
        self.assertAlmostEqual(2.0 / math.sqrt(1.5), float(camera_z_m[0, 0]), places=10)

    def test_nonfinite_camera_position_raises(self):
        depth = np.array([[1.0]], dtype=np.float64)
        with self.assertRaises(ValueError):
            _call_unproject(
                depth,
                camera_position_asset=np.array([0.0, np.nan, 0.0], dtype=np.float64),
            )


class HypersimGeometryResidualContractTest(unittest.TestCase):
    def _finite_world_pair(self, h=2, w=3):
        # Analytic world metres and matching asset reference (scale 2).
        scale = 2.0
        world = np.zeros((h, w, 3), dtype=np.float64)
        ref_asset = np.zeros((h, w, 3), dtype=np.float64)
        # Pixel (0,0): identical → error 0
        world[0, 0] = [1.0, 2.0, 3.0]
        ref_asset[0, 0] = [0.5, 1.0, 1.5]
        # Pixel (0,1): error 5 m along x after scale
        world[0, 1] = [10.0, 0.0, 1.0]
        ref_asset[0, 1] = [2.5, 0.0, 0.5]  # *2 = [5,0,1] → delta [5,0,0]
        # Pixel (0,2): invalid world
        world[0, 2] = [np.nan, 0.0, 0.0]
        ref_asset[0, 2] = [1.0, 1.0, 1.0]
        # Pixel (1,0): invalid ref
        world[1, 0] = [0.0, 0.0, 4.0]
        ref_asset[1, 0] = [np.inf, 0.0, 2.0]
        # Pixel (1,1): error ~sqrt(0+0+16)=4 along z
        world[1, 1] = [0.0, 0.0, 9.0]
        ref_asset[1, 1] = [0.0, 0.0, 2.5]  # *2 = 5 → delta z=4
        # Pixel (1,2): both finite, small residual
        world[1, 2] = [0.0, 1.0, 2.0]
        ref_asset[1, 2] = [0.0, 0.4, 0.8]  # *2 = [0,0.8,1.6] → err hypot(0,0.2,0.4)
        return world, ref_asset, scale

    def test_residual_summary_percentiles_and_json_native(self):
        world, ref_asset, scale = self._finite_world_pair()
        semantics = np.array(
            [
                [NYU40_WALL, NYU40_FLOOR, NYU40_TABLE],
                [NYU40_FLOOR_MAT, NYU40_WALL, NYU40_FLOOR],
            ],
            dtype=np.int16,
        )
        summary = hypersim_geometry.residual_summary(
            world, ref_asset, scale, semantics
        )
        # Shared finite: (0,0), (0,1), (1,1), (1,2) → 4 pixels
        self.assertEqual(4, summary["compared_pixels"])
        errors = [
            0.0,
            5.0,
            4.0,
            _nested_hypot3(0.0, 0.2, 0.4),
        ]
        self.assertAlmostEqual(
            float(np.percentile(errors, 50, method="linear")),
            summary["error_m"]["p50"],
            places=10,
        )
        self.assertAlmostEqual(
            float(np.percentile(errors, 90, method="linear")),
            summary["error_m"]["p90"],
            places=10,
        )
        self.assertAlmostEqual(
            float(np.percentile(errors, 99, method="linear")),
            summary["error_m"]["p99"],
            places=10,
        )
        self.assertAlmostEqual(max(errors), summary["error_m"]["max"], places=10)
        # Strict JSON: native types, no NaN encoding.
        payload = json.dumps(summary, allow_nan=False)
        roundtrip = json.loads(payload)
        self.assertEqual(4, roundtrip["compared_pixels"])
        self.assertIsInstance(roundtrip["error_m"]["p50"], (int, float))
        self.assertNotIsInstance(roundtrip["error_m"]["p50"], bool)

    def test_by_semantic_uses_shared_finite_world_z_only(self):
        world, ref_asset, scale = self._finite_world_pair()
        semantics = np.array(
            [
                [NYU40_WALL, NYU40_FLOOR, NYU40_TABLE],
                [0, NYU40_WALL, NYU40_FLOOR],  # unknown 0 at (1,0) ignored (ref invalid)
            ],
            dtype=np.int32,
        )
        summary = hypersim_geometry.residual_summary(
            world, ref_asset, scale, semantics
        )
        by_sem = summary["by_semantic"]
        # Wall shared finite: (0,0) z=3 and (1,1) z=9 → median 6
        self.assertEqual(2, by_sem["wall"]["pixels"])
        self.assertAlmostEqual(6.0, by_sem["wall"]["median_world_z_m"], places=12)
        # Floor: (0,1) z=1 and (1,2) z=2 → median 1.5
        self.assertEqual(2, by_sem["floor"]["pixels"])
        self.assertAlmostEqual(1.5, by_sem["floor"]["median_world_z_m"], places=12)
        # Table pixel (0,2) has invalid world → not compared
        self.assertEqual(0, by_sem["table"]["pixels"])
        self.assertIsNone(by_sem["table"]["median_world_z_m"])
        # floor_mat never appears among shared finite
        self.assertEqual(0, by_sem["floor_mat"]["pixels"])
        self.assertIsNone(by_sem["floor_mat"]["median_world_z_m"])

    def test_unknown_and_negative_one_labels_excluded_from_named_classes(self):
        world = np.zeros((1, 4, 3), dtype=np.float64)
        ref = np.zeros((1, 4, 3), dtype=np.float64)
        world[0, 0] = [0.0, 0.0, 1.0]
        world[0, 1] = [0.0, 0.0, 2.0]
        world[0, 2] = [0.0, 0.0, 3.0]
        world[0, 3] = [0.0, 0.0, 4.0]
        ref[:] = world  # asset units with scale 1
        semantics = np.array([[-1, 0, NYU40_TABLE, NYU40_FLOOR_MAT]], dtype=np.int64)
        summary = hypersim_geometry.residual_summary(world, ref, 1.0, semantics)
        self.assertEqual(4, summary["compared_pixels"])
        self.assertEqual(0, summary["by_semantic"]["wall"]["pixels"])
        self.assertIsNone(summary["by_semantic"]["wall"]["median_world_z_m"])
        self.assertEqual(0, summary["by_semantic"]["floor"]["pixels"])
        self.assertEqual(1, summary["by_semantic"]["table"]["pixels"])
        self.assertAlmostEqual(3.0, summary["by_semantic"]["table"]["median_world_z_m"])
        self.assertEqual(1, summary["by_semantic"]["floor_mat"]["pixels"])
        self.assertAlmostEqual(4.0, summary["by_semantic"]["floor_mat"]["median_world_z_m"])

    def test_residual_rejects_bad_shapes_scale_labels_and_area(self):
        world = np.zeros((2, 2, 3), dtype=np.float64)
        ref = np.zeros((2, 2, 3), dtype=np.float64)
        sem = np.zeros((2, 2), dtype=np.int32)
        with self.assertRaises(ValueError):
            hypersim_geometry.residual_summary(world, ref[:1], 1.0, sem)
        with self.assertRaises(ValueError):
            hypersim_geometry.residual_summary(world[:, :, :2], ref, 1.0, sem)
        with self.assertRaises(ValueError):
            hypersim_geometry.residual_summary(world, ref, 0.0, sem)
        with self.assertRaises(ValueError):
            hypersim_geometry.residual_summary(world, ref, float("nan"), sem)
        bad_bool = np.zeros((2, 2), dtype=np.bool_)
        with self.assertRaises(ValueError):
            hypersim_geometry.residual_summary(world, ref, 1.0, bad_bool)
        bad_float = np.zeros((2, 2), dtype=np.float64)
        with self.assertRaises(ValueError):
            hypersim_geometry.residual_summary(world, ref, 1.0, bad_float)
        out_of_range = np.array([[41, 0], [1, 2]], dtype=np.int32)
        with self.assertRaises(ValueError):
            hypersim_geometry.residual_summary(world, ref, 1.0, out_of_range)
        below = np.array([[-2, 0], [1, 2]], dtype=np.int32)
        with self.assertRaises(ValueError):
            hypersim_geometry.residual_summary(world, ref, 1.0, below)
        huge_w = np.zeros((1025, 1024, 3), dtype=np.float64)
        huge_r = np.zeros((1025, 1024, 3), dtype=np.float64)
        huge_s = np.zeros((1025, 1024), dtype=np.int16)
        with self.assertRaises(ValueError):
            hypersim_geometry.residual_summary(huge_w, huge_r, 1.0, huge_s)

    def test_zero_compared_pixels_yields_null_error_stats_json_safe(self):
        world = np.full((2, 2, 3), np.nan, dtype=np.float64)
        ref = np.zeros((2, 2, 3), dtype=np.float64)
        sem = np.full((2, 2), NYU40_FLOOR, dtype=np.int8)
        summary = hypersim_geometry.residual_summary(world, ref, 1.0, sem)
        self.assertEqual(0, summary["compared_pixels"])
        for key in ("p50", "p90", "p99", "max"):
            self.assertIsNone(summary["error_m"][key])
        self.assertEqual(0, summary["by_semantic"]["floor"]["pixels"])
        self.assertIsNone(summary["by_semantic"]["floor"]["median_world_z_m"])
        json.dumps(summary, allow_nan=False)

    def test_residual_inputs_not_mutated(self):
        world = np.array([[[1.0, 0.0, 0.0], [2.0, 0.0, 0.0]]], dtype=np.float64)
        ref = np.array([[[1.0, 0.0, 0.0], [1.5, 0.0, 0.0]]], dtype=np.float64)
        sem = np.array([[NYU40_WALL, NYU40_FLOOR]], dtype=np.int32)
        w0, r0, s0 = world.copy(), ref.copy(), sem.copy()
        for arr in (world, ref, sem):
            arr.setflags(write=False)
        summary = hypersim_geometry.residual_summary(world, ref, 1.0, sem)
        self.assertEqual(2, summary["compared_pixels"])
        np.testing.assert_array_equal(w0, np.asarray(world))
        np.testing.assert_array_equal(r0, np.asarray(ref))
        np.testing.assert_array_equal(s0, np.asarray(sem))


if __name__ == "__main__":
    unittest.main()
