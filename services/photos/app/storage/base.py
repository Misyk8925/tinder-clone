from __future__ import annotations

from abc import ABC, abstractmethod


class MediaStorage(ABC):
    """Unified storage abstraction implemented by Local, AWS S3, and Memory backends."""

    @abstractmethod
    def put(self, key: str, data: bytes, content_type: str) -> None:
        """Persist raw bytes at the given object key."""

    @abstractmethod
    def get(self, key: str) -> bytes:
        """Read raw bytes stored at the given object key or raise PhotoNotFoundError."""

    @abstractmethod
    def delete(self, key: str) -> None:
        """Best-effort delete of the object at key. Missing objects are not an error."""

    @abstractmethod
    def list_keys(self, prefix: str) -> list[str]:
        """List all stored object keys starting with prefix."""

    @abstractmethod
    def public_url(self, key: str) -> str:
        """Return the durable public/canonical URL for key."""

    @abstractmethod
    def presigned_download_url(self, key: str) -> str:
        """Return a time-limited authorized download URL for key."""


# Backward-compatible alias for existing callers and tests.
ObjectStorage = MediaStorage
