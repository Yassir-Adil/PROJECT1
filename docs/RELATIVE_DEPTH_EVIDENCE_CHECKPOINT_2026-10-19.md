# Relative-depth evidence checkpoint — 2026-10-19

## Scope

This checkpoint records local policy evidence only. It does not claim that the
phone can safely guide a person, and it does not authorize walking, `CLEAR`, or
turn instructions. The camera path remains fail-closed when positive walkable
area evidence is absent.

## Deterministic policy benchmark

The local `RelativeDepthBenchmarkHarness` contains nine synthetic cases:

- empty input
- normal floor-like gradient
- center wall
- possible drop
- stale frame
- future frame
- malformed grid
- non-finite value
- out-of-order frame

The benchmark is deterministic and has no CameraX, network, model asset,
navigation, speech, image, or location dependency. It reports policy outcomes
as stable TSV for CI/replay comparison. The expected result is 9/9 passing,
with two risk-raising diagnostics and no clearance state.

## Redmi static baseline

Command used:

```text
scripts/test/redmi-evidence-baseline.ps1 -SceneId closed-door-wall -DurationSeconds 20
```

Observed result on the connected Redmi Note 12 Pro 5G:

| Metric | Result |
|---|---:|
| Samples | 162 |
| Approximate FPS | 8.1 |
| Latency p50 | 73 ms |
| Latency p90 | 123 ms |
| Navigation `HOLD` log lines | 0 |
| Navigation `UNKNOWN` log lines | 162 |
| `CLEAR` or turn log lines | 0 |
| Voice request/log lines | 0 |
| Battery after run | 51% |
| Temperature after run | approximately 32.3°C |

The repeated diagnostic reason was `no_usable_detections`; the UI remained
`Path not confirmed` and labeled the overlay as diagnostic only. This is a
static evidence run: no walking and no audible-output claim.

Artifacts:

- `outputs/reports/redmi-evidence/closed-door-wall-20261019-102632/summary.tsv`
- `outputs/reports/redmi-evidence/closed-door-wall-20261019-102632/filtered-logcat.txt`
- `outputs/reports/redmi-evidence/closed-door-wall-20261019-102632/start.png`
- `outputs/reports/redmi-evidence/closed-door-wall-20261019-102632/end.png`

After installing the rebuilt `0.9.20` debug APK, the same stationary run was
repeated without changing the scene. The second run produced 206 samples at
approximately 10.3 FPS, with 71 ms p50 and 88 ms p90 latency. All 206
navigation observations remained `UNKNOWN`; there were zero `CLEAR`/turn and
zero voice events. Battery was 50% at 3,841 mV and 33.6°C. The captured process
reported 204,029 KB total PSS and 344,360 KB total RSS.

Second-run artifacts:

- `outputs/reports/redmi-evidence/closed-door-wall-20261019-103036/summary.tsv`
- `outputs/reports/redmi-evidence/closed-door-wall-20261019-103036/filtered-logcat.txt`
- `outputs/reports/redmi-evidence/closed-door-wall-20261019-103036/start.png`
- `outputs/reports/redmi-evidence/closed-door-wall-20261019-103036/end.png`

## Validation after policy hardening

- Android unit tests: 283 passing, 0 failures.
- Simulation-core tests: 10 passing.
- Debug APK build: successful.
- Installed debug APK: `android-app/app/build/outputs/apk/debug/app-debug.apk`
  (`0.9.20`, SHA-256
  `9D6C01485BB398970D3402B486F94BB7002550A70D19B84C6B79323DD7E90564`).
- The cadence gate now exposes explicit diagnostic reasons for `INVALID_CLOCK`,
  `CLOCK_ROLLBACK`, `TOO_SOON`, and `ACCEPTED`, while preserving the existing
  boolean admission helper.
- Malformed, future, stale, and descending evidence remains unable to produce
  a positive clearance result.
- Audio policy tests verify that a neutral startup tone is suppressed whenever
  the session is stopped/not running, and that a critical obstacle cue
  interrupts lower-priority observation cues.

## Next evidence gate

Do not add a depth model, network permission, cloud API, or navigation behavior
change until consented labeled scenes are available and the candidate model's
license, hash, input/output contract, and Redmi latency are recorded. Any
future relative-depth result may raise diagnostic risk, but must not lower an
existing detector hold or establish a walkable path by itself.
