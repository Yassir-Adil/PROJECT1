# 08 — ARCore Depth integration: camera ownership, Raw Depth specs, drop-off check, licence/privacy (TriGuide AI)

Grok Bot, 2026-10-19 16:30 MYT (UTC+8). Target: Redmi Note 12 Pro 5G **22101316G** ("ruby"), Dimensity 1080 / Mali-G68, Android 14, offline app (no INTERNET), phone chest-mounted.
Follows report 07 (`reports/07-stairs-dropoff-detection.md`) and reviews Codex's ARCore probe (0.9.22 tree, read only, nothing written on the PC).

**Provenance labels:** **DOC** = Google ARCore docs / API reference. **ISSUE** = Google ARCore GitHub issue (Google staff comment unless stated). **SAMPLE** = official `google-ar/arcore-android-sdk` sample code (master `204ddfd`, 2026-10-15). **REDMI (me, read-only adb)** = `dumpsys` / `logcat -d` today; nothing installed or launched by me. **CODEX** = Codex docs/code, not re-run by me. **INFERENCE** = my reasoning, not measured.
**I ran no tests on the phone. No ARCore frame, depth image, FPS or thermal number in this report was measured by me.**

**Safety rule unchanged:** depth can only **raise** HOLD / POSSIBLE_OBSTACLE. "No drop seen", "no depth", "floor found" are **never** CLEAR. Cane stays mandatory.

---

## 0. What's new and verified today

- **REDMI (me, logcat -d, 16:07 / 16:08 / 16:10 MYT):** `TriGuideDepthCapability: DEPTH_CAPABILITY status=CHECKED availability=SUPPORTED_INSTALLED automatic=true raw=true frames=0 camera_resumed=false metric_accuracy=unverified navigation=none` — confirms Codex's claim independently (3 runs). ARCore also logged `W/ARCore-ContentProvider: No usable local profile` and `W/ARCore-ContextUtils: The API key for use with the Google AR service could not be obtained!` (§3.4).
- **REDMI (me, dumpsys):** `com.google.ar.core` versionName **1.57.262640093** (updated 2026-10-16 18:42), holds **INTERNET + ACCESS_NETWORK_STATE granted**. `com.triguide.ai` is now **0.9.22**, no INTERNET permission listed. Thermal status 0 at the moment of the query (CPU sensor 58 °C, a one-off snapshot while Codex was testing).
- **PC build intermediates (read only):** merged **debug** manifest adds only `<queries><package com.google.ar.core/>`, `com.google.ar.core.min_apk_version`, `InstallActivity`, and `com.google.ar.core=optional`. **No INTERNET.** Merged **release** manifest has no ARCore entries.

---

## 1. Camera ownership: can CameraX and ARCore run together?

### 1.1 Facts

| Question | Answer | Source |
|---|---|---|
| CameraX + ARCore session on the same camera at once? | **No.** Android camera is single-client; ARCore assumes it owns the camera. Sharing only works through ARCore's **SharedCamera** wrappers around **Camera2** callbacks, which CameraX does not expose. | ISSUE #1210 (Google, devbridie: "CameraX is not (easily) compatible with ARCore", must use `createARDeviceStateCallback` / `setCaptureCallback`, "not exposed in CameraX") https://github.com/google-ar/arcore-android-sdk/issues/1210 ; Android camera team answer https://stackoverflow.com/questions/68682709 |
| Google's recommended path if you only need frames for ML | Use **`Frame.acquireCameraImage()`** instead of CameraX ImageAnalysis; start from the `computervision_java` / `ml_kotlin` samples | ISSUE #1210 (devbridie, fredsa) ; SAMPLE `samples/ml_kotlin` (feeds ARCore CPU images to ML Kit) |
| CPU image format / size | `acquireCameraImage()` → **YUV_420_888**; ARCore's motion-tracking CPU stream is **640×480** by default; higher CPU resolutions selectable per device via `CameraConfig` (sample offers low/medium/high, e.g. 1280×720 / 1920×1080) | DOC Frame ref https://developers.google.com/ar/reference/java/com/google/ar/core/Frame ; DOC shared camera ("1x YUV CPU stream, currently always 640x480") https://developers.google.com/ar/develop/java/camera-sharing ; SAMPLE `computervision_java` |
| `acquireCameraImage()` availability | can throw `NotYetAvailableException` for several frames after start and "for a few frames at a time while running"; `ResourceExhaustedException` if images aren't closed; `DeadlineExceededException` if not the current frame | DOC Frame ref |
| FPS | `BLOCKING` update mode waits for a new image, "on most devices … 30 frames per second", timeout 66 ms; filter `TargetFps.TARGET_FPS_30` (avoid 60 fps configs) | DOC Config.UpdateMode https://developers.google.com/ar/reference/java/com/google/ar/core/Config.UpdateMode ; DOC camera configs https://developers.google.com/ar/develop/java/camera-configs |
| Needs OpenGL even with no preview? | **Yes.** `Session.update()` throws `MissingGlContextException` without a current GL context; a camera texture must be set via `setCameraTextureName(s)` (GL_TEXTURE_EXTERNAL_OES). Google staff: "You don't have to draw the camera image, but you need a context… A purely offscreen context created directly with EGL would work." Others report trouble doing it headless (#360, #1375 still open), and `update()` must run on the thread that owns the context. | DOC Session ref https://developers.google.com/ar/reference/java/com/google/ar/core/Session ; ISSUE #259 https://github.com/google-ar/arcore-android-sdk/issues/259 ; #360 https://github.com/google-ar/arcore-android-sdk/issues/360 ; #1375 https://github.com/google-ar/arcore-android-sdk/issues/1375 ; #1210 (fredsa: "Session.update must happen on the thread that has the GL context") |
| Back camera only? | Depth / tracking / planes need the world-facing camera. `FRONT_CAMERA` sessions don't track (hit tests empty). Use `CameraConfigFilter.setFacingDirection(BACK)` (ml_kotlin sample does this). | DOC Frame ref (FRONT_CAMERA note) ; SAMPLE ml_kotlin `MainActivity.kt:78` |
| Shared Camera (Camera2) limits | While ARCore is active, the app **must not call `setRepeatingRequest`**; ARCore creates a GPU surface (~1920×1080/1280) + a 640×480 tracking surface; **one** extra app surface at a listed CPU resolution is guaranteed, more is device-dependent. Mid-tier phones: roughly 1 YUV CPU + 1 GPU stream. **Shared mode can't use a hardware depth sensor** (irrelevant here: the Redmi has no ToF; Depth is from motion). `getImageMetadata()` throws in shared mode. | DOC SharedCamera https://developers.google.com/ar/reference/java/com/google/ar/core/SharedCamera ; DOC camera-sharing guide |
| Performance / thermal | Google gives no per-device numbers. It says: app and ARCore share CPU/GPU; CPU-bound apps starve SLAM → watch logcat **"VIO frequency low"**; monitor `PowerManager.getCurrentThermalStatus()` / `getThermalHeadroom()`; disable unused features; limit extra camera streams; Raw Depth costs **about half** the compute of full (AUTOMATIC) depth. | DOC performance https://developers.google.com/ar/develop/performance ; DOC Raw Depth https://developers.google.com/ar/develop/java/depth/raw-depth |
| Haptics | Google: **"Avoid using haptic feedback"** — vibration interferes with ARCore pose estimation. TriGuide uses `confirmPulse` / `successPulse` (50–140 ms) in `ui/ScreenTaps.kt`. | DOC runtime https://developers.google.com/ar/develop/runtime |
| Published ARCore thermal/latency on Dimensity 1080 | **None found.** Must be measured (report 02 protocol). | — |

### 1.2 Options compared

| Option | How | Pros | Cons | Verdict |
|---|---|---|---|---|
| **A. ARCore owns the camera; ARCore CPU image feeds MOSAIC + MediaPipe** | `Session` (no SHARED_CAMERA), GL thread runs `update()`, `acquireCameraImage()` (640×480 YUV, same size as today's CameraX analysis 640×480) + `acquireRawDepthImage16Bits()` + confidence from the **same Frame** | One camera client; RGB, depth, pose and intrinsics share one timestamp and `transformCoordinates2d` gives exact RGB↔depth mapping; Google-recommended for CV; simplest | Must add YUV→RGB (CameraX did it for us), a GL context, and manual lifecycle; no CameraX conveniences; ML competes with SLAM CPU | **Recommended** |
| B. Shared Camera (Camera2 + ARCore) | Rewrite camera on Camera2 with ARCore-wrapped callbacks; add an app ImageReader surface | Can add a higher-res or extra stream; can pause ARCore and keep Camera2 running | CameraX must go anyway; most complex; extra-stream limits on mid-tier; no gain for us (640×480 is enough for MOSAIC 512 / EfficientDet 320) | Not now |
| C. CameraX and ARCore at the same time | — | — | Not possible (single client) | Rejected |
| D. Switch CameraX ↔ ARCore at runtime | Close CameraX, resume ARCore (and back) | Keeps the existing tested CameraX path as fallback | Switch gap with **no frames** (unmeasured) → must be HOLD/UNKNOWN during the gap; ARCore restarts tracking/depth from zero each time | Only as an explicit mode change (e.g. "depth mode" on start), never mid-walk |

### 1.3 Recommended architecture (for 0.9.22 Scene Lab first, production later)

```
                 ┌──────────────── FrameSource (interface, exactly one active) ────────────────┐
CameraXFrameSource (today, fallback when ARCore unavailable)      ArCoreFrameSource (new, debug first)
                                                                   GL thread (GLSurfaceView or EGL pbuffer):
                                                                     session.update()  [BLOCKING, 30 fps config]
                                                                     camera.trackingState / failureReason
                                                                     acquireCameraImage()  → copy Y/U/V (strides!) → close()
                                                                     acquireRawDepthImage16Bits()+Confidence → copy if NEW timestamp → close()
                                                                     camera.pose, imageIntrinsics, textureIntrinsics, planes (HORIZONTAL_UPWARD)
                                                                   → AnalysisFrame{tsNs, yuv/rgb, rotation, pose?, depth?, conf?, tracking}
                                          ▼ (keep-only-latest hand-off, never block the GL thread)
     Vision executor @ ≤5–8 Hz: MOSAIC R4 (NNAPI) + EfficientDet-Lite0      Depth executor @ new-depth (~10 Hz max): DropOffAnalyzer (§2.5)
                                          ▼                                                     ▼
                                PathRiskAnalyzer / NavigationDecisionEngine: risk = max(detector, seg-stairs, depth); tracking loss ⇒ HOLD/UNKNOWN
```
Rules:
1. **One camera owner at a time.** Starting `ArCoreFrameSource` = unbind CameraX first; `onPause` = `session.pause()` before anything else; STOP stays instant and independent of ARCore.
2. **Copy, then close, on the GL thread.** ARCore images are bounded (`ResourceExhaustedException`); the ml_kotlin sample hands the live `Image` to a coroutine and closes later — don't copy that pattern. Use plane `rowStride`/`pixelStride` (the raw_depth sample assumes tightly packed buffers; don't).
3. **Config:** `DepthMode.RAW_DEPTH_ONLY` (half the cost of AUTOMATIC), `PlaneFindingMode.HORIZONTAL`, `LightEstimationMode.DISABLED`, `InstantPlacementMode.DISABLED`, no augmented images, no Cloud Anchors / Geospatial / semantics, `FocusMode.FIXED` (ARCore default, better for tracking), `CameraConfigFilter`: BACK, `TARGET_FPS_30`, `DepthSensorUsage.DO_NOT_USE`, smallest GPU texture offered.
4. **Display:** Scene Lab can render the camera texture with the sample's `BackgroundRenderer` (Apache-2.0) in a `GLSurfaceView`. A true headless pbuffer is possible per Google but has reported pitfalls (#360/#1375), so don't do it first.
5. **No vibration while an ARCore session runs** (DOC runtime). Use audio cues; or measure the effect on tracking before allowing short pulses.
6. **Budget gate (Redmi, measure):** `VIO frequency low` count = 0 over 5 min; `getThermalHeadroom(10)` logged; vision ≥ 5 Hz; new raw depth ≥ 5 Hz; GL `update()` p90 < 40 ms. If VIO starves, drop vision to 4–5 Hz before touching depth.
7. **Release builds:** keep ARCore debug-only until the licence notice (§3) and the measured gates pass. Then `com.google.ar.core=optional` + `checkAvailability()`; CameraX path remains for devices without ARCore.

---

## 2. Raw Depth: practical specs and the "below the floor" check

### 2.1 Specs (DOC unless stated)

| Item | Value | Source |
|---|---|---|
| Resolution | "typically around **160×120**, up to 640×480 on some devices"; same size for depth, raw depth and confidence; **aspect = GPU texture aspect**, so on a 16:9 texture expect a 16:9 image (e.g. 160×90 — **INFERENCE, log the real size on the Redmi**). Depth is a **crop** of the 4:3 CPU image; some CPU pixels have no depth. | Frame ref (acquireRawDepthImage16Bits) ; Depth guide https://developers.google.com/ar/develop/java/depth/developer-guide |
| Format | `D_16` little-endian **millimetres**, value = Z along the principal axis (not ray length); 0 = no estimate | Frame ref ; Depth guide "Understand depth values" |
| Range | Raw depth best **0.5–5 m** (deprecated 13-bit API text) / 16-bit API text says optimal 0.5–15 m, reliable to 25 m. **Error grows quadratically with distance.** Google's own occlusion shader ignores < 150–200 mm and > 7.5–8 m. Use 0.5–3 m for safety logic. | Frame ref ; Depth guide shader |
| Confidence | Y8 0–255, linear; 0 where no depth; Google: "filtering out pixels **below 128** tends to work well"; textured areas high, blank walls/floors ~0 | Frame ref (acquireRawDepthConfidenceImage) ; Raw Depth guide |
| Update rate | "about **10** updates per second"; other frames are **reprojections** → compare `Image.timestamp` with the previous one, use only new ones (or `frame.timestamp == rawDepth.timestamp`) | Frame ref ; Raw Depth guide |
| Motion needed | Depth comes "from motion"; `NotYetAvailableException` when "insufficient motion", poor light, occlusion or too few frames; `NotTrackingException` unless TRACKING; quality improves as the user moves while looking at the area | Frame ref ; Depth guide |
| Tracking loss | `Camera.getTrackingState()` PAUSED with `TrackingFailureReason` = EXCESSIVE_MOTION, INSUFFICIENT_FEATURES (blank walls/surfaces), INSUFFICIENT_LIGHT, CAMERA_UNAVAILABLE, BAD_STATE; NONE briefly after resume | https://developers.google.com/ar/reference/java/com/google/ar/core/TrackingFailureReason ; Camera ref https://developers.google.com/ar/reference/java/com/google/ar/core/Camera |
| Gravity | World +Y is always up relative to gravity (Google staff, ISSUE #119); horizontal planes typed `HORIZONTAL_UPWARD_FACING` (floor/tabletop), plane center pose +Y = normal | https://github.com/google-ar/arcore-android-sdk/issues/119 ; https://developers.google.com/ar/reference/java/com/google/ar/core/Plane.Type ; Plane ref |
| World frame drift | "every frame should be considered a completely unique world coordinate space" — never compare raw world coordinates across frames; use per-frame values or anchors | Pose ref https://developers.google.com/ar/reference/java/com/google/ar/core/Pose |
| Camera pose convention | OpenGL: +X right, +Y up, **−Z forward** | Camera ref (getPose) |
| Unprojection | sample: `fx,fy,cx,cy` from `camera.getTextureIntrinsics()` scaled to depth W/H; point = (d·(x−cx)/fx, d·(cy−y)/fy, −d) in camera space | SAMPLE `raw_depth_java/PointCloudHelper.java:48–82` |
| Hardware ToF on Redmi | none known; depth is motion-stereo only | INFERENCE (spec sheets list no ToF) |

### 2.2 Chest-mount walking: what to expect (INFERENCE + sources)
- **Walking helps depth** (it provides the parallax depth-from-motion needs), but **standing still at start** gives no depth → session must start in HOLD/UNKNOWN until TRACKING + first new raw depth + floor estimate. Plane detection typically needs a few seconds of movement (unmeasured on the Redmi).
- **Gait bounce / sway** is short, fast motion; ARCore bridges brief fast motion with the IMU (DOC runtime) but "extended periods of rapid movement" lose tracking. Expect EXCESSIVE_MOTION on turns or bumps → HOLD.
- **Body-worn precedent:** Project Guideline ran ARCore on a **waist-mounted** phone with runners (Pixel 6–8, camera ~1 m high, level, sighted spotter). Shows body-worn ARCore tracking can work, not that it works on a Redmi chest mount. https://research.google/blog/open-sourcing-project-guideline-a-platform-for-computer-vision-accessibility-technology/ , https://github.com/google-research/project-guideline
- **Texture-poor floors** (plain tiles, polished concrete) → confidence ~0 and INSUFFICIENT_FEATURES. That's exactly where a step edge can hide → no-data must be UNKNOWN, never "no drop".
- **Pitch:** a level camera (like Guideline) barely sees the floor 1–2 m ahead from 1.3 m height with ~4:3 FOV; tilt 20–40° down. Keep report 07's gate (pitch 15–45° below horizon, |roll| < 15°), but compute it from ARCore camera pose (−Z vs world −Y) when tracking, `TYPE_GRAVITY` otherwise.

### 2.3 How big is an 8–10 cm drop in depth terms? (INFERENCE, geometry only)
Camera height H = 1.3 m. Along one viewing ray, the floor beyond a drop h is hit farther by a factor (H+h)/H: +6.2 % for 8 cm, +7.7 % for 10 cm.

| Floor distance ahead | Slant range | Extra depth for 8 cm | for 10 cm |
|---|---|---|---|
| 1.0 m | 1.64 m | 0.10 m | 0.13 m |
| 1.5 m | 1.98 m | 0.12 m | 0.15 m |
| 2.0 m | 2.39 m | 0.15 m | 0.18 m |
| 2.5 m | 2.82 m | 0.17 m | 0.22 m |

So Raw Depth noise must stay well under ~5 cm at 2–3 m for a reliable 8 cm threshold. **No published Redmi/Mali figure exists; this is the first thing the Scene Lab must measure** (flat floor: SD of point heights per distance bin). Note also the **occlusion shadow**: just beyond a step-down edge the lower floor is hidden, so the first symptom is often "floor points stop", not "points below floor".

### 2.4 Floor plane estimate (per frame, gravity-aligned)
1. Gate: TRACKING; new raw depth timestamp; pose in mount range; not within 1 s of a tracking recovery.
2. Points: confident pixels (conf ≥ 128), depth 0.5–3.0 m, unprojected with texture intrinsics scaled to depth size, transformed by `camera.getPose()` of **that frame** to world.
3. Near-floor band: points whose horizontal distance from the camera is 0.5–1.2 m in the walking direction (camera −Z projected onto the horizontal plane), ±0.4 m lateral.
4. y_floor = robust mode of world-Y in that band (histogram 1 cm bins, or RANSAC with normal forced to world +Y, inlier ±3 cm). Need ≥ 150 inliers and inlier ratio ≥ 0.6 (thresholds to tune, log them).
5. Cross-checks: (a) camera height above y_floor in 0.9–1.7 m (chest prior); (b) if a tracked `HORIZONTAL_UPWARD_FACING` plane's polygon contains the user's foot point, |plane Y − y_floor| < 5 cm. Disagreement → `FLOOR_NOT_CONFIRMED` → UNKNOWN.
6. Smooth y_floor relative to the camera (camera height), not in world coordinates, because world coordinates can jump between frames.

### 2.5 Drop-off rule "points ≥ 8–10 cm below floor ahead" (proposed STAIR-1)
- Corridor: lateral ±0.4 m, distance 0.8–2.5 m, 10 cm distance bins.
- **POSSIBLE_DROP** in a bin if ≥ 15 confident points have `y < y_floor − 0.08 m` (and ≥ 2 adjacent bins agree).
- **FLOOR_ENDS** if near bins have confident floor points but ≥ 3 consecutive bins farther ahead (before 2.5 m) have none while the image region is in view → UNKNOWN + "possible edge" log; HOLD if it repeats (occlusion shadow).
- **RAISED** if ≥ 15 points `y > y_floor + 0.10 m` in the corridor → POSSIBLE_OBSTACLE (bonus: walls/boxes that the detector misses).
- Temporal: POSSIBLE_DROP in 2 of the last 3 **new** depth frames → HOLD, latched ≥ 3 s; release → UNKNOWN.
- Any of: tracking PAUSED, depth NotYetAvailable > 0.5 s, pose out of range, `FLOOR_NOT_CONFIRMED` → UNKNOWN (and HOLD if the user was being guided forward).
- Never: "path clear", lowering a detector/segmentation risk, or using reprojected (old-timestamp) depth as fresh evidence.

---

## 3. Licence, ToS, privacy, offline

### 3.1 Licence / terms
| Component | Terms | Source |
|---|---|---|
| `com.google.ar:core` AAR + Google Play Services for AR APK | **ARCore Additional Terms of Service** + **Google APIs Terms of Service** (not open source) | SDK LICENSE §1 https://github.com/google-ar/arcore-android-sdk/blob/master/LICENSE ; https://developers.google.com/ar/develop/terms ; https://developers.google.com/terms |
| ARCore SDK samples (e.g. BackgroundRenderer, PointCloudHelper) | **Apache-2.0** (SDK LICENSE §2); `ml_kotlin/third_party/YuvToRgbConverter.kt` Apache-2.0 (AOSP) but uses **deprecated RenderScript** → don't copy it | SDK LICENSE ; file headers (SAMPLE) |
| ARCore Additional ToS obligations | (1) your app's terms must tell users the app includes ARCore by Google and that its use is subject to the **Google Terms of Service** (policies.google.com/terms) and **Google Privacy Policy** (google.com/policies/privacy); (2) follow the user privacy requirements; (3) no Cloud Anchors/Geospatial in child-directed apps; (4) **don't re-create a Google product**; must have "substantial, independent value" | https://developers.google.com/ar/develop/terms (last modified 2022-05-11) |
| Commercial vs non-commercial | ARCore terms have **no non-commercial restriction**; a public non-commercial graduation project is fine. Not legal advice. | same |
| "No re-creating Google products" | Google's Lookout app also describes surroundings for blind users. TriGuide's guidance, stop/hold logic and robot/cart simulation are independent value, so **low risk**, but the README should frame ARCore as a depth sensor inside our own product, not a Lookout clone. (INFERENCE) | same |

### 3.2 Required disclosure text (verbatim from Google)
Put on the main menu / About / first-run notice, "prominently… easily accessible":
> **This application runs on Google Play Services for AR (ARCore), which is provided by Google and governed by the Google Privacy Policy.**

And in the app's terms / README (from the Additional ToS):
> This app includes ARCore functionality provided by Google. Use of ARCore is subject to the Google Terms of Service (https://policies.google.com/terms) and the Google Privacy Policy (https://policies.google.com/privacy).

Arabic UI version (my translation; keep the English text too): «هذا التطبيق يعمل على خدمات Google Play للواقع المعزز (ARCore)، وهي مقدّمة من Google وتخضع لسياسة خصوصية Google.»
The Cloud Anchors / Geospatial notice ("To power this session, Google will process sensor data…") is **not** needed: we use neither. Sources: https://developers.google.com/ar/develop/privacy-requirements

### 3.3 What Play Services for AR collects (Google's own statement)
From ARCore's Play **Data safety** guidance (https://developers.google.com/ar/develop/play-safety-label) and Help Center (https://support.google.com/ar/answer/12148145):
- **Always collected, regardless of API:** Google account IDs (if signed in) or device IDs; performance/diagnostic data (battery, latency, frame rate…); **ARCore API usage/app activity**. Sent over HTTPS, not shared with third parties.
- **Only for Cloud Anchors / Geospatial:** video data, precise location, anchor data. → **Depth/tracking/planes alone do not upload camera video** per Google's statement (not independently verified).
- **Consequence for "offline":** our APK has no INTERNET, and that is verified (merged manifest + dumpsys). But **Play Services for AR is a separate app that holds INTERNET (REDMI dumpsys)** and may send device IDs, diagnostics and API-usage analytics when the phone is online. So the honest statement is: *"TriGuide itself sends nothing. If depth mode is used, Google's ARCore service may send usage and diagnostic data to Google under Google's Privacy Policy. Camera images stay on the phone."* Put this in the privacy note. It's a change from "fully offline, nothing leaves the phone".
- If ever published on Play: the Data safety form must declare ARCore's collection (Google says the developer is solely responsible).

### 3.4 Does ARCore need the network?
- **Install/update** of Play Services for AR: yes (Play Store). Already installed on the Redmi (1.57.262640093).
- **`checkAvailability()`** "may initiate a query with a remote service" and return `UNKNOWN_CHECKING`; `checkAvailabilityAsync()` never returns that. DOC https://developers.google.com/ar/reference/java/com/google/ar/core/ArCoreApk ; https://developers.google.com/ar/develop/java/enable-arcore . Offline it may return UNKNOWN_* → app must treat that as "depth unavailable", not crash.
- **Device profile:** today's Redmi logcat shows `ARCore-ContentProvider: No usable local profile`, yet the session was created and depth reported supported. Similar messages in issues come with missing/fallback device profiles and worse tracking (#1595 https://github.com/google-ar/arcore-android-sdk/issues/1595 , #1791 https://github.com/google-ar/arcore-android-sdk/issues/1791). Whether a profile is fetched online and whether tracking quality differs offline is **unknown** → test (§5 step 6).
- The "API key could not be obtained" warning only matters for cloud features (Cloud Anchors/Geospatial); we don't use them (INFERENCE from the ARCore docs on API keys).
- Tracking/depth themselves run on device (all processing in Play Services for AR); I found no Google statement that they need a connection. **Must be confirmed by an airplane-mode run on the Redmi.**

---

## 4. Review of Codex's ARCore probe (0.9.22 tree, read only)

Files read: `app/src/debug/java/.../diagnostics/DepthCapabilityDiagnosticActivity.kt` (1,795 B, 15:52), `debug/AndroidManifest.xml`, `debug/.../SceneLabLauncher.kt`, `release/.../SceneLabLauncher.kt`, `app/build.gradle.kts` (`debugImplementation("com.google.ar:core:1.57.0")`, versionName 0.9.22 / vc35), merged debug/release manifests, `docs/WALL_RUG_DEPTH_ROADMAP_2026-10-19.md`. `docs/SURFACE_LAB_AND_DEPTH_2026-10-19.md` (referenced by the roadmap) **does not exist yet**.

**Good (no duplication needed):**
1. Debug-only dependency; release has a no-op `SceneLabLauncher` (`available=false`, `intent()=null`); merged release manifest has no ARCore. Correct seam.
2. `com.google.ar.core=optional` (AR Optional) in the debug manifest; no `requestInstall`, no `resume`, no image acquisition, no API key, no cloud features. Session closed in `finally`, then `finish()`.
3. Exported diagnostic activities are protected by `android.permission.DUMP` (adb shell only). Fine for debug.
4. Log line is honest: `frames=0 camera_resumed=false metric_accuracy=unverified navigation=none`. Matches the roadmap's "capability ≠ accuracy" language. I confirmed the log on the phone (§0).
5. Roadmap already says "one camera, close CameraX before opening ARCore, one frame source fans out to consumers" — same as my §1.3. I'm adding the sources, the GL-context requirement and the config/threads, not a different design.

**Findings (for the 0.9.22 Scene Lab / next probe):**
| # | Sev | Finding | Fix |
|---|---|---|---|
| R1 | Med | `checkAvailability()` result is only logged, then `Session(this)` is created regardless. Offline it can be `UNKNOWN_CHECKING`/`UNKNOWN_TIMED_OUT` (remote query). | Use `checkAvailabilityAsync`, or retry, and create the Session only on `SUPPORTED_INSTALLED`; log `UNKNOWN_*` as `depth=unavailable_reason=availability_unknown`. |
| R2 | Med | `isDepthModeSupported` depends on the **selected camera config**; the probe doesn't log which one. | Log `session.cameraConfig` (cameraId, facing, imageSize, textureSize, fpsRange, depthSensorUsage) and the list from `getSupportedCameraConfigs(filter BACK+30fps)`. |
| R3 | High (Scene Lab) | Next step needs `resume()` + `update()` — that requires a **current GL context on the calling thread** and `setCameraTextureName` before `update()` (`MissingGlContextException`). Compose has no GL surface by default. | Host a `GLSurfaceView` (sample BackgroundRenderer, Apache-2.0) and run `update()` in `onDrawFrame`; don't call `update()` from coroutines/executors. |
| R4 | High (Scene Lab) | CameraX `LiveSegmenterDiagnosticActivity` and an ARCore session must never be open together (single camera client). | Hard invariant + test: `ArCoreFrameSource.start()` requires CameraX unbound; log `camera_owner=arcore|camerax`. |
| R5 | Med | `Session(this)` on the main thread in `onCreate`; fine for a one-shot probe, but Scene Lab should not block UI with session create/config. | Create/configure off the UI frame, resume in `onResume`, pause first in `onPause`. |
| R6 | Med | No haptic guard: `confirmPulse`/`successPulse` vibrate; Google says haptics disturb ARCore pose. | Disable vibration while an ARCore session is resumed (or measure first). |
| R7 | Low | Depth images: use plane strides; the raw_depth sample assumes tightly packed buffers. Use **new-timestamp** depth only (≈10 Hz); reprojections aren't new evidence. | Encode in the depth-capture helper + unit test with a fake strided buffer. |
| R8 | Low | Privacy copy: the new ARCore disclosure (§3.2) and the "Play Services for AR may send usage/diagnostic data" note aren't in docs/UI yet. | Add to About / OPEN_SOURCE_LICENSES / SAFETY_LIMITATIONS before any release build includes ARCore. |
| R9 | Info | Merged debug manifest gains `<queries>com.google.ar.core</queries>` + `InstallActivity`; no INTERNET. OK. Keep the existing INTERNET `tools:node="remove"` audit and add an assertion that release has no `com.google.ar.core` entries. | — |

---

## 5. Measurement plan for Scene Lab "surfaces + depth" (Redmi only, no walking needed for steps 1–4)
1. Log per session: availability, cameraConfig (R2), depth W×H, confidence histogram (0/1–127/128–255), new-depth rate, `NotYetAvailable` time-to-first-depth, tracking state/reason timeline, `VIO frequency low` count, thermal status/headroom each 10 s.
2. Static, hand-moved phone (owner standing still): wall at 1.0 / 2.0 / 3.0 m (tape measure) → median and SD of center depth; flat textured floor → SD of point height per 0.5 m distance bin (this decides if 8 cm is detectable, §2.3); plain floor → fraction of confident points.
3. A **10 cm wooden board / book stack** on the floor at 1.5 m: does RAISED fire? Then the board's far edge: are points ≥ 8 cm below its top visible? (Safe stand-in for a step.)
4. Combined load: ARCore + MOSAIC + EfficientDet together for 5 min: vision Hz, depth Hz, `update()` p50/p90, VIO warnings, thermal. Compare against CameraX-only 0.9.21/0.9.22 baseline (~8 fps).
5. Chest mount, slow walk on a flat corridor (helper present): tracking-loss count per minute, false POSSIBLE_DROP per minute. No stairs yet.
6. **Airplane mode** repeat of step 1 (after a fresh reboot): does `checkAvailability`, session create, tracking and raw depth still work? Any change in time-to-first-depth?
7. Only after 1–6: the report 07 §6.7 stairs protocol (sighted helper, stop line 1 m, eyes open, nobody descends), STAIR-1 vs STAIR-0 on the same stairs.

---

## 6. Risks (summary)
1. **Depth noise vs 8 cm** at 2–3 m on a mid-range phone is unknown (§2.3). Could force a shorter look-ahead (≤ 1.5 m) = less reaction time.
2. **Texture-poor floors / dim light** → no depth exactly where edges hide; must stay UNKNOWN, which may make the app HOLD a lot.
3. **Tracking loss while walking** (bounce, turns, EXCESSIVE_MOTION) → frequent HOLD; must be measured with chest mount.
4. **CPU/GPU contention**: ARCore SLAM + depth + MOSAIC + EfficientDet on Dimensity 1080; VIO starvation degrades depth silently unless logged.
5. **GL lifecycle bugs** (MissingGlContext, pause/resume, ResourceExhausted from unclosed images) can freeze frames → stale-frame detection must still force HOLD.
6. **Privacy claim change**: Play Services for AR may send usage/diagnostic data; "nothing leaves the phone" is no longer exactly true when depth mode is on.
7. **Device profile / offline behaviour** unknown ("No usable local profile").
8. **Haptics** conflict with ARCore guidance.
9. **Vendor dependency**: closed-source Google service; behaviour can change with Play updates (1.57 updated 2026-10-16 without us).

---

## 7. Reviewer note for Cursor / Codex (#9, short)
- ARCore and CameraX can't share the camera. Use **one** `FrameSource`: ARCore owns the camera in depth mode, and its CPU image (640×480 YUV, same as today's analysis size) feeds MOSAIC + EfficientDet. No Shared Camera / Camera2 rewrite now.
- `session.update()` needs a current GL context on the same thread + `setCameraTextureName` → host a `GLSurfaceView` in Scene Lab (sample BackgroundRenderer, Apache-2.0). Copy Y/U/V and depth with strides, close images on the GL thread, hand copies to the vision/depth executors (keep-only-latest).
- Config: RAW_DEPTH_ONLY, planes HORIZONTAL, light/instant placement off, BACK + TARGET_FPS_30, FocusMode FIXED. Log cameraConfig, depth W×H, conf histogram, new-depth Hz, tracking reasons, `VIO frequency low`, thermal.
- Probe fixes: R1 (UNKNOWN_CHECKING → don't create Session), R2 (log cameraConfig), R6 (no vibration during ARCore).
- Depth may only raise HOLD/POSSIBLE_OBSTACLE. Tracking loss / no depth / floor not confirmed → UNKNOWN (HOLD while guiding). No CLEAR, ever.
- Before any release build with ARCore: add the verbatim disclosure "This application runs on Google Play Services for AR (ARCore), which is provided by Google and governed by the Google Privacy Policy." + Google ToS/Privacy links, and the note that Play Services for AR may send usage/diagnostic data.
- Measurement plan §5 (steps 1–6) before STAIR-1. I didn't run any of it.
