# Safety limitations

TriGuide AI in this build is a **research and simulation** app.

- It is not a medical device, not a certified cane, and not a substitute for orientation and mobility training.
- Object detection can draw boxes from an on-device model. That is not a certified obstacle scan. Misses and false boxes are expected. Do not walk from these boxes.
- In 0.4.0 the virtual robot can steer from the horizontal position of those boxes, and from an authored simulator map. A camera decision does not measure distance. An empty frame is not open space. A center box does not prove a side is free. The drawn path is a sketch of the current command, labeled as not verified.
- Spoken cues repeat that research decision in a short phrase. They are not a safety proof, not a clearance, and not a reason to walk. Quiet mode and speech off do not make the detector more accurate.
- Floor segmentation is a **diagnostic seam only**. No rights-cleared floor model is loaded into navigation. A missing / UNMAPPED / empty mask is not open floor and cannot authorize CLEAR, STRAIGHT, or a turn. DeepLab-v3 / ADE20K / SegFormer weights are not bundled for movement.
- The AI Vision green strip (if ever shown) is labeled **“observed candidate — not a safety signal”** and requires validated fusion evidence. Detector-empty frames stay neutral. Soft camera-active tone means the stream started — not that a path is safe.
- Emergency STOP wins over soft tones, overlays, and analysis: guidance pauses, camera unbinds, interrupting speech confirms pause.
- Human walking tests with eyes closed are **prohibited** until independent safety validation, a controlled environment, a spotter, an emergency fallback, and explicit evidence exist.
- Describe this frame repeats the latest detector boxes, counted by left, center, and right. It is not a clearance and not a floor reading. A saved name is spoken only if a future matcher reports that consented person. This build’s matcher reports nobody.
- Spoken side cues name a detector box and a suggested turn. They are not a measured gap. A box in the middle band, an empty frame, or two competing sides still holds. Box area is not a distance.
- Object notes (label, side, time) stay in app-private storage under `guidance-memory/`. That folder is excluded from Android backup. Notes do not steer and do not upload.
- Person names are off until the user turns **Remember people** on in Settings. A name is written only after **Confirm save**. The camera does not enroll strangers. No face crop, embedding, or photo is stored. Saved names can be deleted on the same screen. There is no cloud face database.
- The synthetic map uses coordinates written in the app. Those meters are not a camera measurement.
- `FutureHardwareController` is a mock and refuses commands. The on-screen robot does not move a physical machine.
- This build’s merged manifest requests camera access and does not request internet access. Frames are processed on device. A dependency that wanted network permission was stripped.
- Test scenario cards run the navigation engine on an authored map. They are not a camera frame and they have not been executed on a route.
- Indoor or outdoor travel decisions must be made by the user and a qualified human guide, not by this APK.
