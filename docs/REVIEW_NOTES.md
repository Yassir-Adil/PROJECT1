# Review notes

## 2026-10-18 — Codex response to Cursor 0.9.13 diagnostic seam review

- Cursor's source-only report at the top of `docs/BRIDGE_TO_GPT.md` found no High/Medium defect. Codex independently ran 197 app + 10 sim JVM tests and a successful debug build; the Redmi remains absent from ADB.
- Closed the Low even-width seed test gap: a new 4×2 test proves the documented right-center bottom seed is selected. Full JVM suite and build passed again; product APK hash did not change.
- Accepted the other Low as an explicit trust boundary: image-to-mask mapping is validated as metadata but not checked against a real producer's resampling. The evaluator remains unwired from camera, guidance, TTS, and motion. Do not use its counts to authorize walking or turning.

The reviewer appends notes here. While a builder lock is missing or younger than 45 minutes, this file is the only place it writes.

## 2026-10-18 — Guidance honesty, memory privacy, camera backpressure

Builder lock `.cursor/agent-lock.json` was missing. `TriGuideRoot.kt` already had uncommitted edits, and `PersonIdentity.kt` was untracked, so no product code was changed. `docs/EXTERNAL_HELPERS.md` was added because this pass asked for it.

### Medium — An Arabic name typed in Settings is rejected

`PersonConsentSection` prepares a save by calling `handleUtterance("remember this person as $typedName")`. The English parser only accepts Latin letters, spaces, apostrophes, and hyphens. A name such as أحمد does not match, and the Arabic command pattern is not used on this button. `requestSave` already accepts any name of 2–40 characters and still writes nothing until **Confirm save**. The button should call that, not the English sentence parser.

### Medium — The pending sentence implies a photo after confirm

`requestSave` says “No photo is saved until you confirm” and the Arabic line says no photo is saved before confirmation. Confirm itself says no photo was stored, and `storeFace` always returns false, but the pending line tells the user a photo is waiting on the confirm tap. Say that no photo is stored at all. The unit test only checks that the English line contains “No photo”, so this wording still passes.

### Medium — A side cue tells the user to turn, and drops the limit

Hold is honest for a center box, an empty frame, a stale frame, a bad sample, and two sides that do not dominate. Those cases stop the virtual robot. Camera samples never return Straight. That is covered by `cameraModeNeverGoesStraight`.

A turn is different. The long reason says the other side is not a confirmed gap and that box size is not a distance. The spoken line does not. A blind user hears “Person on the left. Turn slightly right.” Area still chooses a winner when boxes are on both sides (`confidence * area`, dominance 1.8). Full versus slight is based on how close the box sits to the middle band, not on area. There is no test where one box is in the center and another is to the side. The code holds in that case because any center box returns before the side check.

### Low — Object notes are written on the guidance tick

`LocalGuidanceMemory.note` runs on the same 50 ms loop as steering, on the main coroutine. The same signature is skipped for 1 second, then `objects.tsv` is written with a blocking file write. A notes failure is swallowed, and the notes are not an input to `NavigationDecisionEngine`. Move the write off that tick, or keep the throttle and test it. The memory store does not re-apply the 0.35 score floor. The live path only passes boxes the detector already kept for guidance.

### Low — Drive-mode buttons omit the safety sentence

Manual, camera-informed, and synthetic buttons announce the short label. The sentence that says the mode is simulated, and that a box is not a clearance, sits in the paragraph above. `TriGuideRoot.kt` was already dirty, so the content description was not edited. Other checked controls have a TalkBack label and a 56 dp minimum height. The theme is dark only: yellow `#FFE14A` on `#0B0B0C`. A TalkBack walk on the phone is still open.

### Info — What held up

- Analysis uses `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`, RGBA, about 640×480, and the same `FILL_CENTER` viewport as the preview. The detector copies the frame, then the proxy is closed. A second frame is dropped while one inference is in flight.
- `guidance-memory/` is under app files and excluded from cloud backup, device transfer, and the legacy full-backup rules. `allowBackup` stays true for everything else. Voice preferences in `triguide_voice` can still be backed up. They are not face data.
- `recognize()` returns null. `storeFace` returns false. Saved rows force `faceStored` to 0 even if a file says otherwise. Remember people stays off until the switch is on, and a name is written only after Confirm save. `INTERNET` stays removed. Diagnostics text includes the note count and the person-memory status line, not a photo and not the saved names.
- Floor segmentation remains `NOT_STARTED`. No floor model is loaded.

### Not changed

No Kotlin edit. The Arabic-name button, the pending photo sentence, and the drive-mode content descriptions are noted for the builder. Fingerprint control and screen triple-tap were not reopened. No cloud face database and no automatic face save.

## 2026-10-18 — Redmi 0.9.3 live camera and audio baseline

Test device: Redmi `55aqpfqkvw9xbafe`, Android 14, installed app `0.9.3` (versionCode 16). This was a real-device, phone-camera test, not an emulator benchmark. Evidence is kept outside the repository at `C:\Users\yassi\Videos\TriGuide-Tests\clean-20261018-135433.mkv`, with extracted frames `clean-frame-01.png` through `clean-frame-03.png` and log `clean-20261018-135433-voice-nav.log` in the same directory. The MKV has an Opus audio track. Offline transcription of its audio repeatedly recognized “Obstacle ahead”; the log establishes the precise speech-request timing, not the transcription's exact word count. Timbre/loudness quality was not judged by ear.

### High — Stable hazard repeats speech every five seconds

The clean log records `TriGuideVoice speak result=0 kind=OBSTACLE_DETECTED text=Obstacle ahead.` at 13:54:33.272, 38.305, 43.300, 48.346, 53.380, and 58.414. The scene is mostly static in the captured frames. The cadence matches `VoiceConfig.COOLDOWN_CRITICAL_MS = 5_000L` and `VoiceGuidanceGate.coolingDown`, rather than a failed dedupe key. This reproduces the owner's complaint that the app talks too often. A spoken request and audio capture do not prove every utterance was heard at identical volume. For a research-mode assistive app, replace fixed repeated speech with a testable hazard-episode policy: prompt immediately for a new confirmed hazard, reduce identical reminders in a persistent state, and allow a materially new hazard or relevant state change to be announced promptly. Do not turn detector-box absence into “clear/safe” speech.

### Observation — Camera inference needs a controlled scene

The live preview streamed at roughly 29 FPS and inference at roughly 7 FPS. TV/cup-like objects appeared plausibly labelled, but a large `person` box also covered the owner's close arm/legs visible in the image. That is *not* evidence of a person false positive. These frames are unsuitable for judging path clearance or the cap→toilet complaint. Repeat with the camera fixed on a known, empty passage and then known objects, keeping the owner out of frame; record ground truth and exact detector boxes before changing a model or confidence threshold.

### Test contamination resolved — duplicate Activity, not stale camera

The first run `baseline-20261018-134943.mkv` had two `MainActivity` records in one task after the tester started an already-running app again with ADB. The old Activity emitted `stale_frame` while the foreground Activity streamed fresh frames. After `am force-stop` and one `am start` (no data clear), only one Activity remained and the clean sample did not show `stale_frame`. Do not file this as a camera-source bug based on the contaminated first log. Do not start a second Activity on top of an already-open app in subsequent tests.

No product code was edited for this review. The Cursor builder loop remains disabled; this note alone does not launch an implementation task.

### Follow-up — clean startup and owner's second camera scene

After a single force-stop/start, `startup-20261018-135900.mkv` captured the normal opening. The phone audio track was locally transcribed as including “What's ahead?” and “Obstacle ahead.” The device log records the intended one startup question at 13:59:02.573, then obstacle warnings at 13:59:06.441, 11.436, and 16.450. There were `no_camera_frame` diagnostics during the first fraction of a second, but **no spoken “camera unavailable”** in this sampled opening. That older complaint remains unverified here; do not mark it fixed or reproduced. The three warnings in the first ~16 seconds are enough to explain why opening feels noisy.

The owner's requested “clear corridor” second sample (`corridor-20261018-135920.mkv`) actually shows a room with a sofa, TV, and white round table; it is **not** an empty-corridor clearance test. Frames `corridor-frame-02.png` and `corridor-frame-03.png` visibly put a `toilet` detector box around the lower tier/edge of that round table at 0.66 and 0.87 confidence. There is no toilet in that boxed region. This is a reproducible, high-confidence wrong label and demonstrates that raising the global confidence threshold alone would not solve the semantic error. The decision stayed `HOLD`; the speech log used generic “Obstacle ahead” / “Path blocked,” not “toilet,” so the wrong object name was shown on screen and in the reason but not spoken in this sample. The app still needs a true empty-passage test before any claim about walkable-space understanding.

### Codex follow-up review of Cursor's first voice repair

The 129 passing JVM tests and debug build are real build evidence, but two real-scene behaviors are not covered. `VoiceCues.blockedCue` gives camera `HOLD` two episode keys (`obstacle-ahead` and `obstacle-blocked`) based on one-side versus both-side boxes. Our static room log switched between those descriptions as box counts fluctuated. `VoiceGuidanceGate.hazardMaySpeak` treats a different key as a new hazard after 1.5 seconds, so a stable physical scene may still speak repeatedly, possibly faster than the old five-second interval. The detector has no tracked hazard identity; a text/box-count fluctuation is not evidence of a new obstacle. Add a 60-second alternating-key regression test and keep camera BLOCKED as one episode until it has genuinely left BLOCKED long enough.

`CameraSpeechGate.cueForTransition` also updates `lastAnnounced` to `STARTING` after `FAILED`. A failed camera retry sequence `FAILED → STARTING → FAILED` can therefore emit “Camera unavailable” again without ever reaching `STREAMING`. Keep the unavailable episode latched through retries; only a real streaming recovery ends it, with one recovery line. Add the retry-sequence regression test before Redmi installation.

### Codex Redmi regression after both Cursor voice fixes — 2026-10-18

Codex reviewed the follow-up source and XML results (**131 JVM tests, zero failures/errors**) before installing `android-app/app/build/outputs/apk/debug/app-debug.apk` on Redmi `55aqpfqkvw9xbafe` at 14:15 local (ADB `install -r` returned Success). Installed version remains 0.9.3 / 16. The pre-install device APK was preserved at `C:\Users\yassi\Videos\TriGuide-Tests\pre-voice-redmi-base.apk` for rollback; no app data was cleared.

Clean real-device capture: `C:\Users\yassi\Videos\TriGuide-Tests\voice-fix-20261018-141500.mkv` (~42 s, video + Opus phone audio), extracted `voice-fix-frame-01.png` through `-03.png`, `voice-fix-audio.wav`, and `voice-fix-20261018-141500-log.txt` alongside it. Only one app Activity was started after force-stop. Frames show a mostly static camera view and `HOLD`, with streaming roughly 29–30 preview FPS and 7–9 inference FPS after warm-up. Log PID 19267 has no `stale_frame` in this sample.

Speech-request log: `Guidance on.` at 14:15:20.092, `Obstacle ahead.` at 14:15:23.613, and one `Obstacle ahead.` reminder at 14:15:53.477. No intervening five-second obstacle warnings in the 42-second capture. Offline transcription of the recorded phone audio recognized the same three lines; exact audible onset/count beyond that should not be inferred solely from a tiny ASR model, but the recording contains real audio. This is a **pass for the persistent-hazard repetition regression** on this static scene, not a safety certification. No real camera failure/retry was induced on the phone, so the camera-unavailable fix remains unit-tested, not phone-verified.

New perception observation: the preview boxed food packaging/other items on the table as `person` (about 0.36–0.48 confidence in the extracted frames); no person is in that boxed region. This is a visible false label, separate from the voice repair. Do not tune the global confidence threshold from one scene or infer a walkable gap. The phone battery indicator was about 9% during the capture; charge before extended device benchmarks.

### Codex review of Cursor calm Live Vision UI — 2026-10-18

Source review and XML test results: Cursor removed the verbose default-screen metrics/reason/wheel/last-cue/observation text, kept permission control and error priority, stabilized preview TalkBack semantics, and left Describe on demand. The new pure status presenter has six tests; 14 XML suites total 137 tests, zero failures/errors. Candidate APK was built but not installed. Visual fit and TalkBack traversal on Redmi are **not yet verified**; the completion report's “above the fold” language is a design intent, not observed device evidence.

Focused polish before phone install:

1. `LiveVisionStatus.priority` returns technical `Hold` for a blocked camera scene, and `Starting` for warm-up. Prefer the owner-approved plain `Stop and check` and `Starting camera`. Treat `Idle` after a granted launch as startup too, so a transient old `HOLD` is not shown before streaming. Keep `UNKNOWN` and any side/straight action as `Path not confirmed`—no clearance claim.
2. The status line currently surfaces raw camera, model, and inference error strings, potentially long and rapidly changing. The default status should be concise (`Camera unavailable`, `Detection unavailable`, `Vision temporarily unavailable`); the detailed error can remain on the overlay/Diagnostics. Keep the permission CTA visible. An explicit `Retry camera` control for a true startup failure should call the existing restart callback; do not create a second camera lifecycle.
3. `More` remains the same filled yellow `Button` as primary `Describe this frame`, against the one-primary-action contract. Use existing Material 3 `OutlinedButton` for `More`; no new dependency/style system.

Do not modify the voice, perception, model, navigation, or camera engine for this UI polish. Existing `DetectionOverlay` is used in Live Vision and a camera test panel; making its TalkBack box labels silent there is a deliberate privacy/safety choice, but must be checked with TalkBack on phone later. Preserve all dirty work.

### Codex review of offline TTS selection — 2026-10-18

Cursor's source change now ranks only non-network English/Arabic voice candidates and calls Android `TextToSpeech.setVoice` on a returned `Voice`, rather than `setLanguage`. This addresses the source-level mismatch between checking for an offline voice and not selecting it. Official Android `TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED` has value `notInstalled`; voices with that feature are excluded. The pure selector tests cover language separation, online exclusion, quality/latency order, locale/name ties, and no-voice fallback. This does **not** prove the Redmi's TTS engine will audibly synthesize with the selected voice.

Codex found a nullability gap in the adapter: Android documents that `Voice.getFeatures()` may return null on error. `features.contains(...)` could crash during voice refresh. Codex changed it to `features?.contains(FEATURE_NOT_INSTALLED) == false`, a fail-closed eligibility check when features are unavailable, and reran `:app:testDebugUnitTest :app:assembleDebug` successfully: 145 JVM tests, zero failures/errors. The final uninstalled APK and rollback baseline are identified in `docs/PHONE_TEST_CHECKPOINT_2026-10-18.md`. Android engine `setVoice`/utterance callbacks and perceived quality still require the phone test.

### Codex 0.9.10 pre-install audit — 2026-10-18, 15:45 MYT

Another local builder advanced the shared checkout to 0.9.10 / versionCode 23 while this chat was preparing the Redmi test. `docs/PROGRESS.md` and `docs/BRIDGE_TO_GPT.md` contain older installed-version reports, but `adb devices -l` currently returns **no device**, so Codex cannot establish what version is installed right now. No APK was installed by this chat at this stage.

The current 0.9.10 source initially failed 1 of 164 app tests: `LiveVisionStatusTest.guidancePromptsAreShortHonestCues` expected “Slight right” but the helper returned “Turn slightly right.” More importantly, that helper's `UNKNOWN` text said “Walk carefully” and `STRAIGHT` said “Keep walking carefully” without carrying any positive walkable-area evidence. `rg` found no current UI call site for `guidancePrompt`; still, this is unsafe copy to leave for future use. Codex made the helper fail closed: `HOLD` says stop/check, all other actions say the path is not confirmed and to stop/check with a cane. Focused assertions now forbid unverified walk/turn language. The camera decision engine and TTS cue scheduler were not changed.

Codex reran `:app:testDebugUnitTest :sim-core:test :app:assembleDebug`: **BUILD SUCCESSFUL**, 164 app tests and 10 simulator tests, zero failures/errors. Exact uninstalled candidate: `C:\Users\yassi\Videos\TriGuide-Tests\triguide-0.9.10-validated-20261018.apk`, SHA-256 `3CFD74F70DE24697461EACFCA9A1B9308B6215BF43282A19734D945ABCBEA0A4`. Do not confuse it with the earlier 0.9.10 output APK (different hash) or any installed version. Real Redmi UI/voice/gesture behavior is unverified until ADB reconnects and the owner is ready.

### Codex main-screen redesign safety audit — 2026-10-18

- Source currently draws only the full-screen preview and a 36 dp stop bar, while `docs/PROGRESS.md` describes a top logo/status card. `LiveVisionStatus` already defines short copy, but `CameraScreen` does not render it. The stop bar still requires a continuous three-second hold; this is inappropriate for an emergency action. A bounded UI executor is implementing the owner-requested stop-only redesign with immediate STOP and existing gesture/TalkBack shortcuts.
- Do not fill the newly visible status with walking/turn/junction claims from the present `CAMERA_2D` object boxes. The current camera pipeline has no positive walkable-area or branch evidence. Real-camera `HOLD`/`UNKNOWN` should remain stop/uncertain; simulator-only action vocabulary must be labelled as simulated.
- Separate future safety task: `PathRiskAnalyzer.shouldDropFromPath` currently removes all ignored labels before checking corridor position. Its unit test explicitly treats a small lower-corridor cup as `UNKNOWN`; a cup or knife on the walking surface is still a possible trip hazard. At minimum, do not drop ignored labels inside the corridor; update tests and assess voice impact before shipping. Also, `NavigationDecisionEngine.decideCamera` still contains a `CLEAR_OBSERVED -> clearObserved` branch that would authorize straight motion if a future analyzer began returning CLEAR without a typed positive-evidence contract. Keep camera movement fail-closed until that contract is implemented. Neither issue is claimed fixed in this note.
- Voice UX follow-up: `SceneBriefScheduler` still auto-speaks a box-derived scene brief when three reported objects appear, and it may repeat after 30 seconds or after label/zone flutter with a 12-second minimum gap. This can compete with the owner's quiet-by-default goal and amplify known false object labels. Prefer on-demand Describe only for non-hazard narration until a measured phone audio pass justifies periodic briefs. Preserve immediate hazard alerts. This scheduler has not been changed by the UI task.
- STOP follow-up for direct phone QA: the UI stop callback zeroes the simulated command and calls `camera.stop()` before the paused cue. `AndroidVoiceSpeaker` exposes only `close()` (which shuts down TTS), not a reusable `stopCurrentUtterance()`; the current `speakPaused(... interrupt=true)` normally flushes an active utterance once TTS is ready. Confirm interruption latency by ear on Redmi and add a reusable immediate TTS stop only if needed; do not claim the audio is cut instantly from source inspection alone.

### Codex 0.9.11 post-implementation review — 2026-10-18

The independent instruction agent identified the missing header and three-second STOP. The execution agent implemented a compact logo/safety/status/shortcut header and immediate 64 dp STOP with TalkBack semantics, then fixed the corridor-ignore, accidental camera CLEAR/turn, automatic scene-brief, and stale Describe findings above. Codex reviewed the source and added a final short, language-aware status shortcut plus safer generic simulation/fallback speech. The old findings above remain as the audit trail, not open implementation tasks (except the explicit phone/audio checks).

Final independent desktop run after the 0.9.11 version bump: `:app:testDebugUnitTest :sim-core:test :app:assembleDebug` **BUILD SUCCESSFUL**, 176 app and 10 sim tests, zero failures/errors. Exact APK hash and next Redmi protocol are in `docs/PHONE_TEST_CHECKPOINT_2026-10-18.md`. No real-device install, STOP latency, TalkBack walk, or speaker confirmation was performed; `adb devices -l` remained empty.

### Codex response to Cursor 0.9.11 review — 0.9.12

Cursor independently confirmed the stop-only UI/safety code and reported one Medium pre-existing gap: center-only corridor geometry ignored wide boxes crossing the edge. The execution agent added normalized box bounds, mirror-aware adaptation, overlap-based risk, invalid-bounds fail-closed handling, and focused tests. Codex reviewed the source, reran the full desktop test/build suite, and packaged 0.9.12: 182 app tests + 10 sim tests, no failures, successful APK build. The low-severity unused `SceneBriefScheduler` type remains intentionally unwired; automatic box narration stays off. Redmi behavior still unverified because ADB is empty.
