# Redmi test checkpoint — 2026-10-18, 14:43 MYT

## Last verified phone state

- Device: Redmi Android 14, ADB serial `55aqpfqkvw9xbafe` (`device`).
- Installed APK: **0.9.4** / versionCode **17**, `lastUpdateTime=2026-10-18 14:42:41`. `adb install -r` Success; app data not cleared.
- APK copies: `outputs/apk/TriGuideAI-0.9.4-debug.apk` and `C:\Users\yassi\Videos\TriGuide-Tests\quality-094-installed-20261018.apk`.
- Rollback (earlier voice-fix only): `C:\Users\yassi\Videos\TriGuide-Tests\voice-fix-redmi-installed-20261018.apk`.
- JVM: **158** `:app:testDebugUnitTest`, 0 failures. Screenshot: `outputs/reports/redmi-094-live.png` (calm Live Vision).
- Log after clean launch: offline TTS `voice=en-US-language`, `Obstacle ahead.` (`speak result=0`), Nav `HOLD` / `UNKNOWN` as designed. **Owner ear confirmation still open.**

## What 0.9.4 includes vs the 14:15 voice-fix baseline

- Calm Live Vision UI + offline `setVoice` + R1a distinct camera votes + Describe humility (toilet never named; person ≥0.75) + Arabic `تذكر هذا الشخص باسم`.

## Still not proven on phone

- Empty-corridor scene, camera failure/retry, TalkBack walk, and perceived TTS quality by ear.
- Detector false labels on the overlay (model); Do not claim clear path from empty frames.

## Resume next session

1. Owner listens once after open + taps Describe on a table scene (expect hedge, not “toilet”).
2. Codex reviews `docs/BRIDGE_TO_GPT.md` when usage resets; then Quality Plan priority 3 (labeled clips) before any model swap.

## Pending newer build — 2026-10-18, 15:45 MYT

- Source is now 0.9.10 / versionCode 23. A fail-closed guidance-prompt correction passed 164 app tests and 10 sim-core tests; `:app:assembleDebug` succeeded.
- Exact validated APK: `C:\Users\yassi\Videos\TriGuide-Tests\triguide-0.9.10-validated-20261018.apk`; SHA-256 `3CFD74F70DE24697461EACFCA9A1B9308B6215BF43282A19734D945ABCBEA0A4`.
- **Not installed or phone-tested by this session**: `adb devices -l` currently shows no device. The 0.9.4 record above is historical, not proof of the currently installed version. A concurrent project note mentions 0.9.8; verify with `dumpsys package` after reconnection.
- Resume: connect the unlocked Redmi using a USB data cable, approve USB debugging, verify `device`, then install this exact APK without clearing app data and confirm versionName/versionCode before testing.

## Latest candidate — 0.9.11 / 24, 2026-10-18

- Supersedes the pending 0.9.10 APK above; keep that older copy for rollback. Exact validated file: `C:\Users\yassi\Videos\TriGuide-Tests\triguide-0.9.11-validated-20261018.apk` (88,650,981 bytes), SHA-256 `58B18750401174BE92C48D5A194949E9FE7D2AE16D321D706967C85C0330D82C`.
- 176 app tests + 10 sim tests passed; debug build passed. No device in `adb devices -l`, so **not installed and not phone-tested** by Codex.
- Resume on Redmi: verify exact serial `55aqpfqkvw9xbafe` is `device`; inspect installed version first; `adb install -r` this exact APK without clearing data; confirm `0.9.11`/24 in `dumpsys package`; force-stop once and launch once. Then check viewport/logo, one visible STOP, instant touch/TalkBack STOP and zero virtual speed, shortcut mapping, permission/retry, one quiet start + hazard episode, Describe on live vs paused/stale image, and audible language/quality. Record screenshot/log/audio only with the owner's physical test available. Do not infer actual speaker quality from `speak result=0`.

## Latest candidate — 0.9.12 / 25, 2026-10-18

- Supersedes the pending 0.9.11 APK above. Exact validated file: `C:\Users\yassi\Videos\TriGuide-Tests\triguide-0.9.12-validated-20261018.apk` (88,650,981 bytes), SHA-256 `F950A37F9675772E667650CE72D062ADBE4FE94949D9F3CE62302DEA597A7666`.
- 182 app tests + 10 sim tests and debug build passed. `adb devices -l` still shows no device, so **not installed and not phone-tested**.
- Resume using the exact protocol in the 0.9.11 paragraph above, but install **this 0.9.12 file** and confirm **0.9.12 / versionCode 25**. Include a camera view with a wide chair/table partly crossing the corridor edge and another view with an object wholly outside it; compare decisions without claiming depth or safe passage. Preserve earlier APKs for rollback.
- Cursor independently reviewed 0.9.12 source and found no High/Medium defect in the box-extent correction. One low item remains: slightly out-of-frame detector boxes become UNKNOWN and may under-alert. It has not been measured on Redmi. The Cursor report is at the top of `docs/BRIDGE_TO_GPT.md`.

## Latest candidate — 0.9.13 / 26, 2026-10-18

- Supersedes the pending 0.9.12 APK for the next install. Exact file:
  `C:\Users\yassi\Videos\TriGuide-Tests\triguide-0.9.13-validated-20261018.apk`
  (88,650,981 bytes), SHA-256
  `2CE55AFFD78B6CA62846415B53D8A4DDF37A698B2F772A70A789998E22DB0CEF`.
- 197 app JVM tests + 10 sim tests and debug build passed; ADB still listed no
  device. **Not installed, opened, heard, or phone-tested.** New floor-mask
  evaluator is unwired diagnostic code; no live floor model or movement claim.
- When Redmi serial `55aqpfqkvw9xbafe` is `device`, inspect installed version,
  install this exact APK with `adb install -r` (do not clear app data), confirm
  versionName 0.9.13/versionCode 26, and run the 0.9.12 phone protocol above.
  Confirm the Diagnostics floor line says no model is bundled/validated. Keep
  0.9.12 and earlier APKs for rollback.

## Redmi 0.9.13 partial test — 2026-10-18 23:17 MYT

- Install/version complete: serial `55aqpfqkvw9xbafe`, `adb install -r -t`
  Success; from 0.9.10/23 to 0.9.13/26, app data retained. USB debugging
  reauthorization restored ADB after a brief dropout.
- Preview and stop smoke complete: screenshots
  `outputs/reports/redmi-0913-live-2.png` and
  `outputs/reports/redmi-0913-stopped.png`. STOP log at 23:11:05.277, one
  paused speech request at 23:11:05.393; detector/nav output ceased after one
  in-flight callback. Three UP swipes invoked Describe.
- Fixed mat/floor scene checked: `redmi-0913-fixed.png` at 23:15:17 and
  `redmi-0913-fixed-end.png` at 23:16:15 showed no boxes and `Path not
  confirmed`; 506 UNKNOWN navigation entries and zero voice requests in the
  23:15 minute. This is not proof of a safe corridor or audible silence.
- After the fixed sample, rapid label changes started around 23:16:35 and
  two `Obstacle ahead.` voice requests appeared 11 s apart. Scene stability
  was lost, so record the moving-scene cadence for follow-up without calling
  it a static-scene regression. A paused-screen triple-UP at 23:17:34 spoke
  `Guidance paused. Restart to describe.`; no stale frame was narrated.
- App is paused at this checkpoint. Not yet verified: actual corridor,
  partly crossing wide chair, TalkBack action/order, large text, camera
  failure/retry, actual perceived TTS, clipped-box frequency, and Diagnostics
  floor line. Ask owner before a new physical scene test; never treat an empty
  object detection as a safe path.

## Redmi 0.9.14 safety fix — 2026-10-18 23:23 MYT

- Reproduced 0.9.13 STOP → More → Android Back unintended restart. See
  `outputs/reports/redmi-0913-after-more.png`; source Back callback used
  `start()` rather than screen-only return.
- Fixed and installed 0.9.14/27 without data clear. 200 app + 10 sim JVM
  tests/build passed. Exact test copy:
  `C:\Users\yassi\Videos\TriGuide-Tests\triguide-0.9.14-validated-20261018.apk`;
  SHA-256 `25D20786100EAE71D33159ADED7CAF1807B5BC3F3D30BEF8D9AFE556CDCDA11B`.
- Redmi STOP → More → Back now stays paused with no preview or STOP
  (`outputs/reports/redmi-0914-paused-more-back.png`). Explicit triple-left
  restart and a second STOP both registered. App left paused. Diagnostics
  floor line in 0.9.13 More screen was visually truthful; unchanged in 0.9.14.
- Remaining: actual corridor/wide-chair visual case, TalkBack/large text,
  camera fail/retry, audio as heard by the owner, detector clip frequency,
  controlled moving-scene speech. Do not claim safe route from UNKNOWN.
