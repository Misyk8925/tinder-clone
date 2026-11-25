from urllib.parse import urlparse
from uuid import UUID

from app.exceptions import PhotoValidationError

VARIANTS = ("original", "large", "medium", "small")
EXTENSION = ".jpg"
ALLOWED_NAMESPACES = ("photos", "chat/photos")


def require_namespace(namespace: str) -> str:
    if namespace not in ALLOWED_NAMESPACES:
        raise PhotoValidationError(f"Unknown photo namespace: {namespace}")
    return namespace


def require_known_variant(variant: str) -> str:
    if variant not in VARIANTS:
        raise PhotoValidationError(f"Unknown photo size: {variant}")
    return variant


def owner_prefix(namespace: str, owner_id: UUID) -> str:
    return f"{require_namespace(namespace)}/{owner_id}/"


def base_key(namespace: str, owner_id: UUID, storage_id: str) -> str:
    return f"{require_namespace(namespace)}/{owner_id}/{storage_id}"


def variant_key(namespace: str, owner_id: UUID, storage_id: str, variant: str) -> str:
    return f"{base_key(namespace, owner_id, storage_id)}/{require_known_variant(variant)}{EXTENSION}"


def all_variant_keys(namespace: str, owner_id: UUID, storage_id: str) -> list[str]:
    return [variant_key(namespace, owner_id, storage_id, variant) for variant in VARIANTS]


def storage_id_of(key_or_url: str) -> str:
    if not key_or_url:
        raise PhotoValidationError("S3 key or URL cannot be null or empty")

    path = _path_of(key_or_url) if key_or_url.startswith("http") else key_or_url
    parts = path.split("/")
    if len(parts) < 4:
        raise PhotoValidationError(
            f"Invalid S3 key format: {path}. Expected {{namespace}}/{{ownerId}}/{{storageId}}/{{variant}}.jpg"
        )
    if parts[0] == "photos":
        return parts[2]
    if len(parts) >= 5 and parts[0] == "chat" and parts[1] == "photos":
        return parts[3]
    raise PhotoValidationError(
        f"Invalid S3 key format: {path}. Expected {{namespace}}/{{ownerId}}/{{storageId}}/{{variant}}.jpg"
    )


def validate_key(key: str) -> str:
    """Validate that a media object key matches `{namespace}/{ownerId}/{storageId}/{variant}.jpg`."""
    if not key or key.startswith("/") or "\\" in key or "\x00" in key:
        raise PhotoValidationError(f"Invalid media key: {key}")
    parts = key.split("/")
    if any(part in ("", ".", "..") for part in parts):
        raise PhotoValidationError(f"Invalid media key: {key}")
    if len(parts) == 4 and parts[0] == "photos":
        owner_raw, storage_id, filename = parts[1], parts[2], parts[3]
    elif len(parts) == 5 and parts[0] == "chat" and parts[1] == "photos":
        owner_raw, storage_id, filename = parts[2], parts[3], parts[4]
    else:
        raise PhotoValidationError(
            f"Invalid media key format: {key}. Expected {{namespace}}/{{ownerId}}/{{storageId}}/{{variant}}.jpg"
        )
    try:
        UUID(owner_raw)
    except ValueError as exc:
        raise PhotoValidationError(f"Invalid owner id in media key: {owner_raw}") from exc
    if not storage_id or not filename.endswith(EXTENSION):
        raise PhotoValidationError(f"Invalid media key format: {key}")
    variant = filename[: -len(EXTENSION)]
    require_known_variant(variant)
    return key


def _path_of(url: str) -> str:
    parsed = urlparse(url)
    if not parsed.scheme or not parsed.netloc:
        raise PhotoValidationError("Invalid URL format: " + url)
    return parsed.path[1:] if parsed.path.startswith("/") else parsed.path
