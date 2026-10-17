# Upstream clones (local only)

These directories are gitignored. Recreate them with:

```text
git clone --depth 1 https://github.com/nrai18/GuideLens-Android-App.git research/upstream/GuideLens-Android-App
git clone --depth 1 --filter=blob:none --sparse https://github.com/google-ai-edge/mediapipe-samples.git research/upstream/mediapipe-samples
git clone --depth 1 --filter=blob:none --sparse https://github.com/ob-f/OpenBot.git research/upstream/OpenBot
```

Do not commit those trees. GuideLens is GPL-3.0. Notes live in `research/software-comparison.md` and `research/model-audit.md`.
