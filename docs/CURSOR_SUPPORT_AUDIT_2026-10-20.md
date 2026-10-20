# Cursor H — Hypersim structural GT support audit (2026-10-20)

## Scope (exclusive writes)

- `research/evaluate_hypersim_support.py` (this response)
- `docs/CURSOR_SUPPORT_AUDIT_2026-10-20.md` (this file)

Codex owns `research/tests/test_hypersim_support.py`. No Android / Gradle / other
docs / source / tests / Git / ADB / network / private-output / fixture access by
this Cursor turn. Source implemented only; **no execution claimed**.

## What was implemented

### `evaluate_support(labels, positions, grid=8, keep_every=1)`

Pure aggregate audit over caller-supplied arrays:

| Contract | Behaviour |
| --- | --- |
| Labels | 2D integer, non-bool; area ≤ 1 048 576 |
| Positions | Real numeric `H×W×3` matching labels; reject complex / bool / object / wrong shape |
| `grid` | Strict `int` (non-bool) in `{8,16,32}`; `H,W ≥ grid` |
| `keep_every` | Strict `int` (non-bool) in `1..64` |
| Samples | Centres `floor((i+0.5)·dim/grid)` via integer math; exactly `grid²` distinct cells |
| Floor | NYU40 label **2** only; ADE floor **4**, table **7**, rug **20** never floor |
| Component | Four-connected among floor samples; anchored to bottom row at centre columns `grid/2−1` and `grid/2` |
| Supports | Floor-component cells with **finite** XYZ and `flatGridIndex % keep_every == 0`; non-finite never imputed |
| Mutability | Labels / positions copied; caller arrays unchanged |

JSON-safe return (no point / coordinate arrays, no CLEAR / turn / safe-path / model
accuracy wording):

- `sampled_cells`, `floor_component_cells`, `potential_floor_supports`
- `scope='ground_truth_structural_only'`, `navigation='none'`
- `availability='full_ground_truth'` when `keep_every==1`, else
  `'synthetic_periodic_drop_not_sensor_confidence'`
- Optional aggregates: `finite_xyz_floor_component_cells`, `six_support_count_met`,
  `finite_support_axis_spans` (overflow → `null`, never NaN/Infinity JSON)

### Optional CLI `--fixture PATH --out PATH`

Runs the fixed **27** cases (`3` held-out SPEC scenes × grids `8/16/32` × drops
`1/4/16`) against an **existing** hash-verified public fixture supplied by the
caller. Reuses `verify_fixture`, `read_hdf5_array`, `parse_dimension`, `PIN` /
`SPEC`, split/public-val checks, and the same keyframe integrity validation as
`evaluate_hypersim_fixture.py`. Loads semantic labels + GT world positions only
(no RGB, no model, no download). Fresh `--out` directory required; writes
`summary.json` with `allow_nan=False`, NYU40 provenance, and case counts without
exposing point coordinates.

**Limitation:** CLI does not invent fixture paths. A prior public fixture root
must be passed explicitly. This Cursor turn did not access any fixture bytes.

## Verification status (honest)

| Step | Status |
| --- | --- |
| Source written | Yes |
| Unit tests / GREEN | **Not run** — wait for Codex |
| Real fixture CLI | **Not run** — wait for Codex with caller fixture path |
| New engineering gate / phone / ADB | **Not run**; 0.9.36 / 836-gate figures remain historical |

## Non-claims

- Not phone sensor confidence, ground clearance, or navigation permission.
- Not a model score or recognition benchmark.
- Does not restart older ACTIVE scopes or claim new Redmi results.
