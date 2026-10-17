# User testing guide

This build is Research and Simulation Mode. Detection is off.

## Install on a Redmi (or any Android 8.0+ phone)

1. Copy `outputs/apk/TriGuideAI-0.1.0-debug.apk` to the phone, or connect USB and run `adb install -r` on that file after USB debugging is enabled.
2. If the phone blocks the install, allow install from that source in Android settings. That prompt is yours to tap.
3. Open **TriGuide AI**.
4. Read the yellow research-mode line.
5. Tap **Start Camera** and allow the camera permission when Android asks.
6. Confirm you see a live preview and the text that detection is not running.
7. Back out, open **Robot Simulation**, and try Forward, Steer left, Steer right, Stop, and Reset. The numbers should change. The arrow is the virtual robot, not a real machine.
8. Open **Diagnostics** and **Settings**. Scenarios should say `NOT_STARTED`.

## TalkBack

Turn TalkBack on in Android accessibility settings. Move focus across the home buttons. Each button has a spoken description. The camera view is labeled as a live preview with detection off.

## What not to expect

- No spoken obstacle guidance yet.
- No physical robot motion.
- No account, no cloud upload, no model download in this APK.

Device results belong in `docs/TEST_RESULTS.md` only after someone actually installs and runs the app.
