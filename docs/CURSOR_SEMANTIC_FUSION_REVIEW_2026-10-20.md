# Independent Cursor source review and Codex dispositions — 2026-10-20

Read-only CLI worker, scoped new diagnostics/Activity/test changes only. No
executed tests/runtime/physical accuracy claim by reviewer. Codex owns verification.

| Finding | Codex disposition |
| --- | --- |
| Medium: overlay freshness hardcodes running/tracking true | Fixed: overlay invokes same live publication/revision/stop validator as Activity at draw time; no hardcoded flags. Receipt-based invalidation timer fixed too. UI scheduling is not a hard real-time guarantee. |
| Medium:180° rotation may not change viewport size | Fixed with pure SemanticViewport rotation+size state, current display rotation checked on each GL frame; both changes invalidate pending semantic publication before SDK display geometry update. Actual missing-helper RED, new regression cases. Physical rotation still pending. |
| Medium:bilinear image prepare vs nearest model-label lookup | Not accepted as a required interpolation change. Bilinear RGB resize is existing preprocessing; discrete argmax-label lookup is necessarily categorical. Using nearest labels does not require nearest RGB. Switching RGB filtering would change the measured model input. Boundary blending/Float-vs-Double rounding remains a diagnostic uncertainty, NOT exact pixel/ground proof. No raster-golden Android test exists. |
| Medium:CPU copy on GL may stall tracking | Performance risk, not an observed stall. Current SDK Frame requires acquire and transform before next update; moving native Image/Frame to worker is rejected. Copies now take only validated required plane span, bounded dimensions/strides/bytes.1Hz one-inflight cap unchanged. Added actual GL-copy, worker prepare/inference and end-to-end p50/p90 counters for phone measurement. No speed claim before measurement. |
| Low:unmapped transforms represented as ignored class0 | Ground contract uses0 as unknown/ignored, not obstacle/floor. Added explicit unmapped_cells counter/result metadata and log disclosure; invalid UV never contributes wall/floor/rug labels. |
| Low:duplicated512 model size | Fixed mapper uses existing DiagnosticSegmenter.SIZE const. No model/tensor contract change. |

Reviewer confirmed no escaped Frame/Image/Pose/native buffers to worker, no
second camera owner, capture after raw same-frame gate, `.use` closure on errors,
debug-only scope and no production guidance/CLEAR/turn coupling. Sparse grid,
single-frame-only candidate and BT.601 assumption are explicit limitations.

Post-fix independent Cursor re-review COMPLETE: no remaining High/Medium concrete
scoped source defect. F3 not re-raised without a concrete coordinate counterexample;
RGB interpolation versus categorical labels are distinct. Runtime rotation, GL
cost and floor identity remain measurement limits. Codex independently executed
94focused /791full tests and stable build/policy gate PASS; Cursor did not execute
those tests and its earlier pending status is historical.

## Final0.9.33 orientation/fast-path review

Same independent read-only reviewer additionally checked SDK axes→CW turns,
crop/scale/nonfinite/mirror/skew rejection, exact integer pixel rotation and
oriented letterbox lookup, AndroidMatrix sign/recycle lifecycle, and YUVheap
arrayOffset/position/paired odd rows/cols/direct fallback. **No actionable High/
Medium correctness findings**.100focused and797full tests/build/policy independently
executed by Codex, not reviewer. Physical0.9.33 orientation/speed/classification
remain unverified;0.9.32 logs do not prove the changed pipeline.
