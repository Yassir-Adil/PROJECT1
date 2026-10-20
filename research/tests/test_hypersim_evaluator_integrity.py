"""Offline end-to-end provenance/calibration tests; all data are tiny synthetic fixtures."""
import copy
import csv
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import h5py
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from hypersim_fixture import PIN, SPEC
from evaluate_hypersim_fixture import evaluate_fixture


class EvaluatorIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.parent = Path(self.temporary.name)
        self.root = self.parent / "fixture"
        self.root.mkdir()
        self.output = self.parent / "report"
        self.scene = SPEC[0][0]
        rows = []
        split = []
        for scene, camera, frames in SPEC:
            rows.append({"scene_name": scene, "settings_output_img_height": "1.0", "settings_output_img_width": "1.0",
                         "settings_units_info_meters_scale": "1.0",
                         **{f"M_cam_from_uv_{i}{j}": str(np.diag([1, 1, -1])[i, j]) for i in range(3) for j in range(3)}})
            split.append({"scene_name": scene, "camera_name": camera, "frame_id": "0",
                          "included_in_public_release": "True", "split_partition_name": "val"})
            self.write_csv(f"{scene}/_detail/metadata_scene.csv", [{"parameter_name": "meters_per_asset_unit", "parameter_value": "1.0"}])
            detail = f"{scene}/_detail/{camera}/"
            self.write_h5(detail + "camera_keyframe_positions.hdf5", [[9., 0., 0.], [0., 0., 0.]])
            self.write_h5(detail + "camera_keyframe_orientations.hdf5", np.array([np.eye(3), np.eye(3)]))
            self.write_h5(detail + "camera_keyframe_frame_indices.hdf5", np.array([7, 0], dtype="i8"))
            prefix = f"{scene}/images/scene_{camera}_geometry_hdf5/frame.0000."
            self.write_h5(prefix + "depth_meters.hdf5", [[1.]])
            self.write_h5(prefix + "position.hdf5", [[[0., 0., -1.]]])
            self.write_h5(prefix + "semantic.hdf5", np.array([[2]], dtype="i2"))
        self.write_csv("_metadata/camera_parameters.csv", rows)
        self.write_csv("_metadata/split.csv", split)
        self.manifest = {"schema_version": 1, "status": "complete", "upstream_commit": PIN,
                         "frames": [{"scene": s, "camera": c, "frame": f} for s, c, fs in SPEC for f in fs], "files": []}
        self.rehash()

    def write_h5(self, name, values):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        with h5py.File(path, "w") as file:
            file.create_dataset("dataset", data=values)

    def write_csv(self, name, rows):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
            writer.writeheader(); writer.writerows(rows)

    def save_manifest(self):
        (self.root / "manifest.json").write_text(json.dumps(self.manifest), encoding="utf-8")

    def rehash(self):
        self.manifest["files"] = [{"path": path.relative_to(self.root).as_posix(), "bytes": path.stat().st_size,
                                  "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                                 for path in sorted(self.root.rglob("*")) if path.is_file() and path.name != "manifest.json"]
        self.save_manifest()

    def test_sparse_pose_mapping_and_reproducibility_hashes(self):
        report = evaluate_fixture(self.root, self.output)
        self.assertEqual(3, len(report["frames"]))
        for frame in report["frames"]:
            self.assertEqual(0.0, frame["geometry"]["error_m"]["p90"])
        self.assertIn("requirements-eval.txt", report["source_sha256"])
        with self.assertRaises(FileExistsError):
            evaluate_fixture(self.root, self.output)

    def test_pin_missing_duplicate_frames_and_missing_hash_record_rejected(self):
        original = copy.deepcopy(self.manifest)
        for kind in ("pin", "missing_frame", "duplicate_frame", "missing_hash"):
            self.manifest = copy.deepcopy(original)
            if kind == "pin": self.manifest["upstream_commit"] = "0" * 40
            elif kind == "missing_frame": self.manifest["frames"].pop()
            elif kind == "duplicate_frame": self.manifest["frames"][1] = self.manifest["frames"][0]
            else: self.manifest["files"] = [r for r in self.manifest["files"] if r["path"] != "_metadata/split.csv"]
            self.save_manifest()
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                evaluate_fixture(self.root, self.output)
            self.assertFalse(self.output.exists())

    def test_mismatched_scale_and_depth_dimensions_fail(self):
        scale_path = f"{self.scene}/_detail/metadata_scene.csv"
        self.write_csv(scale_path, [{"parameter_name": "meters_per_asset_unit", "parameter_value": "2.0"}])
        self.rehash()
        with self.assertRaises(ValueError): evaluate_fixture(self.root, self.output)
        self.write_csv(scale_path, [{"parameter_name": "meters_per_asset_unit", "parameter_value": "1.0"}])
        self.write_h5(f"{self.scene}/images/scene_cam_00_geometry_hdf5/frame.0000.depth_meters.hdf5", np.ones((2, 2)))
        self.rehash()
        with self.assertRaises(ValueError): evaluate_fixture(self.root, self.output)

    def test_keyframe_index_missing_or_duplicate_fails(self):
        for values in ([7, 8], [0, 0]):
            self.write_h5(f"{self.scene}/_detail/cam_00/camera_keyframe_frame_indices.hdf5", np.array(values, dtype="i8"))
            self.rehash()
            with self.subTest(values=values), self.assertRaises(ValueError):
                evaluate_fixture(self.root, self.output)

    def test_hash_tampering_fails_before_evaluation(self):
        self.write_h5(f"{self.scene}/_detail/cam_00/camera_keyframe_positions.hdf5", [[4., 5., 6.]])
        with self.assertRaises(ValueError): evaluate_fixture(self.root, self.output)
        self.assertFalse(self.output.exists())

    def test_nonfinite_range_delta_fails_before_creating_report(self):
        self.write_h5(f"{self.scene}/images/scene_cam_00_geometry_hdf5/frame.0000.depth_meters.hdf5", [[1e308]])
        self.rehash()
        with patch("hypersim_geometry.unproject_range", return_value=(np.zeros((1, 1, 3)), np.array([[-1e308]]), np.array([[True]]))):
            with np.errstate(over="ignore"), self.assertRaises(ValueError):
                evaluate_fixture(self.root, self.output)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
