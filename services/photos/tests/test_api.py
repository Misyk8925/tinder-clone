from uuid import UUID

from fastapi.testclient import TestClient

from app.main import create_app
from app.storage import MemoryStorage
from tests.image_fixtures import png_bytes

OWNER = UUID("11111111-2222-3333-4444-555555555555")


def client() -> tuple[TestClient, MemoryStorage]:
    storage = MemoryStorage()
    return TestClient(create_app(storage=storage)), storage


def test_given_a_valid_image_when_posted_then_the_service_returns_variant_urls():
    api, _ = client()
    response = api.post(
        "/api/v1/photos",
        data={"owner_id": str(OWNER)},
        files={"file": ("photo.png", png_bytes(), "image/png")},
    )
    assert response.status_code == 201
    body = response.json()
    assert body["storageId"]
    assert body["originalUrl"].endswith("/original.jpg")
    assert body["largeUrl"].endswith("/large.jpg")
    assert body["contentType"] == "image/jpeg"
    assert body["width"] == 1024


def test_given_an_unsupported_type_when_posted_then_invalid_image_is_returned():
    api, _ = client()
    response = api.post(
        "/api/v1/photos",
        data={"owner_id": str(OWNER)},
        files={"file": ("doc.pdf", b"%PDF", "application/pdf")},
    )
    assert response.status_code == 400
    assert response.json()["code"] == "INVALID_IMAGE"
    assert "Invalid image type" in response.json()["message"]


def test_given_a_stored_photo_when_deleted_then_the_objects_are_gone():
    api, storage = client()
    uploaded = api.post(
        "/api/v1/photos",
        data={"owner_id": str(OWNER)},
        files={"file": ("photo.png", png_bytes(), "image/png")},
    ).json()

    response = api.delete(f"/api/v1/photos/{uploaded['storageId']}", params={"owner_id": str(OWNER)})
    assert response.status_code == 204
    assert storage.list_keys(f"photos/{OWNER}/") == []


def test_given_a_stored_photo_when_a_download_url_is_requested_then_a_signed_url_is_returned():
    api, _ = client()
    uploaded = api.post(
        "/api/v1/photos",
        data={"owner_id": str(OWNER)},
        files={"file": ("photo.png", png_bytes(), "image/png")},
    ).json()

    response = api.get(
        f"/api/v1/photos/{uploaded['storageId']}/download-url",
        params={"owner_id": str(OWNER), "size": "small"},
    )
    assert response.status_code == 200
    assert "small.jpg" in response.json()["url"]


def test_given_an_unknown_size_when_a_download_url_is_requested_then_it_is_rejected():
    api, _ = client()
    uploaded = api.post(
        "/api/v1/photos",
        data={"owner_id": str(OWNER)},
        files={"file": ("photo.png", png_bytes(), "image/png")},
    ).json()
    response = api.get(
        f"/api/v1/photos/{uploaded['storageId']}/download-url",
        params={"owner_id": str(OWNER), "size": "gigantic"},
    )
    assert response.status_code == 400
    assert response.json()["code"] == "INVALID_IMAGE"


def test_given_orphans_when_cleanup_is_posted_then_only_uncatalogued_objects_are_removed():
    api, storage = client()
    kept = api.post(
        "/api/v1/photos",
        data={"owner_id": str(OWNER)},
        files={"file": ("a.png", png_bytes(), "image/png")},
    ).json()
    api.post(
        "/api/v1/photos",
        data={"owner_id": str(OWNER)},
        files={"file": ("b.png", png_bytes(400, 400), "image/png")},
    )

    response = api.post(
        "/api/v1/photos/cleanup-orphaned",
        json={"ownerId": str(OWNER), "cataloguedStorageIds": [kept["storageId"]]},
    )
    assert response.status_code == 200
    assert response.json()["deleted"] == 1
    remaining = storage.list_keys(f"photos/{OWNER}/")
    assert all(kept["storageId"] in key for key in remaining)


def test_given_a_health_check_when_requested_then_the_service_is_up():
    api, _ = client()
    assert api.get("/health").json() == {"status": "UP"}
    assert api.get("/actuator/health").json() == {"status": "UP"}


def test_given_an_empty_file_when_posted_then_it_is_rejected():
    api, _ = client()
    response = api.post(
        "/api/v1/photos",
        data={"owner_id": str(OWNER)},
        files={"file": ("empty.png", b"", "image/png")},
    )
    assert response.status_code == 400
    assert "Photo file is required" in response.json()["message"]
