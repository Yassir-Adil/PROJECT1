# Same-Frame depth pairing and observed coverage — 2026-10-20

## Scope and motivation

Previous installed0.9.26 completed20.18s with46 decoded pairs but0 admitted:
40 lacked confident pixels and6 failed exact timestamp equality. We do not
change confidence128, navigation, speech, models, permissions or dependencies.
This round repairs the debug measurement contract before evaluating geometry.

[ARCore Frame](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame)
documents corresponding depth/confidence pixels from the current Frame and
raw-depth timestamp changes relative to previous raw-depth samples.
[Android Image](https://developer.android.com/reference/android/media/Image#getTimestamp())
warns that clocks depend on the source. Exact image-timestamp equality and a
Frame-minus-depth age limit were app policy, not a verified common-clock contract.

## Two implemented increments (execution evidence below)

1. RawDepthPairScope passes one captured Frame to both SDK acquisitions, checks
   its positive unchanged timestamp, exposes a private-constructor live proof
   only during processing, and closes confidence then depth on all exits.
   RawDepthSampleGate requires that proof, tracking, positive image stamps,
   advancing frame/depth stamps relative to their own source, and at most250ms
   local receipt-to-summary processing time. Stop is terminal. No tolerance or
   cross-source ordering/equality/age calculation remains.
2. RawDepthCoverageStats records weighted coverage of all successfully decoded
   pairs, including later rejects and reprojected estimates. Immutable scalar
   totals are bounded at10000 pairs. Invalid counts cannot mutate state; stop
   is terminal. Missing observations yield null, observed zeros yield zero.
   Raw counts and non-rounded fraction strings keep sparse data visible.

The generic helper is a trusted-adapter structural contract, not protection
against malicious callbacks/reflection. Activity supplies SDK methods on that
one Frame under the existing GL/session lock; no Images escape the callback.
No function accepts a standalone timestamp to mint a proof.

## Tests first and division of responsibility

Codex wrote the active work list before dispatch. Cursor P owned pair/gate tests
and helpers; Cursor C owned coverage tests/helper. Both stopped after tests until
Codex recorded real compile RED. Cursor R independently reviewed design/source;
its external re-fetch was approval-blocked, so Codex fetched official contracts.
No worker executed Gradle, ADB or Git. All source integration/build/phone QA is
Codex-owned. Shared docs/Activity and unrelated dirty owner-rule were excluded
from worker writes. Worker reports identify their historical mid-fix snapshots.

Actual RED logs (ignored local evidence): coverage-red.log (20s) and pair-red.log
(8s), under outputs/reports/raw-depth-pair-20261020/. First source integration
failed: Kotlin enclosing object could not access its nested private constructor
and invalidate method. Codex retained private construction and moved the full
acquisition operation into the proof companion; outer scope delegates. No
forgeable mint(stamp), unsafe overload or test weakening was added.

## Interpretation and remaining gates

Admission means same-Frame processing only. Explicit output labels:
acceptance_scope=same_frame_processing_only, sensor_age=unknown,
coverage=observed_only, navigation=none, metric_accuracy=unverified.
Processing age is not physical sensor age. A first admitted estimate is a probe
baseline, not proof of a newly captured depth estimate. Subsequent admission
requires advancing depth stamps relative to previously admitted depth; observed
coverage includes duplicate/reprojected pairs and is not independent geometry.

Result schema2 names acquired_pair_attempts honestly (old new_depth_attempts
counted paired acquisitions, not proven new estimates). Observed totals and last
admitted fractions are separate. NO_DEPTH still means no admitted sample, even
when observed_decoded_pairs is positive; COMPLETE is diagnostic timer completion,
not an accuracy or safety pass.

Physical wall/mat/raised-edge/stair error, sufficient spatial coverage, sensor
age, valid-depth explicit Stop/background, sustained power/thermal load and
audible/TalkBack behavior require separate evidence. No CLEAR/turn or walking.

## Verification status

Focused48 tests PASS (12 scope+23 gate+13 coverage), then final offline gate
PASS at outputs/reports/quality-gate/20261020T014138822521Z/summary.json,
109.609s:460 app+10 simulator+19 policy+10 evaluation=499 tests,0 failure/error/
skip. Both APK builds, permission/model/debug-isolation policy pass. Lint has
0 fatal/errors,55 debug/34 release warnings (unchanged from0.9.26).
Source fingerprint (172 files):
abb7fdca6218631533454bc8e8aa8117d7943f9342f51e23cb9fc1bef7ab58b5.
Debug/staged/actual installed APK SHA256:
33be27f2d0cc247d31af4b2835537179b111e4626bee18357b3e589946ab3374.
Unsigned release APK SHA256:
1f6a22127e4989e45c8f2ecb198d108a42c3d706d3baba7b6b15eb0655397f83.
USB install-r succeeded without data clear; package0.9.27/40 and installed hash
verified. All earlier APKs preserved. R's corrected-source follow-up found no
remaining blocker; it did not execute builds or touch the phone.

### Redmi executed probe (owner-coordinated, stationary,20s)

Android14, Google Play Services for AR1.57.262640093. Owner confirmed rear lens
toward a lit furnished scene; no walking. Installed0.9.27/40, PID19530, start
09:45:00.582 MYT,20.16s observed. RESULT09:45:20.820, CLOSED09:45:22.031.

| Measurement | Actual result |
| --- | --- |
| Distinct camera frames | 553;521 TRACKING,32 PAUSED/NONE initialization |
| Acquired/decoded pairs | 142;160x90; D_16 strides320/2, Y8 strides160/1 |
| Diagnostic admitted pairs | 9 (not geometry/navigation acceptance) |
| Rejections | 113 no_valid_confident_pixels;20 depth_not_new |
| NotYetAvailable / invalid summary | 0 /0 |
| Observed pixel totals | 2044800 sampled;256473 nonzero depth;8416 confident |
| Observed weighted coverage | 12.5427% nonzero depth;0.411581% confident |
| Pairs with any confident pixels | 29/142; includes repeated/reprojected estimates |
| Latest admitted pair coverage | 81.5972% nonzero depth;3.18056% confident |
| Latest admitted depth distribution | median1072mm,p901258mm; NOT calibrated or a wall-distance measurement |
| Receipt-to-summary latency | p503.26ms,p903.95ms over9 admitted samples; not total camera/AI latency |
| Result / resources | COMPLETE/duration, followed by session_closed=true |

This exercises same-Frame pairing and observed coverage on device.113+20+9=142
pairs are accounted for; observed totals include rejected/reprojected pairs, not
independent geometry observations. First admitted sample is only a baseline;
physical estimate age remains unknown. Different scenes/tracking conditions mean
this is not a controlled accuracy A/B comparison with0.9.26. No ground-truth
distance/height was supplied or inferred.

Initial host log collection using date-string -T failed argument parsing; Codex
recovered the SAME completed probe's filtered process log from the buffer, without
rerunning or clearing system logs. Ignored evidence:
outputs/reports/raw-depth-pair-20261020/redmi-0.9.27-20s.log.
No RGB/depth files or audio recorded/uploaded. No paid service/network permission.
Explicit Stop/background while valid depth and sustained thermal/geometry tests
remain separate gates. Source/build/timer completion is not walking safety.

## Ranked next options

| Priority | Existing component / value | Required evidence | Deferred because |
| --- | --- | --- | --- |
| 1 | Official ARCore pose/intrinsics/planes for wall versus ground geometry | Accepted depth, pose/coordinate alignment, measured wall/floor/edge errors | A class label or sparse depth map is not calibrated traversability |
| 2 | Same-frame detector + segmentation + depth diagnostics | One camera owner, measured camera-to-result age/FPS/thermal, held-out failure scenes | Avoid competing CameraX/ARCore sessions and premature navigation fusion |
| 3 | Pretrained relative-depth alternative already researched | Licensed exact weights, camera benchmark and calibration limits | No model swap to conceal a proven instrument defect; relative depth is not metric distance |
| 4 | Cloud description or extra sensor | Separate privacy/payment approval and measured benefit | Local existing hardware first; no current authority or need to purchase/upload |
