"""Stage-1 contract tests for research/hypersim_transport.py RangeReader.

Bounded HTTPS Range + zipfile transport for official Hypersim scene zips.
Fake opener only — no network, no disk writes, no URL table. Adapted from the
MIT (Thomas Germer 2020) WebFile Range/seek idea linked by apple/ml-hypersim;
guards (206, Content-Range, identity, ETag, budgets) are TriGuide requirements.

Codex recorded actual RED (ModuleNotFoundError) then released source.
Contract uses absolute ``bytes=start-end`` Ranges only (no suffix form).
"""

from __future__ import annotations

import contextlib
import email.message
import io
import sys
import unittest
import urllib.request
import zipfile
from pathlib import Path

_RESEARCH_DIR = Path(__file__).resolve().parents[1]
if str(_RESEARCH_DIR) not in sys.path:
    sys.path.insert(0, str(_RESEARCH_DIR))

import hypersim_transport  # noqa: E402


VALID_URL = (
    "https://docs-assets.developer.apple.com/"
    "ml-research/datasets/hypersim/v1/scenes/ai_003_010.zip"
)
VALID_ETAG = '"hypersim-ai_003_010-v1"'
MAX_SIZE = 32 * 1024**3  # 32 GiB


def _headers(**pairs: str) -> email.message.Message:
    msg = email.message.Message()
    for key, value in pairs.items():
        msg[key] = value
    return msg


class FakeResponse:
    """Minimal urllib-like response; read() may be overridden to trap touches."""

    def __init__(
        self,
        *,
        status: int,
        headers: email.message.Message,
        body: bytes,
        url: str = VALID_URL,
        read_raises: BaseException | None = None,
    ) -> None:
        self.status = status
        self.headers = headers
        self._body = body
        self._url = url
        self._offset = 0
        self._closed = False
        self._read_raises = read_raises
        self.read_calls: list[int | None] = []

    def geturl(self) -> str:
        return self._url

    def read(self, n: int = -1) -> bytes:
        if self._read_raises is not None:
            raise self._read_raises
        self.read_calls.append(n)
        if n is None or n < 0:
            chunk = self._body[self._offset :]
            self._offset = len(self._body)
            return chunk
        chunk = self._body[self._offset : self._offset + n]
        self._offset += len(chunk)
        return chunk

    def close(self) -> None:
        self._closed = True


class RecordingOpener:
    """opener(Request, *, timeout) -> context manager yielding FakeResponse."""

    def __init__(self, responses: list[FakeResponse] | None = None) -> None:
        self._responses = list(responses or [])
        self.requests: list[urllib.request.Request] = []
        self.timeouts: list[float] = []

    def push(self, response: FakeResponse) -> None:
        self._responses.append(response)

    def __call__(self, request: urllib.request.Request, *, timeout: float):
        self.requests.append(request)
        self.timeouts.append(timeout)
        if not self._responses:
            raise AssertionError("opener called with no queued response")
        response = self._responses.pop(0)

        @contextlib.contextmanager
        def _cm():
            try:
                yield response
            finally:
                response.close()

        return _cm()


def _ok_headers(start: int, end: int, size: int, etag: str = VALID_ETAG) -> email.message.Message:
    length = end - start + 1
    return _headers(
        **{
            "Content-Range": f"bytes {start}-{end}/{size}",
            "Content-Length": str(length),
            "ETag": etag,
            "Content-Encoding": "identity",
        }
    )


def _reader(
    opener: RecordingOpener,
    *,
    payload: bytes | None = None,
    size: int | None = None,
    url: str = VALID_URL,
    etag: str = VALID_ETAG,
    timeout: float = 15,
    max_request_bytes: int = 8 * 1024 * 1024,
    max_total_bytes: int = 64 * 1024 * 1024,
    max_requests: int = 512,
):
    body = b"" if payload is None else payload
    return hypersim_transport.RangeReader(
        url,
        size if size is not None else len(body),
        etag,
        opener=opener,
        timeout=timeout,
        max_request_bytes=max_request_bytes,
        max_total_bytes=max_total_bytes,
        max_requests=max_requests,
    )


class HypersimTransportContractTest(unittest.TestCase):
    def test_rejects_non_official_https_url_shapes(self):
        opener = RecordingOpener()
        bad_urls = (
            "http://docs-assets.developer.apple.com/ml-research/datasets/hypersim/v1/scenes/ai_003_010.zip",
            "https://example.com/ml-research/datasets/hypersim/v1/scenes/ai_003_010.zip",
            "https://docs-assets.developer.apple.com/ml-research/datasets/hypersim/v1/scenes/ai_003_010.zip?x=1",
            "https://docs-assets.developer.apple.com/ml-research/datasets/hypersim/v1/scenes/ai_003_010.zip#frag",
            "https://user:pass@docs-assets.developer.apple.com/ml-research/datasets/hypersim/v1/scenes/ai_003_010.zip",
            "https://docs-assets.developer.apple.com:443/ml-research/datasets/hypersim/v1/scenes/ai_003_010.zip",
            "https://docs-assets.developer.apple.com/ml-research/datasets/hypersim/v1/scenes/ai_3_10.zip",
            "https://docs-assets.developer.apple.com/ml-research/datasets/hypersim/v1/scenes/ai_003_010.tar",
        )
        for url in bad_urls:
            with self.subTest(url=url):
                with self.assertRaises(Exception):
                    _reader(opener, size=1, url=url)
        self.assertEqual([], opener.requests)

    def test_rejects_invalid_size_etag_and_budget_args(self):
        opener = RecordingOpener()
        with self.assertRaises(Exception):
            _reader(opener, size=True)  # bool must not count as positive int
        with self.assertRaises(Exception):
            _reader(opener, size=0)
        with self.assertRaises(Exception):
            _reader(opener, size=-1)
        with self.assertRaises(Exception):
            _reader(opener, size=MAX_SIZE + 1)
        with self.assertRaises(Exception):
            _reader(opener, size=1, etag="")
        with self.assertRaises(Exception):
            _reader(opener, size=1, etag="W/" + VALID_ETAG)
        with self.assertRaises(Exception):
            _reader(opener, size=1, etag="unquoted")
        with self.assertRaises(Exception):
            _reader(opener, size=1, timeout=0)
        with self.assertRaises(Exception):
            _reader(opener, size=1, timeout=float("nan"))
        with self.assertRaises(Exception):
            _reader(opener, size=1, max_request_bytes=0)
        with self.assertRaises(Exception):
            _reader(opener, size=1, max_total_bytes=-1)
        with self.assertRaises(Exception):
            _reader(opener, size=1, max_requests=True)
        self.assertEqual([], opener.requests)

    def test_rawio_seekable_readable_tell_seek_and_close(self):
        opener = RecordingOpener()
        reader = _reader(opener, size=100)
        self.assertIsInstance(reader, io.RawIOBase)
        self.assertTrue(reader.seekable())
        self.assertTrue(reader.readable())
        self.assertEqual(0, reader.tell())
        self.assertEqual(40, reader.seek(40, io.SEEK_SET))
        self.assertEqual(50, reader.seek(10, io.SEEK_CUR))
        self.assertEqual(100, reader.seek(0, io.SEEK_END))
        self.assertEqual(120, reader.seek(20, io.SEEK_END))  # past EOF allowed
        self.assertEqual(120, reader.tell())
        reader.close()
        with self.assertRaises(Exception):
            reader.read(1)
        with self.assertRaises(Exception):
            reader.seek(0)
        with self.assertRaises(Exception):
            reader.tell()
        self.assertEqual([], opener.requests)

    def test_eof_and_zero_length_read_make_no_request(self):
        opener = RecordingOpener()
        reader = _reader(opener, size=8)
        self.assertEqual(b"", reader.read(0))
        reader.seek(8)
        self.assertEqual(b"", reader.read(1))
        self.assertEqual(b"", reader.read(-1))
        self.assertEqual(0, reader.request_count)
        self.assertEqual(0, reader.bytes_read)
        self.assertEqual([], opener.requests)

    def test_negative_read_size_other_than_minus_one_rejects(self):
        opener = RecordingOpener()
        reader = _reader(opener, size=8)
        with self.assertRaises(Exception):
            reader.read(-2)
        self.assertEqual(0, reader.tell())
        self.assertEqual([], opener.requests)

    def test_range_if_match_identity_headers_and_successful_partial_read(self):
        payload = b"abcdefghijklmnopqrstuvwxyz"
        opener = RecordingOpener()
        opener.push(
            FakeResponse(
                status=206,
                headers=_ok_headers(4, 8, len(payload)),
                body=payload[4:9],
            )
        )
        reader = _reader(opener, payload=payload, timeout=9.5)
        reader.seek(4)
        self.assertEqual(b"efghi", reader.read(5))
        self.assertEqual(9, reader.tell())
        self.assertEqual(1, reader.request_count)
        self.assertEqual(5, reader.bytes_read)
        self.assertEqual([9.5], opener.timeouts)
        req = opener.requests[0]
        self.assertIsInstance(req, urllib.request.Request)
        self.assertEqual(VALID_URL, req.full_url)
        self.assertEqual("bytes=4-8", req.get_header("Range"))
        self.assertEqual(VALID_ETAG, req.get_header("If-match"))
        self.assertEqual("identity", req.get_header("Accept-encoding"))

    def test_http_200_with_huge_body_declines_without_reading(self):
        opener = RecordingOpener()
        trap = FakeResponse(
            status=200,
            headers=_headers(
                **{
                    "Content-Length": str(10**12),
                    "ETag": VALID_ETAG,
                    "Content-Encoding": "identity",
                }
            ),
            body=b"",
            read_raises=AssertionError("body must not be read on non-206"),
        )
        opener.push(trap)
        reader = _reader(opener, size=10**9, max_request_bytes=64, max_total_bytes=10**6)
        with self.assertRaises(Exception):
            reader.read(16)
        self.assertEqual(0, reader.tell())
        self.assertEqual(0, reader.bytes_read)
        self.assertEqual(1, reader.request_count)
        self.assertEqual([], trap.read_calls)
        self.assertTrue(trap._closed)

    def test_header_mismatches_close_without_body_or_position_advance(self):
        payload = b"0123456789abcdef"
        cases = (
            # wrong Content-Range
            _headers(
                **{
                    "Content-Range": "bytes 0-3/999",
                    "Content-Length": "4",
                    "ETag": VALID_ETAG,
                    "Content-Encoding": "identity",
                }
            ),
            # Content-Length mismatch vs range
            _headers(
                **{
                    "Content-Range": f"bytes 0-3/{len(payload)}",
                    "Content-Length": "99",
                    "ETag": VALID_ETAG,
                    "Content-Encoding": "identity",
                }
            ),
            # ETag mismatch
            _headers(
                **{
                    "Content-Range": f"bytes 0-3/{len(payload)}",
                    "Content-Length": "4",
                    "ETag": '"other"',
                    "Content-Encoding": "identity",
                }
            ),
            # non-identity encoding
            _headers(
                **{
                    "Content-Range": f"bytes 0-3/{len(payload)}",
                    "Content-Length": "4",
                    "ETag": VALID_ETAG,
                    "Content-Encoding": "gzip",
                }
            ),
        )
        for headers in cases:
            with self.subTest(headers=dict(headers.items())):
                opener = RecordingOpener()
                trap = FakeResponse(
                    status=206,
                    headers=headers,
                    body=payload[:4],
                    read_raises=AssertionError("body must not be read on bad headers"),
                )
                opener.push(trap)
                reader = _reader(opener, payload=payload)
                with self.assertRaises(Exception):
                    reader.read(4)
                self.assertEqual(0, reader.tell())
                self.assertEqual(0, reader.bytes_read)
                self.assertEqual(1, reader.request_count)
                self.assertEqual([], trap.read_calls)
                self.assertTrue(trap._closed)

    def test_final_url_mismatch_closes_without_body(self):
        payload = b"0123456789"
        opener = RecordingOpener()
        trap = FakeResponse(
            status=206,
            headers=_ok_headers(0, 3, len(payload)),
            body=payload[:4],
            url=VALID_URL + ".redirected",
            read_raises=AssertionError("body must not be read on URL mismatch"),
        )
        opener.push(trap)
        reader = _reader(opener, payload=payload)
        with self.assertRaises(Exception):
            reader.read(4)
        self.assertEqual(0, reader.tell())
        self.assertEqual(0, reader.bytes_read)
        self.assertEqual([], trap.read_calls)

    def test_short_body_counts_transferred_bytes_without_position_advance(self):
        payload = b"0123456789abcdef"
        opener = RecordingOpener()
        opener.push(
            FakeResponse(
                status=206,
                headers=_ok_headers(0, 7, len(payload)),
                body=b"0123",  # short vs Content-Length 8
            )
        )
        reader = _reader(opener, payload=payload)
        with self.assertRaises(Exception):
            reader.read(8)
        self.assertEqual(0, reader.tell())
        self.assertEqual(4, reader.bytes_read)
        self.assertEqual(1, reader.request_count)

    def test_overlong_body_detected_by_reading_at_most_requested_plus_one(self):
        payload = b"0123456789abcdef"
        opener = RecordingOpener()
        overlong = FakeResponse(
            status=206,
            headers=_ok_headers(0, 3, len(payload)),
            body=b"012345",  # longer than the 4-byte range
        )
        opener.push(overlong)
        reader = _reader(opener, payload=payload)
        with self.assertRaises(Exception):
            reader.read(4)
        self.assertEqual(0, reader.tell())
        self.assertEqual(1, reader.request_count)
        self.assertGreaterEqual(reader.bytes_read, 4)
        self.assertLessEqual(reader.bytes_read, 5)
        # Implementation may issue one read(4) then read(1), or a single read(5).
        for n in overlong.read_calls:
            if n is not None and n >= 0:
                self.assertLessEqual(n, 5)

    def test_read_all_remaining_rejects_when_exceeds_request_budget_before_network(self):
        opener = RecordingOpener()
        reader = _reader(opener, size=1000, max_request_bytes=64)
        with self.assertRaises(Exception):
            reader.read(None)
        with self.assertRaises(Exception):
            reader.read(-1)
        self.assertEqual(0, reader.tell())
        self.assertEqual(0, reader.request_count)
        self.assertEqual([], opener.requests)

    def test_request_and_total_budget_prechecks(self):
        payload = b"x" * 32
        opener = RecordingOpener()
        opener.push(
            FakeResponse(
                status=206,
                headers=_ok_headers(0, 7, len(payload)),
                body=payload[:8],
            )
        )
        reader = _reader(
            opener,
            payload=payload,
            max_request_bytes=8,
            max_total_bytes=8,
            max_requests=1,
        )
        self.assertEqual(payload[:8], reader.read(8))
        with self.assertRaises(Exception):
            reader.read(1)  # max_requests / max_total_bytes exhausted
        self.assertEqual(1, len(opener.requests))
        self.assertEqual(8, reader.tell())
        self.assertEqual(1, reader.request_count)
        self.assertEqual(8, reader.bytes_read)

    def test_zipfile_member_read_via_local_fake_ranges(self):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_STORED) as zf:
            zf.writestr("scene/meta.txt", b"hypersim-fixture")
            zf.writestr("scene/note.txt", b"seek-ok")
        payload = buf.getvalue()

        class ServingOpener:
            def __init__(self) -> None:
                self.requests: list[urllib.request.Request] = []

            def __call__(self, request: urllib.request.Request, *, timeout: float):
                self.requests.append(request)
                range_hdr = request.get_header("Range")
                assert range_hdr.startswith("bytes=")
                start_s, end_s = range_hdr[len("bytes=") :].split("-", 1)
                start, end = int(start_s), int(end_s)
                chunk = payload[start : end + 1]

                @contextlib.contextmanager
                def _cm():
                    resp = FakeResponse(
                        status=206,
                        headers=_ok_headers(start, end, len(payload)),
                        body=chunk,
                    )
                    try:
                        yield resp
                    finally:
                        resp.close()

                return _cm()

        opener = ServingOpener()
        reader = _reader(opener, payload=payload, max_request_bytes=len(payload))
        with zipfile.ZipFile(reader) as zf:
            self.assertEqual(b"hypersim-fixture", zf.read("scene/meta.txt"))
            self.assertEqual(b"seek-ok", zf.read("scene/note.txt"))
        self.assertGreaterEqual(len(opener.requests), 2)
        self.assertGreater(reader.request_count, 0)
        self.assertGreater(reader.bytes_read, 0)


if __name__ == "__main__":
    unittest.main()
