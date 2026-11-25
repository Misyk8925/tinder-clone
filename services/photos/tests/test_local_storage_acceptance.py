"""Executable behavioural acceptance tests for local VPS media storage & storage abstraction.

Traceability: docs/features/local-media-storage/03-behaviour/README.md
"""

from __future__ import annotations

import time
from io import BytesIO
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from uuid import UUID

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.config import Settings
from app.exceptions import PhotoStorageError
from app.main import create_app
from app.storage import LocalFilesystemStorage, MediaStorage, MemoryStorage, build_storage
from tests.image_fixtures import png_bytes

OWNER = UUID("11111111-2222-3333-4444-555555555555")
INTERNAL_SECRET = "acceptance-internal-secret"
SIGNING_SECRET = "acceptance-signing-secret"
PUBLIC_BASE = "https://api.lunari.test"


def _local_settings(root: Path, **overrides) -> Settings:
    defaults = {
        "photos_storage_backend": "local",
        "photos_local_root": str(root),
        "photos_public_base_url": PUBLIC_BASE,
        "photos_internal_auth_secret": INTERNAL_SECRET,
        "photos_signing_secret": SIGNING_SECRET,
        "photos_presign_exp_seconds": 300,
    }
    defaults.update(overrides)
    return Settings(**defaults)


def _local_client(root: Path, **overrides) -> tuple[TestClient, Settings]:
    cfg = _local_settings(root, **overrides)
    api = TestClient(create_app(settings=cfg))
    return api, cfg


# --- FR-1: Pluggable storage abstraction & backend selection ---


def test_given_local_backend_configured_when_storage_is_built_then_local_storage_is_selected(
    tmp_path: Path,
):
    storage = build_storage(_local_settings(tmp_path))

    assert isinstance(storage, LocalFilesystemStorage)
    assert isinstance(storage, MediaStorage)


def test_given_memory_backend_configured_when_storage_is_built_then_memory_storage_is_selected():
    storage = build_storage(
        Settings(
            photos_storage_backend="memory",
            photos_internal_auth_secret=INTERNAL_SECRET,
        )
    )

    assert isinstance(storage, MemoryStorage)
    assert isinstance(storage, MediaStorage)


def test_given_unknown_backend_configured_when_storage_is_built_then_startup_fails_fast():
    with pytest.raises(PhotoStorageError, match="Unsupported storage backend"):
        build_storage(
            Settings(
                photos_storage_backend="gcs",
                photos_internal_auth_secret=INTERNAL_SECRET,
            )
        )


# --- FR-2 & FR-9: E2E local photo saving, disk persistence, signed GET, delete ---


def test_given_local_storage_when_photo_is_uploaded_fetched_and_deleted_then_e2e_lifecycle_succeeds(
    tmp_path: Path,
):
    api, _ = _local_client(tmp_path)
    auth_headers = {"X-Internal-Auth": INTERNAL_SECRET}

    # 1. Upload via HTTP
    upload_resp = api.post(
        "/api/v1/photos",
        headers=auth_headers,
        data={"owner_id": str(OWNER), "namespace": "photos"},
        files={"file": ("avatar.png", png_bytes(800, 600), "image/png")},
    )
    assert upload_resp.status_code == 201
    uploaded = upload_resp.json()
    storage_id = uploaded["storageId"]

    # 2. Verify all 4 real JPEG variants exist on disk under PHOTOS_LOCAL_ROOT
    variant_dir = tmp_path / "photos" / str(OWNER) / storage_id
    for variant in ("original", "large", "medium", "small"):
        file_path = variant_dir / f"{variant}.jpg"
        assert file_path.is_file(), f"Missing variant file on disk: {file_path}"
        with Image.open(file_path) as img:
            assert img.format == "JPEG"
            assert img.width > 0 and img.height > 0

    # 3. Request signed download URL (FR-4)
    dl_resp = api.get(
        f"/api/v1/photos/{storage_id}/download-url",
        headers=auth_headers,
        params={"owner_id": str(OWNER), "size": "medium"},
    )
    assert dl_resp.status_code == 200
    signed_url = dl_resp.json()["url"]
    parsed = urlparse(signed_url)
    assert signed_url.startswith(f"{PUBLIC_BASE}/media/photos/{OWNER}/{storage_id}/medium.jpg")
    qs = parse_qs(parsed.query)
    assert "exp" in qs and "sig" in qs

    # 4. Fetch bytes over public signed route WITHOUT X-Internal-Auth (FR-5)
    media_resp = api.get(f"{parsed.path}?{parsed.query}")
    assert media_resp.status_code == 200
    assert media_resp.headers["content-type"] == "image/jpeg"
    assert "private" in media_resp.headers.get("cache-control", "")
    assert media_resp.content == (variant_dir / "medium.jpg").read_bytes()

    # 5. Delete via HTTP and verify files are removed from disk (FR-3)
    del_resp = api.delete(
        f"/api/v1/photos/{storage_id}",
        headers=auth_headers,
        params={"owner_id": str(OWNER)},
    )
    assert del_resp.status_code == 204
    for variant in ("original", "large", "medium", "small"):
        assert not (variant_dir / f"{variant}.jpg").exists()

    # 6. Fetching previously signed URL now returns 404 PHOTO_NOT_FOUND
    missing_resp = api.get(f"{parsed.path}?{parsed.query}")
    assert missing_resp.status_code == 404
    assert missing_resp.json()["code"] == "PHOTO_NOT_FOUND"


# --- FR-3: Local orphan cleanup & chat/photos namespace ---


def test_given_local_orphans_on_disk_when_cleanup_is_called_then_only_uncatalogued_files_are_removed(
    tmp_path: Path,
):
    api, _ = _local_client(tmp_path)
    auth_headers = {"X-Internal-Auth": INTERNAL_SECRET}

    kept = api.post(
        "/api/v1/photos",
        headers=auth_headers,
        data={"owner_id": str(OWNER), "namespace": "chat/photos"},
        files={"file": ("kept.png", png_bytes(), "image/png")},
    ).json()
    orphan = api.post(
        "/api/v1/photos",
        headers=auth_headers,
        data={"owner_id": str(OWNER), "namespace": "chat/photos"},
        files={"file": ("orphan.png", png_bytes(400, 400), "image/png")},
    ).json()

    resp = api.post(
        "/api/v1/photos/cleanup-orphaned",
        headers=auth_headers,
        json={
            "ownerId": str(OWNER),
            "cataloguedStorageIds": [kept["storageId"]],
            "namespace": "chat/photos",
        },
    )
    assert resp.status_code == 200
    assert resp.json()["deleted"] == 1
    assert (tmp_path / "chat" / "photos" / str(OWNER) / kept["storageId"] / "original.jpg").is_file()
    assert not (tmp_path / "chat" / "photos" / str(OWNER) / orphan["storageId"]).exists()


# --- FR-5, NFR-1, NFR-2: Signed URL error paths & path traversal security ---


def test_given_invalid_or_expired_signature_when_media_is_requested_then_403_invalid_signature_is_returned(
    tmp_path: Path,
):
    api, _ = _local_client(tmp_path)
    auth_headers = {"X-Internal-Auth": INTERNAL_SECRET}
    uploaded = api.post(
        "/api/v1/photos",
        headers=auth_headers,
        data={"owner_id": str(OWNER)},
        files={"file": ("a.png", png_bytes(), "image/png")},
    ).json()
    signed = api.get(
        f"/api/v1/photos/{uploaded['storageId']}/download-url",
        headers=auth_headers,
        params={"owner_id": str(OWNER), "size": "small"},
    ).json()["url"]
    parsed = urlparse(signed)

    # Missing signature
    no_sig = api.get(parsed.path)
    assert no_sig.status_code == 403
    assert no_sig.json()["code"] == "INVALID_SIGNATURE"

    # Tampered signature
    tampered = api.get(f"{parsed.path}?exp={int(time.time()) + 300}&sig=0000deadbeef")
    assert tampered.status_code == 403
    assert tampered.json()["code"] == "INVALID_SIGNATURE"

    # Expired URL
    qs = parse_qs(parsed.query)
    expired_exp = int(time.time()) - 10
    expired = api.get(f"{parsed.path}?exp={expired_exp}&sig={qs['sig'][0]}")
    assert expired.status_code == 403
    assert expired.json()["code"] == "INVALID_SIGNATURE"


def test_given_path_traversal_attempt_when_media_is_requested_then_it_is_rejected(
    tmp_path: Path,
):
    secret_file = tmp_path.parent / "outside-secret.txt"
    secret_file.write_text("do-not-leak")
    api, _ = _local_client(tmp_path)

    resp = api.get(
        "/media/photos/..%2F..%2Foutside-secret.txt?exp=9999999999&sig=deadbeef"
    )
    assert resp.status_code in (400, 403, 404)
    assert b"do-not-leak" not in resp.content


def test_given_no_signing_secret_when_local_storage_is_built_then_it_fails_closed(
    tmp_path: Path,
):
    with pytest.raises(PhotoStorageError, match="signing secret"):
        build_storage(
            _local_settings(
                tmp_path,
                photos_signing_secret="",
                photos_internal_auth_secret="",
            )
        )


# --- FR-8: Compose & env contract for VPS local storage ---


def test_given_docker_compose_when_inspected_then_photos_supports_local_storage_volume_and_backend():
    repo_root = Path(__file__).resolve().parents[3]
    compose_text = (repo_root / "docker-compose.yml").read_text()
    env_example = (repo_root / ".env.prod.example").read_text()

    assert "PHOTOS_STORAGE_BACKEND" in compose_text
    assert "PHOTOS_LOCAL_ROOT" in compose_text
    assert "photos-data:/var/lib/photos" in compose_text
    assert "photos-data:" in compose_text
    assert "PHOTOS_STORAGE_BACKEND" in env_example
