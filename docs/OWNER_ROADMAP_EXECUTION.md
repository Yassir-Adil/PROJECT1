# Owner roadmap → TriGuide execution map

**Living plan — update after each milestone.**  
**Last audit:** 2026-10-19 ~06:20 MYT (Milestone B shipped as 0.9.18).

## Success metric (owner)

Prefer **correct direction + fewer critical errors** (false STOP, missed corridor blockers, invented clearance) over raw label count or “more AI features.”

## Hard safety rules (never relax)

| Rule | Meaning |
| --- | --- |
| No fake clearance | Detector-only / empty boxes / unmapped masks **must not** produce `CLEAR` or spoken “path is clear” / «في طريق فاضي». Prefer HOLD / UNKNOWN / “no obstacle seen — go carefully”. |
| Not a mobility aid | Research / simulator. Cane and O&M remain primary. See `docs/SAFETY_LIMITATIONS.md`. |
| Licence | No BlindAssist (AGPL) / GuideLens (GPL) code copy; no Ultralytics YOLO weights; no SegFormer (NVIDIA NC). Rights-cleared models only. |
| Offline | No `INTERNET` permission; no paid cloud vision/ASR APIs. |
| One camera | One CameraX stream; drop stale frames (`KEEP_ONLY_LATEST` style). |
| Depth / floor policy | Relative depth / floor masks may **raise** risk (HOLD) only; they never authorize CLEAR until independent validation. |

---

## DONE (do not re-litigate)

| Slice | Version / notes |
| --- | --- |
| App shell, CameraX, EfficientDet-Lite0, Live Vision, STOP UX | Through **0.9.14** |
| Path-risk corridor logic, silence-by-default voice, emergency STOP | Committed + iterated; see `docs/PROGRESS.md` |
| Perception seam Phase 0–1 | **0.9.15** — `FloorSegmenterDiagnostic` / `FloorMaskEvidence` / replay harness; class map **UNMAPPED**; no weights in APK |
| Phase 2–3 continued (desktop) | **0.9.16** — candidate ledger + `EvidenceFusionGate` fail-closed |
| Milestone A | **0.9.17** — corridor **0.25–0.75** + `DecisionEvidenceRecorder`; installed on Redmi |
| Milestone B (diagnostic AI Vision) | **0.9.18** / code **31** — PathRisk research overlay (no floor weights); tests **246** app + **10** sim; installed on Redmi `55aqpfqkvw9xbafe` |
| Redmi evidence collector | `scripts/test/redmi-evidence-baseline.ps1` → `outputs/reports/redmi-evidence/` |
| Codex handoff checklist | `docs/CODEX_TEST_CHECKLIST.md` |

---

## Milestone A — DONE (0.9.17)

Corridor narrow + Evidence Recorder. See PROGRESS. Owner ear/scene still useful for false-STOP feel.

---

## Milestone B — DONE (0.9.18)

| Item | Status |
| --- | --- |
| Rights-cleared floor model in APK | **No** — PHASE2 candidates unsuitable or uncleared |
| SegFormer | **Rejected** (NVIDIA NC) |
| AI Vision PathRisk overlay | **Shipped** — Live toggle; green≠safe |
| Floor seam Diagnostics lines | **Shipped** |
| Unit tests + assemble + Redmi install | **Done** this turn |
| CLEAR → nav/TTS | **Not wired** (correct) |

---

## Next milestones C → N

| # | Owner topic | TriGuide work | When |
| --- | --- | --- | --- |
| **C** | Depth | Spike per `research/03-depth-model-comparison.md`: **MiDaS v2.1 small** HOLD-only first; optional ARCore Raw Depth **probe log-only** on Redmi. Depth never lowers detector risk. | Next after Codex/owner smoke on 0.9.18 |
| **D** | Tracking | Lightweight IoU / track-id temporal smoother | After C or thin parallel |
| **E** | NDE + cost map / A* | Extend `NavigationDecisionEngine`; synthetic occupancy in `:sim-core` | After B/C evidence |
| **F** | Perf | GPU delegate where safe; Redmi median/p90 + thermal | Continuous with C |
| **G–H** | Sim + AI→wheels | Tighten reasons from perception → virtual steering UI | Partial today |
| **I** | Junctions | Planned / experimental in `KNOWN_ISSUES` until real detector | Blocked on better walkable evidence |
| **J–K** | Voice intents + Describe | Offline SR; sparse cues; owner voice UX | Partial; ear tests |
| **L** | Campus / maps | Synthetic graph first | Later |
| **M** | Vision lab + wrong-detection report | Build on Evidence Recorder | Later UI |
| **N** | QA dashboard | Metrics from recorder + scene replays | Later |

---

## Existing evidence (reuse)

| Path | Use |
| --- | --- |
| `outputs/reports/redmi-evidence/closed-door-wall-20261019-050957/` | Wall/door negative scene |
| `outputs/reports/redmi-evidence/owner-occlusion-*` | Close-range occlusion baseline |
| `test-data/scene-manifest.example.tsv` | Template only |

---

## Active focus (this week)

```
[B done 0.9.18] → [Codex] docs/CODEX_TEST_CHECKLIST.md + owner ear
        → [C] MiDaS HOLD-only spike (no CLEAR)  → tracking/NDE as evidence allows
```

## Owner questions (only when blocked)

1. Ear test on 0.9.18 (corridor chair edge vs center, wall, AI Vision toggle) — done this session?
2. Proceed to Milestone C depth after Codex checklist?

---

## Related docs

- Progress / honesty: `docs/PROGRESS.md`, `docs/TEST_RESULTS.md`, `docs/KNOWN_ISSUES.md`
- Codex: `docs/CODEX_TEST_CHECKLIST.md`
- Trust / depth / seg research: `docs/GUIDANCE_TRUST_IMPROVEMENT_PLAN.md`, `research/03-*.md`, `research/04-*.md`
- Segmenter ledger: `docs/PHASE2_SEGMENTER_CANDIDATE_BENCHMARK.md`
- Agent loop: `docs/AGENT_WORKFLOW.md`
