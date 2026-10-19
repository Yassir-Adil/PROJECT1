"""Offline, local educational evaluation of the three pinned MLCommons models.

Reference-derived resize/pad and scoring follow these Apache-2.0 sources:
https://github.com/mlcommons/mobile_open/blob/main/vision/deeplab/models_and_code/core/preprocess_utils.py
https://github.com/mlcommons/mobile_open/blob/main/vision/deeplab/models_and_code/input_preprocess.py
https://github.com/mlcommons/mobile_app_open/blob/master/flutter/cpp/datasets/ade20k.cc

Uses Pillow JPEG decoding and NumPy interpolation, rather than TensorFlow JPEG
decoding. This is an internal evaluation, not a certified MLPerf result. No
network, camera, guidance, model training, or image export is performed.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import importlib.metadata
import json
import platform
import time
from pathlib import Path

import numpy as np
from PIL import Image

SIZE = 512
CLASS_COUNT = 32
STAIR_IDS = (54, 60, 97, 122)
MODEL_FILES = {
    "float": ("deeplabv3_mnv2_ade20k_float.tflite", "12a2141f28928a9bccce5063d0f868211c9d2633a263b9141594ff300daeafc0"),
    "uint8": ("deeplabv3_mnv2_ade20k_uint8.tflite", "2d58c681d81b5e181816b81cc815b82edf3daad905b4d7d35afade80aee93b5f"),
    "mosaic": ("mobile_segmenter_r4_quant_argmax_uint8.tflite", "da8b3822a22fde45ecaf0ff32022287eff58b500096971b04f3c1b292da66a3c"),
}


def resize_coordinates(source: int, target: int, align_corners: bool) -> np.ndarray:
    if align_corners:
        scale = np.float32((source - 1) / (target - 1)) if target > 1 else np.float32(0)
        return np.arange(target, dtype=np.float32) * scale
    return np.clip((np.arange(target, dtype=np.float32) + np.float32(.5)) *
                   np.float32(source / target) - np.float32(.5), 0, source - 1)


def resize_pair(rgb: np.ndarray, label: np.ndarray, height: int, width: int,
                align_corners: bool) -> tuple[np.ndarray, np.ndarray]:
    """Bilinear RGB and nearest integer labels with a shared coordinate mapping."""
    ys = resize_coordinates(rgb.shape[0], height, align_corners)
    xs = resize_coordinates(rgb.shape[1], width, align_corners)
    y0, x0 = np.floor(ys).astype(int), np.floor(xs).astype(int)
    y1, x1 = np.minimum(y0 + 1, rgb.shape[0] - 1), np.minimum(x0 + 1, rgb.shape[1] - 1)
    wy, wx = (ys - y0.astype(np.float32))[:, None, None], (xs - x0.astype(np.float32))[None, :, None]
    rgb = rgb.astype(np.float32)
    top = rgb[y0[:, None], x0] + (rgb[y0[:, None], x1] - rgb[y0[:, None], x0]) * wx
    bottom = rgb[y1[:, None], x0] + (rgb[y1[:, None], x1] - rgb[y1[:, None], x0]) * wx
    resized = top + (bottom - top) * wy
    # TF nearest-neighbor with align_corners rounds positive coordinates at .5.
    yn, xn = np.floor(ys + .5).astype(int), np.floor(xs + .5).astype(int)
    return resized, label[yn[:, None], xn]


def preprocess(rgb: np.ndarray, label: np.ndarray, mode: str) -> tuple[np.ndarray, np.ndarray]:
    if rgb.ndim != 3 or rgb.shape[2] != 3 or label.shape != rgb.shape[:2]:
        raise ValueError("Image and single-channel mask dimensions must match")
    if not np.issubdtype(label.dtype, np.integer) or np.any((label < 0) | (label > 150)):
        raise ValueError("Expected ADE20K integer label IDs 0..150")
    h, w = rgb.shape[:2]
    scaled = np.maximum(1, np.floor(np.asarray([h, w], np.float32) * np.float32(SIZE / max(h, w))).astype(int))
    rh, rw = int(scaled[0]), int(scaled[1])
    resized, resized_label = resize_pair(rgb, label, rh, rw, align_corners=mode == "reference")
    # The reference PNG preparation casts float RGB (mean pad 127.5) to uint8.
    canvas = np.full((SIZE, SIZE, 3), 127 if mode == "reference" else 128, np.uint8)
    target = np.zeros((SIZE, SIZE), np.uint8)
    top, left = (0, 0) if mode == "reference" else ((SIZE - rh) // 2, (SIZE - rw) // 2)
    canvas[top:top + rh, left:left + rw] = np.clip(resized, 0, 255).astype(np.uint8)
    target[top:top + rh, left:left + rw] = resized_label
    return canvas, target


def encode_input(rgb: np.ndarray, dtype, quantization: tuple[float, int], uint8_mode: str) -> np.ndarray:
    if dtype == np.uint8 and uint8_mode == "reference":
        # MLPerf AddDefaultNormalizationStep is deliberately a no-op for UINT8.
        return rgb[None]
    real = rgb.astype(np.float32) / np.float32(127.5) - np.float32(1)
    if dtype == np.float32:
        return real[None]
    scale, zero = quantization
    if dtype != np.uint8 or not np.isfinite(scale) or scale <= 0:
        raise ValueError("Unsupported model input contract")
    return np.clip(np.floor(real / np.float32(scale) + zero + .5), 0, 255).astype(np.uint8)[None]


def confusion(prediction: np.ndarray, ground_truth: np.ndarray) -> np.ndarray:
    if prediction.shape != ground_truth.shape or not np.issubdtype(prediction.dtype, np.integer):
        raise ValueError("Prediction must be a same-size integer class map")
    if np.any((prediction < 0) | (prediction >= CLASS_COUNT)):
        raise ValueError("Prediction contains a class ID outside 0..31")
    valid = (ground_truth >= 1) & (ground_truth < CLASS_COUNT)
    return np.bincount(ground_truth[valid].astype(np.int64) * CLASS_COUNT + prediction[valid],
                       minlength=CLASS_COUNT ** 2).reshape(CLASS_COUNT, CLASS_COUNT)


def metrics(matrix: np.ndarray) -> dict:
    tp = np.diag(matrix)[1:]
    actual, predicted = matrix.sum(axis=1)[1:], matrix.sum(axis=0)[1:]
    union = actual + predicted - tp
    iou = np.divide(tp, union, out=np.zeros(31, dtype=float), where=union > 0)
    return {
        "miou_31_fixed_denominator": float(iou.mean()),
        "miou_present_classes": float(iou[union > 0].mean()) if np.any(union > 0) else None,
        "represented_classes": int(np.count_nonzero(union)),
        "scored_pixels": int(actual.sum()),
        "pixel_accuracy": float(tp.sum() / actual.sum()) if actual.sum() else None,
        "classes": [{"id": c, "iou": float(iou[c - 1]) if union[c - 1] else None,
                     "ground_truth_pixels": int(actual[c - 1]), "predicted_pixels": int(predicted[c - 1]),
                     "true_positive": int(tp[c - 1])} for c in range(1, CLASS_COUNT)],
    }


def paired_images(root: Path, limit: int) -> list[tuple[Path, Path]]:
    images = sorted((root / "images" / "validation").glob("ADE_val_*.jpg"))
    if not images:
        raise ValueError("No official validation JPGs found")
    if limit < 0 or limit > len(images):
        raise ValueError("Limit must be zero (all), or no larger than the dataset")
    if limit:
        images = [images[i] for i in np.linspace(0, len(images) - 1, limit, dtype=int)]
    pairs = [(image, root / "annotations" / "validation" / (image.stem + ".png")) for image in images]
    if any(not mask.is_file() for _, mask in pairs):
        raise ValueError("Missing same-stem ground-truth mask")
    return pairs


def run(args) -> dict:
    from ai_edge_litert.interpreter import Interpreter
    model_name, expected_hash = MODEL_FILES[args.variant]
    model = args.model_dir / model_name
    model_hash = hashlib.sha256(model.read_bytes()).hexdigest()
    if model_hash != expected_hash:
        raise ValueError("Pinned model SHA-256 mismatch")
    pairs = paired_images(args.data_root, args.limit)
    interpreter = Interpreter(model_path=str(model), num_threads=args.threads)
    input_info, output_info = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    tensors = {t["index"]: t for t in interpreter.get_tensor_details()}
    argmax_shapes = [tensors[int(op["inputs"][0])]["shape"].tolist()
                     for op in interpreter._get_ops_details() if op["op_name"] == "ARG_MAX"]
    expected_dtype = np.float32 if args.variant == "float" else np.uint8
    if (input_info["shape"].tolist() != [1, SIZE, SIZE, 3] or input_info["dtype"] != expected_dtype or
        output_info["shape"].tolist() != [1, SIZE, SIZE] or output_info["dtype"] != np.int32):
        raise ValueError("Unexpected tensor contract for pinned model")
    interpreter.allocate_tensors()
    args.output_dir.mkdir(parents=True, exist_ok=False)
    total = np.zeros((CLASS_COUNT, CLASS_COUNT), np.int64)
    stair_predictions = np.zeros(CLASS_COUNT, np.int64)
    digest, latencies = hashlib.sha256(), []
    started = time.perf_counter()
    columns = ["scene_id", "image_sha256", "mask_sha256", "scored_pixels", "correct_pixels",
               "inference_ms", "stair_gt_pixels", "stair_predicted_floor_pixels"]
    with (args.output_dir / "frames.tsv").open("x", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, columns, delimiter="\t")
        writer.writeheader()
        for index, (image, mask) in enumerate(pairs):
            image_hash, mask_hash = (hashlib.sha256(p.read_bytes()).hexdigest() for p in (image, mask))
            digest.update(f"{image.stem}\0{image_hash}\0{mask_hash}\n".encode())
            with Image.open(image) as im, Image.open(mask) as gt:
                rgb, target = preprocess(np.asarray(im.convert("RGB")), np.asarray(gt), args.preprocessing)
            model_input = encode_input(rgb, input_info["dtype"], input_info["quantization"], args.uint8_input)
            interpreter.set_tensor(input_info["index"], model_input)
            if index == 0:
                for _ in range(2):
                    interpreter.invoke()
            before = time.perf_counter_ns()
            interpreter.invoke()
            elapsed = (time.perf_counter_ns() - before) / 1e6
            prediction = interpreter.get_tensor(output_info["index"])[0]
            current = confusion(prediction, target)
            total += current
            latencies.append(elapsed)
            stairs = np.isin(target, STAIR_IDS)
            stair_predictions += np.bincount(prediction[stairs], minlength=CLASS_COUNT)
            writer.writerow(dict(zip(columns, [image.stem, image_hash, mask_hash, int(current.sum()),
                int(np.trace(current)), round(elapsed, 4), int(stairs.sum()), int(np.count_nonzero(prediction[stairs] == 4))])))
            if (index + 1) % 32 == 0 or index == len(pairs) - 1:
                stream.flush()
                print(f"EVALUATED {index + 1}/{len(pairs)} {args.variant} elapsed_s={time.perf_counter() - started:.1f}", flush=True)
    report = {
        "status": "COMPLETE", "scope": "local_public_ADE20K_validation_host_CPU_semantics_only",
        "variant": args.variant, "model_sha256": model_hash, "sample_count": len(pairs),
        "script_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "input_dtype": np.dtype(input_info["dtype"]).name,
        "input_quantization": [float(input_info["quantization"][0]), int(input_info["quantization"][1])],
        "argmax_input_shapes": argmax_shapes,
        "dataset_provenance": json.loads((args.data_root / "provenance.json").read_text()) if (args.data_root / "provenance.json").is_file() else None,
        "selection_sha256": digest.hexdigest(), "dataset_size": len(list((args.data_root / 'images' / 'validation').glob('ADE_val_*.jpg'))),
        "preprocessing": args.preprocessing, "uint8_input": args.uint8_input,
        "recipe": "long_side_512_floor_dimensions_bilinear_align_corners_top_left_pad127_nearest_labels" if args.preprocessing == "reference" else "centered_pad128_half_pixel_bilinear_approximation_of_Android_probe",
        "decode": "Pillow_RGB_original_JPEG_no_extra_JPEG_encode; reference-derived_not_bit_identical_TensorFlow",
        "metrics": metrics(total), "confusion_gt_rows_prediction_columns": total.tolist(),
        "stairs_ground_truth_ids": STAIR_IDS, "stairs_pixel_count": int(stair_predictions.sum()),
        "stairs_predicted_ids": stair_predictions.tolist(),
        "stairs_predicted_floor_fraction": float(stair_predictions[4] / stair_predictions.sum()) if stair_predictions.sum() else None,
        "host_invoke_p50_ms": float(np.percentile(latencies, 50, method="inverted_cdf")),
        "host_invoke_p90_ms": float(np.percentile(latencies, 90, method="inverted_cdf")),
        "host_invoke_mean_ms": float(np.mean(latencies)), "elapsed_seconds": time.perf_counter() - started,
        "threads": args.threads, "host": {"os": platform.system(), "machine": platform.machine()},
        "packages": {p: importlib.metadata.version(p) for p in ("ai-edge-litert", "numpy", "Pillow")},
        "limitations": ["Not Redmi or live camera performance", "No floor confidence or traversability measurement",
                        "Top31 excludes stairs; stair diagnostics are scored separately from Top31 mIoU"],
    }
    with (args.output_dir / "summary.json").open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2, allow_nan=False)
    print(json.dumps({"status": report["status"], "variant": args.variant, "samples": len(pairs),
                      "miou": report["metrics"]["miou_31_fixed_denominator"],
                      "stairs_as_floor": report["stairs_predicted_floor_fraction"]}), flush=True)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-root", type=Path, required=True)
    parser.add_argument("--model-dir", type=Path, default=Path(__file__).resolve().parents[1] / "android-app/app/src/debug/assets/diagnostics")
    parser.add_argument("--variant", choices=MODEL_FILES, default="mosaic")
    parser.add_argument("--preprocessing", choices=("reference", "centered"), default="reference")
    parser.add_argument("--uint8-input", choices=("reference", "normalized-quantized"), default="reference")
    parser.add_argument("--limit", type=int, default=0, help="0 = all; otherwise evenly spaced samples")
    parser.add_argument("--threads", type=int, choices=range(1, 17), default=4)
    parser.add_argument("--output-dir", type=Path, required=True, help="A new directory; existing reports are never overwritten")
    run(parser.parse_args())


if __name__ == "__main__":
    main()
