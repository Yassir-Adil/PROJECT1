# Guidance, voice, and description — trust improvement plan

**Date:** 2026-10-18  
**Context:** Owner feedback — current live guidance is not trustworthy with eyes closed. Detection misses walls and structural barriers; speech and Describe are not accurate enough to navigate by.

**Standing safety rule:** Camera-only RGB object detection **cannot prove** a path is clear. Never say “path is clear” / “في طريق فاضي”. Prefer “no obstacle seen in this frame — go carefully”.

---

## 1. Why trust is low today (honest diagnosis)

| Gap | What TriGuide does now | Why eyes-closed navigation fails |
|-----|------------------------|----------------------------------|
| **Walls / free space** | EfficientDet-style COCO boxes (chairs, people, bottles…) | Walls, door frames, glass, open doorways, and floor drops are **not** COCO classes. Empty detections ≠ empty corridor. |
| **Distance** | 2D box size/position heuristics | No metric depth → “near” vs “far” is guesswork. |
| **Persistence** | Mostly per-frame / short vote windows | A wall that leaves the frame is forgotten; no occupancy map. |
| **Describe** | Speaks detector labels + humility text | Useful for “what boxes fired”, not for “can I walk”. |
| **Voice** | Sparse hazard cues (good) but still built on weak evidence | Quiet app ≠ safe path; user may still over-trust a rare “go carefully”. |

**Verdict:** Improving copy alone will not make eyes-closed use safe. The stack must add **structure + depth + temporal map**, and voice must stay **humble and sparse**.

---

## 2. What research and open systems recommend

### 2.1 Project Guideline (Google Research) — primary reference

[github.com/google-research/project-guideline](https://github.com/google-research/project-guideline)

On-device stack that is closer to “trustworthy guidance” than box-only apps:

1. **ARCore pose** — phone position/orientation in a world frame.
2. **Segmentation** (guideline / path cues) + **monocular depth** per frame.
3. **Depth aligned to AR features** (RANSAC) so depth is less fantasy.
4. **Occupancy map** updated over time from depth point clouds.
5. **Control system** — lateral error, target point ahead, stop when tracking lost or no safe corridor.
6. **Audio design** — low-latency AAudio, pitch/pan/spatial packs; not long sentences.

**Takeaway for TriGuide:** Path risk should come from a **map + depth corridor**, not from “chair at x=0.4”. Stop conditions matter as much as turn cues.

### 2.2 Depth on phones (usability evidence)

PMC study on smartphone distance estimation for BLV apps ([PMC10939328](https://pmc.ncbi.nlm.nih.gov/articles/PMC10939328/)):

- Centre of image at 1–3 m: several methods are usable.
- **Periphery is much worse** for ML-only depth.
- LiDAR / ARKit-style depth wins on accuracy when hardware exists.
- Pure RGB ML depth is cheaper and more portable but must be treated as **soft evidence**.

**Takeaway:** On Redmi-class devices (no LiDAR), use **monocular depth as a weak sensor** + never claim centimetre precision in speech.

### 2.3 Indoor walls from depth planes

HCI literature on depth for VI obstacle avoidance: treat large **vertical planes** (wall-like normals, large area) as **hard barriers**, not as “objects to name”. Walls should trigger HOLD / STOP / “wall ahead”, not Describe-style labels.

### 2.4 OpenBot / GuideLens-style lessons

- Prefer **waist/chest phone mount** and a fixed forward FOV over hand-waving.
- Separate **perception** (what is sensed) from **policy** (what to say / when to stop).
- Log every decision with reason codes for post-walk review (already partly in PathRisk).

---

## 3. Target product behaviour (eyes-closed bar)

User must be able to answer “do I trust this enough to walk slowly in a known room?” only after these are true:

1. **Structural barriers** (wall/large plane closing the corridor) produce a spoken HOLD or STOP within ~1 s of entering the walking corridor in the frame (or mapped cells).
2. **No false CLEAR** after empty detector frames when depth/plane evidence says otherwise.
3. **Voice:** danger and HOLD first; direction hints rare; Describe never implies clearance.
4. **Fail closed:** tracking lost, camera failed, or depth unavailable → silence or “cannot guide — use cane”, never “go carefully” as if sensing worked.
5. Owner ear-test in a hallway with a blank wall and an open door — wall must be caught; doorway must not be sold as “clear”.

---

## 4. Phased roadmap (implement in order)

### Phase A — Honesty and voice (days, low risk)

Already partly done; finish and lock:

- [ ] Describe always ends with fixed disclaimer (boxes ≠ clearance).
- [ ] Ban clearance language in all spoken templates (unit-test string list).
- [ ] Empty / unknown → silence or one careful line; never “go straight” as confidence.
- [ ] Emergency stop (3 s hold) + pause speech (0.9.7 UI).
- [ ] Log reason codes + cue boxes for every spoken navigation line.

**Exit:** Owner confirms the app no longer “sounds sure” when it should not.

### Phase B — Corridor geometry from current detector (1–2 weeks)

Still no depth, but less wrong:

- [ ] Walking corridor mask (x 0.25–0.75, lower frame) as sole place that can raise POSSIBLE/BLOCKED from boxes.
- [ ] Large near boxes spanning width → BLOCKED regardless of COCO label.
- [ ] Off-corridor detections never produce CLEAR.
- [ ] Temporal vote: require N distinct frames before speaking a turn (R1a pattern).
- [ ] “Wall-like” heuristic v0: very large, tall, centred, high-aspect boxes or full-frame dark edges → HOLD experimental (document false positives).

**Exit:** Unit tests for corridor + trip hazards; REDMI hallway still fails on blank walls (expected) but chairs/tables stop lying as CLEAR.

### Phase C — Monocular depth + free-space (2–4 weeks) — **highest leverage**

- [ ] Prefer **LiteRT CompiledModel + MiDaS-small** (`litert-community/MiDaS-small`, ~33 MB fp16, NHWC 256²) — verified path for on-device relative inverse depth; licence check before bundling (DECISIONS + OPEN_SOURCE_LICENSES).
- [ ] MediaPipe Tasks has no first-class Depth Estimator task for navigation; do not wait on it. ARCore Depth API only if Play Services + device support (spike separately).
- [ ] Build per-frame **corridor depth strip** (bottom-centre): median relative depth, % of “near” pixels.
- [ ] Map: CLOSE_MASS → POSSIBLE_OBSTACLE; VERY_CLOSE_MASS → BLOCKED/HOLD. Relative depth ≠ metres — speak distance categories, not numbers.
- [ ] Fuse with EfficientDet: either modality can raise risk; neither alone may claim CLEAR.
- [ ] Performance budget: ≤15–20 FPS combined on Redmi; drop Describe quality before dropping depth. Expect GPU delegate; CPU fallback may be too slow (MiDaS fp16 notes).

**Exit:** Blank wall and large furniture produce HOLD in lab video replay without needing a COCO label.

### Phase D — Planes / walls and ARCore occupancy (4–8 weeks)

Follow Project Guideline shape, adapted indoors:

- [ ] Optional ARCore session when Play Services available (graceful fallback).
- [ ] Project depth into a short-range **occupancy grid** in front of user (2–3 m).
- [ ] Plane / wall detector: large vertical surfaces in corridor → WALL_AHEAD / HOLD.
- [ ] Stop if pose tracking lost for >T ms while guiding.
- [ ] Research Apache-2 / MIT models only; record in OPEN_SOURCE_LICENSES.

**Exit:** Eyes-closed walk in a known corridor with cane backup; walls and closed doors stop speech/policy correctly.

### Phase E — Audio UX for trust (parallel with C–D)

Project Guideline’s lesson: **tones > long sentences**.

- [ ] Distinct haptic+tone for STOP / HOLD / mild nudge (left/right).
- [ ] Spoken words only for: STOP, HOLD, chosen direction confirm, Describe on demand.
- [ ] Spatial pan for left/right if latency allows (AAudio later; TTS first).
- [ ] Rate limit: same cue family ≤1 / N seconds unless severity rises.

**Exit:** Owner can navigate a short known path by tone+haptic with eyes closed, cane in other hand.

### Phase F — Evaluation harness (ongoing)

- [ ] Indoor scripted routes: blank wall, chair corridor, open door, glass door (expected fail), low light.
- [ ] Record video + decision log; score false CLEAR and missed WALL.
- [ ] Never mark “passed eyes-closed” without owner confirmation by ear and cane.

---

## 5. What we will **not** do

- Cloud vision as the primary path (privacy + latency + offline requirement).
- Claiming “wall detection done” from COCO chair/person models.
- Emulator-only validation for guidance trust.
- Replacing the white cane / human judgment.

---

## 6. Suggested next build unit (after 0.9.7 UI)

1. Phase A string bans + Describe disclaimer tests.  
2. Start Phase C spike: one depth model on Redmi, corridor depth strip → HOLD, no new spoken clearance.  
3. Document spike FPS and failure modes in `docs/TEST_RESULTS.md` and `docs/KNOWN_ISSUES.md`.

---

## 7. References (for implementers)

1. Google Research — Project Guideline (open source): architecture of pose + depth + occupancy + control audio.  
2. PMC10939328 — smartphone depth accuracy / periphery / ML vs LiDAR tradeoffs.  
3. Depth-based VI obstacle systems — wall = vertical plane criteria.  
4. TriGuide docs: `SAFETY_LIMITATIONS.md`, `PATH_RISK_UPGRADE.md`, `AGENT_VISION_VOICE.md`.
