# Agent workflow

One sentence starts a chat that keeps working on this PC. A second sentence starts a reviewer that does not grab the same files.

## التشغيل — جملة واحدة

البناء (يكمل خطة المشروع، يبني APK، ويجرب على الريدمي إذا كان موصولاً):

1. افتح شات **Agent** جديد داخل مشروع TriGuide-AI.
2. اكتب: `شغّل البناء`
3. اتركه. هو يكمل لوحده.

المراجعة (ينظر في الكود والوثائق ويقترح أو يطبق إصلاحات صغيرة آمنة):

1. افتح شات **Agent** ثاني.
2. اكتب: `شغّل المراجعة`
3. اتركه. هو يكتب الملاحظات في `docs/REVIEW_NOTES.md`.

لو شات الكشف أو الأرضية لسه شغال، خلّيه يخلّص. لا تفتح بناء ثاني على نفس الملفات.

للإيقاف اكتب `أوقف الحلقة` أو اضغط Stop.

لو الحلقة ما كملت بعد أول رد، أغلق Cursor وافتحه مرة واحدة. بعدين اكتب الجملة من جديد.

## What runs without you

After that sentence, a project hook (`.cursor/hooks.json`) submits the next step when the agent finishes a turn. It does this up to **12** extra turns in that chat, then stops so usage does not run without a cap.

The builder, on its own:

- Reads `docs/DEVELOPMENT_PLAN.md` and the latest `docs/PROGRESS.md`.
- Does the next unfinished plan item. Phone stays the computer. No robot body.
- Does not reopen fingerprint control or screen triple-tap.
- Builds the debug APK and runs unit tests, and writes only results it actually ran.
- Installs on the Redmi only when `adb devices` shows `55aqpfqkvw9xbafe` as `device`.

The reviewer, on its own:

- Reads recent code and docs.
- Looks for architecture, TalkBack labels, 48 dp targets, contrast, tests, and performance.
- Appends notes to `docs/REVIEW_NOTES.md`.
- Edits product code only when the builder lock is idle or older than 45 minutes, and only outside files the builder listed.
- Skips any path that already has uncommitted edits from another chat.

Neither chat asks you questions unless it is blocked.

## What still needs you

The loop pauses and writes the reason in `docs/AGENT_STATUS.md` when it needs:

- The USB cable, USB debugging, or the phone unlock screen.
- The install confirmation on the phone.
- Someone at the phone to confirm speaker audio.
- A TalkBack walk, by a person.

It does not start an emulator to fill those gaps.

## How the loop is armed

The hook does nothing until the chat writes `.cursor/loop-state.json` with `enabled: true` and **this** chat's id. Other chats are not continued. The state file is local and gitignored.

To raise the cap, edit `loop_limit` in `.cursor/hooks.json`. `null` removes the cap and will keep spending usage until you press Stop.

English start phrases, if you prefer them: `Start TriGuide builder`, `Start TriGuide reviewer`, `Stop TriGuide loop`.

## جسر GPT الخارجي (API على الجهاز)

بعد ما تحط `OPENAI_API_KEY` في ملف `.env` (من `.env.example`):

```powershell
.\scripts\bridge\run_gpt_bridge.ps1
```

الملف الناتج: `docs/BRIDGE_FROM_GPT.md`.  
في شات Cursor اكتب: `نفّذ جسر GPT` أو `بادل` عشان الوكيل ينفّذ مهمة GPT ويحدّث `docs/BRIDGE_TO_GPT.md`.

التفاصيل: `docs/EXTERNAL_GPT_HANDOFF.md`.

## Cursor Automations are a different tool

An Automation opened from the Automations editor runs away from this PC. It checks out the GitHub copy of the repo. It does not see unpushed local edits, and it cannot use the Redmi USB cable. That is why the builder and the reviewer above are local chats plus a hook, not cloud Automations.

A cloud reviewer is useful only after you choose to push. Setting that up is one save in the Automations editor, and it was not turned on here:

- Open Automations from the Agents window.
- New automation, on a schedule you pick.
- Instructions: review the pushed branch and comment. Do not build for the phone and do not claim device tests.
- Save once.

Until that save, no cloud automation exists for this project.
