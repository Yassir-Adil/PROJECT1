# Device compatibility

| Item | Value |
| --- | --- |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 35 |
| ABIs | Default Gradle set (includes `arm64-v8a` for typical Redmi phones and `x86_64` for the emulator) |
| Camera | `uses-feature` camera is `required=false` so a device without a camera can still install. Preview then shows an unavailable state. |
| Network | No `INTERNET` permission |

## This PC

- Android Studio is installed at `C:\Program Files\Android\Android Studio` (build id `AI-243.22562.218.2431.13114758` from `build.txt`).
- SDK platforms present: `android-34`, `android-35`. Platform `android-36` is **not** installed. System image `android-36` google_apis_playstore x86_64 is installed.
- AVDs listed by `emulator -list-avds`: `Medium_Phone_API_36`, `Pixel_8a`.
- `adb devices` on 2026-10-18 during the environment audit showed **no** authorized phone.

On-device results are recorded in `docs/TEST_RESULTS.md` only when an install actually runs.
