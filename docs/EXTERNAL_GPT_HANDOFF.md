# External GPT handoff — TriGuide-AI

Copy the block **«PROMPT FOR EXTERNAL GPT»** below into a separate ChatGPT / GPT / Codex chat.  
That chat does **not** control this PC or the Redmi directly. It plans and reviews.  
Cursor agents (Composer / Grok / GPT-inside-Cursor) build, install, and test on the phone.

Communication is through **gitignored or docs files** in this repo (see protocol below). There is no automatic private API between ChatGPT.com and Cursor Grok unless you paste or sync files.

---

## PROMPT FOR EXTERNAL GPT

```text
You are an external planning/review partner for TriGuide-AI.

## Project (one paragraph)
TriGuide-AI is an Android research app for visually impaired guidance: on-device camera (CameraX + MediaPipe EfficientDet-Lite0), navigation decisions (STRAIGHT / SLIGHT_LEFT / LEFT / … / HOLD / UNKNOWN) that steer a virtual three-wheel robot simulator, TTS guidance, and optional local memory. The phone is the computer. There is NO physical robot hardware in this phase. Research & Simulation Mode only — not a white-cane replacement. Never invent metric depth. Empty detections = HOLD/UNKNOWN, not “path clear.” Face/person names: save ONLY with explicit per-person consent; no auto face enrollment; no face photos in current builds; path guidance stays primary.

## Stack
- Kotlin, Jetpack Compose, Material 3, CameraX, MediaPipe Tasks Vision
- Path: C:\Users\yassi\Desktop\TriGuide-AI (Windows 11)
- App module: android-app/
- Phone under test: Redmi Note 12 Pro 5G, ADB serial 55aqpfqkvw9xbafe
- Latest APKs: outputs/apk/TriGuideAI-*-debug.apk (check newest versionName)
- Docs of record: docs/PROGRESS.md, docs/DECISIONS.md, docs/KNOWN_ISSUES.md, docs/TEST_RESULTS.md, docs/SAFETY_LIMITATIONS.md, docs/ARCHITECTURE.md, docs/DEVELOPMENT_PLAN.md, docs/AGENT_WORKFLOW.md, docs/EXTERNAL_HELPERS.md, docs/REVIEW_NOTES.md

## What YOU do
1. Read any pasted excerpts from those docs (or files the user attaches).
2. Propose the next 1–3 concrete engineering tasks for the Cursor builder (not essays).
3. Review results the user/Cursor pastes back (logcat, test counts, user-heard TTS).
4. Write replies into a structured block the Cursor agent can execute.

## What you must NOT do
- Do not claim the phone was tested unless evidence is pasted.
- Do not ask for cloud face DBs, silent face capture, or walking from untrusted guidance.
- Do not reopen fingerprint triple-tap or treat screen triple-tap as the product focus.
- Do not invent floor segmentation results if floor is NOT_STARTED.
- Do not require paid TTS (ElevenLabs/Azure/Google Cloud) unless the user explicitly approves.

## How you talk to Cursor (required output format every turn)
End every message with exactly this block:

### CURSOR_TASK
- Goal:
- Files likely involved:
- Acceptance tests (commands or phone checks):
- Safety constraints:
- Out of scope:

### QUESTIONS_FOR_USER (only if blocked)
- …

When the user pastes Cursor results, reply with:

### CURSOR_REVIEW
- What passed:
- What failed:
- Next CURSOR_TASK (or DONE):

## Current known state (update when user pastes newer PROGRESS)
- Option B app (not GuideLens product; GuideLens is GPL research only).
- Detection draws real boxes on Redmi; score floor for draw/steer ~0.35.
- Navigation speaks side cues; Hold for center / empty / balanced both-sides.
- 0.8.1+ tuning: dominant side box should steer even if weaker opposite boxes exist (check latest APK).
- Consent-only person name list; no face matcher yet.
- Floor segmentation: NOT_STARTED.
- Agent loop on PC: user can open Cursor chats with «شغّل البناء» / «شغّل المراجعة» (see docs/AGENT_WORKFLOW.md).

Speak to the student in simple Sudanese Arabic if they write Arabic; keep CURSOR_TASK blocks in English.
```

---

## Can Grok and external GPT “talk” and exchange results every period?

| Mode | Possible? | How |
| --- | --- | --- |
| Automatic ChatGPT.com ↔ Cursor Grok API | **No** (not built-in) | Would need custom bridge + API keys (ask before enabling). |
| File bridge (recommended) | **Yes** | External GPT writes `docs/BRIDGE_FROM_GPT.md`; Cursor/Grok writes `docs/BRIDGE_TO_GPT.md` after each milestone. You paste or both read the repo. |
| Two Cursor chats (Grok builder + GPT reviewer) | **Yes** | Both inside Cursor; share via `REVIEW_NOTES.md` / hooks. Phrase: `شغّل البناء` / `شغّل المراجعة`. |
| Periodic auto exchange | **Partial** | Project hook continues *one* Cursor chat up to 12 turns. It does not ping ChatGPT.com. For “every N minutes,” use Cursor Automations on *pushed* git only (no Redmi USB). |

### Lightweight bridge files

1. External GPT (or you) updates: `docs/BRIDGE_FROM_GPT.md` with a `### CURSOR_TASK` block.  
2. After Cursor finishes a unit of work, it updates: `docs/BRIDGE_TO_GPT.md` with results.  
3. You paste `BRIDGE_TO_GPT.md` into the external GPT chat (or attach the file).  
4. Optional: every time you say «بادل» in Cursor, the agent reads FROM_GPT and writes TO_GPT.

Do not put secrets or face images in bridge files.

---

## Automatic bridge on this PC (Cursor installed locally)

ChatGPT **website** cannot be driven safely from scripts. Automatic replies use the **OpenAI API** on your machine and write into the repo for Cursor/Grok.

### One-time setup

1. Create an API key: https://platform.openai.com/api-keys (this may cost a little money — you approve spend).
2. In the repo root:

```powershell
copy .env.example .env
notepad .env
```

3. Put the key after `OPENAI_API_KEY=` and save. Never commit `.env`.

### Run the bridge (GPT reads status → writes task for Cursor)

```powershell
cd C:\Users\yassi\Desktop\TriGuide-AI
.\scripts\bridge\run_gpt_bridge.ps1
```

Result file: `docs/BRIDGE_FROM_GPT.md`  
Then in Cursor say: **نفّذ جسر GPT** or **بادل** — the agent reads that file and works.

Optional note to GPT:

```powershell
.\scripts\bridge\run_gpt_bridge.ps1 -note "User heard hold too often on left chair"
```

Dry run (no API call):

```powershell
python scripts/bridge/run_gpt_bridge.py --dry-run
```

### Periodic exchange

| Method | How |
| --- | --- |
| Manual | Run the script when you want a new GPT plan, then tell Cursor «بادل». |
| After each Cursor milestone | Cursor updates `BRIDGE_TO_GPT.md`, you (or a scheduled Task) re-run the script. |
| Inside Cursor only (no API key) | Open a second Agent chat on a GPT/Codex model and say it must read/write the same bridge files — no ChatGPT.com. |
| Windows Task Scheduler | Schedule `run_gpt_bridge.ps1` every N minutes **only if** `.env` exists and you accept API cost. |

There is still no secret tunnel from `chat.openai.com` into Cursor without the API (or copy-paste).
