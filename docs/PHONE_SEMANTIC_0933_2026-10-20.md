# Redmi0.9.33 semantic/raw-depth verification —2026-10-20

## Verified scope

Owner connected/unlocked Redmi, then explicitly confirmed a lit stationary
scene with floor in lower half. Version0.9.33/46 installed via USB, baseAPK
SHA256 matched `4eeeac171d0587988b2cb093ca896cdb2c8dd47ea25cd9f21420107ccb27dfcb`.
Source216file fingerprint matched prior797-test/build gate exactly before install.
No model/permission/source change was needed for this run.

Actual20.16s probe:MYT18:11:53start→18:12:13result→18:12:14closed,PID24201.
AUTOMATIC depth plus opt-in semantic_fusion; raw-only/semanticsOFF default unchanged.
CameraCPU640x480,GPU1920x1080,depth160x90,viewport1080x1292;backcamera,
autofocus,cloud/geospatialDISABLED. Filtered aggregate logs only, no RGB/depth/
audio/mask recording. Scoped force-stop afterwards verified no appPID.

Private ignored log `outputs/reports/semantic-0933-phone-20261020-181150/phone.log`:
SHA256 `bba9be0bc9f470f11096649a073a29366bbb4cd454448dfc5a331b71b6190755`.

## Actual observations

- COMPLETE+CLOSED:561distinct frames,542tracking/19nontracking;33rawadmits,
151decodedpairs,1rawNotYetAvailable.111depth_not_new/7empty rejections retained.
- **13 fresh semantic results,0stale,0floor-reference candidates**; accepted
session rate0.644750/s, NOT cameraFPS. Every sample SDK-derived clockwise turn1
and unmapped_cells0;1–25 raw3D grid points,0–6connected floorgeometry supports.
- Twelve insufficient_support, one no_floor_component. One reached6supports,
but the old status does not distinguish span/area/horizontality rejection. No
points retained, so that exact reason cannot be reconstructed after the run.
Do not lower thresholds or manufacture a reference to hide this uncertainty.
- Wall-like labels19–42/64, floor-like9–18/64,rug0–3/64; labels are not annotated
accuracy. Heightunknown64 every sample.0vertical SDKplane inventory/rays.
Upward17/19inventory/height and16/19ray samples are orientation only.
- Existing3x3 projection13samples/8finiteworldpoints; dense151maps with no
confidence/authority. SDK maxsegmentdisplacement0.131960m, not calibrated motion.
- No semantic-failure/disabled/AndroidRuntime crash marker in scoped logs.
Semantic interpreter,GLbackground and rawsession closure markers all present.

| Local processing stage | p50ms | p90ms |
| --- | ---: | ---: |
| Semantic receipt→result (13 attempts,allaccepted) |61.137769|84.951308|
| GL acquire/copy/map |1.776615|6.983385|
| Worker preparation incl queue/YUV/bitmap/upright/letterbox |27.947385|49.511769|
| Model invocation |24.641846|31.123077|
| SDK session.update |1.33|3.92|
| Raw receipt→summary |1.38|5.08|

Across samples, receipt→result44.59–229.68ms. These are local processing durations,
not absolute sensor age.0.9.32 earlier p50receipt165.96ms/workerprepare118.64ms
versus0.9.33 observed61.14/27.95ms, but scene,device temperature,SDKmotion and
cache/warmup differ. This is an observed cross-run difference, NOT controlled
causalA/B or an isolated converter/model accuracy benchmark.

End process samplePSS202917KiB (~198MiB),RSS344924KiB,notpeak/leak/longrun.
Battery79% both,charging;temperature32.2→31.7°C,not energy/thermal efficiency
proof from20s. No walking/TalkBack/audio/obstacle/safe-path validation.

## Owner visual result and next bounded repairs

Owner confirms **natural/upright live image**, but **did not see coloured cells**.
The log proves inference acceptance, NOT main-thread publication/canvas/compositor
or human visibility. At1Hz cadence, cells expire250ms after receipt (typically
~165–190ms available after inference), with subtle fill. Brief/faint rendering
is a plausible explanation, not a proven diagnosis. Do not extend stale live
overlays to manufacture visibility. No screenshot/photo upload was requested.

0.9.34 diagnostic follow-up adds tests-first publication→Canvas counters (not
human-visibility claims), first-draw timing, and precise ground rejection reasons.
Thresholds, freshness, model and production navigation unchanged. Need actual
new candidate gate and fresh owner setup before another camera run.
