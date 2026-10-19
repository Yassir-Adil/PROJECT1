# Combined observation preview — 2026-10-19

## Outcome and scope

The old object detector, yellow named boxes, and audio were not removed from
the main application. The isolated MOSAIC benchmark deliberately had neither.
An explicit debug-only combined mode now reuses the original presentation while
running object detection and wall/floor segmentation from one camera source.
The final candidate passed Redmi UI/control and bounded runtime checks. This
is an observation preview, not a navigation integration or a safety validation.

Two complementary additions:

1. Existing EfficientDet/MediaPipe objects and `DetectionOverlay` boxes beside
   the MOSAIC wall/floor diagnostic. Original `TriGuideTheme` charcoal/sand and
   yellow labels are reused, not replaced by another detector or design system.
2. Explicit **وصف صوتي** and **إيقاف** controls. Describe reuses the existing
   conservative `SceneDescription`, offline `AndroidVoiceSpeaker`, saved voice
   settings, and `VoiceGuidanceGate`. No periodic narration is added.

The default diagnostic remains segmentation-only and silent. Combined mode
requires `triguide.diagnostic.objects=true`. The DUMP-protected activity remains
in `src/debug`; no production screen, dependency, model, or manifest was changed
in this follow-up. MOSAIC output is not spoken, fused with boxes, or supplied to
`EvidenceFusionGate`/navigation. Independent observations are not a synchronized
multimodal evidence claim.

## Implementation and review

- `LiveSegmenterDiagnosticActivity`: keeps one `CameraXSource` and its sole
  ImageProxy-close ownership. Detector submission copies its input synchronously
  before the segmentation path uses that proxy. Native submission/teardown is
  serialized; the existing one-in-flight segmentation/receipt-age gate remains.
- `DiagnosticObjectPresentation`: hides stopped, stale, future, untrusted, and
  malformed results. Uses the existing score threshold and result cap; rejects
  out-of-frame boxes conservatively. Box age is measured since detector result
  completion, **not** camera capture. Empty valid detections never mean clear.
- `DiagnosticObjectCompanion`: no automatic utterances. Explicit requests honor
  saved quiet/speech/language/rate settings, offline-only voice selection and
  deduplication. Failed speech admission releases the gate. Stop/pause hides
  boxes and shuts speech down before waiting for native inference cleanup.
- `CombinedDiagnosticScreen`: shared preview coordinates for the reused overlay;
  min-56-dp controls, a diagnostic-only warning and no green path cue. The
  activity keeps the display on only while foreground, without OS setting edits.
- Found and fixed an actual display-clock race: a 100-ms cached expiry clock
  could precede a newly completed result, making a valid frame appear future-dated
  and intermittently disabling Describe/hiding boxes. The timer now only triggers
  expiry reevaluation; each result/render uses current monotonic time. The pure
  future-result rejection policy was not weakened. The previously missed Describe
  tap reached its handler and speech engine after this correction.

## Verification

Final build: `:app:testDebugUnitTest :sim-core:test :app:assembleDebug
:app:assembleRelease --no-daemon` succeeded in 19 s. **320 app tests, zero
failures/errors; 10 unchanged sim-core tests.** Nine added presentation tests
cover valid legacy boxes, expiry boundary, stopped/failed state, missing/negative/
future/non-real metadata, malformed geometry/confidence, conservative description,
stale description, empty-frame wording, and quiet/off/stopped speech policy.

Physical device: known Redmi serial `55aqpfqkvw9xbafe`, paired Wi-Fi. No walking
test or emulator. Final sealed candidate: SHA-256
`565B0145FAF6CC85894FF451C04045B4F54512E4BF03B2DF308E570ED0D38BED`,
`outputs/apk/TriGuideAI-0.9.21-combined-vision-clockfix-20261019.apk`.

| Final phone run | Result | Detector completions | Describe / accepted speech | Callback-to-map p50 / p90 |
| --- | --- | ---: | ---: | ---: |
| Combined, no click, 20.00 s | COMPLETE / duration | 214 | 0 / 0 | 42.85 / 52.61 ms |
| Combined, explicit Describe then Stop, 12.26 s | INTERRUPTED / user_stop, expected | 116 | 1 / 1 | 46.95 / 66.28 ms |
| Default silent, 8.00 s | COMPLETE / duration | 0 | 0 / 0 | 34.53 / 43.92 ms |

The first run delivered 575 analyzer callbacks and accepted 165 segmentation
results (163 measured after two warmups), 8.15 measured results/s under the
existing 10/s admission cap. Concurrent inference adds detector submission/input
work to callback timing. These short, uncontrolled scene runs are **not** an A/B
speedup claim, sustained thermal/power benchmark, capture latency or accuracy.

Actual screenshots confirm yellow labeled boxes, the reused theme, and the
changed description caption. Normal ADB taps reached Describe and Stop. The
control log contains `DESCRIBE_REQUEST origin=user speech_accepted=true`; its
three-second post-stop log contains no later `LIVE_SAMPLE` or `LIVE_START`.
Acceptance by TTS does not prove audible quality, language intelligibility,
volume, interruption latency, or TalkBack behavior.

Raw evidence is local under:
`outputs/reports/redmi-evidence/20261019-combined-1792394878282/verified-run-6-clockfix/`
including `summary.json`, process-filtered logs and before/after screenshots.
The activity logs `image_files=0`; these deliberate local QA screenshots were
taken by the test tools, not stored or uploaded by the application.

Earlier attempt folders are retained, not presented as passing runs. They include
PowerShell pull-progress/argument parsing errors in the local QA controller, a
user-interrupted run, a run with an explicit user Describe during a presumed
no-click interval, and UIAutomator's `could not get idle state` on the changing
live screen. The final check used centers grounded in inspected screenshots of
the same 1080x2400 portrait UI, verified the handler logs, and changed no phone
security settings. A pre-fix tap missed the disabled Describe control; Stop
already worked. Final post-fix controls and the silent regression all passed.

## Restoration and remaining gates

After testing, original **0.9.21 / versionCode 34** was reinstalled without
clearing app data. A fresh installed APK pull exactly matches the original:
`88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`.
The app is left stopped. This temporary preview is not the installed production
default, and its version was not promoted.

Release APK remains byte-identical:
`5B2D6B483624277B032F1F3FC3C10DF6DA928C6D820C31BDE0E62EC505C40540`.
No Internet permission, upload, cloud/billing service, training data, release
model assets, CLEAR, turn instruction, safe-path wording or navigation behavior
was added. No Cursor review is claimed for this follow-up; Codex owns the edits
and recorded checks.

Open: wrong/jittering COCO labels, overlapping/edge-clipped labels inherited
from the old overlay, caption timing versus moving objects, calibrated capture
age, long-run combined load/memory/thermal behavior, labeled Redmi accuracy,
heard audio and TalkBack. MOSAIC Top31 still lacks stairs and its known stair-as-
floor failure is unresolved. No connected walkable-region/free-space evidence
or independently proven alternate path exists. Keep HOLD/UNKNOWN; do not test
eyes-closed walking. See the earlier live and ADE20K reports for those limits.

Authorized-shell launch after deliberately installing this debug candidate:

```powershell
adb -s <current-paired-endpoint> shell am start -W `
  -n com.triguide.ai/.diagnostics.LiveSegmenterDiagnosticActivity `
  --ei triguide.diagnostic.duration_seconds 20 `
  --ez triguide.diagnostic.objects true
```

Omit the objects extra for the original silent benchmark. Recheck the current
paired endpoint and exact device serial; the connection port can change.
