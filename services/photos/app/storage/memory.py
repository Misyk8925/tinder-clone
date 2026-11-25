from __future__ import annotations

from app.exceptions import PhotoNotFoundError
from app.storage.base import MediaStorage


class MemoryStorage(MediaStorage):
    """In-memory media storage backend for unit tests and ephemeral demos."""

    def __init__(self, public_base: str = "https://cdn.test") -> None:
        self._objects: dict[str, bytes] = {}
        self._public_base = public_base.rstrip("/")

    def put(self, key: str, data: bytes, content_type: str) -> None:
        self._objects[key] = data

    def get(self, key: str) -> bytes:
        if key not in self._objects:
            raise PhotoNotFoundError(f"Photo not found: {key}")
        return self._objects[key]

    def delete(self, key: str) -> None:
        self._objects.pop(key, None)

    def list_keys(self, prefix: str) -> list[str]:
        return [key for key in self._objects if key.startswith(prefix)]

    def public_url(self, key: str) -> str:
        return f"{self._public_base}/{key}"

    def presigned_download_url(self, key: str) -> str:
        return f"{self._public_base}/{key}?X-Amz-Expires=300"
