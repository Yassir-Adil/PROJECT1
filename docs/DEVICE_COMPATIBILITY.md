# Device compatibility

## Latest transport check — 2026-10-19

Known Redmi USB serial `55aqpfqkvw9xbafe` is authorized, with Windows ADB/USB
interfaces OK. Read-only shell and installed-APK pull comparison favored cable:
**2.118 s USB versus 3.646 s paired Wi-Fi** for the same 88,732,901-byte file.
Prefer explicit USB targeting while healthy; retain paired Wi-Fi as fallback.
Both file copies match original **0.9.21/34**. No phone settings or app changed.
Earlier instructions/checkpoints below are historical, not current failure
evidence; long-run reliability remains unverified. Full method and limits:
`docs/REDMI_USB_TRANSPORT_CHECK_2026-10-19.md`.

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

## Checked on 2026-10-18

- Emulator `Pixel_8a` results in `docs/TEST_RESULTS.md` are historical and are not the authoritative install for this user.
- Physical Redmi Note 12 Pro 5G (`55aqpfqkvw9xbafe`, model `22101316G`, device `ruby`): Android 14, SDK 34, HyperOS `OS2.0.16.0.UMOMIXM`. Cable ADB listed it as `device`. `com.triguide.ai` 0.4.1 is installed. Early the same day Windows also showed it as MTP only (`VID_2717` `PID_FF40`) before the ADB interface appeared.

## Fastest Redmi path (does not change app speed)

Cable ADB is the install path. It only copies the APK. It does not change on-device detection, CameraX, or speech. Do not switch the model, add a network upload, or enable wireless debugging to “make it faster.”

Use this binary, not the word `adb` alone, until a new terminal inherits PATH:

`C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`

On this PC, JDK 17 for Gradle is `C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1`.

### If `adb devices` is empty, unauthorized, or offline

Keep the phone unlocked. Use a data cable in a PC USB port, not a charge-only cable and not a hub if the phone disappears.

1. الإعدادات → حول الهاتف → اضغط **إصدار نظام التشغيل** (OS version / HyperOS version) 7 مرات حتى تظهر خيارات المطوّر.
2. الإعدادات → إعدادات إضافية → **خيارات المطوّر** (Additional settings → Developer options).
3. شغّل **تصحيح أخطاء USB** (USB debugging).
4. شغّل **تصحيح USB (إعدادات الأمان)** — USB debugging (Security settings). HyperOS may ask for a Mi account and a SIM. This is the switch that was off on 2026-10-18: `adb shell input` failed with `INJECT_EVENTS`.
5. شغّل **التثبيت عبر USB** (Install via USB). Without it, install can return `INSTALL_FAILED_USER_RESTRICTED`.
6. في خيارات المطوّر، اجعل إعداد USB الافتراضي **نقل الملفات** (Default USB configuration → File Transfer).
7. بعد التوصيل، اسحب شريط الإشعارات واختر USB → **نقل الملفات** (File Transfer). Charging only hides ADB on many Xiaomi builds. File transfer working is not the same as ADB authorized.
8. عند السؤال **السماح بتصحيح USB؟** (Allow USB debugging?) علّم **السماح دائمًا من هذا الكمبيوتر** (Always allow from this computer) ثم **السماح** (Allow). Check the RSA fingerprint is this PC.
9. If the status is `unauthorized`: خيارات المطوّر → **إلغاء أذونات تصحيح USB** (Revoke USB debugging authorizations), unplug, replug, tap Allow again. Then `adb kill-server` and `adb start-server`.
10. If a line named **وضع تقييد USB** (USB restricted mode) exists, turn it off.
11. If install says `INSTALL_FAILED_USER_RESTRICTED` / Install canceled by user: leave the screen on and unlocked, watch for **السماح** (Allow) or Install, and tap it. Then confirm steps 4 and 5 are on. One retry on 2026-10-18 succeeded after a canceled attempt. Do not turn off MIUI optimization unless those two switches are already on and install still cancels; that toggle reboots the phone.

Target only `55aqpfqkvw9xbafe`. Ignore any `emulator-*` line.

### Wireless ADB, only if the cable never lists `device`

Same Wi-Fi as the PC. خيارات المطوّر → **تصحيح الأخطاء اللاسلكي** (Wireless debugging) → Pair device with pairing code. On the PC: `adb pair PHONE_IP:PAIR_PORT` then `adb connect PHONE_IP:CONNECT_PORT`. Turn wireless debugging off when finished. It drops when Wi-Fi sleeps, and it is slower for the first install. It does not make the app more efficient.
