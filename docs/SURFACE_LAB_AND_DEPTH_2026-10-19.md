# 0.9.22 surface lab and depth capability — 2026-10-19

## Delivered

**0.9.22 / versionCode 35 is installed and retained on Redmi via USB**, data
preserved and guidance left paused. Installed APK pull exactly matches candidate
`outputs/apk/TriGuideAI-0.9.22-debug.apk`, SHA-256:
`05E3BCBBB8DF465CD8B91CD1DE3C89F19605EC5A3E5F300DDD48B16A3C5473CA`.

Original 0.9.21/34 backup remains at
`outputs/reports/redmi-evidence/20261019-surfaces-depth/installed-before-0922.apk`,
SHA `88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`.
Failed QA cycles rolled back before correction/retry. Final state is the updated
app, not the restored original. Nothing was published or pushed.

Entry: **More → فحص الجدار والأرضية · 60 ثانية**. This is an explicit debug lab,
not the default navigation stream. Original 0.9.21 baseline showed UNKNOWN;
there was no labeled rug scene proving the cause of every reported false alert.

## Changes

- Reused pinned MOSAIC, EfficientDet, one CameraX source and original boxes/theme.
  The square model map is unletterboxed to a bounded 64-square source-space grid,
  projected with existing center-crop/mirror geometry. Muted blue wall, amber rug,
  gray floor; other classes transparent. No green path cue. Display updates at
  most 4/s, expires after 1 s since completion, and clears on Stop. Argmax is not
  confidence. Tiny display bitmaps are GC-owned, not recycled while GPU-retained.
- Explicit offline Describe can cautiously mention surfaces, unmeasured rug-edge
  height and unresolved steps. Empty object labels never append “Nothing obvious
  ahead” to a predicted wall. Existing language/quiet/rate settings are preserved.
  No periodic semantic speech, movement command or mask-to-navigation connection.
- Live priority uses actual risk: possible is not confirmed blockage; unknown
  HOLD is not an obstacle assertion. No risk thresholds or stopping policy changed.
- Saveable navigation restores paused; Main restoration also starts paused.
  Lab uses an activity-result callback returning explicitly to paused More.
  Variant-specific SceneLabLauncher is unavailable/no-intent in release.
- Separate DUMP-protected depth capability activity; debug ARCore SDK **1.57.0**
  checked against Google Maven and the official SDK release. No session resume,
  install prompt, cloud configuration, API key, depth frame or image acquisition.

## Phone depth result

Installed Google Play Services for AR: **1.57.262640093**. Final APK logged:

`DEPTH_CAPABILITY status=CHECKED availability=SUPPORTED_INSTALLED automatic=true raw=true frames=0 camera_resumed=false metric_accuracy=unverified navigation=none`

This confirms an available software depth route, not a physical ToF sensor,
actual height/distance accuracy or reliable rug/step detection. Google service
terms/privacy apply. The next experiment must test Raw Depth/confidence and not
compete with CameraX for camera ownership. Full free-first roadmap and sources:
`docs/WALL_RUG_DEPTH_ROADMAP_2026-10-19.md`.

## Verification

Final build succeeded in **53 s**: **339 app tests, zero failures/errors; 10
unchanged sim-core tests**, debug/release assembly. Nineteen new tests since
320: fourteen surface projection/expiry/taxonomy/narration/malformed-input tests,
three risk-presentation tests and two fail-closed navigation restoration tests.

| Phone run | Outcome | Detector / accepted segmentation results | Callback-to-map p50 / p90 |
| --- | --- | ---: | ---: |
| Pre-lifecycle candidate, More entry, 60.00 s | COMPLETE; no Describe/speech | 688 / 510 | 42.33 / 52.46 ms |
| Final candidate, real More → Describe → Stop, 10.40 s | Expected INTERRUPTED / user_stop; one speech accepted | 91 / 82 | 50.11 / 70.40 ms |
| Final candidate, silent default, 8.00 s | COMPLETE; detector/speech absent | 0 / 54 | 29.55 / 31.87 ms |

Final UI check verified result callback to More then Android Back to **Guidance
paused**, no sampled live/surface results after Stop. Screenshots show regions,
boxes and revised captions. TTS acceptance is not heard-audio proof. Scenes and
camera rates differ: no speedup/FPS/accuracy claim. Capture age and UI/audio delay
are not in callback-to-map time. No labeled rug-height or walking test was run.

One pre-lifecycle snapshot with Main services resident reported 290,400 KiB PSS
and 419,548 KiB RSS; not a memory peak/leak/thermal/power benchmark. Top31's known
stairs-as-floor limitation remains open. Actual activity recreation was not
forced: restore is unit-tested, real entry/return/Back is phone-tested.

## Failed attempts, isolation and limits

A mixed direct-ADB/UI attempt did not prove a paused return. It did not isolate
launch-context versus recreation as the cause. Code review found non-saveable
navigation; saveable paused restore and explicit result handling now defend the
real product route. Two later harness assertions falsely rejected paused More
because its LazyColumn showed different scroll positions. Both XMLs and result
callback logs show More. The corrected final check accepts either valid viewport
and independently checks Back. Failed artifacts are retained; no independent
Cursor review or edit is claimed in this follow-up.

Raw directory: `outputs/reports/redmi-evidence/20261019-surfaces-depth/`.
Authoritative final evidence is **`final-v3/summary.json`**, logs, XML, screenshots
and installed APK pull. `final/` and `final-v2/` retain failed harness assertions.
The app records no image files; local QA screenshots came from the test tools.

Both merged manifests lack INTERNET/ACCESS_NETWORK_STATE. Release ZIP contains
zero ARCore/diagnostic model entries and only the existing EfficientDet TFLite.
Release bytes changed for version/copy/lifecycle code; release SHA:
`34C42542C68F4E1DF7D77EAA12C82C12C158A5452260F068009054FE1582E05F`.
No release APK installed or published. Production floor seam remains UNMAPPED;
its status text describes navigation, not the separate debug lab's model.

Open: wrong/jittering labels, edge clipping, TalkBack/heard audio, sustained
memory/thermal load, depth confidence/geometry, rug folds, stairs and glass.
No CLEAR, turn, safe-path tone, paid API or hardware purchase was enabled.
Do not remove stops merely because something is called rug/floor or attempt
eyes-closed walking. The next gate is measured Raw Depth, not another blind model swap.
