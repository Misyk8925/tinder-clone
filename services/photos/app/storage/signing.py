from __future__ import annotations

import hashlib
import hmac
import time
from typing import Callable

from app.exceptions import PhotoSignatureError, PhotoStorageError


class MediaUrlSigner:
    """HMAC-SHA256 URL signer and verifier for local media downloads."""

    def __init__(
        self,
        secret: str,
        ttl_seconds: int = 300,
        clock: Callable[[], float] = time.time,
    ) -> None:
        self._secret = secret.strip()
        self._ttl_seconds = max(1, int(ttl_seconds))
        self._clock = clock

    @property
    def is_configured(self) -> bool:
        return bool(self._secret)

    def sign(self, key: str, ttl_seconds: int | None = None) -> tuple[int, str]:
        if not self._secret:
            raise PhotoStorageError("Missing config: PHOTOS_SIGNING_SECRET must be set for local media URLs")
        ttl = self._ttl_seconds if ttl_seconds is None else max(1, int(ttl_seconds))
        exp = int(self._clock()) + ttl
        sig = self._compute(key, exp)
        return exp, sig

    def verify(self, key: str, exp: int | None, sig: str | None) -> int:
        if not self._secret:
            raise PhotoStorageError("Photos service is not configured for signed media delivery")
        if exp is None or not sig:
            raise PhotoSignatureError("Missing media signature or expiration")
        now = int(self._clock())
        if exp <= now:
            raise PhotoSignatureError("Media download URL has expired")
        expected = self._compute(key, exp)
        if not hmac.compare_digest(sig, expected):
            raise PhotoSignatureError("Invalid media download signature")
        return max(1, exp - now)

    def _compute(self, key: str, exp: int) -> str:
        payload = f"{key}\n{exp}".encode("utf-8")
        return hmac.new(self._secret.encode("utf-8"), payload, hashlib.sha256).hexdigest()
