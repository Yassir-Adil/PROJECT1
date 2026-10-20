# CURSOR_RGBD_REVIEW — independent R source-only (public RGB-D pair)

**Role:** Independent reviewer R
**Date:** 2026-10-20
**Baseline (queue):** e145115 / 0.9.31/44 — bounded public RGB-D fixtures + geometry oracle
**Owned file:** this document only
**Method:** Read/edit tools only. No shell, build, ADB, Git, network, private outputs, other writes, or approval bypass. Tests and Codex host measurements were **not** re-run here. No GREEN / device / data-accuracy claim.

**Sources reviewed:** `research/hypersim_transport.py`, `hypersim_geometry.py`, `hypersim_fixture.py`, `evaluate_hypersim_fixture.py`; matching `research/tests/test_hypersim_*.py` + `test_evaluate_hypersim_fixture.py`; `scripts/test/quality_policy.py` `source_fingerprint` + `scripts/test/tests/test_quality_policy.py`; `research/requirements-eval.txt`. Contracts cross-read: `docs/WORK_QUEUE.md` (active pair), `docs/CURSOR_RGBD_TRANSPORT_2026-10-20.md`, `docs/CURSOR_RGBD_GEOMETRY_2026-10-20.md`.

---

## Verdict

Transport / ZIP / manifest / HDF5 hard-link gates and pure-numpy ray math look deliberately bounded and aligned with the stated MIT Range + Apple toolkit contracts. Several **concrete** issues remain (pose keyframe binding, evaluator test gap, residual JSON edge, extract-after-CRC residue). None were executed against private fixture bytes in this review. **Not GREEN.** Fake offline tests ≠ physical or CDN accuracy.

---

## Finding table

| ID | Severity | Location | Finding | Fix or accepted + limits |
| --- | --- | --- | --- | --- |
| F1 | **Medium** | `hypersim_fixture.py` `choose_members` ~57–63; `evaluate_hypersim_fixture.py` ~87–99 | Pose uses `positions[frame]` / `rotations[frame]` with **frame_id as row index**. Required ZIP set omits `camera_keyframe_frame_indices.hdf5`. Official toolkit binds pose by frame→keyframe indirection, not raw `frame_id` indexing. | **Fix** before any SPEC frame ≠ dense keyframe row: fetch + hash `camera_keyframe_frame_indices.hdf5`, resolve keyframe row where index equals `frame_id`, reject missing. **Accepted limit for current SPEC** (only `frame: 0` on three scenes) **only if** documented that row 0 is assumed ≡ frame 0 and non-zero frames are out of contract until fixed. |
| F2 | **Medium** | `research/tests/test_evaluate_hypersim_fixture.py` (HDF5-only); `evaluate_hypersim_fixture.py` `evaluate_fixture` | Evaluator reproducibility gates exist in source (PIN, SPEC set equality, public-val membership, scale cross-check, depth vs calib shape, pose trajectory shape) but **have no unit tests**. Only `read_hdf5_array` is covered. | **Fix:** add offline fake-manifest / tiny synthetic HDF5+CSV tests for pin mismatch, duplicate/missing frames, scale mismatch, depth shape mismatch, missing hash path — still no network / no private outputs. |
| F3 | **Low** | `evaluate_hypersim_fixture.py` `read_hdf5_array` ~19–34 vs `hypersim_geometry.MAX_DEPTH_AREA` | HDF5 allows `MAX_ARRAY_ELEMENTS = 3 * 1_048_576` for any rank-1..3 array, so a 2D depth larger than geometry’s area cap can be fully loaded then rejected in `unproject_range`. | **Fix:** for `ndim == 2`, cap `data.size` at `MAX_DEPTH_AREA` (and for `ndim == 3` with last dim 3, cap `H*W` similarly) before `data[:]`. |
| F4 | **Low** | `hypersim_fixture.py` `extract_selected` ~102–116 | On ZipExtFile CRC failure at close, destination bytes may already exist under `open("xb")`; member is not appended to records; download marks `incomplete`. Next `download_fixture` cannot reuse the same root (`mkdir` exist_ok=False). | **Accepted + limits:** intentional “preserve partial evidence / never silently resume”. Document that incomplete roots may contain untrusted partial members; always use a fresh output root. Optional harden: delete the current target on CRC/size failure before re-raise. |
| F5 | **Low** | `evaluate_hypersim_fixture.py` ~106–107, ~125–126 | `range_minus_camera_z_m_p50/p90` use `float(np.percentile(...))` without finite/Inf sanitization. Pathological finite overflow → Inf → `json.dump(..., allow_nan=False)` raises after work. Geometry `residual_summary` correctly **raises** on nonfinite shared residuals (~265–270). | **Fix:** mirror residual policy — reject nonfinite `delta` with `ValueError`, or map to null only if product policy prefers soft omit (prefer hard fail for oracle honesty). |
| F6 | **Low** | `evaluate_hypersim_fixture.py` report `source_sha256` ~115–116; `requirements-eval.txt` | Report pins hashes of geometry/fixture/evaluate modules and records numpy/h5py versions, but **not** `requirements-eval.txt` (h5py==3.16.0 pin) nor transport (download-only). | **Accepted + limits** for transport omission. **Fix** for eval reproducibility: include `requirements-eval.txt` SHA (and optionally installed package versions already present). |
| F7 | **Low** | `hypersim_geometry.py` `_require_invertible_camera_from_uv` ~96–110 | Returns `matrix / max\|M\|` (scale-normalized) rather than the original finite invertible matrix. Unit-ray unprojection is invariant to uniform M scale, so **camera rays stay correct**; surprising for callers who expect the CSV matrix bytes. | **Accepted + limits** for current `unproject_range` (unit normalize after). Prefer **return original `matrix`** after slogdet on the normalized copy to avoid silent rescaling. |
| F8 | **Info** | `quality_policy.py` `source_fingerprint` ~128–143; test ~26–42 | Fingerprint now includes all `research/**/*.py` and `research/requirements*.txt` (covers helpers + `requirements-eval.txt`). Test exercises helper + requirements name pattern. | **OK** for this pair’s dirty/untracked research surface. No defect found in the selection predicate relative to stated intent. |
| F9 | **Info** | Transport / fixture / geometry overall | Strict 206-before-body, Content-Range/Length/ETag/identity, absolute Range only, budgets, official host/path, no-redirect opener, ZIP path/symlink/encrypt/size caps, complete-only manifest hash verify, GT NYU40 labels diagnostic-only, no nav/CLEAR coupling, fake opener tests. | **OK** against the review checklist. Fake tests do not prove CDN or fixture numeric accuracy. Codex’s three-scene host run is out of scope for this R pass. |

---

## Checklist (requested concerns)

### Transport (`hypersim_transport.RangeReader`)
- **HTTP 206 before body:** `_validate_response_headers` runs before `response.read`; non-206 / bad headers leave body unread (contract tests trap read). **OK.**
- **ETag identity / budgets / no giant fetch:** strong quoted ETag + `If-Match`; `Accept-Encoding: identity` + reject non-identity encoding; precheck `max_request_bytes` / `max_total_bytes` / `max_requests`; read-all remaining refused when over request budget. **OK.**
- **Absolute Range only:** `bytes=start-end` inclusive; no suffix form. **OK.**
- **Note:** `request_count` increments before opener CM (failed attempts count). Safe for budget DoS; matches “attempted request” comment.

### Fixture ZIP / manifest (`hypersim_fixture`)
- **Path / member / cap / CRC / no overwrite:** `_safe_path` + resolve containment; reject `..`, abs, symlink mode, encrypt bit, non-stored/deflate; per-member and total extract caps; pre-existence `FileExistsError`; SHA-256 of written bytes; `verify_fixture` requires `status==complete` and full hash/size match. CRC relies on stdlib ZipExtFile at EOF (F4). **OK with F4 limit.**
- **Complete-only:** incomplete downloads never set `status=complete`; finally still writes manifest with error. **OK.**

### Geometry (`hypersim_geometry`)
- **Pixel centres / reversed v / full M / range≠camera-z / asset units / proper R:** matches stated toolkit terms; corner test expects range≠z; tilt-shift retained; position×scale; det+1 orthonormal world R. **OK** (source-level).
- **Finite / overflow:** invalid pixels → NaN + `valid=False`; residual overflow on shared finite pairs → `ValueError` (not fake zero). **OK.**
- **GT labels:** NYU40 named buckets for median world component diagnostics only; not classifier/nav. **OK.**
- **`median_world_z_m` uses `world[..., 2]`:** height-separation interpretation depends on Hypersim vertical axis convention; report already says not walking permission. Treat as diagnostic label, not gravity-aligned height proof. **Accepted + limits** (no in-repo primary cite of vertical axis in these files).

### Evaluator / HDF5
- **External / VDS / size:** HardLink-only via `getlink=True`; reject `is_virtual`, `external` storage, non-iuf, itemsize>8, shape/size/byte caps. Tests cover external link, external storage, VDS, huge, bool, string. **OK** (with F3 cap inconsistency).
- **Reproducibility:** PIN + SPEC + split membership + scale consistency + source file hashes. Gaps: F1, F2, F6.

### quality_policy / requirements-eval
- Fingerprint widened to research Python + `requirements*.txt`; `requirements-eval.txt` pins `h5py==3.16.0` among other eval deps. **OK** (F8).

---

## Explicit non-claims

- No GREEN / PASS on focused or full gates (not run here).
- No claim on Codex’s downloaded three public val scenes, private output digests, or numeric residual magnitudes.
- No Redmi / ARCore / navigation accuracy claim.
- No assertion that fake Range/ZIP/HDF5 tests equal physical dataset fidelity.

---

## Final disposition (source-only re-review, 2026-10-20)

**Method:** Re-read post-fix sources/tests only. No shell, network, ADB, private outputs, or other writes. Codex’s reported 86 research-test PASS and fixture-v2 fetch are **noted as external claims, not re-executed here**. Still **not GREEN**; no device/data accuracy claim.

**Sources re-checked:** `hypersim_fixture.choose_members` (now requires `camera_keyframe_frame_indices.hdf5`); `evaluate_hypersim_fixture` (unique exact `indices == frame` → `pose_row`; rank-2/rank-3 area gates before `data[:]`; finite range−z before report mkdir; `source_sha256` includes transport + `requirements-eval.txt`); `test_hypersim_evaluator_integrity.py` (six synthetic E2E cases); `test_hypersim_fixture.py` (7 members); `test_evaluate_hypersim_fixture.py` (area + `parse_dimension`); `hypersim_geometry._require_invertible_camera_from_uv` + `test_hypersim_geometry_regressions.py` (1e200 scale invariance).

### F1–F9 disposition

| ID | Prior | Final disposition |
| --- | --- | --- |
| F1 | Medium — frame_id as row; missing indices member | **Fixed.** `choose_members` requires `camera_keyframe_frame_indices.hdf5`; evaluator validates 1D integer unique indices aligned with pose length, resolves **exactly one** matching keyframe (`matches.size != 1` → reject), uses `positions[pose_row]` / `rotations[pose_row]`. Integrity sparse `[7,0]` oracle (error_m p90==0) proves non-row assumption. |
| F2 | Medium — evaluate_fixture untested | **Fixed.** `test_hypersim_evaluator_integrity.py`: six offline synthetic HDF5/CSV cases (sparse map + SHA pins; pin/missing/dup frame/missing hash; scale + depth shape; missing/dup indices; hash tamper; nonfinite range−z before report). |
| F3 | Low — HDF5 area load then geometry reject | **Fixed.** Rank-2 `size > 1_048_576` and rank-3 (`last!=3` or `H*W > 1_048_576`) raise **before** `data[:]`. Rank-2 oversize covered in `test_evaluate_hypersim_fixture`. |
| F4 | Low — CRC/partial bytes on disk | **Accepted + limits (unchanged).** Preserve partial evidence; no deletions; incomplete never `complete`; next fetch needs a **new** `--output-root` (fixture-v2 pattern). Documented in fixture finally-comment. |
| F5 | Low — Inf range−z → JSON fail after work | **Fixed.** `np.all(np.isfinite(delta))` else `ValueError` before report/output creation; integrity patches unproject to force overflow and asserts no report dir. |
| F6 | Low — missing requirements (and was-optional transport) SHA | **Fixed.** Report `source_sha256` lists geometry, fixture, evaluate, **transport**, and **requirements-eval.txt**. Integrity asserts requirements key present. |
| F7 | Low — prefer return original M | **Not a defect — recommendation withdrawn.** Private helper only feeds unit-ray unprojection; positive scaling is invariant; max-coefficient normalize + slogdet avoids squared-norm overflow. Codex RED on `1e200*M` before this fix is the justification. Normalized M **must remain**; it is not a public CSV-byte API. Regression: `test_hypersim_geometry_regressions.test_ray_matrix_positive_scale_does_not_change_geometry`. |
| F8 | Info — fingerprint | **Still OK.** Unchanged intent. |
| F9 | Info — checklist OK | **Still OK** at source. Fake/synthetic tests ≠ physical CDN/fixture accuracy. Fixture-v2 / 86 PASS not verified in this R pass. |

### New cases — source OK

- Sparse unique frame→keyframe binding + reject missing/duplicate indices.
- PIN / SPEC / hash / scale / depth-dimension / tamper / nonfinite Δ gates with **no** report directory on failure.
- HDF5 area reject-before-read; integer-float calib dims via `parse_dimension`.

### Remaining defects / limits (none blocking at prior Medium+)

| Sev | Note |
| --- | --- |
| Info | Integrity asserts `requirements-eval.txt` in `source_sha256` but not `hypersim_transport.py` (present in source list). Optional assert only. |
| Info | Sparse-map success inferred via residual p90==0; no explicit `pose_keyframe_row == 1` assert (row is emitted in report). |
| Info | Rank-3 `(1025,1024,3)` may hit `MAX_ARRAY_ELEMENTS` before the dedicated area message; still rejected before read. Optional dedicated unit case. |
| Limit | `median_world_z_m` / world[...,2] remains diagnostic, not gravity height or nav authority. |
| Limit | F4 fresh-root / partial-byte policy. |

No new Medium/High source defects found in this re-review.

### Stop

Final source-only disposition appended to this file only. **Not GREEN.** No further R writes.
