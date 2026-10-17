# User testing guide

This build is Research and Simulation Mode. Version 0.3.0 can run an on-device detector and can steer the virtual robot from box position or from an authored simulator map. It is not a mobility aid. An empty detection is not a clear path. Do not walk from the on-screen decision.

## Install on a Redmi (or any Android 8.0+ phone)

USB debugging has to be turned on, and the phone has to accept this PC’s debugging prompt. Until `adb devices` lists the phone, a phone test has not happened.

1. From this project folder, with the phone connected:

   `adb install -r outputs\apk\TriGuideAI-0.3.0-debug.apk`

   Or copy that APK to the phone and install it there. If Android blocks the install, allow that source. That prompt is yours to tap.
2. Open **TriGuide AI**.
3. Read the yellow research-mode line.
4. On Home, the object-detection line should become `READY. MediaPipe EfficientDet-Lite0 int8`. Floor segmentation should say `NOT_STARTED` and disabled. If detection says `MODEL_MISSING` or `LOAD_FAILED`, no boxes should appear. Write down the exact lines.
5. Tap **Start Camera** and allow the camera permission when Android asks.
6. Point the back camera at a chair, a bottle, or a person in a normal room. A real result shows a yellow box, a label, and a confidence such as `0.80`. The decision block should name a decision, a reason, a confidence, and a front-wheel angle. The line “Illustrative path, not verified.” should be visible. The performance line should change from “not measured yet” to milliseconds and FPS after the first results.
7. An empty room, or a scene the model does not know, can correctly say that nothing is at or above 0.50 confidence. The decision should be Hold or Unknown. That is not a promise the path is clear. Write down the decision and the reason.
8. Back out with the on-screen **Back to home** control. Open **Robot Simulation**. Manual drive starts selected. Try Forward, Steer left, Steer right, Stop, and Reset. The wheel number should change. Then select **Synthetic world**. The decision should name the synthetic map, and the wheel or the speed should change. The arrow is the virtual robot, not a real machine.
9. Open **Diagnostics** and **Settings**. Scenario cards should still say `NOT_STARTED`. Settings should say floor segmentation is not started.

## TalkBack

Turn TalkBack on in Android accessibility settings. Move focus across the home buttons. Each button has a spoken description. The camera view and the detection overlay have spoken labels.

## What not to expect

- The decision text is on screen. This build does not speak it aloud.
- No physical robot motion. Camera steering stops when you leave Live Vision.
- No account and no camera upload. The model is already inside the APK.
- Emulator timings from `docs/TEST_RESULTS.md` are not this phone’s timings.

Device results belong in `docs/TEST_RESULTS.md` only after someone actually installs and runs the app.
