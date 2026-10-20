"""Bounded HTTPS Range reader for official Apple Hypersim scene zip archives.

Approach adapted from Thomas Germer's 2020 MIT-licensed WebFile (HTTP Range +
seekable file object for zipfile), as linked from apple/ml-hypersim's selective
downloader. This module is not a copy of that URL table; TriGuide adds strict
206 / Content-Range / Content-Length / ETag / identity / budget guards and
allows only absolute ``bytes=start-end`` Ranges (suffix ranges are out of
contract). Fake ``opener`` injection keeps unit tests offline.

Full MIT licence for the upstream WebFile approach (Copyright (c) 2020 Thomas
Germer) is retained below. TriGuide-specific validation and budgeting are
additional restrictions on top of that approach.

---------------------------------------------------------------------------
MIT License

Copyright (c) 2020 Thomas Germer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
---------------------------------------------------------------------------
"""

from __future__ import annotations

import io
import math
import re
import urllib.request
from urllib.parse import urlparse

_MAX_ARCHIVE_BYTES = 32 * 1024**3  # 32 GiB
_OFFICIAL_HOST = "docs-assets.developer.apple.com"
_OFFICIAL_PATH_RE = re.compile(
    r"^/ml-research/datasets/hypersim/v1/scenes/ai_\d{3}_\d{3}\.zip$"
)
_STRONG_ETAG_RE = re.compile(r'^"[^"]+"$')


def _require_positive_int(name: str, value: object) -> int:
    if type(value) is not int:
        raise TypeError(f"{name} must be a positive int (bool rejected)")
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return value


def _require_positive_finite_timeout(value: object) -> float:
    if type(value) is bool or not isinstance(value, (int, float)):
        raise TypeError("timeout must be a positive finite number (bool rejected)")
    timeout = float(value)
    if not math.isfinite(timeout) or timeout <= 0:
        raise ValueError("timeout must be a positive finite number")
    return timeout


def _validate_official_url(url: str) -> str:
    if type(url) is not str or not url:
        raise ValueError("url must be a non-empty str")
    parsed = urlparse(url)
    if parsed.scheme != "https":
        raise ValueError("url must use https")
    if parsed.username is not None or parsed.password is not None:
        raise ValueError("url must not include userinfo")
    if parsed.port is not None:
        raise ValueError("url must not include an explicit port")
    if parsed.hostname != _OFFICIAL_HOST:
        raise ValueError("url host is not the official Hypersim docs-assets host")
    if parsed.query or parsed.fragment:
        raise ValueError("url must not include query or fragment")
    if parsed.params:
        raise ValueError("url must not include path parameters")
    if _OFFICIAL_PATH_RE.fullmatch(parsed.path) is None:
        raise ValueError("url path is not an official Hypersim scene zip")
    # Reject netloc forms that embed a port even if urlparse quirks differ.
    if parsed.netloc != _OFFICIAL_HOST:
        raise ValueError("url netloc must be exactly the official host")
    return url


def _validate_strong_etag(etag: str) -> str:
    if type(etag) is not str or not etag:
        raise ValueError("etag must be a non-empty quoted strong validator")
    if etag.startswith("W/") or etag.startswith("w/"):
        raise ValueError("etag must not be a weak validator")
    if _STRONG_ETAG_RE.fullmatch(etag) is None:
        raise ValueError("etag must be a nonempty quoted strong validator")
    return etag


def _header_get(headers: object, name: str) -> str | None:
    """Read a response header via email.Message.get (case-insensitive)."""
    getter = getattr(headers, "get", None)
    if not callable(getter):
        raise TypeError("response.headers must support .get")
    value = getter(name)
    if value is None:
        # Retry with title / lower forms; Message.get is usually case-insensitive.
        value = getter(name.lower())
        if value is None:
            value = getter(name.title())
    if value is None:
        return None
    if type(value) is not str:
        value = str(value)
    return value


class RangeReader(io.RawIOBase):
    """Seekable RawIOBase over absolute HTTPS byte Ranges for one Hypersim zip.

    ``opener(request, *, timeout)`` must return a context manager yielding an
    HTTP-like response with ``.status``, ``.headers``, ``.geturl()``, and
    ``.read(n)``. No retries or fallbacks.
    """

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
    ) -> None:
        super().__init__()
        if opener is None or not callable(opener):
            raise TypeError("opener must be callable")
        self._url = _validate_official_url(url)
        self._size = _require_positive_int("size", size)
        if self._size > _MAX_ARCHIVE_BYTES:
            raise ValueError("size must be <= 32 GiB")
        self._etag = _validate_strong_etag(etag)
        self._opener = opener
        self._timeout = _require_positive_finite_timeout(timeout)
        self._max_request_bytes = _require_positive_int(
            "max_request_bytes", max_request_bytes
        )
        self._max_total_bytes = _require_positive_int(
            "max_total_bytes", max_total_bytes
        )
        self._max_requests = _require_positive_int("max_requests", max_requests)
        self._pos = 0
        self.bytes_read = 0
        self.request_count = 0

    def readable(self) -> bool:
        return True

    def seekable(self) -> bool:
        return True

    def tell(self) -> int:
        self._checkClosed()
        return self._pos

    def seek(self, offset: int, whence: int = io.SEEK_SET) -> int:
        self._checkClosed()
        if type(offset) is not int:
            raise TypeError("seek offset must be int (bool rejected)")
        if type(whence) is not int:
            raise TypeError("seek whence must be int (bool rejected)")
        if whence == io.SEEK_SET:
            if offset < 0:
                raise ValueError("seek offset must be >= 0 for SEEK_SET")
            pos = offset
        elif whence == io.SEEK_CUR:
            pos = self._pos + offset
        elif whence == io.SEEK_END:
            pos = self._size + offset
        else:
            raise ValueError("seek whence must be SEEK_SET, SEEK_CUR, or SEEK_END")
        if pos < 0:
            raise ValueError("seek result must be >= 0")
        self._pos = pos
        return self._pos

    def read(self, size: int | None = -1) -> bytes:  # type: ignore[override]
        self._checkClosed()
        if type(size) is bool:
            raise TypeError("read size must not be bool")
        if size is not None and type(size) is not int:
            raise TypeError("read size must be int, None, or -1")
        if size is None or size == -1:
            if self._pos >= self._size:
                return b""
            want = self._size - self._pos
            if want > self._max_request_bytes:
                raise ValueError(
                    "remaining bytes exceed max_request_bytes; pass an explicit size"
                )
        else:
            if type(size) is not int:
                raise TypeError("read size must be int, None, or -1")
            if size < 0:
                raise ValueError("read size must be >= 0, or -1 for remaining")
            if size == 0 or self._pos >= self._size:
                return b""
            want = min(size, self._size - self._pos)

        if want <= 0:
            return b""
        if want > self._max_request_bytes:
            raise ValueError("requested byte count exceeds max_request_bytes")
        if self.request_count >= self._max_requests:
            raise OSError("max_requests budget exhausted")
        if self.bytes_read + want > self._max_total_bytes:
            raise OSError("max_total_bytes budget would be exceeded")

        start = self._pos
        end = start + want - 1  # inclusive absolute Range end
        request = urllib.request.Request(
            self._url,
            headers={
                "Range": f"bytes={start}-{end}",
                "If-Match": self._etag,
                "Accept-Encoding": "identity",
            },
            method="GET",
        )

        # Failed connection/context entry is still an attempted request.
        self.request_count += 1
        with self._opener(request, timeout=self._timeout) as response:
            self._validate_response_headers(response, start=start, end=end, want=want)
            # At most requested+1 bytes to detect an overlong body.
            chunk = response.read(want + 1)
            transferred = len(chunk)
            self.bytes_read += transferred
            if transferred != want:
                raise OSError(
                    f"range body length mismatch: expected {want}, got {transferred}"
                )
            self._pos = start + want
            return chunk

    def _validate_response_headers(
        self, response: object, *, start: int, end: int, want: int
    ) -> None:
        status = getattr(response, "status", None)
        if status != 206:
            raise OSError(f"expected HTTP 206 Partial Content, got {status!r}")

        geturl = getattr(response, "geturl", None)
        if not callable(geturl):
            raise OSError("response is missing geturl()")
        final_url = geturl()
        if final_url != self._url:
            raise OSError("final URL does not match the requested URL")

        headers = getattr(response, "headers", None)
        if headers is None:
            raise OSError("response is missing headers")

        content_range = _header_get(headers, "Content-Range")
        expected_range = f"bytes {start}-{end}/{self._size}"
        if content_range != expected_range:
            raise OSError(
                f"Content-Range mismatch: expected {expected_range!r}, got {content_range!r}"
            )

        content_length = _header_get(headers, "Content-Length")
        if content_length != str(want):
            raise OSError(
                f"Content-Length mismatch: expected {want}, got {content_length!r}"
            )

        etag = _header_get(headers, "ETag")
        if etag != self._etag:
            raise OSError("ETag mismatch versus If-Match")

        encoding = _header_get(headers, "Content-Encoding")
        if encoding is not None and encoding.strip().lower() != "identity":
            raise OSError("Content-Encoding must be identity when present")
