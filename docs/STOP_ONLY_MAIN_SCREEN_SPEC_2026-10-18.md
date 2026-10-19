# Stop-only Live Vision — owner request, 2026-10-18

This supersedes the default-screen action layout in `LIVE_VISION_UI_SPEC.md`.
The earlier spec remains historical. Codex owns this design and safety contract;
an execution agent implements the bounded Compose slice. The Redmi visual,
TalkBack, gesture, and audible experience remain unverified until tested on device.

## Normal Live Vision

1. Compact top brand: existing logo mark and `TriGuide`, a short visible
   `Research mode · Not a mobility aid` warning, one high-priority status,
   and at most two short lines of shortcut help. No diagnostic numbers or
   unverified detector names in this header.
2. The live camera occupies the remaining space. Detector boxes may remain
   visible for debugging; the overlay must not auto-read changing guesses to
   TalkBack. An explicit Describe shortcut is on-demand only.
3. The sole in-app pressable control on the normal screen is a red, high-contrast
   `STOP` target at least 56 dp tall. It acts immediately on first press and
   through its TalkBack activation. Never require a three-second hold. It pauses
   guidance and camera, sets virtual speed and steering to zero immediately,
   vibrates, and gives one short paused confirmation. Restart is a separate
   shortcut, not part of STOP.
4. Preserve established shortcuts: three swipes up = Describe; down = More;
   right = hear status; left = restart. Triple-tap also restarts. Because
   TalkBack can reserve swipes, expose equivalent custom accessibility actions.
   Camera-permission recovery is a necessary exceptional state and may show an
   explicit grant/open-Android-settings button; it is not the normal screen.

## Safety language

`CAMERA_2D` object boxes do not establish free space, depth, a side passage,
or an intersection. Current camera status may say `Stop and check`, `Path not
confirmed`, or a concise camera/model error; it must not issue `walk`, `keep
walking`, left/right turns, or `several paths` from boxes or missing detections.
Those movement phrases require future positive, fresh, temporally stable
walkable-area and branch evidence. Synthetic-map cues belong only in the
clearly marked simulator. Do not enable placeholder junction wording.

## Verification

Run focused status/shortcut tests, all app and sim-core JVM tests, and a debug
APK build. Review source for exactly one normal-screen pressable element,
immediate STOP behavior, honest copy, TalkBack actions, and permission recovery.
Install/test only if the Redmi is an authorized ADB `device`. Desktop tests and
source review are not phone UI, TalkBack, or speaker verification.
