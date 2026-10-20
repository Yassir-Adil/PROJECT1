# CURSOR_RGBD_GEOMETRY — Hypersim geometry oracle

Codex final integration record:20original +4independent numeric regressions PASS;
two incorrect testfixtures corrected; max-coefficient normalization supersedes
the intermediate Frobenius implementation below.86research/713fulltests PASS,
Rfinalsourceaccepted. PUBLIC_RGBD_ORACLE_2026-10-20.md records actualexecution.
Worker phases below are history; no worker-executedGREEN claim is implied.

**Status:** `SOURCE_READY` (post–NUMERIC_RED fixes; **no GREEN claim** — Codex owns re-run)
**Worker:** Cursor G
**Date:** 2026-10-20
**Baseline:** e145115 / 0.9.31/44
**Scope this turn:** Fix only `research/hypersim_geometry.py` + this report after Codex first GREEN (10× `np.stack` broadcast errors) and independent NUMERIC_RED in `research/tests/test_hypersim_geometry_regressions.py` (Codex-owned, **read-only** here). No shell/network/ADB/Git/private outputs/other writes. Original 20 contract cases preserved untouched.

## Changed files (this turn)

1. `research/hypersim_geometry.py` — broadcast / invertibility / complex / bool-scale / residual-overflow fixes
2. `docs/CURSOR_RGBD_GEOMETRY_2026-10-20.md` — this update

Unchanged: `research/tests/test_hypersim_geometry.py` (20 cases). Not edited: Codex `test_hypersim_geometry_regressions.py`.

## Fixes vs Codex RED / review

| Issue | Fix |
| --- | --- |
| `u` (1,W) / `v` (H,1) / `ones` (H,W) → `np.stack` does not broadcast | `np.broadcast_arrays` then `stack` |
| Absolute `\|det\| ≤ 1e-12` rejects valid `1e-8 * M` (det ∝ s³) | Frobenius-normalise then `slogdet`; `sign == 0` ⇒ singular only |
| Complex arrays silently cast to real | Reject `complexfloating` / `iscomplexobj` with `ValueError` |
| `True` / `np.bool_(True)` scale → `float(True)==1.0` | Reject bool / `np.bool_` scales before coerce |
| Finite `1e308` vs `-1e308` residual → Inf published or dropped | If any shared finite pair yields non-finite error → **`ValueError`** |

## Math (unchanged contract)

Official pixel-centred u/v (reversed vertical), full `M_cam_from_uv`, nested-hypot unit rays, `camera_z = -CameraXYZ.z`, world metres via `R_world_from_cam` + asset position × metre scale. Attribute pin 3463c5c in source header. **No hardware accuracy claim.**

## Stop

**SOURCE_READY** after NUMERIC_RED fixes. No GREEN claim. Codex re-runs focused + regression tests.
