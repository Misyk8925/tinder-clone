from uuid import UUID

from app.exceptions import PhotoValidationError
from app.keys import all_variant_keys, base_key, owner_prefix, storage_id_of, variant_key

PROFILE_ID = UUID("11111111-2222-3333-4444-555555555555")
STORAGE_ID = "99999999-8888-7777-6666-555555555555"


def test_given_a_profile_owner_when_keys_are_built_then_they_follow_the_photos_layout():
    assert owner_prefix("photos", PROFILE_ID) == f"photos/{PROFILE_ID}/"
    assert base_key("photos", PROFILE_ID, STORAGE_ID) == f"photos/{PROFILE_ID}/{STORAGE_ID}"
    assert variant_key("photos", PROFILE_ID, STORAGE_ID, "medium") == (
        f"photos/{PROFILE_ID}/{STORAGE_ID}/medium.jpg"
    )
    assert len(all_variant_keys("photos", PROFILE_ID, STORAGE_ID)) == 4


def test_given_an_unknown_variant_when_a_key_is_built_then_it_is_rejected():
    try:
        variant_key("photos", PROFILE_ID, STORAGE_ID, "gigantic")
        raise AssertionError("expected PhotoValidationError")
    except PhotoValidationError as exc:
        assert "Unknown photo size: gigantic" in exc.message


def test_given_a_stored_key_when_the_storage_id_is_recovered_then_it_matches():
    assert storage_id_of(variant_key("photos", PROFILE_ID, STORAGE_ID, "original")) == STORAGE_ID


def test_given_cdn_and_bucket_urls_when_the_storage_id_is_recovered_then_it_matches():
    assert storage_id_of(
        f"https://d123.cloudfront.net/photos/{PROFILE_ID}/{STORAGE_ID}/medium.jpg"
    ) == STORAGE_ID
    assert storage_id_of(
        f"https://bucket.s3.eu-north-1.amazonaws.com/photos/{PROFILE_ID}/{STORAGE_ID}/small.jpg"
    ) == STORAGE_ID


def test_given_a_chat_namespace_key_when_the_storage_id_is_recovered_then_it_matches():
    key = variant_key("chat/photos", PROFILE_ID, STORAGE_ID, "original")
    assert key.startswith("chat/photos/")
    assert storage_id_of(key) == STORAGE_ID


def test_given_a_foreign_key_when_parsed_then_it_is_rejected():
    try:
        storage_id_of("avatars/whatever.jpg")
        raise AssertionError("expected PhotoValidationError")
    except PhotoValidationError as exc:
        assert "Invalid S3 key format" in exc.message
