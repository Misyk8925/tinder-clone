from pathlib import Path

import pytest

from app.config import Settings
from app.exceptions import PhotoNotFoundError, PhotoSignatureError, PhotoStorageError, PhotoValidationError
from app.main import _build_storage
from app.storage import (
    LocalFilesystemStorage,
    MediaStorage,
    MediaUrlSigner,
    MemoryStorage,
    build_storage,
    resolve_backend_name,
)


def test_given_an_empty_bucket_when_storage_is_built_then_memory_is_used():
    storage = _build_storage(
        Settings(aws_s3_bucket="", photos_internal_auth_secret="demo-secret")
    )

    assert isinstance(storage, MemoryStorage)


def test_given_a_blank_bucket_when_storage_is_built_then_memory_is_used():
    storage = _build_storage(
        Settings(aws_s3_bucket="   ", photos_internal_auth_secret="demo-secret")
    )

    assert isinstance(storage, MemoryStorage)


def test_given_no_explicit_backend_and_bucket_set_when_resolved_then_s3_is_selected():
    settings = Settings(
        photos_storage_backend="",
        aws_s3_bucket="my-prod-bucket",
        photos_internal_auth_secret="demo-secret",
    )
    assert resolve_backend_name(settings) == "s3"


def test_given_local_backend_when_put_get_list_delete_run_then_files_persist_and_cleanup_cleanly(
    tmp_path: Path,
):
    signer = MediaUrlSigner(secret="unit-secret", ttl_seconds=120, clock=lambda: 1000.0)
    storage = LocalFilesystemStorage(
        root_dir=tmp_path,
        public_base_url="https://api.example.test",
        signer=signer,
    )
    assert isinstance(storage, MediaStorage)

    key = "photos/11111111-2222-3333-4444-555555555555/abc/original.jpg"
    storage.put(key, b"jpeg-bytes", "image/jpeg")

    assert (tmp_path / key).read_bytes() == b"jpeg-bytes"
    assert storage.get(key) == b"jpeg-bytes"
    assert storage.list_keys("photos/11111111-2222-3333-4444-555555555555/") == [key]
    assert storage.public_url(key) == f"https://api.example.test/media/{key}"

    signed = storage.presigned_download_url(key)
    assert signed.startswith(f"https://api.example.test/media/{key}?exp=1120&sig=")

    storage.delete(key)
    assert not (tmp_path / key).exists()
    assert not (tmp_path / "photos" / "11111111-2222-3333-4444-555555555555" / "abc").exists()
    with pytest.raises(PhotoNotFoundError):
        storage.get(key)


def test_given_local_storage_when_traversal_key_is_passed_then_validation_error_is_raised(
    tmp_path: Path,
):
    signer = MediaUrlSigner(secret="unit-secret", ttl_seconds=120)
    storage = LocalFilesystemStorage(root_dir=tmp_path, public_base_url="", signer=signer)

    for bad_key in ("../escape.jpg", "/etc/passwd", "photos/../secret.jpg", "photos\\win.jpg"):
        with pytest.raises(PhotoValidationError):
            storage.put(bad_key, b"x", "image/jpeg")
        with pytest.raises(PhotoValidationError):
            storage.get(bad_key)


def test_given_media_url_signer_when_clock_advances_past_exp_then_verify_rejects():
    now = [1000.0]
    signer = MediaUrlSigner(secret="secret-key", ttl_seconds=60, clock=lambda: now[0])
    key = "photos/11111111-2222-3333-4444-555555555555/abc/medium.jpg"

    exp, sig = signer.sign(key)
    assert signer.verify(key, exp, sig) == 60

    now[0] = 1060.0
    with pytest.raises(PhotoSignatureError, match="expired"):
        signer.verify(key, exp, sig)


def test_given_local_backend_with_internal_secret_only_when_built_then_signing_falls_back_to_internal_secret(
    tmp_path: Path,
):
    storage = build_storage(
        Settings(
            photos_storage_backend="local",
            photos_local_root=str(tmp_path),
            photos_internal_auth_secret="shared-secret",
            photos_signing_secret="",
        )
    )
    assert isinstance(storage, LocalFilesystemStorage)


def test_given_unknown_backend_when_built_then_storage_error_is_raised():
    with pytest.raises(PhotoStorageError):
        build_storage(Settings(photos_storage_backend="azure"))
