# Codex verification checklist — TriGuide through 0.9.20 (foundation)

> Historical checklist below. Current engineering gate:
> `scripts/test/verify-local.ps1` (no phone mutation), 339 app / 10 sim baseline
> plus Python suites and Lint/APK/privacy checks. Current audit and limitations:
> `docs/ENGINEERING_AUDIT_2026-10-19.md`. Do not install an APK or assert the
> device version merely from the older examples below; verify version and hash.

**For:** Codex (desktop source + Redmi if connected)  
**Owner:** ear/scene tests separately; do not mark voice PASS without hearing.  
**Language:** Arabic + English short bullets  
**APK under test:** `android-app/app/build/outputs/apk/debug/app-debug.apk` — versionName **0.9.20** / versionCode **33**  
**Last phone package known:** Redmi may still show **0.9.18** until reinstall — re-check with `adb devices` + `dumpsys`.

---

## Safety (must fail the review if violated)

- EN: Never approve spoken or UI wording that claims **path clear** / free corridor as fact.
- AR: لا تعتمد أي جملة صوت أو واجهة تقول إن الطريق فاضي أو آمن.
- EN: Green strip only with validated fusion evidence; labeled **observed candidate — not a safety signal**. Empty/detector-only = no green.
- AR: الأخضر فقط مع دليل دمج صالح؛ مكتوب إنه مش إشارة أمان. الفراغ/الكاشف وحده = بلا أخضر.
- EN: Floor segmenter / ADE20K / SegFormer must not authorize CLEAR or TTS movement.
- AR: ما في موديل أرضية في الـ APK يخوّل CLEAR أو كلام مشي.
- EN: One soft camera-active tone ≠ safety. STOP preempts tones/speech overlays.
- AR: نغمة تشغيل الكاميرا مش أمان؛ STOP يقطعها.

---

## A) Desktop commands (always)

```text
cd android-app
# JDK 17 session — prefer repo scripts
..\scripts\build\assemble-debug.ps1
gradlew.bat :app:testDebugUnitTest :sim-core:test --no-daemon
```

Expected (foundation 0.9.20 — re-measure after run):

| Suite | Count | Result |
| --- | --- | --- |
| `:app:testDebugUnitTest` | **≥246** (adds foundation tests) | PASS (0 failures) |
| `:sim-core:test` | **10** | PASS |
| `assembleDebug` | APK `TriGuideAI-0.9.20-debug.apk` | SUCCESS |

Re-run and record **your** counts in `docs/TEST_RESULTS.md` if they differ.

Key unit areas to skim if anything fails:

- `PathRiskAnalyzerTest` — corridor **0.25–0.75**, cup/knife in corridor ≥ POSSIBLE, edge chair UNKNOWN
- `NavigationDecisionEngineTest` — fail-closed camera; no live CLEAR from empty/off-path
- `EvidenceFusionGateTest` — UNMAPPED / fail-closed
- `FloorSegmenterDiagnosticTest` — NO_MODEL seam; no nav/TTS coupling
- `DecisionEvidenceRecorderTest` — text ledger
- `AiVisionDiagnosticTest` — band colors; banned clearance phrases
- `VoiceGuidanceGateTest` / gesture / emergency-stop related tests
- `TricycleKinematicsTest` (sim-core)

---

## B) Features completed so far (verify in source + phone)

### 0.9.14 — safety navigation / UX baseline

- EN: Returning from More/tools does not silently restart guidance; explicit restart only.
- AR: الرجوع من More ما يعيد التوجيه لوحده.
- EN: Fail-closed navigation when camera evidence is weak/empty.
- AR: القرار يقفل (HOLD/UNKNOWN) لما الدليل ضعيف.
- EN: Installed historically on Redmi; re-verify only if rolling back.

### 0.9.15 — Floor segmenter diagnostic seam

- EN: `FloorSegmenterDiagnostic` + `FloorSegmenterCameraAdapter` on shared CameraX analysis; **no weights in APK**.
- AR: وصلة تشخيص تجزئة أرضية بدون موديل في التطبيق.
- EN: Results → `FloorMaskEvidence` / `EvidenceReplayHarness` only; class map **UNMAPPED**.
- AR: النتائج للتشخيص فقط؛ الخريطة UNMAPPED.
- EN: Does **not** feed NavigationDecisionEngine or TTS.

### 0.9.16 — Phase 2 ledger + EvidenceFusionGate

- EN: Candidate ledger in `docs/PHASE2_SEGMENTER_CANDIDATE_BENCHMARK.md` (MediaPipe DeepLab VOC / Selfie; ADE20K not cleared for APK).
- AR: سجل مرشحين؛ ADE20K مش مدمج؛ SegFormer ممنوع (NVIDIA NC).
- EN: `EvidenceFusionGate` fail-closed; production mapping UNMAPPED ⇒ live CLEAR cannot fire from mask.
- AR: بوابة الدمج تفشل بالإغلاق؛ CLEAR الحي من القناع مستحيل بالوضع الحالي.

### 0.9.17 — Narrow corridor + Evidence Recorder

- EN: Walking corridor **x ∈ [0.25, 0.75]** in `PathRiskAnalyzer`.
- AR: ممر المشي أضيق؛ كرسي على الحافة ما يفرض STOP.
- EN: `DecisionEvidenceRecorder` text-only via `GuidanceSession`; share/dump from Diagnostics.
- AR: مسجّل أدلة قرارات (نص فقط) من شاشة Diagnostics.
- EN: Previously installed as 0.9.17 on Redmi before 0.9.18.

### 0.9.18 — Milestone B AI Vision research overlay (this build)

- EN: Live Vision toggle **AI Vision on/off** — corridor band + PathRisk colors (green unknown/off-path, yellow possible, red blocked).
- AR: زر AI Vision على المعاينة الحية؛ ألوان المخاطر فقط.
- EN: **Not** floor segmentation; **not** wired to CLEAR/TTS authorization.
- AR: مش تجزئة أرضية ومش مربوط بـ CLEAR.
- EN: Diagnostics shows floor-segmenter seam summary + AI Vision legend + Evidence Recorder.
- AR: Diagnostics يعرض حالة الـ seam والأسطورة ومسجّل الأدلة.
- EN: No SegFormer / no ADE20K weights in APK assets.

### Cross-cutting (through 0.9.x)

- EN: Emergency STOP — immediate on touch-down (not a long 3 s hold); vibrate + spoken confirm; resume has the careful path.
- AR: إيقاف الطوارئ فوري عند الضغط؛ التأكيد الصوتي/اهتزاز؛ الحذر على الاستئناف.
- EN: Gestures: Describe / More / status / restart (swipe patterns); triple-tap restart; buttons secondary.
- AR: التحكم بالإيماءات أساسي؛ الأزرار ثانوية.
- EN: Offline: no `INTERNET` permission; on-device detector (EfficientDet-Lite0) + system TTS/ASR constraints.
- AR: بدون إنترنت في الصلاحيات.
- EN: Virtual three-wheel sim only; no physical robot required.
- AR: محاكاة ثلاث عجلات فقط.

---

## C) Redmi phone checks (only if `adb devices` shows `55aqpfqkvw9xbafe` as `device`)

```text
adb devices -l
adb -s 55aqpfqkvw9xbafe shell dumpsys package com.triguide.ai | findstr version
# expect versionName=0.9.18 versionCode=31
```

Optional reinstall: `.\scripts\test\redmi-install.ps1`  
Optional evidence: `.\scripts\test\redmi-evidence-baseline.ps1 -SceneId <id> -DurationSeconds 20`  
→ `outputs/reports/redmi-evidence/`

### Scene matrix (owner may do ear; Codex logs + screenshots OK)

| Scene | Expect (behavior) | EN / AR note |
| --- | --- | --- |
| Chair at **far edge** (outside 0.25–0.75) | Prefer UNKNOWN / no false STOP | Edge ≠ corridor block / الحافة مش عائق ممر |
| Chair **centered** in corridor | HOLD / BLOCKED path-risk; red if AI Vision on | Center blocker / كرسي في النص |
| Blank wall / closed door | UNKNOWN or HOLD; never “path clear” | Wall fail-closed / جدار |
| Carpet / mat / shadow | Quiet or careful unknown — no invented clearance | Mat ≠ open path / السجادة مش طريق فاضي |
| Describe gesture | Short scene line; cautious labels | وصف عند الطلب |
| More screen | Risk line, floor DIAGNOSTIC_SEAM, AI Vision legend | |
| Diagnostics | Evidence dump shareable; segmenter UNMAPPED / NO_MODEL | |
| AI Vision toggle | Band visible; colors match risk; caption “research only” | |
| Hold STOP | Immediate stop + confirm; guidance paused | إيقاف فوري |
| Restart (triple-tap / gesture) | Camera/guidance restart without spam loops | |

Voice: logcat `TriGuideVoice` / `TriGuideNav` is **not** proof of audible output — owner ear confirms.

---

## D) Explicitly NOT done (do not treat as shipped)

- EN: Live depth in APK (MiDaS / ARCore Raw Depth) — research only; Milestone C.
- AR: العمق الحي مش مدمج في الـ APK.
- EN: SegFormer / ADE20K floor weights in assets — blocked (licence / rights).
- AR: أوزان الأرضية غير مدمجة وغير مصرّح بها.
- EN: Live CLEAR from camera or mask; spoken «في طريق فاضي» / “path is clear”.
- AR: CLEAR الحي وكلام الطريق الفاضي ممنوع.
- EN: Junction / intersection detector (“مفترق طرق”) — planned/experimental (`KNOWN_ISSUES`).
- AR: مفترق الطرق مش جاهز.
- EN: Campus maps / rich offline voice intents — partial / later.
- AR: خرائط الحرم ونوايا صوت غنية لاحقاً.
- EN: Floor mask → navigation speech gate — future; needs rights-cleared model + Redmi bench.
- AR: قناع الأرضية ما يوجّه الصوت بعد.

---

## E) Codex report back (suggested)

1. Commands run + pass/fail counts  
2. `versionName` on device (or “ADB empty — phone skipped”)  
3. Which scenes observed + whether any CLEAR / “path clear” wording appeared (must be **none**)  
4. AI Vision overlay: on/off works? colors sensible?  
5. Blockers only (USB, unlock, installer UI)

Owner keeps ear/scene sign-off. Codex verifies engineering checklist above.
