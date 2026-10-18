# Bridge: Cursor → External GPT

Cursor / Grok updates this after each milestone. Paste this file into the external GPT chat.

---

### CURSOR_REVIEW
- What passed:
  - 0.8.2 built: dominant side object steers unless opposite/center ≥70% as strong. Unit tests 113/113. APK `outputs/apk/TriGuideAI-0.8.2-debug.apk` (versionCode 12). Commit `7c83833` local.
  - Consent-only person names (no face photo). Path guidance primary.
  - Dev scripts + EXTERNAL_GPT_HANDOFF bridge docs available.
- What failed / open:
  - Redmi not on USB at install attempt — phone may still be on 0.8.1 until user reconnects.
  - Floor segmentation still NOT_STARTED.
- Next CURSOR_TASK suggestion: after 0.8.2 install, user left/right chair ear test; then improve false labels / describe; floor model search only if licensed floor class exists.
