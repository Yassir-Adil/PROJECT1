# Reconnected Redmi: inventory and zero-depth diagnosis — 2026-10-20

## Actual baseline, no navigation changes

Installed0.9.29/42 was read back from Redmi. Source68aa9b5; prior572-test gate
and APK hash verification are recorded in REUSE_EVALUATION_2026-10-20.md, not
re-run at the start of this phone-only check. USB transport50 briefly disappeared
before a new probe launched;51 returned. Windows reports ADB/Redmi USB devices
OK; scoped mDNS discovery found no wireless endpoint. No server reset, driver/
security changes, new pairing, reboot, lock bypass or image recording/upload.
External untracked research/10-reuse-licence-check.md and dirty owner-rule are
preserved; external review suggestions are not task instructions or verified
licence/performance evidence by themselves.

## Two actual0.9.29 runs

| Measurement | First instructed30s | Owner-ready lateral-motion30s |
| --- | ---: | ---: |
| App PID |29743|32107|
| Start, local MYT Oct20 |10:58:29.395|11:01:49.421|
| Final RESULT |10:58:59.785|11:02:19.779|
| Session CLOSED |10:59:01.059|11:02:21.031|
| Elapsed seconds |30.32|30.30|
| Distinct camera frames |859|869|
| Tracking / nontracking |828 /31|836 /33|
| Decoded D_16/Y8 pairs |222|227|
| Raw valid / confident pixels |0 /0|0 /0|
| Raw admitted samples |0|0|
| NotYetAvailable / invalid summaries |1 /0|1 /0|
| Inventory samples / known planes |29 /0|29 /0|
| Vertical / upward ray-hit samples |0 /0|0 /0|
| Background draws, NOT camera FPS |1740|1758|
| Session.update p50/p90 ms |1.60 /3.45|1.65 /3.33|
| Surface query p50/p90 ms |1.08 /2.00|1.03 /1.76|
| Raw outcome |NO_DEPTH/duration|NO_DEPTH/duration|

The second run followed explicit owner readiness for slow side-to-side phone
translation while standing, without walking. That is owner-reported positioning,
not measured translation, lighting or physical calibration. Battery snapshot
before it:89%,37.2C; not an energy/thermal benchmark. No frames or coordinates
were captured. Both results and CLOSED are observed, not inferred from a timer.
Filtered bounded logcat captures retained privately under
`outputs/reports/phone-inventory-20261020/`. No global log clearing.

## What is and is not established

All decoded depth pixels were0 **before** confidence threshold128 is considered;
lowering confidence cannot recover absent geometry.29 inventories include
`status=observed inspected=0`, so four-ray misses are not the sole explanation:
the session inventory itself was empty at those samples. No claim the physical
scene contained no wall, nor that current geometry is sufficient for navigation.

Independent bounded Cursor Z source audit found no matching reproduced config,
stride, hardcoded-zero, provenance or lifecycle defect. Codex separately inspected
the actual source and the SDK log. This is not proof the SDK or scene is healthy.
Current runtime lacks applied-config and measured-camera-translation telemetry,
so physical motion/texture/service causes cannot yet be separated conclusively.

Google documents [depth from motion](https://developers.google.com/ar/develop/depth),
and [raw depth](https://developers.google.com/ar/develop/java/depth/raw-depth)
can be sparse on low-texture surfaces. Those are plausible failure modes, not
proof of this run's cause. [DepthMode](https://developers.google.com/ar/reference/java/com/google/ar/core/Config.DepthMode)
supports diagnostic AUTOMATIC comparison; raw remains available in that mode.
Smoothed depth adds compute and has no matching per-pixel confidence contract.

## Next bounded diagnostic pair

Tests first for bounded primitive SDK-pose displacement stats and a depth-only
counter reusing the existing16bit decoder/validation (never fake confidence).
Codex integrates applied-config readback and opt-in AUTOMATIC comparison.
RAW_DEPTH_ONLY remains default; raw pairing/confidence/admission gates unchanged.
No movement readiness from pose delta, no smoothed-depth navigation authority,
model/runtime/cloud/permission changes or sensor purchases. No third identical
owner probe requested before instrumentation can answer a new question.

## Implemented follow-up —0.9.30/43

Two bounded Cursor M/D builders wrote tests first. Codex compiled them before
source: actual RED exit1 for missing CameraMotionStats/Snapshot and
summarizeDepthOnly. Then54 focused tests passed. Codex added a finite1e200 motion
regression; it failed with `invalid_pose` under intermediate-square overflow.
Replacing `sqrt(dx*dx+...)` with nested `hypot` fixed that edge without accepting
nonfinite inputs. Final55 focused cases are included in the full gate:
18 motion +37 depth statistics (20 preserved raw tests +17 new depth-only).

- Motion: bounded10000 tracked poses; timestamp/finite checks, segment reset on
  tracking loss, immutable scalar snapshots, terminal stop. Max displacement
  from segment origin is NOT path length, verified physical movement, sufficient
  parallax or navigation evidence. Raw coordinates are not logged/persisted.
- Reused16bit decoder/stride validation now supports depth-only counts. No
  synthetic confidence, percentiles, admission or navigation authority.
- Debug `triguide.diagnostic.depth_mode` accepts RAW_DEPTH_ONLY (default) or
  AUTOMATIC only; support and applied SDK config checked. Config log reports
  facing/dimensions/modes, and cloud/geospatial readback must stay DISABLED.
- AUTOMATIC optionally acquires smoothed depth on the same current frame at
  the existing≤10Hz slot. Images close on all paths; bounded600 sample counters
  and last600 latency observations. Counts include reprojections, no freshness
  or confidence inferred; raw proof/confidence128/admission remains separate.
- Empty-map logs now distinguish all-zero depth from low-confidence data.
  Requested mode is visible in status; instructions explicitly keep feet still
  and move the phone sideways, not merely rotate it. Unexpected dense failures
  terminate with `dense_process_*`, not misleading `gl_frame_*` classification.

Independent Cursor Z reviewed actual source and final polish: no P1 found.
Optional softer handling of invalid dense maps/live dense UI remains deferred;
we intentionally terminate an invalid diagnostic rather than silently succeed.
No new dependency/model/permission/Internet/cloud/primary guidance behavior.

Final gate: `outputs/reports/quality-gate/20261020T031404611070Z/summary.json`.
PASS,551 app+10 simulator+19 policy+27 evaluation=607 tests,0failure/error/skip;
both builds/APK policy pass, Lint0errors55/34warnings unchanged. Source SHA:
`317823170f12eaa2bcdc4d2ec952ad069bce62073f4262301ee05dfe637a449d`.
Installed/staged/debug APK exact SHA:
`4c71595f4e1a5f42a9f386da74a0476ba1a0e814a0bcf1c887346abb05cd209f`.
Install used `-r`, no app data clear. Invalid mode smoke on actual Redmi PID9577:
FAIL/invalid_depth_mode,0 frames/draws/depth, no Session created; not a scene test.

## Host connection interruption and bounded recovery

During development Windows still listed USB/ADB interfaces while `adb devices`
was empty. `server-status` initially returned unknown host service. After checking
only the target phone/no other connected device and only one adb server process,
Codex revised the no-reset plan for one controlled host adb-server restart. The
server then identified SDK37.0.1 at the configured platform-tools path, and the
phone returned without new key approval. No driver, permanent PATH, security,
phone reboot or account changes. **This did not establish a durable fix:** ADB
later disappeared again; Windows no longer listed the prior composite/ADB set.
No cause was conclusively assigned to cable, phone USB mode or host driver.
No second reset or driver change attempted. Owner unplugged/reconnected and
unlocked the phone; actual0.9.30 comparison could then launch. All log captures
have8s host timeout and kill only that capture if the device disconnects.

## Actual owner-ready0.9.30 comparison — COMPLETE and CLOSED in both modes

Owner confirmed readiness for two25s tests on the same lit floor/furniture view,
slow lateral movement, standing/no walking/no Back. First comparison attempt
failed the prelaunch device check: **neither mode ran**. After owner reconnected
USB/unlocked, both runs actually completed. Installed APK SHA matches the final
gate. Fresh process per mode, no app data clear, no image/coordinate recording.

| Measurement | RAW_DEPTH_ONLY | AUTOMATIC |
| --- | ---: | ---: |
| PID |10779|11481|
| START, MYT Oct20 |11:18:38.382|11:19:08.482|
| RESULT |11:19:03.626|11:19:33.718|
| Session CLOSED |11:19:04.649|11:19:34.730|
| Actual elapsed seconds |25.17|25.18|
| Distinct camera frames |702|722|
| Tracking / nontracking |670 /32|688 /34|
| Raw decoded pairs |184|189|
| Raw admitted diagnostic samples |15|42|
| Empty raw confidence/depth rejects |98|33|
| Repeated-depth rejects |71|114|
| Raw observed valid pixels |1114443 /2649600|1274627 /2721600|
| Raw observed valid fraction |42.0608%|46.8337%|
| Raw observed confident pixels |5320 /2649600|448331 /2721600|
| Raw observed confident fraction |0.200785%|16.473067%|
| SDK max segment displacement |0.225066m|0.408565m|
| Tracked motion samples / segments |670 /1|688 /1|
| Smoothed maps decoded |disabled|189|
| Smoothed nonzero pixels |not observed|2260800 /2721600 (83.0688%)|
| Smoothed confidence / admission |none /none|unavailable /none|
| Surface inventory samples |24|25|
| Samples with known tracking upward planes |0|18|
| Samples with upward ray intersections |0|13|
| Known vertical/ray vertical samples |0 /0|0 /0|
| Session.update p50/p90 ms |1.39 /2.24|1.42 /2.64|
| Raw receipt→summary p50/p90 ms |1.55 /2.83|2.03 /3.32|
| Surface query p50/p90 ms |0.58 /2.81|1.08 /4.11|
| Dense processing p50/p90 ms |not observed|0.94 /2.22|
| Outcome |COMPLETE/duration +CLOSED|COMPLETE/duration +CLOSED|

Config readback in each process confirms the requested mode actually applied,
planes HORIZONTAL_AND_VERTICAL, focusAUTO, updateLATEST_CAMERA_IMAGE, facingBACK,
CPU image640×480, texture1920×1080, cloud/geospatial DISABLED. Raw D_16/Y8
dimensions/strides match160×90 /320/2 and160/1. No malformed-summary or dense
invalid errors. Later `pidof com.triguide.ai` returned no PID after scoped stop.
Host ended with the same Redmi online (transport3), no other device used.

One real AUTO sample at11:19:16.191: center upward ray2.1371531m, lower-right
upward ray1.0941515m, polygon/tracking/subsumption filtered. Last inventory:
4 inspected,2 tracking upward and2 subsumed;0 vertical. These are SDK estimates
along rays, not wall identity, floor/table discrimination or calibrated clearance.

**Interpretation:** this phone genuinely produces nonzero raw depth in both
modes; empty input must not be accepted as a successful geometry measurement.
AUTO showed more raw confidence/upward observations on this run, but it was
second, SDK pose displacement was greater, and the exact physical viewpoint/
ground truth was not controlled. This is **not a causal mode A/B, accuracy win,
or permission to change the default**. No dense field participates in raw admit
or main guidance. Reprojected samples count repeatedly in observed pixel totals;
fractions are not independent spatial coverage. Pose distance is uncalibrated.
Short processing latencies exclude full SDK inference/thermal/energy costs.
The two earlier zero-depth runs' single root cause is still unproved.

Private filtered log SHA256:

- RAW: `c02047d1a42af7c4d0e6692f40c5488f885ebd303d3ae6d47e2b734e2a0317fc`
- AUTO: `03bf962fc5d5fbf7d652eb59288d1faf8a808630e9f1e19274234dae1719eb71`

## Next gate and handoff

No more owner action required this round. Keep RAW_DEPTH_ONLY diagnostic default;
no new sensor/model/runtime purchase or main navigation switch. Prioritize tiny
public held-out RGB-D geometry/coordinate checks and observed ground-reference
versus tabletop/vertical-surface assessment. No vertical plane was demonstrated,
no known rug height/stair edge/metric safety labels, no sustained energy test,
real voice/TalkBack or eyes-closed walking acceptance. All workers finished.

Coordinate warning from actual readback: depth160×90 matches the GPUtexture's
16:9 aspect, not CPUimage640×480 (4:3). A future depth/world adapter must use
the applicable SDK intrinsics/transforms; directly scaling CPU intrinsics onto
this depth map is not justified. Preserve this as an explicit regression gate.
