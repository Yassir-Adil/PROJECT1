# User testing guide

This build is Research and Simulation Mode. Version 0.2.0 can run an on-device detector. It is not a mobility aid, and detection does not move the virtual robot.

## Install on a Redmi (or any Android 8.0+ phone)

USB debugging has to be turned on, and the phone has to accept this PC’s debugging prompt. Until `adb devices` lists the phone, a phone test has not happened.

1. From this project folder, with the phone connected:

   `adb install -r outputs\apk\TriGuideAI-0.2.0-debug.apk`

   Or copy that APK to the phone and install it there. If Android blocks the install, allow that source. That prompt is yours to tap.
2. Open **TriGuide AI**.
3. Read the yellow research-mode line.
4. On Home, the object-detection line should become `READY. MediaPipe EfficientDet-Lite0 int8`. If it says `MODEL_MISSING` or `LOAD_FAILED`, no boxes should appear. Write down the exact line.
5. Tap **Start Camera** and allow the camera permission when Android asks.
6. Point the back camera at a chair, a bottle, or a person in a normal room. A real result shows a yellow box, a label, and a confidence such as `0.80`. The line under the title should name the model. The performance line should change from “not measured yet” to milliseconds and FPS after the first results.
7. An empty room, or a scene the model does not know, can correctly say that nothing is at or above 0.50 confidence. That is not a promise the path is clear.
8. Back out, open **Robot Simulation**, and try Forward, Steer left, Steer right, Stop, and Reset. The numbers should change. The arrow is the virtual robot, not a real machine. Camera detections do not drive it.
9. Open **Diagnostics** and **Settings**. Scenarios should still say `NOT_STARTED`.

## TalkBack

Turn TalkBack on in Android accessibility settings. Move focus across the home buttons. Each button has a spoken description. The camera view and the detection overlay have spoken labels.

## What not to expect

- No spoken obstacle guidance yet.
- No physical robot motion.
- No account and no camera upload. The model is already inside the APK.
- Emulator timings from `docs/TEST_RESULTS.md` are not this phone’s timings.

Device results belong in `docs/TEST_RESULTS.md` only after someone actually installs and runs the app.
