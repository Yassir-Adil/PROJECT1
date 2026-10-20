# Raw Depth timing review — 2026-10-20

Cursor performed bounded primary-source research; Codex checked the relevant
contracts and condensed its repeated material. Full worker output remains local
under ignored outputs. No gate, threshold, permission or navigation change.

## Finding

The reviewed [ARCore Frame reference](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame)
documents corresponding raw-depth/confidence pixels, equal image sizes, and
acquisition from the current Frame. It does not promise equality of the two
Image timestamps. Raw-depth timestamps identify new estimates relative to
previous raw-depth timestamps; repeated estimates may be reprojected.

Both [Android Image timestamps](https://developer.android.com/reference/android/media/Image#getTimestamp())
and [NDK ArImage timestamps](https://developers.google.com/ar/reference/c/group/ar-image#arimage_gettimestamp)
warn that clock meaning depends on the source and cross-source comparisons may
be invalid. Confidence-specific timestamp semantics were not established.
Thus exact equality in TriGuide is an application policy, not a documented SDK
invariant found by this review. Nor is a common clock with Frame.timestamp proven.

The [pinned v1.57.0 sample](https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/raw_depth_java/app/src/main/java/com/google/ar/core/examples/java/rawdepth/DepthData.java)
co-acquires both images from one Frame, without a depth/confidence timestamp
equality check. Example code is not a calibration or safety guarantee.

## Measured follow-up (Codex, not worker measurements)

Installed 0.9.26/39 completed 20.18s: 551 distinct frames, 194 tracking,
46 validated pairs, 40 empty/insufficient-confidence rejections and six timing
rejections; zero accepted samples. One throttled rejection logged depth minus
confidence = -67.484ms, with only one confident pixel out of 14,400.

This numeric difference does not prove temporal age, invalid pixel pairing,
clock compatibility or useful depth accuracy. Reprojection and sparse confidence
remain separate questions.

## Next design gate

Keep the current admission policy unchanged. Before revising it, define typed
same-Frame acquisition provenance, independent monotonic receipt/processing age,
and source-specific new-estimate/reprojection rules. Add tests first for mixed
Frames, missing confidence, repeated/out-of-order estimates, invalid metadata,
stop/lifecycle and stale processing. Review any camera-clock comparison against
an explicit contract; do not invent a nanosecond tolerance.

Geometry, wall/mat/step accuracy and navigation integration require separate
held-out measurements. No walking authorization or new owner photo is needed
for the next source-design review.
