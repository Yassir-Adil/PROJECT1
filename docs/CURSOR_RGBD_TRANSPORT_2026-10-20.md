# CURSOR_RGBD_TRANSPORT — hypersim_transport RangeReader

Codex final integration record:14original +2independent transport regressions
PASS. Fixed failed-attempt count and float-1 readsize. Actual boundedpublicfetch
v2 completed23hash-pinnedfiles;86research/713fulltests PASS. Details:
PUBLIC_RGBD_ORACLE_2026-10-20.md. Worker history below is not a claim of worker
shell/network execution; all tests/downloads were independently run by Codex.

**Status:** `SOURCE_READY` (await independent Codex GREEN; this worker does not claim GREEN)
**Worker:** Cursor T
**Date:** 2026-10-20
**Baseline:** e145115 / 0.9.31/44 (queue active pair: bounded public RGB-D fixtures and geometry oracle)
**Scope executed:** T tests first; Codex actual RED (`ModuleNotFoundError: hypersim_transport`, exit 1, `rgbd-tests-red-20261020.log`); explicit RELEASE SOURCE; T implementation. GREEN not run here.

## Changed files (exact)

1. `research/tests/test_hypersim_transport.py` — contract tests (path insert + fake opener; absolute Range)
2. `research/hypersim_transport.py` — **(new)** `RangeReader` (`io.RawIOBase`)
3. `docs/CURSOR_RGBD_TRANSPORT_2026-10-20.md` — this report

No other files touched. No shell, build, ADB, Git, network, private outputs, Android/nav/model/permission edits, or approval bypass.

## Implemented API

```python
class RangeReader(io.RawIOBase):
    def __init__(
        self,
        url: str,
        size: int,
        etag: str,
        *,
        opener,
        timeout: float = 15,
        max_request_bytes: int = 8_388_608,
        max_total_bytes: int = 67_108_864,
        max_requests: int = 512,
    ): ...

    # seekable / readable / tell / seek / read / close
    # Public counters: .bytes_read, .request_count
```

Import: insert `research/` on `sys.path`, then `import hypersim_transport`.

### Behaviour notes

- Official HTTPS scene URL only (`docs-assets.developer.apple.com` … `/ai_NNN_NNN.zip`); no query/fragment/userinfo/port; no URL table.
- `size` / budget ints reject `bool`; `size` in `1..32GiB`; strong quoted ETag (not `W/`).
- Each read issues absolute `Range: bytes=start-end` plus `If-Match` and `Accept-Encoding: identity`.
- Validate status 206, final URL, `Content-Range`, `Content-Length`, ETag, and non-identity `Content-Encoding` **before** any body `.read` (headers via `email` HTTPMessage `.get`, case-insensitive).
- Bad headers: response closed by opener CM, no body read, no position advance; `request_count` still increments.
- Body: at most `n+1` bytes; short/long reject without position advance; transferred bytes always add to `bytes_read`.
- Precheck `max_request_bytes` / `max_total_bytes` / `max_requests` before network; no retry/fallback.
- Closed IO rejects `read` / `seek` / `tell`.
- Module retains full MIT licence text for Thomas Germer 2020 WebFile approach (attribution in module header).

### CDN note (external; not claimed by this worker)

Codex reports that an actual CDN HEAD plus an explicit absolute Range GET returned 206 with a 22-byte body today. Prior suffix-Range attempts saw connection reset. No causal claim is made here; the reader contract simply requires absolute Ranges.

## Tests (14 methods)

Unchanged intent table from the tests-first pass (URL/size/ETag/budgets, RawIOBase, 206 guards, HTTP 200 no-body trap, short/overlong accounting, prechecks, stdlib zipfile over fake absolute Ranges).

## RED / GREEN evidence

- **RED (Codex, independent):** `ModuleNotFoundError: hypersim_transport`, exit 1, log `rgbd-tests-red-20261020.log` (before source).
- **GREEN:** **not claimed by Cursor T.** Codex runs independent offline tests after this source lands.

## Genuine unverified limits

- This worker did not contact the Apple CDN or download scene bytes.
- Transport correctness ≠ Hypersim attribution packaging, geometry oracle, or navigation safety.
- Fake-opener unit tests do not prove production CDN reliability.

## NEXT

`SOURCE_READY` → Codex runs `research/tests/test_hypersim_transport.py` offline and records GREEN or failures. This worker stops here.
