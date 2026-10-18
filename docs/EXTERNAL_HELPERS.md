# External helpers

Tools that sit beside the app. Nothing here is a project dependency. Paid items need a written yes before anyone installs them or adds a key. This APK removes `INTERNET`, so TriGuide cannot download a voice itself.

## Google text-to-speech, Arabic, free

The engine is **Speech Services by Google** (`com.google.android.tts`). TriGuide speaks Arabic only when that engine already has an offline voice whose language is `ar` and whose `isNetworkConnectionRequired` flag is false. A voice that still needs a network is ignored, and the cue stays in English.

On this Redmi, logcat has already reported two offline Arabic voices: `ar-xa-x-arz-local` (Egyptian) and `ar-xa-x-ard-local` (the local Gulf / Saudi pack). If the Settings line says both English and Arabic are installed, no new download is required. Play the voice sample and have someone confirm the speaker. `speak result=0` only means the engine accepted the text.

If Arabic is missing, download it on the phone:

1. Open **Settings** and search for **Text-to-speech**. On HyperOS the screen is also under **Additional settings → Accessibility → Text-to-speech output**. Some builds put it under **Sound & vibration**.
2. Set the preferred engine to **Speech Services by Google**. If that engine is not listed, install or update the free Play Store app **Speech Services by Google**, then come back.
3. Tap the gear beside that engine, then **Install voice data**.
4. Open **Arabic** (العربية) and download the **offline** voice. Do not stop on a voice that says it needs a connection.
5. In TriGuide, open **More → Settings → Language → Arabic**, then **Play voice sample**.

The download uses the phone’s own network inside the Google speech app. TriGuide still has no internet permission.

## Optional free on-device speech

These are not wired in. Do not add them as Gradle dependencies in this pass.

| Option | Licence | What to do |
| --- | --- | --- |
| RHVoice, as its own app | GPL-3.0 | A separate install is the user’s choice, for example from F-Droid. Do not copy it into this repo. An Arabic voice there was not checked. |
| Sherpa-ONNX with a Piper-class voice | Runtime is Apache-2.0. Each voice file has its own licence | Not bundled. A later decision would name the exact voice, its licence, and its size in `docs/DECISIONS.md` before any download. |
| eSpeak NG | GPL-3.0 | Small and offline. The speech is robotic and is a poor fit for guidance. Not a project dependency. |

## Paid speech — not approved

Do not install, subscribe, or add an API key unless you say so in writing.

- Google Cloud Text-to-Speech (Neural2 / WaveNet Arabic)
- Azure AI Speech, neural Arabic
- ElevenLabs

Each one needs a network permission, a key, and a subscription. They are not in this APK.

## Already on the phone

**TalkBack** is part of Android: **Settings → Accessibility → TalkBack**. No store install. A person still needs to walk TriGuide with it. That walk has not been done.

Do not install a face-recognition service or a cloud face database for this app. Person memory, if used, stays a name on this phone after an on-screen confirm, with no photo.
