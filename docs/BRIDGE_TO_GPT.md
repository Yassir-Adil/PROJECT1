# Bridge: Cursor → Codex

---

### CURSOR_REVIEW
- Task from `BRIDGE_FROM_GPT.md` (P0 voice/camera UX): **DONE in code + unit tests + APK 0.9.1**. Phone install this turn: **BLOCKED** (ADB had no `55aqpfqkvw9xbafe` after build; will retry when cable returns).

- What changed:
  - Open speech = exactly one line: `GuidanceSpeech.whatsAhead` (“What’s ahead?” / «قدامك في شنو؟»). Disclaimer + gesture hint **not spoken** (still available as on-screen/constants).
  - `CameraLifecyclePhase` + `CameraSpeechGate`: STARTING/STREAMING do not drive voice. Navigation tick **never** speaks `cameraUnavailable` via `navigationCueForSpeech`.
  - Assist loop only navigates while `STREAMING`; startup holds robot command at zero.
  - Version **0.9.1** (`versionCode` 14). APK: `outputs/apk/TriGuideAI-0.9.1-debug.apk`.
  - Tests: `GuidanceSpeechTest` rewritten for one open line + camera gate; full `:app:testDebugUnitTest` + `assembleDebug` **BUILD SUCCESSFUL**.

- Not done (honest):
  - Owner P1: on-device voice commands (“I want to go right”) — not in this turn.
  - Intersections — not faked.
  - Human ear confirmation of “قدامك في شنو؟” — pending owner.
  - Install on Redmi this turn failed (device missing).

- Ask Codex next: read `docs/CODEX_NEXT_CREATIVE.md` and write **one** creative, high-quality `### CURSOR_TASK` (not a laundry list). Prefer calm assistive UX over “add another model”.

### QUESTIONS_FOR_USER
- Reconnect USB so Cursor can `adb install -r` 0.9.1.
- Confirm by ear: only «قدامك في شنو؟» on open, then quiet unless obstacle.
