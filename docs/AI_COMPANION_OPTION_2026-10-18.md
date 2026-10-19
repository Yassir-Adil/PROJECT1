# Optional AI companion — proposal, not implemented

**Owner question (2026-10-18):** Can TriGuide use a human-like AI voice and a
ChatGPT-like vision/conversation assistant as a second pair of eyes?

**Current state:** 0.9.14 is offline; the manifest removes `INTERNET` and
`ACCESS_NETWORK_STATE`. MediaPipe boxes + Android offline TTS are the only
live vision/voice sources. No OpenAI API, backend, frame upload, or cloud voice
is configured. This document authorizes no cloud access or spending.

## Proposed split

1. Keep STOP, camera-loss handling, urgent hazard cue, and UNKNOWN/HOLD
   decisions on-device. No remote model response may directly emit walk,
   turn, clear-path, or virtual steering. Network loss must not weaken STOP.
2. Add an **opt-in, on-demand** AI scene description: send a selected fresh
   keyframe only after user request, with explicit upload indication; ask for
   a short description of landmarks and possible branches, with uncertainty.
   No autonomous every-frame upload. Check returned claims against local
   evidence where possible; never infer route safety from eloquent prose.
3. Offer higher-quality AI speech for nonurgent descriptions/conversation,
   with an offline Android voice fallback. Urgent local cues always outrank
   remote speech. Test Arabic (including the owner's preferred dialect) by
   listening on Redmi before choosing a voice.
4. If voice conversation is wanted, pilot Realtime separately from visual
   guidance. Images can be supplied as selected snapshots; this does not make
   a continuous video stream or a certified navigation sensor.

## Gates before implementation

- Owner chooses whether photos/audio may leave the phone and accepts possible
  API charges. Use a trusted backend and short-lived client credentials; never
  embed a standard API key in the APK. Assess whether eligible ChatGPT-plan
  Responses access is available; do not assume it covers Realtime/TTS.
- Benchmark image-description errors, Arabic voice quality, round-trip delay,
  data use, and battery on labeled Redmi scenes. Keep cloud feature disabled
  for actual mobility until independent safety evidence exists.
- Preserve a visible/accessible AI-generated voice disclosure and privacy
  controls. Review the current OpenAI data controls before enabling upload.

Official documentation checked 2026-10-18:
[vision](https://developers.openai.com/api/docs/guides/images-vision),
[speech](https://developers.openai.com/api/docs/guides/text-to-speech),
[Realtime](https://developers.openai.com/api/docs/guides/realtime-conversations),
[short-lived client secrets](https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create),
[Sign in with ChatGPT](https://developers.openai.com/siwc/quickstart),
[data controls](https://developers.openai.com/api/docs/guides/your-data).
