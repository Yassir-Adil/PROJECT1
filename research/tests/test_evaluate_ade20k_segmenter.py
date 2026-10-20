import importlib.util
import csv
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location("research.evaluate_ade20k_segmenter", Path(__file__).parents[1] / "evaluate_ade20k_segmenter.py")
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)


def litert_resize_oracle(values, height, width, nearest):
    """Use an existing LiteRT kernel as an independent interpolation oracle."""
    import flatbuffers
    from ai_edge_litert import schema_py_generated as schema
    from ai_edge_litert.interpreter import Interpreter, OpResolverType

    graph = schema.SubGraphT()
    graph.inputs, graph.outputs = [0], [2]
    graph.tensors = []
    for shape, dtype, buffer in (([1, *values.shape], schema.TensorType.FLOAT32, 0),
                                  ([2], schema.TensorType.INT32, 1),
                                  ([1, height, width, values.shape[2]], schema.TensorType.FLOAT32, 0)):
        tensor = schema.TensorT()
        tensor.shape, tensor.type, tensor.buffer = shape, dtype, buffer
        graph.tensors.append(tensor)
    code = schema.OperatorCodeT()
    code.builtinCode = (schema.BuiltinOperator.RESIZE_NEAREST_NEIGHBOR if nearest
                        else schema.BuiltinOperator.RESIZE_BILINEAR)
    code.deprecatedBuiltinCode, code.version = code.builtinCode, 3
    op = schema.OperatorT()
    op.opcodeIndex, op.inputs, op.outputs = 0, [0, 1], [2]
    op.builtinOptionsType = (schema.BuiltinOptions.ResizeNearestNeighborOptions if nearest
                            else schema.BuiltinOptions.ResizeBilinearOptions)
    op.builtinOptions = (schema.ResizeNearestNeighborOptionsT() if nearest
                         else schema.ResizeBilinearOptionsT())
    op.builtinOptions.alignCorners, op.builtinOptions.halfPixelCenters = True, False
    graph.operators = [op]
    model = schema.ModelT()
    model.version, model.subgraphs, model.operatorCodes = 3, [graph], [code]
    sizes = schema.BufferT()
    sizes.data = np.asarray([height, width], dtype="<i4").view(np.uint8)
    model.buffers = [schema.BufferT(), sizes]
    builder = flatbuffers.Builder(1024)
    builder.Finish(model.Pack(builder), file_identifier=b"TFL3")
    interpreter = Interpreter(model_content=bytes(builder.Output()), num_threads=1,
        experimental_op_resolver_type=OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES)
    interpreter.allocate_tensors()
    interpreter.set_tensor(0, values.astype(np.float32)[None])
    interpreter.invoke()
    return interpreter.get_tensor(2)[0]


class EvaluationContractTest(unittest.TestCase):
    def test_export_keeps_excluded_stairs_and_rug_counts_without_changing_top31_score(self):
        class InterpreterFixture:
            def __init__(self, **kwargs):
                pass

            def get_input_details(self):
                return [{"shape": np.array([1, 512, 512, 3]), "dtype": np.uint8,
                         "index": 0, "quantization": (.007843137718737125, 128)}]

            def get_output_details(self):
                return [{"shape": np.array([1, 512, 512]), "dtype": np.int32, "index": 1}]

            def get_tensor_details(self):
                return [{"index": 2, "shape": np.array([1, 512, 512, 32])}]

            def _get_ops_details(self):
                return [{"op_name": "ARG_MAX", "inputs": [2]}]

            def allocate_tensors(self):
                pass

            def set_tensor(self, index, value):
                pass

            def invoke(self):
                pass

            def get_tensor(self, index):
                return np.full((1, 512, 512), 4, np.int32)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for folder in ("images/validation", "annotations/validation", "models"):
                (root / folder).mkdir(parents=True)
            Image.fromarray(np.zeros((2, 3, 3), np.uint8)).save(root / "images/validation/ADE_val_00000001.jpg")
            Image.fromarray(np.array([[0, 4, 1], [54, 29, 4]], np.uint8)).save(root / "annotations/validation/ADE_val_00000001.png")
            (root / "models/fixture.tflite").write_bytes(b"synthetic interpreter fixture, not a model")
            args = SimpleNamespace(variant="uint8", model_dir=root / "models", data_root=root,
                                   limit=1, threads=1, output_dir=root / "report",
                                   preprocessing="reference", uint8_input="reference")
            pin = ("fixture.tflite", hashlib.sha256((root / "models/fixture.tflite").read_bytes()).hexdigest())
            with patch("ai_edge_litert.interpreter.Interpreter", InterpreterFixture), patch.dict(evaluation.MODEL_FILES, {"uint8": pin}):
                report = evaluation.run(args)
                with self.assertRaises(FileExistsError):
                    evaluation.run(args)
            stored = json.loads((args.output_dir / "summary.json").read_text())
            self.assertEqual(report["semantic_risk"], stored["semantic_risk"])
            counts = stored["semantic_risk"]["per_gt"]
            self.assertGreater(counts[54]["predicted_floor_pixels"], 0)
            self.assertGreater(counts[29]["predicted_floor_pixels"], 0)
            self.assertEqual(1., stored["stairs_predicted_floor_fraction"])
            self.assertEqual(151, len(stored["full_confusion_ade150_gt_rows_top31_prediction_columns"]))
            self.assertEqual(64, len(stored["semantic_risk_script_sha256"]))
            self.assertIs(stored["semantic_risk"]["diagnostic_only"], True)
            self.assertEqual(1, stored["semantic_risk_schema_version"])
            self.assertGreater(stored["semantic_risk"]["unlabeled_as_floor_pixels"], 0)
            self.assertEqual(1., stored["semantic_risk"]["non_floor_as_floor_fraction"] +
                             stored["semantic_risk"]["floor_precision_labeled"])
            with (args.output_dir / "frames.tsv").open(newline="", encoding="utf-8") as stream:
                row = next(csv.DictReader(stream, delimiter="\t"))
            self.assertEqual(counts[29]["pixels"], int(row["rug_gt_pixels"]))
            self.assertEqual(counts[29]["predicted_floor_pixels"], int(row["rug_predicted_floor_pixels"]))
            self.assertGreater(int(row["wall_gt_pixels"]), 0)
            self.assertEqual(counts[1]["pixels"], int(row["wall_gt_pixels"]))
            self.assertEqual(counts[1]["predicted_floor_pixels"], int(row["wall_predicted_floor_pixels"]))
            self.assertEqual(stored["metrics"]["scored_pixels"],
                             counts[1]["pixels"] + counts[4]["pixels"] + counts[29]["pixels"])

    def test_background_and_outside_top31_are_ignored_but_missing_prediction_is_not(self):
        gt = np.array([[0, 4, 4, 54, 31]])
        pred = np.array([[4, 4, 0, 4, 31]], dtype=np.int32)
        matrix = evaluation.confusion(pred, gt)
        self.assertEqual(3, matrix.sum())
        self.assertEqual(1, matrix[4, 0])
        result = evaluation.metrics(matrix)
        self.assertEqual(.5, result["classes"][3]["iou"])
        self.assertEqual(1., result["classes"][30]["iou"])
        self.assertEqual(2 / 3, result["pixel_accuracy"])

    def test_invalid_prediction_is_rejected_even_in_ignored_pixels(self):
        with self.assertRaises(ValueError):
            evaluation.confusion(np.array([[32]], np.int32), np.array([[0]]))

    def test_vectorized_scoring_matches_the_reference_scalar_class_loop(self):
        rng = np.random.default_rng(20261019)
        gt = rng.integers(0, 151, (13, 17), dtype=np.int32)
        pred = rng.integers(0, 32, gt.shape, dtype=np.int32)
        actual = evaluation.metrics(evaluation.confusion(pred, gt))
        valid = (gt > 0) & (gt <= 31)
        for category in range(1, 32):
            tp = np.count_nonzero((pred == category) & (gt == category))
            fp = np.count_nonzero((pred == category) & (gt != category) & valid)
            fn = np.count_nonzero((gt == category) & (pred != category))
            expected = tp / (tp + fp + fn) if tp + fp + fn else None
            self.assertEqual(expected, actual["classes"][category - 1]["iou"])

    def test_align_corners_preserves_endpoints_and_nearest_integer_labels(self):
        rgb = np.repeat(np.array([[0, 100], [200, 240]], np.uint8)[..., None], 3, axis=2)
        labels = np.array([[1, 4], [54, 31]], np.uint8)
        resized, mask = evaluation.resize_pair(rgb, labels, 3, 3, True)
        self.assertEqual(0., resized[0, 0, 0])
        self.assertEqual(240., resized[2, 2, 0])
        self.assertEqual(135., resized[1, 1, 0])
        self.assertEqual(31, mask[1, 1])
        self.assertEqual({1, 4, 54, 31}, set(np.unique(mask)))

    def test_reference_padding_is_bottom_right_and_preserves_stair_ids(self):
        rgb, mask = evaluation.preprocess(np.zeros((2, 1, 3), np.uint8), np.full((2, 1), 54, np.uint8), "reference")
        self.assertEqual(54, mask[0, 0])
        self.assertEqual(54, mask[-1, 255])
        self.assertEqual(0, mask[0, 256])
        self.assertEqual(127, rgb[0, 256, 0])

    def test_uint8_reference_preserves_raw_pixel_codes(self):
        pixels = np.array([[[0, 128, 255]]], np.uint8)
        actual = evaluation.encode_input(pixels, np.uint8, (.0078125, 128), "reference")
        np.testing.assert_array_equal(actual[0], pixels)
        quantized = evaluation.encode_input(pixels, np.uint8, (.0078125, 128), "normalized-quantized")
        self.assertEqual(129, quantized[0, 0, 0, 1])

    def test_float_normalization_endpoints(self):
        pixels = np.array([[[0, 255, 128]]], np.uint8)
        actual = evaluation.encode_input(pixels, np.float32, (0., 0), "reference")
        self.assertEqual(-1., actual[0, 0, 0, 0])
        self.assertEqual(1., actual[0, 0, 0, 1])

    def test_mismatched_masks_fail_before_scoring(self):
        with self.assertRaises(ValueError):
            evaluation.preprocess(np.zeros((2, 1, 3), np.uint8), np.zeros((1, 2), np.uint8), "reference")

    def test_subset_guard_prevents_the_duplicate_selection_review_example(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for kind, extension in (("images", "jpg"), ("annotations", "png")):
                folder = root / kind / "validation"
                folder.mkdir(parents=True)
                for index in range(3):
                    (folder / f"ADE_val_{index:08d}.{extension}").touch()
            with self.assertRaises(ValueError):
                evaluation.paired_images(root, 4)
            for limit in (1, 2, 3):
                selected = evaluation.paired_images(root, limit)
                self.assertEqual(limit, len({image for image, _ in selected}))

    def test_large_image_float32_resize_matches_litert_reference_kernels(self):
        rng = np.random.default_rng(9)
        rgb = rng.integers(0, 256, (1536, 2048, 3), dtype=np.uint8)
        labels = rng.integers(0, 151, rgb.shape[:2], dtype=np.uint8)
        actual_rgb, actual_mask = evaluation.resize_pair(rgb, labels, 384, 512, True)
        expected_rgb = litert_resize_oracle(rgb, 384, 512, nearest=False)
        expected_mask = litert_resize_oracle(labels[..., None], 384, 512, nearest=True)[..., 0]
        np.testing.assert_allclose(actual_rgb, expected_rgb, atol=5e-5, rtol=0)
        np.testing.assert_array_equal(actual_mask, expected_mask)


if __name__ == "__main__":
    unittest.main()
