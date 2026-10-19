# Local scene manifest validation — 2026-10-19

## Why this exists

The scene manifest is the gate between an empty example template and any
meaningful perception comparison. This validator checks its structure and
local media references before replay. It does not capture, copy, upload, or
modify media.

## Run it

From the repository root:

```powershell
.\scripts\test\validate-scene-manifest.ps1
```

To require at least one reviewed still image before declaring the set ready:

```powershell
.\scripts\test\validate-scene-manifest.ps1 -RequireLabeledMedia
```

The default example is expected to pass schema validation while reporting
`EVALUATION_READY=False`, because it has no media. A dataset with media must
use relative paths that resolve within `test-data`, still-image formats, a
reviewed scorable ground-truth label, and recorded consent (`granted`). Video
and paths that escape the local data directory are rejected.

## Coverage output

The tool prints aggregate row and media counts plus per-category row,
media-file, and labeled-media counts, followed by ground-truth counts. An
`unscorable` sample is tracked but does not satisfy the minimum scorable-media
requirement. The report omits media paths so copied logs do not disclose the
user's local folder structure; it prints only the manifest filename. Keep
image files local unless the people shown have explicitly agreed to the
intended sharing.

## Current evidence

The checked-in example remains a template with nine scene categories and no
captured media. `-RequireLabeledMedia` should therefore fail until real,
consented, stationary evidence has been reviewed. Passing validation is a
data-integrity check only; it does not validate model accuracy or walking
safety.
