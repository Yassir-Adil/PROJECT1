# Redmi Raw Depth follow-up — 2026-10-20

Later controlled test exposed a source format bug;0.9.25 fixes it and reports
explicit SDK INSUFFICIENT_LIGHT in a full-duration run. See
`docs/RAW_DEPTH_D16_FIX_2026-10-20.md`; the interrupted attempts below are history.

## Result: interrupted, not a depth/accuracy pass

The owner unlocked the phone. Codex recovered an offline USB transport with a
targeted reconnect of the known Redmi only. No global ADB restart, driver change,
debug-security bypass, Wi-Fi enable or TCP5555 exposure. USB later dropped again;
Windows reporting the device/interface OK is not proof of stable transport.

Installed APK SHA was rechecked directly on the phone and still equals the
accepted0.9.23/36 hash:
`3029bc642888e6ea20cccd28195156e22037ca23382d3988ac532679f1d5db58`.
No product source, model, permission or safety policy was changed this round.

## Actual attempts (phone-local times)

| Attempt | Duration requested / observed | Distinct camera frames | Tracking / accepted depth | Outcome |
| --- | --- | --- | --- | --- |
| 08:51:30 | 20s /15.49s | 426 | 0 /0 | INTERRUPTED, reason=paused |
| 08:54:06 | 20s /0.76s | 0 | 0 /0 | INTERRUPTED, reason=paused; CLOSED at08:54:08 |

- First attempt received camera timestamps, but every frame was nontracking:
  depth acquisition was correctly not attempted. No depth distances, confidence
  coverage or receipt-to-summary percentiles are available.
- Both attempts ended before the timer, not `COMPLETE` or a full-time `NO_DEPTH`.
  Activity events show leaving/finishing the probe. Back events and a later
  MainActivity launch are present. The logs alone do not identify whether input
  came from a person or another controller; do not blame a model for interruption.
- A later app-process kill cites a debug-app setting change. This is interference
  evidence, not proof that it caused the initial lack of tracking. The current
  debug-app setting was read, not changed by Codex.
- First attempt: camera-service history shows disconnect on pause, but an
  explicit `RAW_DEPTH_CLOSED` was not observed. Do not claim Session close proven
  for that run. Second attempt: exactly one RESULT followed by CLOSED; no later
  SAMPLE in retained probe-tag logs. This does not stress late *valid* data.
- No own-process AndroidRuntime fatal appeared in the checked first-run filter.
  SDK native warnings are not treated as a diagnosis of sensor/depth failure.

Private evidence remains ignored under `outputs/reports/cursor-20261019-next/`:
`raw-depth-20s-20261020.log`, `raw-depth-20s-20261020-after-close.log`,
`raw-depth-20s-retry-20261020.log`, `raw-depth-20s-retry-final-20261020.log`.
Only this redacted summary is eligible for public source backup, not raw logs.

## Next controlled measurement

Coordinate a20s foreground interval without Back/Home/another debugger or controller.
The probe intentionally shows metrics on a dark surface, not camera preview.
Owner remains stationary, with small slow hand motion toward room detail if
needed; no walking or approach to stairs/obstacles. Keep the real Stop available;
do not disable pause/stop guards to force a completed test.

If a full interval still has no tracking, record the SDK's explicit tracking
failure reason before choosing a remedy. Official reference:
https://developers.google.com/ar/reference/java/com/google/ar/core/TrackingFailureReason .
Do not infer light/motion/texture/sensor causes from `nontracking` counts alone.

Valid depth, confidence coverage, distance/height error, wall/mat/step accuracy,
main UI regression, heard voice and TalkBack remain unverified in this follow-up.
The previous463-test desktop gate remains the last executed full gate; it was
not rerun merely for this docs-only device-result record.
