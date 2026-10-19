# Live Vision UI contract — 2026-10-18

Historical first-pass contract. The owner's later stop-only redesign supersedes
the default-screen button layout here; see `STOP_ONLY_MAIN_SCREEN_SPEC_2026-10-18.md`.

Owner/architecture design by Codex; Cursor implements a bounded Compose Material 3 change. This is an interface simplification, not a new perception or navigation capability. Use the existing Compose/Material 3 stack; no dependency change is justified by this task.

## Default phone view

Visual order (top to bottom), with consistent 8 dp spacing and 16 dp outer padding:

1. `Live Vision` heading and a concise, legible warning: `Research mode · Not a mobility aid`. The full disclaimer remains in Settings. Do not describe the app as a certified mobility guide.
2. One prominent status, derived from the existing camera/decision state. Prefer truthful plain language: `Stop and check` for camera `HOLD`/blocked, `Path not confirmed` for `UNKNOWN`, `Starting camera` during startup, `Camera unavailable` for failure, and `Camera permission needed` when denied. Never show `Clear`, `Safe`, `Proceed`, or a turn direction from detector boxes. The long decision reason, model name, FPS/latency, wheel angle, raw observation list, and last voice cue belong in Diagnostics.
3. The live preview as the main visual region, with existing visual detector boxes allowed but explicitly unverified. Do not use a changing list of guessed labels in the preview's TalkBack description; those labels have been false in real samples.
4. One primary action, `Describe this frame` (on-demand snapshot only), and a visually quieter `More` action to Simulator, Settings, and Diagnostics. Keep both 56 dp minimum height and reachable without scrolling at normal phone font size when feasible; large text may scroll instead of clipping.

## States and accessibility

- Camera permission: expose the existing grant/open-settings button clearly. It must not be buried under the preview.
- Camera failure/model failure: error must be visible and readable. If an explicit `Retry camera` control is added, it must reuse the existing restart callback, not create a second camera owner or lifecycle path. Do not imply retry succeeded before streaming.
- TalkBack must have stable, nonduplicated, truthful descriptions. A static preview/overlay description such as `Live camera preview. Detector boxes are unverified. Use Describe this frame for an on-demand summary.` is better than a per-frame list of guessed labels. Do not automatically read raw box labels, changing FPS, or reason strings as accessibility announcements. Keep the deliberate Describe action accessible.
- Do not change spoken navigation/TTS policy, camera lifecycle, detector/model, risk decision, app version, or the research-mode warning. Shortening visible copy is acceptable only when the longer safety information stays discoverable.
- Use restrained theme color roles, clear contrast, and typography from Material 3. No new badges, AI motifs, gradients, animated attention effects, or new library just for styling.

## Verification boundary

Desktop unit tests and debug build can establish compile/business behavior. Only a later Redmi run after charging can establish actual viewport fit, touch/scroll behavior, and TalkBack/audio experience. `docs/PHONE_TEST_CHECKPOINT_2026-10-18.md` is the exact resume point and installed-APK baseline.
