# Typed RGB-only diagnostic and public structural support audit —0.9.37/50

## Final engineering checkpoint / device pending

Final stable gate `20261020T153635414179Z` **PASS865tests**:
726app+10sim+30policy+99evaluation,0fail/error/skip, both builds/modelpins/
permissions/debug isolation/known-private-rootpolicyPASS. Lint0errors/fatal,
41debug/14release warnings (prior03651debug; no warning suppression).
Source238files SHA256
`96e88d4998b3046ff92e901b8670fed88dbd70f8d12fb8ea392e246d2558b5c6`
independently rechecked after gate. Final narrowR source review no remaining
scopedHigh/Medium; H/Rjobsfinished, no worker execution claim.

Debug/stagedAPK SHA256
`d0fee946dad242d1ae8a7809cb4be86f12a38e04ce83e45d288da78a8e5d39e7`;
releaseunsigned `86ebe459c6cb6d655d5330f46741361934a040791e7d1c2662b20ccbda881491`.
Newcandidate staged, NOT installed/camera-tested. Serial not visible on last
read-only device check; owner asked for fresh USB/unlock/lit-scene readiness.
Last actual phone evidence remains036, not037. Do not infer actualRGB delivery,
geometry benefit, fault-injection behavior or navigation safety from this gate.
Older pending/intermediate counts below describe their historical checkpoint.

## Review follow-up before final acceptance

Independent R accepted typedRGB/publicaudit but found asyncfatal stop could
allow another RGB offer. After actual sourceguardRED, added immediate pending
fence and semanticstop before UIcleanup, covering raw/dense AND allGLcamera/frame/
background failures. Old cleanupCAS remains independent, no deadlock bypass.
Intermediate policyFAIL caught stale sourceassertion; stronger stop+pending
assertion fixed it. Missing requirements pin test added as alreadyGREEN coverage.
114focused remain, policy now30 /support13 (full research99); finalstablegate/
source rereview separate. CURSOR_RGB_SUPPORT_REVIEW_2026-10-20.md has history.

## Trigger / scope

0.9.36 live colours were seen and8freshCanvas were logged, but ground support
was0–4 / minimum6 and no ground candidate existed. Previously segmentation was
called only after accepted freshraw depth. Missing/reprojected/low-confidence
raw data therefore also suppressed otherwise possible visual labels.

This pair implements an explicitly opt-in diagnosticRGB fallback and evaluates
structural support sampling on the existing public3scene fixture. No model,
camera owner, cloud/Internet permission, raw gate, ground threshold, live250ms
deadline, production navigation, speech or walking permission change.

## Implemented Android contract

- `SemanticObservation` is a view-only sealed interface. `Semantic2DObservation`
  owns a copied64cell paint grid and localreceipt/completion/revision/frame
  metadata, but NO world coordinates, ground reference or height inference.
  `SemanticFusionResult` remains the separate geometric branch.
- Shared ARCore owner still copies/closes CPUimage planes before worker queue.
  One existing1Hz/one-inflight/250ms gate is shared by both sources. Raw gets
  first opportunity on each existing bounded raw attempt; optional RGB fallback
  runs afterwards, never borrowing old raw/world points. Same frame cannot obtain
  a second ticket/CPUimage. Dense SDK depth never supplies confidence/geometry.
- 2D grid uses SDK VIEW_NORMALIZED→IMAGE_NORMALIZED directly, then the existing
  exact quarter-turn pixel transform and letterbox lookup. Fused grid retains
  snapped depth→CPU mapping and original admitted-frame raw projection.
- Launch opt-in: `triguide.diagnostic.semantic_rgb_fallback=true` together with
  semantic_fusion=true. DefaultfallbackOFF; default ARCore/raw and normalapp
  unchanged. This is not a new general-purpose user permission.
- UI explicitly says RGB labels only / geometryUNKNOWN. RGB observations never
  enter `SemanticGroundDiagnostic`. Logs distinguish SEMANTIC_2D_SAMPLE from
  SEMANTIC_FUSION_SAMPLE and summary fused/rgb-only counts. Shared processing
  timing may mix sources: do not compare it causally with a fused-only baseline.
- Fallback sampling shares raw10Hz opportunities;1Hz semantic admission does
  not guarantee1Hz delivery. If RGB wins a cadence slot, fewer subsequent raw
  frames can be semantically fused until the slot expires. This tradeoff must be
  measured, not hidden as a guaranteed throughput/geometry improvement.
- RGBfallback still requires existing ARCoreTRACKING and current viewmapping.
  It decouples raw-data availability, NOT tracking-loss/blank-wall availability;
  tracking loss still clears both sources. Do not claim general RGB perception
  in every untracked/dark scene from this implementation.
- Previous six synthetic-lab SetTextI18n warnings addressed by moving unchanged
  copy into debug-only string resources. Source-resource guard was actuallyRED
  before implementation; normalrelease does not package that lab copy.

## Tests-first evidence

Codex ran actual missing-API compileRED for new observation/grid tests and missing
moduleRED for support audit before source. CursorH only wrote host audit module
and its response doc, with no Android/test/ADB/Git/network/privatefixture access.
Codex reviewed actual source and independently executed tests/realpublicdata.

Nine new observation/grid cases cover typedseparation, boundedmetadata/copied
labels, invalidSDK UV/no clamp, all quarter-turn mappings, malformed shapes,
class IDs and oversized input. Two shared-gate tests cover rawfirst/no second
ticket and stale/stop RGB, alreadyGREEN coverage (no fabricatedRED).
Focused **114tests/14suites PASS**, policy/source guards **28PASS**.

Host **12tests PASS**:7pure structural cases plus5end-to-end tiny fakefixture
cases reusing established HDF5 fixture helper. Actual3failures (zero/negative
matchingmetadata scale, missingrequirements hash) were executed before fixes.
Hash/split/selection tamper, output-root separation and refusal to overwrite a
report are tested; source guards are NOT Android SDK or phone execution proof.

## Actual public structural audit — not a trained model benchmark

Reused23file hash-verified fixture, manifestSHA
`36b036d0f3bb09f69e2eb628534a7e4b9ce20588103d720141fe33ca5710ceda`,
Hypersim pin3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254,3held-out publicval frames.
Data alreadylocal; zero network downloads, new datasets, RGB modelinference or
training. Data/provenance/CC-BY-SA3.0 attribution in PUBLIC_RGBD_ORACLE_2026-10-20.md.

Raw aggregate report (ignored):
`outputs/reports/hypersim-support-20261020-v1/summary.json`, SHA256
`51f896d2a05399f15d850ba16df5c3178bd443aa238fc739134f9fc9ce6bf496`.
Execution0.9604s on host, not Redmi latency. Sourcehash includes tool/fixture
verifier/coordinate evaluator/requirements pin. No original points are reported.

Rows show potential finiteXYZ supports in bottom-centre-connected GTfloor
component under FULL GT, synthetickeep-every4, synthetickeep-every16 respectively:

| Scene | Grid8 | Grid16 | Grid32 |
| --- | --- | --- | --- |
| ai_003_010 |0 /0 /0|0 /0 /0|0 /0 /0|
| ai_004_003 |18 /7 /2|65 /19 /8|272 /74 /28|
| ai_004_004 |10 /1 /0|52 /10 /0|184 /41 /5|

27cases total,13met the SIX-SUPPORT COUNT ONLY,9had no anchored floorcomponent,
11had no available support. NYU40floor2, table7/rug20/ADEfloor4 not floor here.
Synthetic periodic dropping is NOT sensor confidence, real ARCore sparsity or a
random sample model; changing grids changes the sampling pattern too.

These are suppliedGTlabels and geometry: not model/phoneaccuracy or the Kotlin
ground evaluator running against realcamera. Meeting sixcount does not validate
span, area, horizontalplanarity, temporal identity, no-obstacle or walkability.
The zero-component scene remains zero even with higher grid resolution; do not
remove the bottomcomponent guard to make its numbers positive.

## Next acceptance / limits

Full stable engineering gate and independent source review must complete before
installation. Phone still0.9.36 from prior actual test until separately verified.
No automatic camera start. A useful next owner-ready20s diagnostic may enable
RGBfallback to measure actual rgb-only vs fused delivery and preserve UNKNOWN
geometry. Controlledsame-scene/order and source-separated statistics are needed
before any comparative gain claim. See SAFETY_GATES_2026-10-20.md for the staged
acceptance backlog; neither this pair nor all unit tests establish safe walking.
