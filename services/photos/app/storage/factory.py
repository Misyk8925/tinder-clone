from __future__ import annotations

from typing import Callable

from app.config import Settings
from app.exceptions import PhotoStorageError
from app.storage.base import MediaStorage
from app.storage.local import LocalFilesystemStorage
from app.storage.memory import MemoryStorage
from app.storage.s3 import S3Storage
from app.storage.signing import MediaUrlSigner

StorageBuilder = Callable[[Settings], MediaStorage]


def _build_local(settings: Settings) -> MediaStorage:
    signer = MediaUrlSigner(
        secret=settings.signing_secret,
        ttl_seconds=settings.presign_exp_seconds,
    )
    return LocalFilesystemStorage(
        root_dir=settings.local_root_path,
        public_base_url=settings.public_base_url,
        signer=signer,
    )


def _build_s3(settings: Settings) -> MediaStorage:
    return S3Storage(settings)


def _build_memory(_settings: Settings) -> MediaStorage:
    return MemoryStorage()


STORAGE_BACKENDS: dict[str, StorageBuilder] = {
    "local": _build_local,
    "s3": _build_s3,
    "memory": _build_memory,
}


def register_storage_backend(name: str, builder: StorageBuilder) -> None:
    """Register a custom storage backend builder under a lowercase name."""
    STORAGE_BACKENDS[name.strip().lower()] = builder


def resolve_backend_name(settings: Settings) -> str:
    explicit = settings.storage_backend
    if explicit:
        return explicit
    # Backward-compatible default when PHOTOS_STORAGE_BACKEND is unset.
    if settings.aws_s3_bucket.strip():
        return "s3"
    return "memory"


def build_storage(settings: Settings) -> MediaStorage:
    backend = resolve_backend_name(settings)
    builder = STORAGE_BACKENDS.get(backend)
    if builder is None:
        supported = ", ".join(sorted(STORAGE_BACKENDS))
        raise PhotoStorageError(
            f"Unsupported storage backend '{backend}'. Supported backends: {supported}"
        )
    return builder(settings)
