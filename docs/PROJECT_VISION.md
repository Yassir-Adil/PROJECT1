# Project vision

TriGuide AI is a phone-first navigation aid for people who are blind or have low vision. The phone camera, on-device models, spoken cues, and a **virtual** three-wheel robot all run on the handset.

The current build is a research and simulation app:

- Live camera preview is real.
- On-device object detection draws boxes only when the model returns them.
- Those boxes can steer the virtual robot from their position in the image. The app does not measure distance from a box, and an empty frame is not treated as open space.
- Floor segmentation is not included.
- The robot on screen is a kinematic simulation. No chassis is connected, and none should be purchased until the user explicitly asks.
