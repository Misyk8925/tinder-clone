from __future__ import annotations

import os
import tempfile
from pathlib import Path
from urllib.parse import quote

from app.exceptions import PhotoNotFoundError, PhotoStorageError, PhotoValidationError
from app.storage.base import MediaStorage
from app.storage.signing import MediaUrlSigner


class LocalFilesystemStorage(MediaStorage):
    """Stores photo variants atomically on local VPS disk and mints HMAC-signed URLs."""

    def __init__(
        self,
        root_dir: str | Path,
        public_base_url: str,
        signer: MediaUrlSigner,
    ) -> None:
        if not signer.is_configured:
            raise PhotoStorageError(
                "Missing config: local storage requires a signing secret "
                "(PHOTOS_SIGNING_SECRET or PHOTOS_INTERNAL_AUTH_SECRET)"
            )
        self._root = Path(root_dir).resolve()
        try:
            self._root.mkdir(parents=True, exist_ok=True)
        except OSError as exc:
            raise PhotoStorageError(f"Failed to initialize local storage root {self._root}") from exc
        self._public_base = public_base_url.strip().rstrip("/")
        self._signer = signer

    @property
    def root(self) -> Path:
        return self._root

    def put(self, key: str, data: bytes, content_type: str) -> None:
        target = self._resolve_key(key)
        tmp_path: Path | None = None
        try:
            target.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile(
                dir=target.parent,
                prefix=".tmp-",
                suffix=".part",
                delete=False,
            ) as tmp:
                tmp.write(data)
                tmp.flush()
                os.fsync(tmp.fileno())
                tmp_path = Path(tmp.name)
            os.replace(tmp_path, target)
            tmp_path = None
        except OSError as exc:
            if tmp_path is not None:
                try:
                    tmp_path.unlink(missing_ok=True)
                except OSError:
                    pass
            raise PhotoStorageError(f"Failed to store local object {key}") from exc

    def get(self, key: str) -> bytes:
        target = self._resolve_key(key)
        if not target.is_file():
            raise PhotoNotFoundError(f"Photo not found: {key}")
        try:
            return target.read_bytes()
        except FileNotFoundError as exc:
            raise PhotoNotFoundError(f"Photo not found: {key}") from exc
        except OSError as exc:
            raise PhotoStorageError(f"Failed to read local object {key}") from exc

    def delete(self, key: str) -> None:
        target = self._resolve_key(key)
        try:
            target.unlink(missing_ok=True)
            self._prune_empty_parents(target.parent)
        except OSError:
            return

    def list_keys(self, prefix: str) -> list[str]:
        normalized_prefix = prefix.lstrip("/")
        if ".." in Path(normalized_prefix).parts or "\\" in normalized_prefix:
            raise PhotoValidationError(f"Invalid storage prefix: {prefix}")
        search_root = (self._root / normalized_prefix).resolve()
        if not self._is_within_root(search_root):
            raise PhotoValidationError(f"Storage prefix escapes root: {prefix}")
        if not search_root.exists():
            return []
        keys: list[str] = []
        try:
            for path in search_root.rglob("*"):
                if path.is_file() and not path.name.startswith(".tmp-"):
                    rel = path.resolve().relative_to(self._root).as_posix()
                    if rel.startswith(normalized_prefix):
                        keys.append(rel)
        except OSError as exc:
            raise PhotoStorageError(f"Failed to list local objects for prefix {prefix}") from exc
        keys.sort()
        return keys

    def public_url(self, key: str) -> str:
        encoded = quote(key.lstrip("/"), safe="/")
        if self._public_base:
            return f"{self._public_base}/media/{encoded}"
        return f"/media/{encoded}"

    def presigned_download_url(self, key: str) -> str:
        normalized = key.lstrip("/")
        exp, sig = self._signer.sign(normalized)
        return f"{self.public_url(normalized)}?exp={exp}&sig={sig}"

    def _resolve_key(self, key: str) -> Path:
        if not key or key.startswith("/") or "\\" in key or "\x00" in key:
            raise PhotoValidationError(f"Invalid object key: {key}")
        parts = Path(key).parts
        if any(part in ("", ".", "..") for part in parts):
            raise PhotoValidationError(f"Invalid object key: {key}")
        candidate = (self._root / key).resolve()
        if not self._is_within_root(candidate) or candidate == self._root:
            raise PhotoValidationError(f"Object key escapes storage root: {key}")
        return candidate

    def _is_within_root(self, candidate: Path) -> bool:
        try:
            candidate.relative_to(self._root)
            return True
        except ValueError:
            return False

    def _prune_empty_parents(self, directory: Path) -> None:
        current = directory
        while current != self._root and self._is_within_root(current):
            try:
                current.rmdir()
            except OSError:
                break
            current = current.parent
