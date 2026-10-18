# Review notes

No review pass yet.

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
