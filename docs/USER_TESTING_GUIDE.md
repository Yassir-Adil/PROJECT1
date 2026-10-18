# User testing guide

This build is Research and Simulation Mode. Version 0.6.0 opens straight into the camera and speaks a short cue. It is not a mobility aid. An empty detection is not a clear path. The voice is not a safety proof. Do not walk from the on-screen decision or from the voice.

The fingerprint reader on the Redmi Note 12 Pro 5G is the power button. Android does not give those presses to TriGuide. Pressing that button does not control the app, and it can turn the screen off. Guidance starts when you open the app. Three quick taps on the Live Vision screen restart the camera and the “Camera started. Guidance on.” cue.

## Install on a Redmi (or any Android 8.0+ phone)

USB debugging has to be turned on, and the phone has to accept this PC’s debugging prompt. Until `adb devices` lists the phone, a phone test has not happened.

1. From this project folder, with the phone connected:

   `adb install -r outputs\apk\TriGuideAI-0.6.0-debug.apk`

   Or copy that APK to the phone and install it there. If Android blocks the install, allow that source. That prompt is yours to tap.
2. Open **TriGuide**. If the PC install says `INSTALL_FAILED_USER_RESTRICTED`, unlock the phone and tap Allow (السماح). Steps are in `docs/DEVICE_COMPATIBILITY.md`.
3. You should already be in **Live Vision**, not a menu. You should hear, once: the research-mode sentence, then “Camera started. Guidance on.”, then a short sentence that the fingerprint button cannot control the app and that a triple-tap restarts guidance. If the camera permission is missing, you hear a request and Android shows the permission dialog. Allow it. If Android will not ask again, the app opens Settings; allow Camera for TriGuide and return.
4. Tap the screen three times quickly, anywhere except on a button you mean to press. You should feel a short vibration and hear “Camera started. Guidance on.” again. One tap on **More** should still open More. A scroll should not restart guidance.
5. The object-detection line should become `READY. MediaPipe EfficientDet-Lite0 int8`. Floor segmentation should say `NOT_STARTED` and disabled, with no floor mask. If detection says `MODEL_MISSING` or `LOAD_FAILED`, no boxes should appear. Write down the exact lines.
6. **More** (or a long-press on the Live Vision title) opens the simulator, settings, and diagnostics. The phone’s Back control returns to live guidance and starts the camera again.
7. Point the back camera at a chair, a bottle, or a person in a normal room. A real result shows a yellow box, a label, and a confidence such as `0.80`. The decision block should name a decision, a reason, and a front-wheel angle, and say the source is Camera 2D. Hold and Unknown stop the virtual robot. The performance line should change from “not measured yet” to milliseconds and FPS after the first results.
8. An empty room, or a scene the model does not know, can correctly say that nothing is at or above 0.50 confidence. The decision should be Hold or Unknown. That is not a promise the path is clear. Write down the decision and the reason. If speech is on, a short cue may follow after the direction settles. It should not talk on every frame. “Navigation uncertain” can repeat on its cooldown. “Obstacle detected. Hold.” is for a box in the center or on both sides. A turn phrase is for a committed left or right. None of those sentences means the path is clear.
9. Open **More**, then **Robot Simulation**. Leaving live guidance can say “Guidance paused.” The screen should say the tricycle is simulated and that nothing there is a physical robot. Manual drive starts selected and stays quiet. Try Forward, Steer left, Steer right, Stop, and Reset. The wheel number should change. Then select **Synthetic world**. The decision should name the synthetic map, and the wheel or the speed should change. After a short wait you should hear one turn phrase, then silence until the decision changes. The arrow is the virtual robot, not a real machine. Back returns to live guidance.
10. Open **Test Scenarios**. The three cards should show a decision from the authored map: Straight, a turn away from the left barrel, and Hold for a post on the axis. They are not the camera.
11. Open **Diagnostics** and **Settings**. Share diagnostics sends a text status dump. Settings should say floor segmentation is not started. In Settings, play the voice sample, try a slower rate, and try Arabic if the voice-engine line says an Arabic voice is installed. If it says Arabic is not installed, the cue should stay in English. Quiet mode should leave turn phrases silent and still allow “Obstacle detected” and “Camera unavailable.” Turning speech off should stop the cues. The research-mode line cannot be turned off.

## TalkBack

Turn TalkBack on in Android accessibility settings. Live Vision is already open. Each control, including More, has a spoken description. The camera view and the detection overlay have spoken labels. The fingerprint power button is not a TriGuide control. Triple-tap is three separate taps; TalkBack may use its own gestures, so try the triple-tap with TalkBack off first.

## What not to expect

- The decision text is on screen, and speech is a short repeat of it. Hearing a cue is not a clearance.
- A phone without an offline voice keeps the cue on screen and does not download one. This build does not request internet access.
- No physical robot motion. Camera steering stops when you leave Live Vision.
- No account and no camera upload. The model is already inside the APK.
- Emulator timings from `docs/TEST_RESULTS.md` are not this phone’s timings.

Device results belong in `docs/TEST_RESULTS.md` only after someone actually installs and runs the app.
