# User testing guide

This build is Research and Simulation Mode. Version 0.4.1 can run an on-device detector, steer the virtual robot from box position or from an authored simulator map, and speak a short cue. It is not a mobility aid. An empty detection is not a clear path. The voice is not a safety proof. Do not walk from the on-screen decision or from the voice.

## Install on a Redmi (or any Android 8.0+ phone)

USB debugging has to be turned on, and the phone has to accept this PC’s debugging prompt. Until `adb devices` lists the phone, a phone test has not happened.

1. From this project folder, with the phone connected:

   `adb install -r outputs\apk\TriGuideAI-0.4.1-debug.apk`

   Or copy that APK to the phone and install it there. If Android blocks the install, allow that source. That prompt is yours to tap.
2. Open **TriGuide**. If the PC install says `INSTALL_FAILED_USER_RESTRICTED`, unlock the phone and tap Allow (السماح). Steps are in `docs/DEVICE_COMPATIBILITY.md`.
3. Read the yellow research-mode line.
4. On Home, the object-detection line should become `READY. MediaPipe EfficientDet-Lite0 int8`. Floor segmentation should say `NOT_STARTED` and disabled. The voice line should say whether speech is on and which offline voices this phone has. If detection says `MODEL_MISSING` or `LOAD_FAILED`, no boxes should appear. Write down the exact lines.
5. Tap **Live Vision** and allow the camera permission when Android asks. Back to home is at the top of that screen. The system Back key also returns Home.
6. Point the back camera at a chair, a bottle, or a person in a normal room. A real result shows a yellow box, a label, and a confidence such as `0.80`. The decision block should name a decision, a reason, a confidence, and a front-wheel angle. The line “Illustrative path, not verified.” should be visible. The performance line should change from “not measured yet” to milliseconds and FPS after the first results.
7. An empty room, or a scene the model does not know, can correctly say that nothing is at or above 0.50 confidence. The decision should be Hold or Unknown. That is not a promise the path is clear. Write down the decision and the reason. If speech is on, a short cue may follow after the direction settles. It should not talk on every frame. “Navigation uncertain” can repeat on its cooldown. “Obstacle detected. Hold.” is for a box in the center or on both sides. A turn phrase is for a committed left or right. None of those sentences means the path is clear.
8. Back out with the on-screen **Back to home** control. Leaving Live Vision can say “Simulation paused.” Open **Robot Simulation**. Manual drive starts selected and stays quiet. Try Forward, Steer left, Steer right, Stop, and Reset. The wheel number should change. Then select **Synthetic world**. The decision should name the synthetic map, and the wheel or the speed should change. After a short wait you should hear one turn phrase, then silence until the decision changes. The arrow is the virtual robot, not a real machine.
9. Open **Diagnostics** and **Settings**. Scenario cards should still say `NOT_STARTED`. Settings should say floor segmentation is not started. In Settings, play the voice sample, try a slower rate, and try Arabic if the voice-engine line says an Arabic voice is installed. If it says Arabic is not installed, the cue should stay in English. Quiet mode should leave turn phrases silent and still allow “Obstacle detected” and “Camera unavailable.” Turning speech off should stop the cues. The research-mode line cannot be turned off.

## TalkBack

Turn TalkBack on in Android accessibility settings. Move focus across the home buttons. Each button has a spoken description. The camera view and the detection overlay have spoken labels.

## What not to expect

- The decision text is on screen, and speech is a short repeat of it. Hearing a cue is not a clearance.
- A phone without an offline voice keeps the cue on screen and does not download one. This build does not request internet access.
- No physical robot motion. Camera steering stops when you leave Live Vision.
- No account and no camera upload. The model is already inside the APK.
- Emulator timings from `docs/TEST_RESULTS.md` are not this phone’s timings.

Device results belong in `docs/TEST_RESULTS.md` only after someone actually installs and runs the app.
