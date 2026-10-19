# ADE20K local semantic evaluation — 2026-10-19

## Dataset and reproducibility

The official [ADEChallengeData2016 archive](https://data.csail.mit.edu/places/ADEchallenge/ADEChallengeData2016.zip)
was downloaded for local non-commercial educational evaluation under the
[ADE20K image terms](https://ade20k.csail.mit.edu/terms/). Images and masks stay
outside the repository in `%LOCALAPPDATA%/TriGuide-AI/datasets/`.

The Windows Store Codex process virtualizes that logical location. For a run
outside Codex, the verified physical dataset root (2,000 images + 2,000 masks) is:
`C:/Users/yassi/AppData/Local/Packages/OpenAI.Codex_2p2nqsd0c76g0/LocalCache/Local/TriGuide-AI/datasets/ADEChallengeData2016`.
The isolated `eval-env` Python environment is a sibling of `datasets` there.

- Archive size: **967,382,037 bytes**.
- Locally computed SHA-256: `7FF1BE44964418441F542A7CC1E1A650E7DC0FC275F5D23252BC9BBDBC977B29`.
  This is our transfer record, not a claimed publisher-supplied checksum.
- All **44,429** ZIP entries passed CRC checks. Only the **2,000 validation
  JPGs and 2,000 paired PNG masks** were extracted, using a strict path whitelist.
- The dataset's `provenance.json` records source, terms, archive hash, counts,
  and CRC status. Training images were not extracted or used to train a model.

`research/evaluate_ade20k_segmenter.py` runs the pinned pretrained models
locally with LiteRT. Each report contains model/script hashes, hashes of every
image/mask pair, a selection digest, package versions, raw confusion counts,
and per-image TSV statistics. It refuses missing masks, unexpected tensor
contracts, invalid prediction IDs, changed model hashes, and existing output
directories. It exports statistics, not images.

## Verified contracts

Independent inspection with LiteRT **2.3.0** found an ARG_MAX input of
`[1,512,512,32]` in each of the exact three Android candidate files. Their
output is `[1,512,512] INT32`, containing category IDs **0..31**. The official
[MLPerf evaluator](https://github.com/mlcommons/mobile_app_open/blob/master/flutter/cpp/datasets/ade20k.cc)
compares these output IDs directly with ground truth, scores categories 1..31,
and ignores ground-truth 0 and categories above 31. It does not shift IDs.
The [ADE20K taxonomy](https://github.com/CSAILVision/sceneparsing/blob/master/objectInfo150.txt)
names wall=1, floor=4, and armchair=31.

The Android diagnostic now accepts only the pinned INT32 class-map contract.
Its unused, incorrect 31-channel logits path was removed. A bulk integer
decoder preserves tensor byte order, rejects truncated/invalid maps, and does
not consume the caller's buffer. Four focused JVM cases cover that boundary.

## Reference-derived preprocessing

The [DeepLab range resize](https://github.com/mlcommons/mobile_open/blob/main/vision/deeplab/models_and_code/core/preprocess_utils.py)
uses floor-rounded dimensions, bilinear RGB with `align_corners=True`, and
nearest-neighbor integer labels. With min/max size 512, the long side becomes
512. [Input preprocessing](https://github.com/mlcommons/mobile_open/blob/main/vision/deeplab/models_and_code/input_preprocess.py)
pads with content at offset (0,0), adding padding on the bottom/right.
[The model helper](https://github.com/mlcommons/mobile_open/blob/main/vision/deeplab/models_and_code/core/feature_extractor.py)
returns mean RGB 127.5. The official
[PNG dataset preparation](https://github.com/mlcommons/mobile_open/blob/main/vision/deeplab/ade20k_512x512_dataset_on_device/README.md)
casts the prepared RGB to uint8, so that padding becomes 127.

The local evaluator reproduces those geometry rules using NumPy, decodes the
original JPG with Pillow, and performs no extra JPEG encoding. This is a
reference-derived internal evaluation; TensorFlow/Pillow JPEG decoding and
floating arithmetic have not been established as bit-identical. Results are
not official MLPerf submissions or Redmi accuracy measurements.

The official [default normalizer](https://github.com/tensorflow/tensorflow/blob/v2.17.0/tensorflow/lite/tools/evaluation/stages/image_preprocessing_stage.h#L149)
normalizes float input to [-1,1] but copies UINT8 RGB codes unchanged. Earlier
Android static batches instead normalized and requantized UINT8; that can
change a code by one (e.g. gray 128 becomes 129 for the DeepLab UINT8 scale).
The diagnostic now defaults to `reference-rgb`, with the earlier encoding
selectable as `triguide.diagnostic.input_encoding=normalized-quantized`.
Its centered gray-128 image canvas remains the historical static probe geometry;
raw input encoding alone does not make that canvas reference-aligned.

## Scoring and failure visibility

Top31 scoring uses a 32x32 confusion matrix with ground-truth rows. Ground-truth
0 and >31 are ignored; a prediction of 0 on valid foreground still counts as a
miss. The primary mean IoU has a fixed denominator of 31, matching the app
evaluator. A present-class mean is also reported for small subsets.

Stair/step/escalator IDs **54, 60, 97, 122** remain in the resized ground truth
for a separate diagnostic. Reports count which of their pixels were predicted
as floor=4. Those classes are excluded from Top31 mIoU and cannot be identified
as stairs by a 32-ID Top31 classifier. This separate statistic prevents the
headline mIoU from hiding that limitation. Pixel fractions are not scene-level
hazard recall, distance, confidence, or traversability.

## Run locally

Use an isolated Python environment with `research/requirements-eval.txt`:

```powershell
python research/evaluate_ade20k_segmenter.py `
  --data-root '<local dataset directory>/ADEChallengeData2016' `
  --variant mosaic --threads 2 `
  --output-dir outputs/reports/ade20k-eval/<new-run-name>
```

Variants are `mosaic`, `float`, and `uint8`. The default processes all validation
images; `--limit 64` selects 64 evenly spaced samples deterministically.
`--uint8-input normalized-quantized` isolates the earlier input encoding;
`--preprocessing centered` is an explicitly named approximation of the old
Android canvas. Reports keep both choices visible.

Three complete 2,000-image evaluations were launched concurrently with two
interpreter threads each on an eight-logical-CPU Windows host. Host inference
times therefore include contention and are not used to rank Redmi latency.

## Results

All three full runs completed successfully over the same **2,000 unique pairs**,
**321,270,074 scored Top31 pixels**, and all 31 foreground classes. The shared
selection digest is `3636cfd743e3b03ec213b18817a513e98a1b2f76c2be6d548ef6802edf630eac`.
Evaluator script SHA-256 is
`83A908D38580E2D1E472C276B7344929F1D77F7A5E021694BAC989FB125E7D9F`.

| Pinned model | Top31 mIoU | Wall IoU | Floor IoU | Stair pixels predicted as floor |
|---|---:|---:|---:|---:|
| MOSAIC R4 UINT8 | **58.81%** | **73.86%** | **77.43%** | 19.45% |
| DeepLab float | 55.25% | 71.79% | 74.46% | **15.21%** |
| DeepLab UINT8 | 54.84% | 70.60% | 73.09% | 20.00% |

There were **180 images** with resized stair/step/escalator ground-truth pixels,
totaling **1,799,391** pixels. MOSAIC called **349,988** of them floor, versus
273,698 for float DeepLab and 359,881 for UINT8 DeepLab. MOSAIC leads average
semantic quality, wall/floor IoU, and the earlier Redmi static speed comparison;
float DeepLab performs better on this particular stair-as-floor diagnostic.
The models cannot be ranked for navigation safety from average mIoU alone.

Raw reports and per-frame hashes/counts:

- `outputs/reports/ade20k-eval/20261019-mosaic-reference-2000/`
- `outputs/reports/ade20k-eval/20261019-float-reference-2000/`
- `outputs/reports/ade20k-eval/20261019-uint8-reference-2000/`

Each directory contains `summary.json` and `frames.tsv`. Complete host runs
took approximately 300.4, 452.5, and 370.5 seconds respectively under concurrent
load. These durations and invoke percentiles are not Redmi performance.

### Encoding/geometry checks on the same 64-image subset

| Model / geometry / UINT8 encoding | Top31 mIoU |
|---|---:|
| MOSAIC / reference / raw RGB | 60.60% |
| MOSAIC / reference / legacy normalized-quantized | 60.03% |
| MOSAIC / centered approximation / raw RGB | 59.63% |
| DeepLab UINT8 / reference / raw RGB | 55.55% |
| DeepLab UINT8 / reference / legacy normalized-quantized | 55.63% |

Subset digest: `1d7afbf9a9b1e2cd2e6a7c47bd6d9f7fa26b5985ed6bec420f3813efa107bdab`.
Raw RGB follows the official contract; the small subset does not establish
that it improves every model. The centered recipe changes geometry and label
sampling together, so its score does not isolate padding location alone.

## Independent review and verification

- Cursor CLI completed two read-only audits. Its final reviewer proposed
  changing subset selection and float32 resize coordinates. Codex checked both
  proposals rather than applying them automatically.
- The reported duplicate-index example requires `limit > dataset length`, which
  the existing guard already rejects. A temporary three-pair fixture verifies
  that rejection and unique valid selections.
- Float32 coordinates match the
  [TensorFlow nearest kernel's float scale and roundf](https://github.com/tensorflow/tensorflow/blob/v2.17.0/tensorflow/core/kernels/image/resize_nearest_neighbor_op.cc#L123).
  A 2048x1536 fixture compares NumPy bilinear RGB and nearest labels against
  actual LiteRT reference kernels: RGB within 0.00005 and labels identical.
  Changing to float64 would change the stated reference arithmetic.
- **10 Python tests passed**: score/ignore/miss handling, scalar-score oracle,
  tensor encoding, dimensions, padding, subset guard, and kernel interpolation.
- **291 app JVM tests passed**, zero failures/errors; **10 sim-core tests** remain
  passing. The final debug tests/build succeeded in 42 seconds. Release had
  assembled successfully in the earlier combined build and is unchanged by
  these debug-only edits.
- Current diagnostic debug APK SHA-256:
  `DC0BE8CED37F89C1771D3EFC58B09870252E01E962CB354DFA1F8C833BD9E567`.
  A hash-verified recovery/staging copy is saved as
  `outputs/apk/TriGuideAI-0.9.21-segmenter-diagnostic-input-fix-20261019.apk`;
  the original stable APK was not overwritten.
- Release APK SHA-256 is unchanged:
  `5B2D6B483624277B032F1F3FC3C10DF6DA928C6D820C31BDE0E62EC505C40540`.
  It contains no diagnostic assets, and its merged manifest contains no
  diagnostic activity or INTERNET permission.

Redmi initially disconnected before the post-fix backup/install step, with no
device modification in that failed preflight. A subsequent session recovered
paired wireless ADB and completed the sealed diagnostic's phone validation:
MOSAIC raw RGB / NNAPI / 1000 static runs gave **17.06 ms p50 / 21.14 ms p90**.
Legacy-input and DeepLab UINT8 raw-input controls also completed. Original
**0.9.21/34** was restored with exact installed APK SHA-256 match before/after.
See `docs/REDMI_WIRELESS_AND_POSTFIX_PROBE_2026-10-19.md` for raw evidence,
connection recovery, method, and limitations. These are still-image results,
not a live-camera or labeled-phone accuracy test.

Next: controlled capture-to-result MOSAIC NNAPI diagnostics on the Redmi,
followed by independent depth/step evidence. Class-map argmax is
not calibrated floor confidence and must not be converted into certainty or a
path-clear decision.
