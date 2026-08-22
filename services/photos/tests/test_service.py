from uuid import UUID

from app.exceptions import PhotoValidationError
from app.keys import variant_key
from app.policy import PhotoPolicy
from app.service import PhotoService
from app.storage import MemoryStorage
from tests.image_fixtures import png_bytes

OWNER = UUID("11111111-2222-3333-4444-555555555555")


def service(storage: MemoryStorage | None = None) -> tuple[PhotoService, MemoryStorage]:
    memory = storage or MemoryStorage()
    policy = PhotoPolicy(5 * 1024 * 1024, ["image/jpeg", "image/png", "image/webp"], 300, 4096)
    return PhotoService(policy, memory), memory


def test_given_a_valid_png_when_uploaded_then_four_jpeg_variants_are_stored():
    photos, storage = service()
    uploaded = photos.upload(OWNER, png_bytes(), "image/png")

    assert uploaded["contentType"] == "image/jpeg"
    assert uploaded["width"] == 1024
    assert uploaded["height"] == 768
    assert len(uploaded["sha256"]) == 64
    assert uploaded["originalKey"] == variant_key("photos", OWNER, uploaded["storageId"], "original")
    assert len(storage.list_keys(f"photos/{OWNER}/")) == 4
    assert uploaded["smallUrl"].endswith("/small.jpg")


def test_given_an_occupied_storage_id_when_deleted_then_all_variants_are_removed():
    photos, storage = service()
    uploaded = photos.upload(OWNER, png_bytes(), "image/png")
    photos.delete(OWNER, uploaded["storageId"])
    assert storage.list_keys(f"photos/{OWNER}/") == []


def test_given_catalogued_and_orphan_objects_when_cleaned_then_only_orphans_are_removed():
    photos, storage = service()
    kept = photos.upload(OWNER, png_bytes(), "image/png")
    orphan = photos.upload(OWNER, png_bytes(400, 400, (1, 2, 3)), "image/png")

    deleted = photos.cleanup_orphaned(OWNER, [kept["storageId"]])

    assert deleted == 1
    remaining = storage.list_keys(f"photos/{OWNER}/")
    assert all(kept["storageId"] in key for key in remaining)
    assert all(orphan["storageId"] not in key for key in remaining)


def test_given_a_pdf_when_uploaded_then_it_is_rejected_before_storage():
    photos, storage = service()
    try:
        photos.upload(OWNER, b"%PDF", "application/pdf")
        raise AssertionError("expected PhotoValidationError")
    except PhotoValidationError as exc:
        assert "Invalid image type" in exc.message
    assert storage.list_keys(f"photos/{OWNER}/") == []


def test_given_undecodable_bytes_when_uploaded_then_corrupted_image_is_returned():
    photos, _ = service()
    try:
        photos.upload(OWNER, b"not-an-image", "image/png")
        raise AssertionError("expected PhotoValidationError")
    except PhotoValidationError as exc:
        assert exc.message == "Corrupted image"


def test_given_a_tiny_image_when_uploaded_then_it_is_rejected():
    photos, _ = service()
    try:
        photos.upload(OWNER, png_bytes(100, 100), "image/png")
        raise AssertionError("expected PhotoValidationError")
    except PhotoValidationError as exc:
        assert exc.message == "Image too small"


def test_given_a_chat_namespace_when_uploaded_then_keys_use_the_chat_prefix():
    photos, storage = service()
    uploaded = photos.upload(OWNER, png_bytes(), "image/png", namespace="chat/photos")
    assert uploaded["originalKey"].startswith(f"chat/photos/{OWNER}/")
    assert storage.list_keys(f"chat/photos/{OWNER}/")
