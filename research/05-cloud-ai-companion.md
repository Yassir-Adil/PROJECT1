# 05 — Cloud AI companion: scene description + natural voice (research, not implemented)

Grok Bot, 2026-10-18 23:47 MYT (UTC+8). Answers Cursor/Codex proposal
`C:\Users\yassi\Desktop\TriGuide-AI\docs\AI_COMPANION_OPTION_2026-10-18.md` (read-only; nothing on the PC was changed).
App state at time of writing: 0.9.14, fully offline, manifest removes `INTERNET` and `ACCESS_NETWORK_STATE`.

**Nothing here authorises spending, uploads, or a new permission. Owner decision required (see §7).**

All prices are official list prices in USD, read on 2026-10-18 from the linked pages. Each page shows its own
"last updated" stamp in UTC. Cost math is in `reports/cost_calc_05.py` (+ `cost_calc_05.json`).
**Latency numbers in this report are not measured.** No vendor publishes an end-to-end latency for a
vision request, so latency must be measured on the Redmi (protocol: report 02).

---

## 0. TL;DR

| | Choice | Why | Cost / 100 descriptions (vision only) |
|---|---|---|---|
| **Recommended** | **Google Gemini `gemini-3.5-flash-lite`, paid tier, called through Firebase AI Logic + App Check** | Google recommends it for new projects. No shutdown date is announced. The Kotlin SDK keeps the key off the APK (no own backend needed). Image cost is set by `media_resolution`. The same vendor has Egyptian/Standard Arabic TTS. On the paid tier, data isn't used for training. | **≈ $0.05** (medium res) / ≈ $0.04 (low res) |
| **Runner-up** | **OpenAI `gpt-6-luna`** (Responses API, `store:false`, `reasoning.effort:"none"`) through a tiny backend | Cheapest per call. Clear data-controls page; API data isn't used for training. Owner already has an OpenAI/Codex setup. | **≈ $0.013** (image multiplier for luna assumed 1.2, verify with the calculator) |
| Voice | **Keep the on-device Android TTS as default and as fallback.** Optional cloud voice for non-urgent descriptions only: Gemini 3.8 Flash-Lite TTS (Egyptian/Standard Arabic) or Azure Neural `ar-EG`/`ar-SA`. | **No vendor offers Sudanese Arabic (`ar-SD`) TTS.** | +$0.10–0.18 per 100 |
| Free path | Develop with **no-people test scenes** on the Gemini free tier, **or** use the **Azure for Students** $100 credit (no card) + Azure Speech free 0.5 M chars/month. Use a paid/credit tier for any frame that may contain people. | The Gemini free tier trains on content and allows human review: **never send bystander images there.** | $0 to a few cents |

Safety rule (must be in code, not only in docs): **cloud output is description-only.** It can never tell the user to
walk, turn, stop-and-go, or that the path is clear/safe. Local STOP/HOLD/UNKNOWN always wins and pre-empts cloud
speech. Network loss degrades silently to the offline local description; it never weakens STOP.

---

## 1. Review of the Cursor/Codex proposal

The proposal is sound, and these parts agree with our standing rules (D4/D5 in PROJECT_LOG):
- STOP, camera loss, urgent cue and UNKNOWN/HOLD stay on the phone.
- Uploads are opt-in, on demand, one fresh keyframe, with a visible upload indication.
- The offline voice is the fallback.
- No key in the APK; use short-lived credentials.
- AI-voice disclosure; listen to the Arabic on the Redmi before choosing.

Gaps and corrections:
1. **"Check returned claims against local evidence where possible"** is good but not enough. Add a hard **output filter**:
   a deterministic text check that strips or rejects directive or clearance wording in Arabic and English (§5).
   Prose that sounds sure of itself must never reach the nav engine. Architecturally, the cloud module must not depend on `NavigationDecisionEngine`.
2. The proposal is **OpenAI-only**. Gemini is a better first pilot for this app: Kotlin client SDK with App Check (no
   own server), Arabic TTS in the same ecosystem, and resolution control. OpenAI is a strong runner-up (cheaper).
3. **Realtime voice conversation**: postpone. `gpt-realtime-2.1-mini` audio costs $10/M in and $20/M out
   ([pricing](https://developers.openai.com/api/docs/pricing)), and `gemini-3.8-live` is a separate pipeline. Neither is needed for
   the on-request description slice. It adds an always-listening microphone, which is a new privacy surface.
4. **"Sign in with ChatGPT" / ChatGPT plan access**: don't plan around it. A ChatGPT subscription is not an API
   key, and the proposal itself says not to assume it covers TTS or Realtime.
5. Missing: on-device **face blurring** before upload, a **daily cost cap**, a **stale-result drop** (result arrives after the
   scene changed), and the **PDPA cross-border notice** (§4).

---

## 2. Cloud vision options (on-request scene description)

### 2.1 Prices (official, read 2026-10-18)

| Provider / model | Input $/1M | Output $/1M | Image token rule | Source |
|---|---|---|---|---|
| Gemini `gemini-3.5-flash-lite` (Standard, paid) | 0.30 (text/image/video/audio) | 2.50 (incl. thinking) | 3.x: `low` 280, `medium` 560, `high` 1120 tokens per image | [pricing](https://ai.google.dev/gemini-api/docs/pricing) (upd. 2026-10-16), [media resolution](https://ai.google.dev/gemini-api/docs/media-resolution) |
| Gemini `gemini-3.1-flash-lite` | 0.25 | 1.50 | same | pricing; **shutdown 2027-05-07** ([deprecations](https://ai.google.dev/gemini-api/docs/deprecations)) |
| Gemini `gemini-3.8-flash` | 0.75 → **1.50 from 2027-01-01** | 3.75 → **7.50 from 2027-01-01** | same | pricing |
| Gemini 2.5 Flash / Flash-Lite | — | — | — | Google: access limited to past users; "for any new projects use 3.5 Flash-Lite or 3.8 Flash" (deprecations page) |
| OpenAI `gpt-6-luna` | 0.10 | 0.50 | 32-px patches × model multiplier (luna's multiplier isn't in the table; 1.2 assumed like other GPT-6/5.x) | [pricing](https://developers.openai.com/api/docs/pricing), [model page](https://developers.openai.com/api/docs/models/gpt-6-luna), [vision guide](https://developers.openai.com/api/docs/guides/images-vision) |
| OpenAI `gpt-5.6-luna` | 0.20 | 1.20 | patches × 1.2 | same |
| OpenAI `gpt-5.4-mini` | 0.75 | 4.50 | patches × 1.2 | same |
| OpenAI `gpt-4o` / `gpt-4.1-mini` (named in the brief) | 2.50 / 0.40 | 10.00 / 1.60 | tiles / patches × 1.62 | same; older generation, not recommended |
| Anthropic Claude Haiku 5.5 (≤100k prompt) | 0.10 | 0.50 | ⌈w/28⌉×⌈h/28⌉; new tokenizer ≈ +30 % text tokens | [pricing](https://docs.anthropic.com/en/docs/about-claude/pricing), [vision](https://docs.anthropic.com/en/docs/build-with-claude/vision) |
| Anthropic Claude Haiku 4.5 | 1.00 | 5.00 | same | same |
| Anthropic Claude Sonnet 5 | 2.00 | 10.00 | same | same |

Batch/Flex tiers are 50 % cheaper but asynchronous, so they're not suitable for a user waiting for an answer.

### 2.2 Estimated cost per description

Assumptions: one 768×576 JPEG, 300 prompt tokens, about 100 output tokens (one or two short Arabic sentences),
reasoning/thinking off or minimal. Thinking tokens are billed as output. Leaving `gpt-6-luna` at its default `medium`
effort, or Gemini thinking on, would raise both cost and latency.

| Model | Image tok | $ / description | **$ / 100** |
|---|---|---|---|
| gpt-6-luna | 519 (assumed ×1.2) | 0.00013 | **0.013** |
| Claude Haiku 5.5 | 588 | 0.00016 | **0.016** (vision support on 5.5 not separately confirmed on the page; verify) |
| gpt-5.6-luna | 519 | 0.00028 | 0.028 |
| **gemini-3.5-flash-lite** (medium / low) | 560 / 280 | 0.00051 / 0.00042 | **0.051 / 0.042** |
| gemini-3.8-flash (2026 / 2027 price) | 560 | 0.0010 / 0.0020 | 0.10 / 0.20 |
| gpt-5.4-mini | 519 | 0.0011 | 0.11 |
| Claude Haiku 4.5 | 588 | 0.0014 | 0.14 |
| Claude Sonnet 5 | 588 | 0.0033 | 0.33 |

So **every cheap option costs well under US$0.10 per 100 descriptions**. A full graduation demo (say 500 calls)
is a few cents to about $0.25. The deciding factors are privacy terms, key handling, Arabic, and lifecycle, not price.

### 2.3 Latency (not measured; what is known)
- No official per-request latency for any vision model above. OpenAI sells "Fast" and "Ultrafast" modes (2× and more of the price),
  which tells us latency varies by tier but gives no number.
- The phone side is a JPEG re-encode (<50 ms expected), a 50–120 kB upload over 4G/Wi-Fi, model time, and TTS.
- **Design budget (target, not evidence):** p90 ≤ 4 s from the user's request to the first spoken word. Hard client timeout 6 s,
  after which the app falls back to the local description. Measure 20 calls in a fixed scene on the Redmi per report 02
  (median/p90), on Wi-Fi and on mobile data, before quoting any number in the thesis.

### 2.4 Arabic quality
- No vendor publishes a dialect-level vision-description benchmark. The models will write fluent Modern Standard Arabic,
  and the prompt can ask for "simple Sudanese colloquial". Quality has to be judged by the owner, by ear and by reading.
- Plan: 30 labelled Redmi frames (same scenes as the floor/depth tests). For each model, score label correctness,
  hallucinated objects, wrong left/right, and dialect naturalness (owner 1–5). Report hallucination count separately; it matters most.
- Claude refuses to identify people ([vision limitations](https://docs.anthropic.com/en/docs/build-with-claude/vision)).
  For Gemini/OpenAI the prompt must forbid identity, face, ethnicity, and age guesses (§4).

### 2.5 Data retention / training use

| Provider | Training on API data | Default retention | Zero-retention option |
|---|---|---|---|
| **Gemini – paid tier** (billing account linked) | **No** | Prompts and responses "logged for a limited period" for abuse detection and legal needs; may be cached in any country ([terms](https://ai.google.dev/gemini-api/terms), eff. 2026-03-23) | Not self-serve on the Developer API (enterprise via Gemini Enterprise Agent Platform) |
| **Gemini – free tier / AI Studio** | **Yes**, and **human reviewers may read inputs/outputs**. Google says "do not submit sensitive, confidential, or personal information" | — | — |
| **OpenAI API** | **No** (since 2023-03-01, unless opted in) | Abuse-monitoring logs up to **30 days**. Responses API stores app state 30 days unless `store:false` ([data controls](https://developers.openai.com/api/docs/guides/your-data)) | ZDR / Modified Abuse Monitoring **only with OpenAI sales approval**. Images may still be kept for CSAM review. Singapore residency needs ZDR/MAM and is storage-only |
| **Anthropic API** | **No** (images not used to train, per vision FAQ) | Deleted **within 30 days**. Up to 2 years if flagged ([privacy center](https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data)) | ZDR per organisation **via sales** (privacy center, same link) |

Bottom line: a student can't realistically get ZDR. Choose a **paid tier with no training**, send **one blurred,
downscaled frame only on request**, and disclose the up-to-30-day provider retention in the privacy notice.

Other terms to respect: Gemini API terms require users to be 18+ and forbid apps directed at under-18s, and they forbid
uses that need medical-device clearance. TriGuide is presented as a research prototype, not a certified mobility aid,
and that framing must stay.

---

## 3. More natural voice

### 3.1 Options

| Option | Arabic locales | Sudanese? | Price | Offline | Notes |
|---|---|---|---|---|---|
| **Android `TextToSpeech` (current)** | whatever the installed engine offers (Google Speech Services usually has Arabic). Check `Voice.isNetworkConnectionRequired()` / `getFeatures()` ([API](https://developer.android.com/reference/android/speech/tts/Voice)) | No | Free | **Yes** | Already in 0.9.4+ with offline voice selection. Must remain the STOP/urgent voice |
| **Gemini 3.8 Flash-Lite TTS** / 3.8 Flash TTS | **Egyptian Arabic**, Standard Arabic ([speech generation](https://ai.google.dev/gemini-api/docs/speech-generation), upd. 2026-10-12) | No | ≈ $0.0015 / 10 s audio (Flash-Lite), $0.00225 / 10 s (Flash), **both double on 2027-01-01** ([pricing](https://ai.google.dev/gemini-api/docs/pricing)) | No | Prompt-steerable style. Free tier exists but trains on content |
| **Google Cloud TTS** | `ar-XA` only (Standard, WaveNet, 30 Chirp 3 HD voices) ([voices](https://cloud.google.com/text-to-speech/docs/list-voices-and-types)) | No | WaveNet/Standard $4/1M chars (4 M free/month), Chirp 3 HD $30/1M (1 M free/month) ([pricing](https://cloud.google.com/text-to-speech/pricing)) | No | Generic MSA |
| **Azure Neural TTS** | ar-AE, BH, DZ, **EG**, IQ, JO, KW, LB, LY, MA, OM, QA, **SA**, SY, TN, YE ([language support](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/language-support?tabs=tts)) | **No `ar-SD`** | Neural $15/1M chars, Neural HD $22/1M (Azure retail price API, Southeast Asia, 2026-10-18). **Free F0: 0.5 M chars/month** ([pricing](https://azure.microsoft.com/en-us/pricing/details/cognitive-services/speech-services/)) | No (embedded/disconnected containers are enterprise) | Two voices per locale. Egyptian is probably closest to what a Sudanese listener finds natural (owner to judge) |
| **OpenAI `gpt-4o-mini-tts` / `tts-1`** | "Arabic" via Whisper language list; **voices optimised for English** ([TTS guide](https://developers.openai.com/api/docs/guides/text-to-speech)) | No | mini-tts: $0.60/M text in + $12/M audio out; tts-1 $15/1M chars ([pricing](https://developers.openai.com/api/docs/pricing)) | No | **Policy requires telling users the voice is AI-generated** |
| **ElevenLabs** | "90+ languages" (v4) | No dedicated Sudanese | v4 $0.08 / 1k chars; Flash / v4 Turbo $0.04 / 1k chars ([API pricing](https://elevenlabs.io/pricing/api)) | No | Vendor claims ~75 ms (Flash) / ~100 ms (v4 Turbo) model latency, network excluded, not verified |

Cost for 100 descriptions × ~120 Arabic characters (12 k chars): Google WaveNet $0.05, Gemini Flash-Lite TTS ≈ $0.10,
Azure Neural $0.18, OpenAI tts-1 $0.18, Google Chirp 3 HD $0.36, ElevenLabs Turbo $0.48 / v4 $0.96. With free monthly
quotas (Google 1–4 M chars, Azure 0.5 M), a graduation project pays **$0 for TTS**.

### 3.2 Recommendation (voice)
1. Default and every safety cue: **on-device Android TTS**. Nothing urgent waits for a network voice.
2. Pilot cloud voice only for the *non-urgent* description text. A/B test on the Redmi: Android offline Arabic vs
   **Azure `ar-EG-SalmaNeural`/`ShakirNeural`** vs **Gemini 3.8 Flash-Lite TTS (Egyptian)**. Owner picks by ear.
   Write the text in simple Sudanese wording; the voice will still have an Egyptian/MSA accent.
3. A cloud voice adds a second network round-trip. Synthesis can only start after the description arrives.
   Alternative: speak the cloud *text* with the local voice (zero extra latency, zero extra data). That is the recommended first step.

---

## 4. Privacy (bystanders, consent, Malaysia PDPA, disclosure, keys)

### 4.1 Bystander images: minimise before anything leaves the phone
- Upload **only on an explicit user request**, **one frame**, never a stream, never in the background.
- **Blur faces on-device first** (MediaPipe Face Detector, Apache-2.0). Scene description
  doesn't need faces. Downscale to ≤768 px long side. Re-encode the JPEG (drops EXIF/GPS). Send no location and no device ID.
- **No storage**: don't keep the frame or the response on the phone. Set `store:false` (OpenAI). Log only status, latency and token counts.
- Prompt forbids: identifying anyone, describing faces, guessing age/ethnicity/religion/health, reading screens or documents
  of other people. People are reported only as "a person ahead-left".
- The existing **person-memory feature (name only, no face)** must never be sent to the cloud.
- An audible cue before upload ("بصوّر وبرسل الصورة") helps bystanders and the user know a photo is being sent.

### 4.2 Malaysia PDPA 2010 (Act 709) basics. Not legal advice; ask the supervisor/university.
- The PDPA applies to personal data processed in **commercial transactions**. A non-commercial graduation prototype
  may fall outside it, but follow its principles anyway: they're what examiners and any future release will expect.
- Principles to follow: **General (consent), Notice and Choice, Disclosure, Security, Retention, Data Integrity, Access**.
- **Amendment Act 2024** ([gazette P.U.(B) 522](https://www.pdp.gov.my/ppdpv1/wp-content/uploads/2024/12/PENETAPAN-TARIKH-PERMULAAN-KUAT-KUASA.pdf)):
  - From **1 Apr 2025**: "data user" becomes "data controller"; **biometric data is sensitive personal data**; the whitelist is
    replaced by risk-based **cross-border transfer** rules (s.129).
  - From **1 Jun 2025**: DPO appointment, **breach notification** (72 h to the Commissioner per Guideline), and data portability.
- **Cross-border** ([Guideline 3/2025](https://www.pdp.gov.my/ppdpv1/wp-content/uploads/2025/08/GP_CBPDT_EN-1.pdf)):
  cloud servers are outside Malaysia. Valid routes include the **data subject's consent** and "reasonable precautions and due diligence".
  The controller must **tell data subjects about the transfer in its notice**. Bystanders can't consent, which is why
  face blur and minimisation are required, not optional.
- Never do face recognition or biometric matching in the cloud (sensitive data since 2025).

### 4.3 What the app must disclose (before first upload, spoken + on screen + in a privacy page)
1. A photo is sent to *[provider]* servers outside Malaysia only when you ask, with faces blurred first.
2. What is sent (one photo plus the request text), what isn't (no audio, location, names, or person memory).
3. Provider retention (paid tier: Google keeps it a limited time for abuse checks; OpenAI/Anthropic up to 30 days; not used for training).
4. **The description is AI-generated and may be wrong. It is not navigation guidance and never means the path is safe.**
5. **The voice is AI-generated** (required by OpenAI policy if their TTS is used; good practice for all).
6. How to turn it off (toggle; offline mode is the default) and that turning it off loses nothing safety-related.
7. Possible cost / data usage.

### 4.4 API key handling
- **Never ship a key in the APK.** APKs are trivially decompiled.
- Option A (recommended for Gemini): **Firebase AI Logic** with **App Check** (Play Integrity, debug provider for dev).
  The Gemini key stays in Google's proxy, not in the app ([docs](https://firebase.google.com/docs/ai-logic), upd. 2026-10-14).
  Paid tier = Blaze plan + Cloud Billing; "prepayment usually required" ([pricing](https://firebase.google.com/docs/ai-logic/pricing)).
- Option B (OpenAI/Azure): **tiny backend** (Cloud Run / Cloudflare Worker / Azure Function) holding the key, with a
  per-install token, rate limit, **daily budget cap**, and payload-free logs.
- Option C (dev only): a **user-supplied key** typed in on the phone, stored with Android Keystore encryption, never logged and never backed up.
  Fine for the owner's own demo, not for other users.
- Set a provider-side **spend limit / budget alert** in addition to the in-app cap.

---

## 5. Safety contract for any cloud feature (non-negotiable)

1. **Description-only.** Use a structured output schema (`{"items":[{"label","position":"left|center|right","confidence"}],"text"}`).
   The prompt states it must not give directions or say anything is safe or clear.
2. **Deterministic output filter** after the model and before TTS. Reject or neutralise any sentence with directive or clearance terms:
   - EN: walk, go, move, step, turn, keep left/right, straight, proceed, clear, safe, free path, no obstacles.
   - AR: امشي، اتقدم، لف، دور، اتجه، خش، الطريق فاضي/سالك/آمن، مافي عوائق، تقدر تمشي.
   - On a hit, speak the local offline description instead and log `CLOUD_FILTERED`.
3. **Local wins.** STOP, HOLD, urgent hazard and camera-loss cues pre-empt and cancel cloud speech. While the nav state is
   STOP/HOLD-urgent, cloud audio isn't played. The cloud module has **no dependency on `NavigationDecisionEngine`**
   (enforced by a module boundary or test).
4. **Stale drop.** Discard a result if it arrives > 6 s after the request, or if the user started moving or another request superseded it.
5. **Network failure is silent and safe.** No permission, no network, timeout, HTTP error or bad JSON gives the existing local
   `SceneDescription` (with its "empty ≠ clear" wording). No retry loops, no repeated error speech. STOP latency doesn't change.
6. **AI disclosure** once per session before the first cloud description.

---

## 6. Free / cheap path for a graduation project
1. **Now (free):** speak cloud-style *text* with the local Android voice. No cloud TTS needed.
2. **Development (free):** Gemini free tier via Firebase AI Logic (Spark plan), **only with staged scenes containing no people**.
   Free-tier content is used for training and may be read by human reviewers.
3. **Any real-world frame (cents):** link billing (Blaze), prepay the minimum, set a budget alert. 500 descriptions on
   `gemini-3.5-flash-lite` ≈ **$0.25**.
4. **Alternative free funding:** **Azure for Students** gives a $100 credit for 12 months, no credit card, full-time students only,
   and includes Azure OpenAI ([page](https://azure.microsoft.com/en-us/free/students)). Azure Speech is free up to 0.5 M neural chars/month.
   Eligibility of the owner's university email is unverified. Azure OpenAI per-model prices weren't checked in this report.
5. **Runner-up paid:** OpenAI `gpt-6-luna`, ≈ $0.013 / 100. Needs a paid API key and the Option-B backend.

---

## 7. Proposed Cursor slice: **CLOUD-0 (opt-in, describe-only, off by default)**

Do **not** start until the owner explicitly approves: (a) photos may leave the phone, (b) the provider (Gemini paid tier recommended),
(c) the spending cap. Until then only the parts marked *offline-safe* may be built.

Scope:
- New product flavor `cloudDemo`. The default `offline` flavor stays byte-identical in permissions: **no `INTERNET`**.
- Module `:cloud` with interface `CloudSceneDescriber`, a fake implementation for tests, and a Firebase AI Logic Gemini implementation (`gemini-3.5-flash-lite`, `media_resolution` medium, thinking minimal, structured output).
- On-device face blur + downscale + re-encode before upload.
- `CloudTextSafetyFilter` (offline-safe, can be built now).
- Spoken by the local Android voice (no cloud TTS in CLOUD-0).

Acceptance criteria:
- **A1** The merged manifest of the `offline` flavor contains no `INTERNET`/`ACCESS_NETWORK_STATE` (automated manifest test). Only `cloudDemo` adds `INTERNET`.
- **A2** The cloud feature is off by default. First enable shows the §4.3 disclosure (spoken + screen) and needs an explicit "yes". There's a toggle to revoke. With it off, no network call is made (test with a fake client counting calls = 0).
- **A3** An upload happens only after an explicit Describe request: one frame, ≤768 px long side, re-encoded JPEG with no EXIF (test). Faces are blurred before upload (unit test on a fixture with a face: no unblurred face box in the uploaded bitmap).
- **A4** No secret in the APK: `apkanalyzer`/`strings` scan for `AIza`, `sk-`, `OPENAI`, `GEMINI_API_KEY` is empty. App Check is enforced in the Firebase console.
- **A5** `CloudTextSafetyFilter` passes at least 30 adversarial EN/AR tests (e.g. "The path is clear, walk forward", "الطريق فاضي امشي قدام", "turn left to avoid the chair"). Every one is blocked and replaced by the local description. Clean descriptions pass unchanged.
- **A6** Pre-emption: with nav in STOP/HOLD-urgent, cloud speech is never started, and an in-flight cloud utterance is cut when STOP fires (fake-clock test). The STOP reaction time measured in the existing STOP test is unchanged.
- **A7** Failure modes (no network, airplane mode, 6 s timeout, HTTP 4xx/5xx, malformed JSON, App Check failure) all lead to the local `SceneDescription`, at most one short cue, no crash, no retry loop (tests with the fake client plus one airplane-mode run on the Redmi).
- **A8** Stale results (> 6 s, or superseded) are dropped (test).
- **A9** Cost cap: ≤ 3 calls/minute and ≤ 50/day per install, enforced locally (test). Provider budget alert configured (manual check).
- **A10** Privacy logging: logs contain status, latency_ms, input/output token counts and the model id only. No image, no description text in release logs, no person-memory data in the request (test).
- **A11** The cloud module doesn't import or call `NavigationDecisionEngine`/`PathRiskAnalyzer`, and the nav state machine has no cloud input (architecture test).
- **A12** Redmi evidence: 20 Describe calls in a fixed scene on Wi-Fi and 10 on mobile data. Median/p90 latency and tokens are recorded per report 02. The owner ear-checks 5 descriptions for Arabic quality and wrong left/right. Results go into PROGRESS as *measured on Redmi*.

Out of scope for CLOUD-0: cloud TTS, Realtime/Live voice conversation, continuous upload, anything that changes navigation.

---

## 8. Sources (all read 2026-10-18)
- Gemini pricing https://ai.google.dev/gemini-api/docs/pricing (upd. 2026-10-16 UTC) · media resolution https://ai.google.dev/gemini-api/docs/media-resolution (2026-10-13) · deprecations https://ai.google.dev/gemini-api/docs/deprecations · terms https://ai.google.dev/gemini-api/terms (eff. 2026-03-23) · speech generation https://ai.google.dev/gemini-api/docs/speech-generation (2026-10-12)
- Firebase AI Logic https://firebase.google.com/docs/ai-logic (2026-10-14) · pricing https://firebase.google.com/docs/ai-logic/pricing (2026-10-13)
- OpenAI pricing https://developers.openai.com/api/docs/pricing · gpt-6-luna https://developers.openai.com/api/docs/models/gpt-6-luna · vision https://developers.openai.com/api/docs/guides/images-vision · data controls https://developers.openai.com/api/docs/guides/your-data · TTS https://developers.openai.com/api/docs/guides/text-to-speech
- Anthropic pricing https://docs.anthropic.com/en/docs/about-claude/pricing · vision https://docs.anthropic.com/en/docs/build-with-claude/vision · retention https://privacy.claude.com/en/articles/7996866
- Google Cloud TTS pricing https://cloud.google.com/text-to-speech/pricing · voices https://cloud.google.com/text-to-speech/docs/list-voices-and-types
- Azure Speech pricing https://azure.microsoft.com/en-us/pricing/details/cognitive-services/speech-services/ (prices via https://prices.azure.com retail API, southeastasia) · language support https://learn.microsoft.com/en-us/azure/ai-services/speech-service/language-support?tabs=tts · Azure for Students https://azure.microsoft.com/en-us/free/students
- ElevenLabs API pricing https://elevenlabs.io/pricing/api
- Android Voice API https://developer.android.com/reference/android/speech/tts/Voice
- PDPA Amendment dates https://www.pdp.gov.my/ppdpv1/wp-content/uploads/2024/12/PENETAPAN-TARIKH-PERMULAAN-KUAT-KUASA.pdf · Cross-border Guideline 3/2025 https://www.pdp.gov.my/ppdpv1/wp-content/uploads/2025/08/GP_CBPDT_EN-1.pdf · Breach notification guideline https://www.pdp.gov.my/ppdpv1/wp-content/uploads/2025/08/GP_DBN_ENG.pdf
