# Raw Depth runtime-contract correction — 2026-10-20

## Confirmed bug, not an unsupported-device conclusion

After the owner coordinated a no-input foreground test, installed0.9.23/36
reached TRACKING and acquired a depth image. At08:58:23 the diagnostic guard
threw `unexpected_depth_format_48`; final status FAIL after2.17s,34 distinct
camera frames (1 tracking),0 accepted samples, followed by CLOSED.

The Java API explicitly returns `HardwareBuffer.D_16` (48), a full unsigned
16-bit little-endian depth plane. The old activity compared it to the unrelated
packed `ImageFormat.DEPTH16` (1144402265). This source defect was missed by the
earlier source review and decoder-only tests; the real-device test exposed it.

Primary contracts:

- [ARCore Frame.acquireRawDepthImage16Bits](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame#acquireRawDepthImage16Bits())
- [Android HardwareBuffer.D_16](https://developer.android.com/reference/android/hardware/HardwareBuffer#D_16)
- [Android packed ImageFormat.DEPTH16](https://developer.android.com/reference/android/graphics/ImageFormat#DEPTH16)

Do not apply a13-bit mask, accept arbitrary16-bit formats, merge confidence into
depth, or lower confidence/tracking/staleness gates to get a successful result.

## Tests-first correction and independent review

Four new `RawDepthImageFormatTest` cases were written first. Codex recorded a45s
compile RED (`RawDepthImageFormat` missing) before adding the helper and wiring:

- Accept the actual runtime code48 / SDK D_16 constant.
- Reject packed DEPTH16, Y8/YUV/JPEG, and invalid/unknown codes.
- Existing20 decoder tests still cover full unsigned depth, strides, bounds and
  immutability; no decoding/masking change was needed.

Cursor independently confirmed the original predicate defect, then re-read the
landed helper/activity/tests and accepted the strict format correction in source.
Its report is `docs/CURSOR_RAW_DEPTH_FORMAT_REVIEW_2026-10-20.md`; review is not a
build, phone or accuracy pass. Codex owns all build/install/device verification.

## 0.9.24/37 executed evidence

Offline gate PASS at
`outputs/reports/quality-gate/20261020T010124697066Z/summary.json`:
428 app +10 simulator +19 policy +10 evaluation =467 tests, no failure/error/skip,
both builds and APK policy pass. Lint0 errors/fatal;55 debug/34 release warnings.
The new InlinedApi warning refers to D_16's API28 constant on minSDK26. Inspected
JVM bytecode contains a literal `bipush48` comparison, not a runtime platform
field/method access; warning is retained, not suppressed. Old-OS device QA is not
claimed by this inspection.

Source fingerprint:
`aea19945a942bf842fc8d581356b0611223fca276a6bbc0c0bfff5b1aee6970d`.
Debug/installed SHA256:
`a04180010cc3f86215b5244576ef0531a25d8fd1ee7d8967c32e7976cfc5b882`.
USB `install -r` succeeded, version0.9.24/37 confirmed, installed bytes hash-matched.

Full20s re-probe at09:04:34 completed its timer (20.18s observed),566 distinct
camera frames, all nontracking,0 depth attempts/accepted samples:
**NO_DEPTH, reason=duration**, followed by CLOSED. No distances, confidence
coverage or receipt-to-summary latency are available. The fixed format branch
was not reached in this run; do not label this accepted-depth success.

## Tracking telemetry (0.9.25/38, intermediate verified version)

The full run exposed insufficient observability: `nontracking` alone cannot
identify initialization/light/features/motion/camera/internal causes. Reuse the
SDK's [TrackingFailureReason](https://developers.google.com/ar/reference/java/com/google/ar/core/TrackingFailureReason)
directly, without inventing a diagnosis. The probe now counts the SDK state/reason
only after distinct timestamp admission, reports it at most1Hz, and updates the
foreground diagnostic status with stopped guards. It still returns before any
depth acquisition while not TRACKING. This is telemetry, not a new sensor/model,
remedy, movement cue or guidance input. Cursor source-reviewed this telemetry;
no blocking defect reported. Four format regression tests and the full gate were
run again after this change.

Intermediate gate PASS:
`outputs/reports/quality-gate/20261020T010656007909Z/summary.json` —467 tests
(428 app +10 sim +19 policy +10 evaluation), no failure/error/skip, debug/release
build and APK privacy/isolation PASS, Lint0 errors/fatal and55/34 warnings.
Source fingerprint:
`babbc2b305d5de2d56f0431069fccf8bbe0c682a26f516b1abb8a62f5ade934c`.
Installed/staged debug SHA256:
`2d8ce24527c4a33be49d17fe57a446b6b7f9c74845164260e932387197daf267`.
Unsigned release SHA256:
`8e2315aea6701a076978b9c74c8eb4cccb422d94b995ca92f41b7d47ac6e9dfb`.
USB install-r succeeded; phone package reports0.9.25/38 and its actual installed
APK hash matches the gate. Original0.9.23 and intermediate0.9.24 APKs preserved.

## Post-fix device outcomes

| Version / start | Observed duration | Unique frames / tracking | Accepted depth | Result |
| --- | --- | --- | --- | --- |
| 0.9.23,08:58:21 | 2.17s | 34 /1 | 0 | FAIL: wrong format guard, then CLOSED |
| 0.9.24,09:04:34 | 20.18s | 566 /0 | 0 | NO_DEPTH: duration, then CLOSED |
| 0.9.25,09:09:55 | 20.16s | 561 /0 | 0 | NO_DEPTH: duration, then CLOSED |
| 0.9.25,09:13:47 (owner-adjusted lit view) | 20.19s | 566 /537 | 0 | NO_DEPTH:90 confidence_mismatch,56 no_valid_confident_pixels; CLOSED |
| 0.9.26,09:20:42 (timing telemetry) | 20.18s | 551 /194 | 0 | NO_DEPTH:6 confidence_mismatch,40 no_valid_confident_pixels; CLOSED |

0.9.25 reports the direct SDK reason:504 frames
`tracking_PAUSED_INSUFFICIENT_LIGHT`,57 `tracking_PAUSED_NONE` (initialization).
This is not a light-meter measurement or proof of the physical cause. Darkness,
an obstructed view and system camera privacy can require different remedies;
do not diagnose a defective model/sensor from the enum alone.

AppOps reports CAMERA allow/foreground and about19.7s use. Camera-service history
confirms disconnect for this run at09:10:15, with CLOSED at09:10:16.792. The
HyperOS sensor_privacy dump did not expose detailed switch states, and its shell
help was unsupported; global camera-privacy status was **not** conclusively
ruled out. No privacy/debug/security setting was changed or bypassed.

The owner then confirmed an unobstructed rear lens toward a lit detailed scene.
Tracking rose to537/566 distinct camera frames.146 image pairs passed the corrected
D_16/Y8 format and decoder checks (160x90, depth strides320/2, confidence160/1).
56 pairs lacked enough valid/confident pixels;90 otherwise reached the gate and
were rejected as confidence_mismatch. The initial depth/confidence timestamps
matched; the original aggregate log does not expose later per-pair differences.
Thus the format correction is exercised on the phone, but accepted-depth timing
and calibrated distance/height remain unverified. Do not weaken equality or add
an arbitrary tolerance merely to count these pairs as success.

## Final 0.9.26/39 timing evidence and engineering gate

Codex added at-most1Hz primitive timestamp/rejection metadata; it preserves the
exact gate and image lifetime. Final offline gate PASS:
`outputs/reports/quality-gate/20261020T011729486962Z/summary.json`,148.328s,
467 tests (428 app +10 sim +19 policy +10 evaluation), no failure/error/skip.
Both builds and APK privacy/isolation pass; Lint0 errors/fatal,55/34 warnings.
Source fingerprint:
`b86f17ee26bdeea4666b67ef3e5ba095e0ca3b191474aa882d00d50ccd5d1bf4`.
Debug/staged/actual installed APK SHA256:
`902204af18e552103c76d13221d765d267770038a102e002fdc14776d647ebe6`.
Unsigned release SHA256:
`c0155896408dc9d347af3f14ca7cc7f8dc337ee06110d6883322ea8fa4666ed0`.
USB install-r succeeded without data clear; package version39 confirmed.

The final20.18s run validated46 D_16/Y8 pairs, with9 NotYetAvailable attempts.
Of551 distinct frames,194 tracked;357 paused (FEATURES251, LIGHT45, NONE61).
40 pairs lacked sufficient valid/confident pixels and six failed timestamp
equality. One throttled rejection logged camera67359775242008ns,
depth67359709396333ns, confidence67359776880333ns: depth-minus-confidence
is -67.484ms. That sample had7255 valid pixels but only1 confident pixel out of
14400. This arithmetic is not evidence of comparable clocks or useful coverage.
Zero samples were accepted; status NO_DEPTH/duration followed by explicit CLOSED.

Cursor research completed; Codex verified and condensed the repeated source
material in `docs/CURSOR_RAW_DEPTH_TIMING_RESEARCH_2026-10-20.md`. The API
documents same-Frame pixel correspondence, but exact depth/confidence timestamp
equality was not found as an SDK guarantee. Image timestamps are source-specific;
cross-source comparisons require a contract, not a numeric tolerance. Current
policy is unchanged. Next is tests-first pairing-provenance/clock-domain design
review, before any diagnostic admission-policy change or further geometry test.

No accepted depth distances, coverage or receipt-to-summary percentiles exist.
Missing aggregate values do not mean zero physical distance. No new owner photo
or physical action is currently needed. Accepted-depth testing remains pending.
Explicit Stop/background under valid depth, geometry/height accuracy, audible
voice and TalkBack remain separate unverified gates.

Private evidence remains ignored under `outputs/reports/raw-depth-20261020-085818/`
and `outputs/reports/raw-depth-lit-20261020-091345/`.
No RGB/depth files were recorded or uploaded. No Internet/other permission,
dependency, model, navigation decision, confidence threshold or paid service
changed. Keep the owner stationary; held-out measured geometry and any movement
authorization remain separate future gates.
