# Redmi wireless recovery and post-fix static probe — 2026-10-19

**Status: PASS_WITH_WARNINGS.** Modern wireless ADB pairing, APK backup,
temporary diagnostic installation, three static batches, and exact original
APK restoration completed over Wi-Fi. This is not a live-camera or walking test.

## Connection recovery

- USB was absent from both Windows' present-device list and ADB. The saved
  Redmi USB device record said disconnected; its driver was installed.
  An isolated fresh ADB server also found no USB device. A Windows device
  rescan was denied by the non-elevated OS token; it was not completed.
- The owner enabled Wireless debugging and supplied a temporary pairing code.
  USB briefly returned, allowing a local screenshot to supply the current
  pairing endpoint. Modern `adb pair` succeeded. The temporary pairing-screen
  capture was removed from the phone and local report directory; the code is
  deliberately not recorded here.
- Initial wireless transfer successfully saved the original 88,732,901-byte
  APK. Both transports then disappeared before diagnostic installation. That
  installation attempt failed with `device offline`; it changed no APK.
- A fresh discovery-only ADB server on unused local port **5038** discovered
  the known serial's `_adb-tls-connect._tcp` service after four seconds.
  Its endpoint had changed. Connecting the normal server to that discovered
  endpoint succeeded without another code. The temporary server was stopped;
  the pre-existing port-5037 server was neither killed nor replaced.
- All subsequent installation, replay, logs, and restoration used Wi-Fi with
  USB absent. Identity was checked with `ro.serialno=55aqpfqkvw9xbafe`.
  The final wireless `get-state` returned `device`.
- No legacy unencrypted `adb tcpip 5555`, firewall changes, driver removal,
  authorization revocation, or app-data clearing was used. The failure's
  physical USB root cause remains unproven; wireless is a verified workaround.

If a future connection fails, discover the **current** connect endpoint rather
than repeatedly using an old screenshot's port. Pairing and connect ports are
different; an endpoint change alone does not require new pairing. Use only
services whose instance identifies the known Redmi serial. Follow the official
[Android wireless debugging workflow](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi).

## APK and method

- Diagnostic candidate:
  `outputs/apk/TriGuideAI-0.9.21-segmenter-diagnostic-input-fix-20261019.apk`.
- Candidate SHA-256:
  `DC0BE8CED37F89C1771D3EFC58B09870252E01E962CB354DFA1F8C833BD9E567`.
- Original and restored APK SHA-256, both verified from installed APK pulls:
  `88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`.
- Final version: **0.9.21 / versionCode 34**. Original installed bytes restored;
  no application data was cleared. The app was left stopped, not guiding.
- Existing 1440x2560 image asset, centered gray-128 aspect-fit RGB canvas,
  512x512 tensor, four interpreter threads, two warmups per batch. Input was
  prepared once; only synchronous `Interpreter.run` calls were timed.
- Each batch used a newly stopped/started process and PID-filtered logcat.
  Global logcat was not cleared. Every batch completed with valid INT32 maps
  and `VOICE_EVENTS=none`; no `RUN_FAILED` occurred in the three batches.

| Model / backend / encoding | Runs | p50 ms | p90 ms | Mean ms | Sequential inference/s |
| --- | ---: | ---: | ---: | ---: | ---: |
| MOSAIC / NNAPI / reference-rgb | 1000 | 17.06 | 21.14 | 17.75 | 56.35 |
| MOSAIC / NNAPI / normalized-quantized control | 20 | 17.56 | 19.48 | 18.44 | 54.23 |
| DeepLab UINT8 / CPU / reference-rgb | 20 | 736.88 | 744.79 | 738.98 | 1.35 |

MOSAIC delegated **100/100 nodes in one partition** in both batches. Its driver
also logged an unsupported diagnostic-callback registration warning; this did
not prevent inference, but driver diagnostic callback support was not verified.
DeepLab UINT8 used XNNPACK, delegating 73/77 nodes across eight partitions.

These are sequential repetitions of one prepared still, not live-camera FPS,
capture-to-result latency, labeled phone accuracy, or evidence of safe walking.
The unequal batch sizes and uncontrolled thermal state do not establish a
statistically significant speed change between MOSAIC input encodings.
The canvas is historical diagnostic geometry, not the dataset evaluator's
reference-derived top-left padding recipe.

The raw-input MOSAIC output called 52.05% of the full canvas wall and 22.91%
floor, but also predicted curtain, sky, cabinet, door, window, and person.
Its lower-center floor fraction was 97.13%: **a fraction of pixels, not model
confidence or traversability**. There is no ground-truth mask for this still.
The published-data stair failures remain open and prohibit floor-only clearance.

Point observations across the three batches: battery 39% -> 38%, reported
battery temperature 35.4 C -> 35.6 C. Final **DeepLab process** PSS was
120,119 KiB and RSS 243,832 KiB. These are not peak/MOSAIC memory, controlled
power-drain, throttling, or long-duration thermal measurements.

## Evidence and next gate

Raw logs, both original/restored APK pulls, and `summary.json` are saved in:
`outputs/reports/redmi-evidence/20261019-postfix-1792392212884/`.

No Kotlin, Gradle, model, production navigation, or application network
permission change was made in this recovery/test session. The sealed candidate
had already passed 291 app tests, 10 sim-core tests, and 10 evaluator tests;
those suites were not rerun for this phone-only validation.

The corrected raw UINT8 input and actual INT32 decoder are now phone-validated
for static replay. Next is a separate controlled camera/capture-to-result
diagnostic and independent depth/step evidence. No CLEAR, turn advice, or
floor-only movement decision is authorized by this result.
