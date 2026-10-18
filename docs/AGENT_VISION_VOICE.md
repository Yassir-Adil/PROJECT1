# Vision and voice agent

This note is what the vision/voice pass owns in 0.8.0. Path guidance stays the primary loop. Scene description and person names are secondary.

## Owns

- Camera steering phrases from 2D box position: slight left/right when a box is clearly to one side, a fuller turn when that box sits near the middle band, Hold or Unknown when the middle is blocked, both sides compete, the frame is empty, or the sample is bad.
- Spoken lines in English and Arabic that name the reported label and the side. Example: “Person on the left. Turn slightly right.” / “شخص على اليسار. انعطف يميناً قليلاً.”
- On-demand **Describe this frame**, plus a short brief only when several boxes are present and a navigation cue did not just speak.
- Android text-to-speech rate, pitch, and voice logging. No cloud speech.
- Local object notes (label, side, time) and an opt-in list of names the user confirms. No face photo.

## Does not own

- Floor segmentation. It stays `NOT_STARTED`.
- Metric depth. None is invented.
- Fingerprint or triple-tap behavior.
- A face recognizer. `recognize()` returns no match. Describe does not attach a saved name unless a future matcher supplies one.
- Paid or cloud voices. Those are listed for approval and are not integrated.

## Safety

A side suggestion is not a measured gap. An empty frame is not a clear path. Person memory cannot enroll someone from the camera by itself. Path guidance is the primary loop. Describe and names do not override it.

## Optional, not installed

Needs your approval before any key or subscription. Not in this APK:

- Google Cloud Text-to-Speech (Neural2 / WaveNet Arabic)
- Azure AI Speech, neural Arabic
- ElevenLabs

Already on the Redmi, free: Google Text-to-Speech, including offline Arabic `ar-xa-x-arz-local` and `ar-xa-x-ard-local`. No voice-pack install was required in the 0.8.0 smoke. RHVoice is GPL and is not a project dependency. A separate install is the user’s choice, and an Arabic voice there was not checked.
