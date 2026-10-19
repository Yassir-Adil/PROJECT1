# Research / simulation bridge (separation)

**Product (this repo):** Android phone-first guidance in `TriGuide-AI`. Local/offline by default. Fail-closed navigation.

**Reference only (do not merge into the Android safety loop):**  
`C:\Users\yassi\Desktop\project 1\AI_SMART_ROBOTIC_GUIDE` — Python / Webots / ESP32 / BLE / dashboard ideas.

## Allowed use of the reference repo

- Architecture inspiration (perception → planner → actuator layers).
- Documented future bridge after independent safety validation.

## Forbidden in the Android safety path (this milestone and near-term)

- Importing Python, Webots, ESP32 firmware, BLE robot control, cloud/LLM APIs, or OpenAI into `NavigationDecisionEngine`, TTS authorization, or CameraX analysis.
- Treating simulator CLEAR as phone-camera CLEAR.
- Walking tests with eyes closed until controlled environment + spotter + evidence exist.

## Future bridge (not started)

Phone local perception evidence → optional offline log export → offline review tools. No INTERNET permission. No autonomous chassis until the owner explicitly requests hardware.
