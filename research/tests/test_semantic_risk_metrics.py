"""Stage-1 contract tests for research/semantic_risk_metrics.py.

Failure-focused ADE150×Top31 diagnostics. No model, network, Android, or
guidance coupling. Implementation must land only after Codex records RED.
"""

from __future__ import annotations

import importlib.util
import json
import unittest
from pathlib import Path

import numpy as np

_MODULE_PATH = Path(__file__).resolve().parents[1] / "semantic_risk_metrics.py"
_SPEC = importlib.util.spec_from_file_location("semantic_risk_metrics", _MODULE_PATH)
semantic_risk_metrics = importlib.util.module_from_spec(_SPEC)
assert _SPEC.loader is not None
_SPEC.loader.exec_module(semantic_risk_metrics)


GT_STAIR_IDS = (54, 60, 97, 122)
TOP31_PRESENT = (1, 4, 29)  # wall, floor, rug — present in Top-31 and ADE150


class SemanticRiskMetricsContractTest(unittest.TestCase):
    def test_constants_match_ade150_by_top31_layout(self):
        self.assertEqual(151, semantic_risk_metrics.GT_CLASS_COUNT)
        self.assertEqual(32, semantic_risk_metrics.PRED_CLASS_COUNT)

    def test_full_confusion_preserves_stair_and_top31_gt_rows(self):
        # Top-31 models cannot emit stair labels, but full_confusion must still
        # keep ADE150 GT rows 54/60/97/122 (and wall/floor/rug) for diagnostics.
        gt = np.array(
            [
                [1, 4, 29, 54],
                [60, 97, 122, 0],
            ],
            dtype=np.int32,
        )
        pred = np.array(
            [
                [1, 4, 29, 4],
                [4, 1, 4, 4],
            ],
            dtype=np.int32,
        )
        matrix = semantic_risk_metrics.full_confusion(pred, gt)
        self.assertEqual((151, 32), matrix.shape)
        self.assertEqual(np.int64, matrix.dtype)
        self.assertEqual(1, int(matrix[1, 1]))
        self.assertEqual(1, int(matrix[4, 4]))
        self.assertEqual(1, int(matrix[29, 29]))
        self.assertEqual(1, int(matrix[54, 4]))
        self.assertEqual(1, int(matrix[60, 4]))
        self.assertEqual(1, int(matrix[97, 1]))
        self.assertEqual(1, int(matrix[122, 4]))
        self.assertEqual(1, int(matrix[0, 4]))
        self.assertEqual(8, int(matrix.sum()))
        for stair_id in GT_STAIR_IDS:
            self.assertEqual(1, int(matrix[stair_id].sum()))
        for class_id in TOP31_PRESENT:
            self.assertGreaterEqual(int(matrix[class_id].sum()), 1)

    def test_unlabeled_gt0_is_counted_but_not_precision_error(self):
        gt = np.array([[0, 0, 4, 1]], dtype=np.int16)
        pred = np.array([[4, 4, 4, 4]], dtype=np.int16)
        matrix = semantic_risk_metrics.full_confusion(pred, gt)
        result = semantic_risk_metrics.metrics(matrix)
        self.assertEqual(4, result["floor_prediction_pixels_all"])
        self.assertEqual(2, result["floor_prediction_pixels_labeled"])
        self.assertEqual(2, result["unlabeled_as_floor_pixels"])
        self.assertEqual(1, result["non_floor_as_floor_pixels"])
        # Precision uses only GT1..150 floor predictions; GT0 is excluded.
        self.assertEqual(0.5, result["floor_precision_labeled"])
        self.assertEqual(1.0, result["floor_recall"])
        self.assertEqual(0.5, result["non_floor_as_floor_fraction"])
        # GT0 must appear in full counts / per_gt, never as negative evidence.
        self.assertEqual(2, result["per_gt"][0]["pixels"])
        self.assertEqual(2, result["per_gt"][0]["predicted_floor_pixels"])
        self.assertEqual(1.0, result["per_gt"][0]["predicted_floor_fraction"])

    def test_all_empty_denominators_are_none(self):
        gt = np.zeros((2, 2), dtype=np.int32)
        pred = np.zeros((2, 2), dtype=np.int32)
        result = semantic_risk_metrics.metrics(
            semantic_risk_metrics.full_confusion(pred, gt)
        )
        self.assertIsNone(result["floor_precision_labeled"])
        self.assertIsNone(result["floor_recall"])
        self.assertIsNone(result["non_floor_as_floor_fraction"])
        self.assertEqual(0, result["floor_prediction_pixels_all"])
        self.assertEqual(0, result["floor_prediction_pixels_labeled"])
        self.assertEqual(0, result["unlabeled_as_floor_pixels"])
        self.assertEqual(0, result["non_floor_as_floor_pixels"])

    def test_all_floor_gt_mispredicted_yields_recall_zero(self):
        # Floor GT present but never predicted as floor → recall 0.0 (not None).
        # Unlabeled floor predictions must not inflate recall.
        gt = np.array([[4, 4, 0, 0]], dtype=np.int64)
        pred = np.array([[1, 1, 4, 4]], dtype=np.int64)
        result = semantic_risk_metrics.metrics(
            semantic_risk_metrics.full_confusion(pred, gt)
        )
        self.assertEqual(0.0, result["floor_recall"])
        self.assertIsNone(result["floor_precision_labeled"])
        self.assertEqual(2, result["unlabeled_as_floor_pixels"])
        self.assertEqual(0, result["floor_prediction_pixels_labeled"])
        self.assertEqual(2, result["floor_prediction_pixels_all"])

    def test_metrics_reports_diagnostic_floor_fields_and_per_gt(self):
        gt = np.array(
            [
                [4, 4, 54, 29],
                [1, 0, 60, 4],
            ],
            dtype=np.uint8,
        )
        pred = np.array(
            [
                [4, 1, 4, 4],
                [1, 4, 4, 4],
            ],
            dtype=np.uint8,
        )
        result = semantic_risk_metrics.metrics(
            semantic_risk_metrics.full_confusion(pred, gt)
        )
        self.assertIs(True, result["diagnostic_only"])
        self.assertEqual(4, result["floor_prediction_id"])
        self.assertEqual(6, result["floor_prediction_pixels_all"])
        self.assertEqual(5, result["floor_prediction_pixels_labeled"])
        self.assertEqual(2 / 5, result["floor_precision_labeled"])
        self.assertEqual(2 / 3, result["floor_recall"])
        self.assertEqual(1, result["unlabeled_as_floor_pixels"])
        self.assertEqual(3, result["non_floor_as_floor_pixels"])
        self.assertEqual(3 / 5, result["non_floor_as_floor_fraction"])
        self.assertEqual(151, len(result["per_gt"]))
        for index, entry in enumerate(result["per_gt"]):
            self.assertEqual(index, entry["id"])
            self.assertIn("pixels", entry)
            self.assertIn("predicted_floor_pixels", entry)
            self.assertIn("predicted_floor_fraction", entry)
            if entry["pixels"] == 0:
                self.assertIsNone(entry["predicted_floor_fraction"])
            else:
                expected = entry["predicted_floor_pixels"] / entry["pixels"]
                self.assertEqual(expected, entry["predicted_floor_fraction"])
        self.assertEqual(3, result["per_gt"][4]["pixels"])
        self.assertEqual(2, result["per_gt"][4]["predicted_floor_pixels"])
        self.assertEqual(2 / 3, result["per_gt"][4]["predicted_floor_fraction"])
        self.assertEqual(1, result["per_gt"][54]["predicted_floor_pixels"])
        self.assertEqual(1.0, result["per_gt"][54]["predicted_floor_fraction"])
        self.assertEqual(1, result["per_gt"][29]["predicted_floor_pixels"])
        # Absent classes stay None fraction (not 0.0).
        self.assertEqual(0, result["per_gt"][7]["pixels"])
        self.assertIsNone(result["per_gt"][7]["predicted_floor_fraction"])

    def test_no_implicit_hazard_or_safe_categories(self):
        gt = np.array([[54, 29, 122, 97]], dtype=np.int32)
        pred = np.array([[4, 4, 4, 1]], dtype=np.int32)
        result = semantic_risk_metrics.metrics(
            semantic_risk_metrics.full_confusion(pred, gt)
        )
        banned = (
            "hazard",
            "safe",
            "walkable",
            "clear",
            "stairs_hazard",
            "rug_safe",
            "rug_hazard",
            "path_clear",
        )
        blob = json.dumps(result, allow_nan=False).lower()
        for token in banned:
            self.assertNotIn(token, blob)
        # Caller-owned interpretation: only raw per_gt diagnostics for those IDs.
        for stair_id in GT_STAIR_IDS:
            entry = result["per_gt"][stair_id]
            self.assertEqual(stair_id, entry["id"])
            self.assertIn(entry["pixels"], (0, 1))
        self.assertEqual(1, result["per_gt"][29]["pixels"])
        self.assertEqual(1, result["per_gt"][29]["predicted_floor_pixels"])

    def test_rejects_shape_dtype_range_and_size_zero(self):
        good_gt = np.array([[4, 1], [0, 29]], dtype=np.int32)
        good_pred = np.array([[4, 1], [0, 29]], dtype=np.int32)

        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.array([[4]], dtype=np.int32), good_gt
            )
        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.array([4, 1, 0, 29], dtype=np.int32),
                np.array([4, 1, 0, 29], dtype=np.int32),
            )
        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.zeros((0, 3), dtype=np.int32),
                np.zeros((0, 3), dtype=np.int32),
            )
        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.zeros((2, 0), dtype=np.int32),
                np.zeros((2, 0), dtype=np.int32),
            )
        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.array([[4.0]], dtype=np.float32),
                np.array([[4]], dtype=np.int32),
            )
        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.array([[True]], dtype=bool),
                np.array([[4]], dtype=np.int32),
            )
        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.array([[32]], dtype=np.int32),
                np.array([[4]], dtype=np.int32),
            )
        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.array([[-1]], dtype=np.int32),
                np.array([[4]], dtype=np.int32),
            )
        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.array([[4]], dtype=np.int32),
                np.array([[151]], dtype=np.int32),
            )
        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.array([[4]], dtype=np.int32),
                np.array([[-1]], dtype=np.int32),
            )
        with self.assertRaises(ValueError):
            semantic_risk_metrics.full_confusion(
                np.array([[np.nan]], dtype=np.float64),
                np.array([[4.0]], dtype=np.float64),
            )
        # Valid pair still accepted after the rejection battery.
        matrix = semantic_risk_metrics.full_confusion(good_pred, good_gt)
        self.assertEqual(4, int(matrix.sum()))

    def test_metrics_rejects_invalid_matrix_without_mutation(self):
        valid = np.zeros((151, 32), dtype=np.int64)
        valid[4, 4] = 3
        valid[1, 4] = 1
        snapshot = valid.copy()

        with self.assertRaises(ValueError):
            semantic_risk_metrics.metrics(np.zeros((32, 151), dtype=np.int64))
        with self.assertRaises(ValueError):
            semantic_risk_metrics.metrics(np.zeros((151, 31), dtype=np.int64))
        with self.assertRaises(ValueError):
            semantic_risk_metrics.metrics(np.zeros((151, 32), dtype=np.float64))
        with self.assertRaises(ValueError):
            semantic_risk_metrics.metrics(np.full((151, 32), -1, dtype=np.int64))
        np.testing.assert_array_equal(snapshot, valid)

        result = semantic_risk_metrics.metrics(valid)
        self.assertEqual(3, result["per_gt"][4]["predicted_floor_pixels"])
        np.testing.assert_array_equal(snapshot, valid)

    def test_full_confusion_does_not_mutate_inputs(self):
        gt = np.array([[4, 54], [0, 29]], dtype=np.int32)
        pred = np.array([[4, 4], [1, 29]], dtype=np.int32)
        gt_before = gt.copy()
        pred_before = pred.copy()
        _ = semantic_risk_metrics.full_confusion(pred, gt)
        np.testing.assert_array_equal(gt_before, gt)
        np.testing.assert_array_equal(pred_before, pred)

    def test_deterministic_confusion_and_metrics(self):
        rng = np.random.default_rng(20261020)
        gt = rng.integers(0, 151, (9, 11), dtype=np.int32)
        pred = rng.integers(0, 32, gt.shape, dtype=np.int32)
        first = semantic_risk_metrics.full_confusion(pred, gt)
        second = semantic_risk_metrics.full_confusion(pred, gt)
        np.testing.assert_array_equal(first, second)
        self.assertEqual(
            semantic_risk_metrics.metrics(first),
            semantic_risk_metrics.metrics(second),
        )

    def test_metrics_json_safe_with_allow_nan_false(self):
        gt = np.array([[4, 54, 0], [29, 1, 122]], dtype=np.int32)
        pred = np.array([[4, 4, 4], [4, 1, 0]], dtype=np.int32)
        result = semantic_risk_metrics.metrics(
            semantic_risk_metrics.full_confusion(pred, gt)
        )
        payload = json.dumps(result, allow_nan=False)
        self.assertIsInstance(payload, str)
        round_trip = json.loads(payload)
        self.assertIs(True, round_trip["diagnostic_only"])
        self.assertEqual(4, round_trip["floor_prediction_id"])
        self.assertEqual(151, len(round_trip["per_gt"]))

    def test_uint8_label_maps_do_not_wrap_flat_indices(self):
        # Without int64 promotion, uint8(150)*32 wraps and bins into the wrong row.
        gt = np.array([[150]], dtype=np.uint8)
        pred = np.array([[31]], dtype=np.uint8)
        matrix = semantic_risk_metrics.full_confusion(pred, gt)
        self.assertEqual(1, int(matrix[150, 31]))
        self.assertEqual(1, int(matrix.sum()))

    def test_metrics_rejects_entry_above_int64_max(self):
        matrix = np.zeros((151, 32), dtype=np.uint64)
        matrix[4, 4] = np.uint64(np.iinfo(np.int64).max) + np.uint64(1)
        with self.assertRaises(ValueError):
            semantic_risk_metrics.metrics(matrix)

    def test_metrics_rejects_aggregate_sum_above_int64_max(self):
        matrix = np.zeros((151, 32), dtype=np.int64)
        half = np.iinfo(np.int64).max // 2 + 1
        matrix[4, 4] = half
        matrix[1, 4] = half
        with self.assertRaises(ValueError):
            semantic_risk_metrics.metrics(matrix)

    def test_metrics_accepts_int64_max_single_cell(self):
        matrix = np.zeros((151, 32), dtype=np.int64)
        matrix[0, 0] = np.iinfo(np.int64).max
        result = semantic_risk_metrics.metrics(matrix)
        self.assertEqual(int(np.iinfo(np.int64).max), result["per_gt"][0]["pixels"])
        self.assertEqual(0, result["floor_prediction_pixels_all"])


if __name__ == "__main__":
    unittest.main()
