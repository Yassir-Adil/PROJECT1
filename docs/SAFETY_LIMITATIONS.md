# Safety limitations

TriGuide AI in this build is a **research and simulation** app.

- It is not a medical device, not a certified cane, and not a substitute for orientation and mobility training.
- Object detection can draw boxes from an on-device model. That is not a certified obstacle scan. Misses and false boxes are expected. Do not walk from these boxes.
- In 0.3.0 the virtual robot can steer from the horizontal position of those boxes, and from an authored simulator map. A camera decision does not measure distance. An empty frame is not open space. A center box does not prove a side is free. The drawn path is a sketch of the current command, labeled as not verified.
- Floor segmentation is disabled. No floor model is loaded, and a missing floor mask is not treated as open floor.
- The synthetic map uses coordinates written in the app. Those meters are not a camera measurement.
- `FutureHardwareController` is a mock and refuses commands. The on-screen robot does not move a physical machine.
- This build’s merged manifest requests camera access and does not request internet access. Frames are processed on device. A dependency that wanted network permission was stripped.
- Test scenario cards are placeholders. They have not been executed on a route.
- Indoor or outdoor travel decisions must be made by the user and a qualified human guide, not by this APK.
