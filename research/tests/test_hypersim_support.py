"""Structural GT support audit tests, not model/phone/geometry acceptance."""
from pathlib import Path
import sys
import unittest
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from evaluate_hypersim_support import evaluate_support
import json
import csv
import copy
import test_hypersim_evaluator_integrity as fixture_helpers
from evaluate_hypersim_support import evaluate_fixture_support
from hypersim_fixture import SPEC
from unittest.mock import patch
import evaluate_hypersim_support as support_module


class HypersimSupportTest(unittest.TestCase):
    def scene(self):
        labels = np.full((32,32),2,dtype=np.int16)  # NYU40 floor, not ADE floor4.
        yy,xx = np.mgrid[:32,:32]
        positions = np.stack((xx/10,yy/10,np.zeros_like(xx)),axis=-1).astype(float)
        return labels,positions

    def test_full_gt_floor_component_is_not_a_navigation_candidate(self):
        report = evaluate_support(*self.scene(),grid=8,keep_every=1)
        self.assertEqual(64,report["floor_component_cells"])
        self.assertEqual(64,report["potential_floor_supports"])
        self.assertEqual("ground_truth_structural_only",report["scope"])
        self.assertEqual("none",report["navigation"])
        self.assertNotIn("clear",report)

    def test_deterministic_thinning_is_explicitly_not_sensor_confidence(self):
        report = evaluate_support(*self.scene(),grid=8,keep_every=4)
        self.assertEqual(16,report["potential_floor_supports"])
        self.assertEqual("synthetic_periodic_drop_not_sensor_confidence",report["availability"])

    def test_no_floor_rug_table_or_disconnected_floor_never_anchors(self):
        for label in (0,1,7,20,4):
            labels,positions = self.scene(); labels[:] = label
            self.assertEqual(0,evaluate_support(labels,positions)["floor_component_cells"])
        labels,positions = self.scene(); labels[:] = 0; labels[:8,:8] = 2
        self.assertEqual(0,evaluate_support(labels,positions)["floor_component_cells"])

    def test_nonfinite_geometry_remains_missing_not_imputed(self):
        labels,positions = self.scene(); positions[:] = np.nan
        self.assertEqual(0,evaluate_support(labels,positions)["potential_floor_supports"])

    def test_grid_resolution_is_bounded_and_reports_exact_sample_count(self):
        for grid in (8,16,32):
            self.assertEqual(grid*grid,evaluate_support(*self.scene(),grid=grid)["sampled_cells"])
        for grid in (True,0,4,64,8.5):
            with self.assertRaises(ValueError): evaluate_support(*self.scene(),grid=grid)

    def test_wrong_shapes_dtypes_and_unbounded_availability_fail_closed(self):
        labels,positions = self.scene()
        for bad_labels,bad_positions in ((labels.astype(bool),positions),(labels.astype(float),positions),
                (labels,positions[:3]),(labels,positions.astype(complex)),(labels,positions[:,:,:2])):
            with self.assertRaises(ValueError): evaluate_support(bad_labels,bad_positions)
        for keep in (True,0,-1,65,1.5):
            with self.assertRaises(ValueError): evaluate_support(labels,positions,keep_every=keep)

    def test_inputs_are_not_mutated_and_report_has_no_point_coordinates(self):
        labels,positions = self.scene(); before = positions.copy()
        report = evaluate_support(labels,positions)
        np.testing.assert_array_equal(before,positions)
        self.assertFalse(any(key in report for key in ("points","positions","coordinates")))


class HypersimSupportFixtureTest(unittest.TestCase):
    def setUp(self):
        # Reuse the established tiny, hash-recorded fake fixture; no downloads.
        self.helper = fixture_helpers.EvaluatorIntegrityTest()
        self.helper.setUp()
        self.addCleanup(self.helper.doCleanups)
        self.root, self.output = self.helper.root, self.helper.output
        with (self.root / "_metadata/camera_parameters.csv").open(newline="",encoding="utf-8") as stream:
            self.rows = list(csv.DictReader(stream))
        yy,xx = np.mgrid[:32,:32]
        for row in self.rows:
            row["settings_output_img_height"] = "32.0"
            row["settings_output_img_width"] = "32.0"
        self.helper.write_csv("_metadata/camera_parameters.csv",self.rows)
        for scene,camera,_ in SPEC:
            prefix = f"{scene}/images/scene_{camera}_geometry_hdf5/frame.0000."
            self.helper.write_h5(prefix+"semantic.hdf5",np.full((32,32),2,dtype="i2"))
            self.helper.write_h5(prefix+"position.hdf5",np.stack((xx/10,yy/10,np.zeros_like(xx)),axis=-1))
        self.helper.rehash()

    def test_cli_core_records_27_cases_and_refuses_reusing_report_directory(self):
        report = evaluate_fixture_support(self.root,self.output)
        self.assertEqual(27,report["case_counts"]["total"])
        self.assertEqual("NYU40",report["provenance"]["label_semantic_id_space"])
        self.assertEqual("none",report["navigation"])
        json.dumps(report,allow_nan=False)
        with self.assertRaises(FileExistsError): evaluate_fixture_support(self.root,self.output)

    def test_nonpositive_scale_is_rejected_even_when_both_metadata_copies_match(self):
        for index,value in enumerate(("0","-1")):
            with self.subTest(scale=value):
                self.rows[0]["settings_units_info_meters_scale"] = value
                self.helper.write_csv("_metadata/camera_parameters.csv",self.rows)
                self.helper.write_csv(f"{self.rows[0]['scene_name']}/_detail/metadata_scene.csv",
                    [{"parameter_name":"meters_per_asset_unit","parameter_value":value}])
                self.helper.rehash()
                output = self.helper.parent/f"invalid-scale-{index}"
                with self.assertRaises(ValueError): evaluate_fixture_support(self.root,output)
                self.assertFalse(output.exists())

    def test_provenance_and_hash_failures_never_create_a_report(self):
        original = copy.deepcopy(self.helper.manifest)
        for kind in ("source_pin","duplicate_frame","missing_hash","modified_bytes"):
            with self.subTest(kind=kind):
                self.helper.manifest = copy.deepcopy(original)
                if kind == "source_pin": self.helper.manifest["upstream_commit"] = "0"*40
                elif kind == "duplicate_frame": self.helper.manifest["frames"][1] = self.helper.manifest["frames"][0]
                elif kind == "missing_hash": self.helper.manifest["files"] = [r for r in original["files"] if r["path"] != "_metadata/split.csv"]
                else: self.helper.manifest["files"][0]["sha256"] = "0"*64
                self.helper.save_manifest()
                with self.assertRaises(ValueError): evaluate_fixture_support(self.root,self.output)
                self.assertFalse(self.output.exists())

    def test_reproducibility_includes_runtime_requirements_pin(self):
        report = evaluate_fixture_support(self.root,self.output)
        self.assertIn("requirements-eval.txt",report["source_sha256"])

    def test_missing_requirements_pin_fails_before_creating_report(self):
        fake_source = self.helper.parent/"tool.py"
        fake_source.write_text("# fake source location for missing-pin test",encoding="utf-8")
        with patch.object(support_module,"__file__",str(fake_source)):
            with self.assertRaises(FileNotFoundError) as caught:
                evaluate_fixture_support(self.root,self.output)
        self.assertEqual("requirements-eval.txt",Path(caught.exception.filename).name)
        self.assertFalse(self.output.exists())

    def test_report_must_not_be_written_inside_fixture_root(self):
        with self.assertRaises(ValueError): evaluate_fixture_support(self.root,self.root/"report")


if __name__ == "__main__": unittest.main()
