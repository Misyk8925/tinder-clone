from app.config import Settings
from app.main import _build_storage
from app.storage import MemoryStorage


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
