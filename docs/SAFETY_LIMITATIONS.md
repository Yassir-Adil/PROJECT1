# Safety limitations

TriGuide AI in this build is a **research and simulation** app.

- It is not a medical device, not a certified cane, and not a substitute for orientation and mobility training.
- Object detection can draw boxes from an on-device model. That is not a certified obstacle scan. Misses and false boxes are expected. Do not walk from these boxes.
- The robot view is a manual kinematic toy. It does not see the room and it does not move a physical machine.
- `FutureHardwareController` is a mock and refuses commands.
- This build’s merged manifest requests camera access and does not request internet access. Frames are processed on device. A dependency that wanted network permission was stripped.
- Test scenario cards are placeholders. They have not been executed.
- Indoor or outdoor travel decisions must be made by the user and a qualified human guide, not by this APK.
