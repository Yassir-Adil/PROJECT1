# Local Redmi evidence set

This directory is intentionally empty of captured media. The manifest template
lists the required scene categories for a controlled, stationary Redmi test.

Rules:

- `.gitignore` now excludes all local contents here except this README, the empty
  example manifest and `.gitkeep`. Copy the example to `scene-manifest.tsv` for
  personal paths/labels; do not put private content into the tracked template.
- `outputs/reports/` is also local-only. The quality gate checks the current Git
  index because ignoring a path does not untrack a file already added. No files
  are auto-deleted; these local folders are not an off-device backup.

- Keep media local and consented. Do not add faces, voices, or room images to a
  remote repository without explicit consent.
- `ground_truth` stays `pending` until the scene is actually reviewed and labeled.
- A detector-only empty frame is not a clear-path label.
- A `branching-view` is descriptive evidence only; it cannot authorize a turn.
- Feed replay data through the pure JVM `EvidenceReplayHarness`; its output is
  diagnostic and cannot produce navigation, TTS, or `CLEAR_OBSERVED`.
