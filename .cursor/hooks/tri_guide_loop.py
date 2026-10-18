"""Continue a TriGuide builder or reviewer chat after it finishes a turn.

Fail open: any error prints {} and exits 0 so the agent is never blocked.
The loop stays off until a chat writes .cursor/loop-state.json with enabled true
and this conversation's id. Other chats, including an in-progress master-plan
chat, are left alone.
"""

from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STATE_PATH = ROOT / ".cursor" / "loop-state.json"
LAST_SESSION_PATH = ROOT / ".cursor" / "hooks" / "last-session.json"
LAST_PAYLOAD_PATH = ROOT / ".cursor" / "hooks" / "last-payload.json"

BUILDER_FOLLOWUP = (
    "TriGuide builder loop. Automatic continue. Do not wait for another user message. "
    "Read docs/AGENT_WORKFLOW.md, docs/DEVELOPMENT_PLAN.md, and the latest entry in docs/PROGRESS.md. "
    "Refresh .cursor/agent-lock.json with role builder, a UTC timestamp, and the files you will change. "
    "Keep the reviewer slot in .cursor/loop-state.json unchanged. "
    "Do the next unfinished master-plan item only. Phone-first. No physical robot. "
    "Do not work on fingerprint control or screen triple-tap. "
    "Build the debug APK and run unit tests. Record only commands you actually ran. "
    "Use Redmi ADB only when adb devices shows 55aqpfqkvw9xbafe as device. Do not start an emulator. "
    "If files changed under you that you did not edit, re-read them and continue from that state. Do not revert them. "
    "If the next step needs the user (USB debugging, phone unlock, install confirmation, hearing the speaker, or a TalkBack walk), "
    "set builder.enabled to false in .cursor/loop-state.json, write the reason in docs/AGENT_STATUS.md, and end with LOOP_PAUSE."
)

REVIEWER_FOLLOWUP = (
    "TriGuide reviewer loop. Automatic continue. Do not wait for another user message. "
    "Read docs/AGENT_WORKFLOW.md and .cursor/agent-lock.json. "
    "If the lock file is missing, or its role is builder and updated_utc is under 45 minutes old, "
    "do not edit Kotlin, Gradle, PROGRESS, TEST_RESULTS, or DEVELOPMENT_PLAN. Append notes to docs/REVIEW_NOTES.md only. "
    "If git status shows changes you did not make in this chat, do not edit those paths. "
    "When the lock is idle or older than 45 minutes, you may apply one small safe fix for architecture, "
    "TalkBack labels, 48dp targets, contrast, tests, or performance, and only on files the builder did not list. "
    "No fingerprint or triple-tap work. No fake AI badges. No dependency downgrades. No emulator. "
    "If nothing safe is left, set reviewer.enabled to false, note that in docs/REVIEW_NOTES.md and docs/AGENT_STATUS.md, "
    "and end with LOOP_PAUSE."
)


def _utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _load_state() -> dict:
    try:
        data = json.loads(STATE_PATH.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def _conversation_id(data: dict) -> str:
    for key in ("conversation_id", "session_id", "composer_id"):
        value = data.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def _record_payload(data: dict) -> None:
    safe = {
        "written_utc": _utc_now(),
        "present_keys": sorted(str(key) for key in data.keys()),
        "hook_event_name": data.get("hook_event_name"),
        "status": data.get("status"),
        "loop_count": data.get("loop_count"),
        "conversation_id": _conversation_id(data),
        "composer_mode": data.get("composer_mode"),
    }
    try:
        _write_json(LAST_PAYLOAD_PATH, safe)
    except OSError:
        return


def _session_start(data: dict) -> dict:
    session_id = _conversation_id(data)
    if not session_id:
        return {}
    try:
        _write_json(
            LAST_SESSION_PATH,
            {
                "session_id": session_id,
                "composer_mode": data.get("composer_mode") or "",
                "written_utc": _utc_now(),
            },
        )
    except OSError:
        pass
    return {
        "additional_context": (
            f"TriGuide session id: {session_id}. "
            "Use this id only if the user starts a loop from docs/AGENT_WORKFLOW.md. "
            "Do not arm .cursor/loop-state.json otherwise. "
            "Do not take over files another chat is editing."
        )
    }


def _slot_matches(state: dict, role: str, conversation_id: str) -> bool:
    slot = state.get(role)
    if not isinstance(slot, dict):
        return False
    if slot.get("enabled") is not True:
        return False
    slot_id = slot.get("conversation_id")
    return isinstance(slot_id, str) and slot_id.strip() == conversation_id


def _stop(data: dict) -> dict:
    _record_payload(data)
    if data.get("status") != "completed":
        return {}
    conversation_id = _conversation_id(data)
    if not conversation_id:
        return {}
    state = _load_state()
    if _slot_matches(state, "builder", conversation_id):
        return {"followup_message": BUILDER_FOLLOWUP}
    if _slot_matches(state, "reviewer", conversation_id):
        return {"followup_message": REVIEWER_FOLLOWUP}
    return {}


def run(data: dict) -> dict:
    event = data.get("hook_event_name")
    if event == "sessionStart" or (
        "session_id" in data and "composer_mode" in data and "status" not in data
    ):
        return _session_start(data)
    if event == "stop" or "status" in data or "loop_count" in data:
        return _stop(data)
    return {}


def main() -> None:
    raw = sys.stdin.read()
    try:
        data = json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        data = {}
    if not isinstance(data, dict):
        data = {}
    try:
        out = run(data)
    except Exception:
        out = {}
    sys.stdout.write(json.dumps(out, ensure_ascii=True))
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
