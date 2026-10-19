# Known Issues

## Current checkpoint — 2026-10-20

0.9.23/36 is installed/hash-verified. Bounded atomic local stores and immediate
RAM/deferred disk notes passed463 total tests after independent Cursor review.
Raw Depth instrument passed only invalid-duration refusal on Redmi; valid frames,
camera Stop/background, current UI/audio and metric/height accuracy await an
unlocked phone and controlled scenes. No walking or CLEAR/turn approval.
`docs/STORAGE_AND_RAW_DEPTH_2026-10-20.md` supersedes the older storage/backup
status below. Failed consent-disable persistence may not survive restart;
explicit durable-clear UX is not exposed yet; best-effort notes are not a
cross-file transaction or guaranteed cold-start history. Lint54/34 warnings.
Final source999bb71 is now remote-verified; ENV-006's old no-backup state is closed.

Current engineering audit and prioritized follow-ups:
`docs/ENGINEERING_AUDIT_2026-10-19.md`. Local gate now covers Lint and actual APK
policy. Private test image removed from the newly built debug APK, not from old
archived/installed copies. Original installed 0.9.22/35 was restored after static
QA; no new walking/depth/voice validation is implied. Local stores still need
atomic writes, bounded validated reads/reboot-safe chronology and clear deletion UX.

| ID | Severity | Issue | Status |
| --- | --- | --- | --- |
| DEPTH-CAP-20261019 | High | Redmi confirms ARCore AUTOMATIC/raw support, but no depth frames, confidence coverage or rug-edge/step height accuracy have been measured. Rug/floor classification is not traversability. | Next gate — isolated official Raw Depth experiment, free first. No hardware/cloud purchase. See `docs/WALL_RUG_DEPTH_ROADMAP_2026-10-19.md`. |
| NAV-RESTORE-20261019 | High | Non-saveable UI state could restart after activity restoration; a mixed direct-ADB/UI return check was inconclusive. New saveable restore always pauses and actual lab result returns to paused More. | Fixed defensively in 0.9.22: two restore tests and real More/lab/Stop/Back phone pass. Forced device recreation remains untested. |
| COMBINED-UI-20261019 | Medium | 0.9.22 adds bounded More surface lab, named boxes/theme, projected wall/floor/rug overlay and cautious explicit narration. Entry/Describe/Stop/Back pass; label errors/overlap, heard audio, TalkBack, sustained load and height accuracy remain unverified. | Partial — updated 0.9.22/35 retained, no navigation integration. See `docs/SURFACE_LAB_AND_DEPTH_2026-10-19.md`. |
| LIVE-SEG-20261019 | High | Isolated rear-camera MOSAIC runs with tested pause/late-result rejection. A short opt-in combined detector run now passes (callback-to-map p50/p90 42.85/52.61 ms); sustained production load, sensor capture age, labeled live accuracy and step/depth hazards are unverified. No throughput improvement is claimed across uncontrolled scenes. | Open — debug only; no segmentation-to-navigation/speech or release integration. See `docs/LIVE_SEGMENTER_DIAGNOSTIC_2026-10-19.md` and `docs/COMBINED_VISION_PREVIEW_2026-10-19.md`. |
| USB-WIFI-20261019 | Medium | Earlier USB disappearance root cause remains unproven. Cable is now authorized with Windows interfaces OK; 12/12 requests pass and one same-APK pull is faster than paired Wi-Fi (2.118 s versus 3.646 s). Long-run reliability unverified; wireless TLS connect port can change. | Prefer recovered USB while healthy; paired Wi-Fi fallback remains verified. See `docs/REDMI_USB_TRANSPORT_CHECK_2026-10-19.md` and earlier wireless report. |
| LANE-20261019 | Medium | Green walk-lane requires fused positive evidence (disabled live while UNMAPPED). Soft **camera-active** tone is not a path cue; volume/comfort not ear-confirmed on Redmi. | Open — owner visual+ear check after 0.9.20 install; never treat as CLEAR TTS |
| FOUND-20261019 | Info | Foundation stats/replay preserved; newer surface lab is accessible from More in installed 0.9.22/35. | Updated checkpoint — installed candidate hash-verified after USB tests, guidance paused. Original 0.9.21 backup retained. |
| NAV-BACK-20261018 | High | 0.9.13 STOP → More → Android Back silently restarted Live Vision by calling `start()`; could re-enable camera/guidance without user restart. | **Fixed in 0.9.14** — separate screen-only return, 3 new JVM tests, Redmi regression screenshot and explicit restart/STOP smoke. |
| FLOOR-20261018 | High | MOSAIC reached 58.81% Top31 mIoU on 2,000 public validation pairs. Static NNAPI replay reached 56.35 sequential inferences/s; isolated live callback-to-map now measures p50 30.15 ms / p90 32.17 ms under a 10/s cap. Sensor capture age, combined production load and labeled Redmi accuracy remain unverified. Argmax IDs are not calibrated confidence or traversability. | Open — debug diagnostics only; see `docs/ADE20K_OFFLINE_EVALUATION_2026-10-19.md` and `docs/LIVE_SEGMENTER_DIAGNOSTIC_2026-10-19.md`. |
| STAIRS-TOP31-20261019 | High | Top31 has no stairs/step/escalator category. Across 1,799,391 stair pixels in 180 validation images, MOSAIC labeled 19.45% as floor; float DeepLab 15.21%; UINT8 DeepLab 20.00%. These failures are excluded from headline Top31 mIoU. | Open — require independent hazard evidence before any movement policy; no floor-only clearance. |
| UI-20261018 | Medium | 0.9.14 stop-only Live Vision was phone-smoked: viewport and one STOP visible, ADB STOP and triple-UP gestures registered, paused Describe refused a stale frame. STOP → More → Back now stays paused. Human touch-down latency, large-text scrolling, and TalkBack order remain unmeasured. | Open — continue controlled Redmi checks via `docs/PHONE_TEST_CHECKPOINT_2026-10-18.md` |
| VOICE-20261018 | Medium | The fixed floor/mat view caused no voice request in the sampled 23:15 minute, but after the scene changed rapidly two `Obstacle ahead.` requests occurred 11 s apart. Current evidence cannot tell whether these were separate hazards or unnecessary re-alerts; audible quality is also unconfirmed. | Open — measure a controlled moving-scene episode and review episode-reset policy before changing suppression |
| DET-20261018 | High | Real camera still gave false labels: round table as `toilet` up to 0.87 and food packaging/items as `person` around 0.36–0.48. No positive walkable-area evidence, metric depth, or clear-corridor phone test. | Open — do not present object boxes as path clearance |
| DET-CLIP-20261018 | Low | Detector boxes with slightly out-of-frame coordinates fail bounds validation and become UNKNOWN instead of a possible obstacle alert. This is fail-closed for movement but can under-alert. | Open — measure clipped-box frequency on Redmi before choosing a clamping policy; noted in Cursor 0.9.12 review |
| TRUST-20261018 | High | Eyes-closed guidance is not trustworthy: COCO boxes miss walls/door frames/glass; no occupancy map. Owner correctly rejects current accuracy. | Open — roadmap `docs/GUIDANCE_TRUST_IMPROVEMENT_PLAN.md` (depth + planes before trust claims) |
| ENV-001 | — | Android Studio not installed | **Resolved** — present at `C:\Program Files\Android\Android Studio`, build `AI-243.22562.218.2431.13114758` |
| ENV-002 | — | SDK platforms / build-tools | **Resolved** (34/35 + build-tools) |
| ENV-003 | Low | Restart Cursor/terminals so User PATH picks up `adb` / JDK17 | Open until restart |
| ENV-004 | — | JDK 17 for Android | **Resolved** (user-local Temurin 17) |
| ENV-005 | Info | winget/choco/scoop not on PATH; used direct downloads | Accepted |
| ENV-006 | Low | GitHub source backup/authentication | Resolved: source999bb71 pushed and exact remote ref verified. Private captures/logs/weights/APKs remain local. |
| ENV-007 | — | No agents before bootstrap | Closed |
| ENV-008 | Info | JDK 25 MSI still default `java` on some shells until PATH order settles | Monitor |
| ENV-009 | Info | cmdline-tools XML version warning (v3 vs v4); packages still installed | Monitor |
| ENV-010 | — | Redmi Note 12 Pro 5G was MTP-only earlier on 2026-10-18 (`VID_2717` `PID_FF40`) | **Resolved** — `55aqpfqkvw9xbafe` is `device` (model `22101316G`, Android 14, HyperOS OS2.0.16.0). `com.triguide.ai` 0.4.1 is installed and Home launched |
| POL-001 | Info | User policy: installs and smoke tests run only on the personal Redmi. Do not use the emulator for those checks. Emulator results already written in `docs/TEST_RESULTS.md` are non-authoritative for this user going forward | Accepted |
| BUILD-001 | Low | `stripDebugDebugSymbols` left native libs unstripped (`libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, and now `libmediapipe_tasks_jni.so`); debug APK still built | Open |
| RES-001 | Info | GuideLens `assembleDebug` not run (`compileSdk` 36 platform missing; GPL, not shipping) | Accepted for Option B |
| TEST-001 | Low | Phase 3 left simulator buttons and TalkBack untested. The 0.3.0 session read Home, manual simulation, synthetic simulation, and Live Vision through `uiautomator`. TalkBack is still open (see TEST-002) | Open |
| DET-001 | Medium | 0.7.0 on the Redmi drew real boxes (person, bottle, laptop) after the viewport crop. The model also mislabels (frisbee, tv). A counted true-positive / false-alarm pass is still open | Open |
| DET-002 | Medium | Emulator camera returned zero objects again in 0.3.0, so bounding-box alignment and a camera-triggered turn were not seen | Open |
| DET-003 | Medium | No labeled true-positive / false-alarm count | Open |
| DET-004 | Info | Screen zone (left/center/right) is the box center in the frame. It is not a distance | Accepted |
| NAV-001 | Medium | Camera steering has no metric depth. The 0.3.0 emulator held at wheel 0 because the frame was empty. A real room has not been run | Open |
| NAV-003 | Info | The drawn path is a kinematic sketch of the current command. It is not a verified free path | Accepted |
| TEST-002 | Low | TalkBack was not walked. Manual Forward / Steer were not tapped. 0.4.1 moves Back to the top and handles the system Back key in code. That path was not tapped on the phone because input injection is denied (ENV-011) | Open |
| VOICE-001 | Info | 0.4.0 logcat on the Redmi: `speak result=0` for `Obstacle detected. Hold.` (engine accepted it). Speaker audio was not confirmed by ear. 0.4.1 Home still says offline English and Arabic voices are installed. No sample was played. This APK has no internet permission | Open |
| VOICE-002 | Info | Quiet mode and the speech on/off switch were not tapped on the Redmi. Gate unit tests cover both | Open |
| ENV-011 | Medium | HyperOS blocked `adb shell input` (`SecurityException` / `INJECT_EVENTS`) during the 0.4.1 check. One `adb install` of 0.4.1 returned `INSTALL_FAILED_USER_RESTRICTED`; the retry succeeded. On 0.6.0, `input tap`, `input swipe`, and `input keyevent` were accepted. The block can return. Steps: `docs/DEVICE_COMPATIBILITY.md` | Open |
| FP-001 | High | Fingerprint triple-tap cannot be implemented. On this Redmi the reader is the power button (`isPowerbuttonFps: true`, `isUdfps: false`). Android does not deliver those presses to a normal app. `BiometricPrompt` authenticates a finger; it does not count taps. Xiaomi’s `fingerprint_double_tap` setting is a system action, currently `none`, and is not an app API | Accepted — 0.6.0 does not read the power button. Screen triple-tap restarts guidance. The limitation is spoken once on a cold start |
| NAV-002 | Info | Floor segmentation is NOT_STARTED and disabled. DeepLab-v3 has no floor class and was not bundled. No floor mask is drawn. A missing mask is not open floor | Accepted for 0.7.0 |
| VOICE-003 | Medium | 0.8.0 committed Slight left and did not speak the turn. A heard Hold line mixed a side word with “center” | **Fixed in 0.8.1** — logcat `speak result=0` for “Toilet on the left. Turn slightly right. Not a measured gap.” Speaker audio for that sentence was not confirmed by ear |
| NAV-004 | Medium | 0.8.1 held a recognized left chair as “objects on both sides” when other boxes were present. A right chair did turn | **Fixed in 0.8.2 unit tests.** A dominant side box turns unless one opposite or center box is at least 70 percent as strong. Phone not checked: after the 0.8.2 build, `adb devices -l` listed no devices |
| NAV-005 | High | Camera detections were treated as path clearance or used to steer away from a side object, although detector boxes do not show whether an alternate route is walkable | **Source + JVM verified in 0.9.12; partial Redmi sample in 0.9.13.** The fixed mat scene stayed UNKNOWN/`Path not confirmed`; a wide-chair corridor-edge case remains untested. Camera-only false CLEAR/turn regressions fail closed in tests. Positive walkable-area evidence still absent. |
| REV-001 | Low | Drive-mode buttons still announce the short label. The simulated-mode sentence stays in the paragraph above. Object notes still write on the guidance tick after a 1 second throttle | Open |
