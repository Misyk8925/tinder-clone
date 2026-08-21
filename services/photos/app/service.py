from hashlib import sha256
from uuid import UUID, uuid4

from app import keys, variants
from app.exceptions import PhotoValidationError
from app.policy import PhotoPolicy
from app.storage import ObjectStorage


class PhotoService:
    def __init__(self, policy: PhotoPolicy, storage: ObjectStorage) -> None:
        self._policy = policy
        self._storage = storage

    def upload(
        self,
        owner_id: UUID,
        image: bytes,
        content_type: str | None,
        namespace: str = "photos",
    ) -> dict:
        keys.require_namespace(namespace)
        self._policy.require_allowed_content_type(content_type)
        self._policy.require_within_size_limit(len(image))

        dimensions = variants.probe(image)
        if dimensions is None:
            raise PhotoValidationError("Corrupted image")
        width, height = dimensions
        self._policy.require_within_dimension_limits(width, height)

        rendered = variants.render(image)
        storage_id = str(uuid4())
        for variant_name in keys.VARIANTS:
            self._storage.put(
                keys.variant_key(namespace, owner_id, storage_id, variant_name),
                rendered.of(variant_name),
                "image/jpeg",
            )

        original_key = keys.variant_key(namespace, owner_id, storage_id, "original")
        return {
            "storageId": storage_id,
            "originalUrl": self._storage.public_url(original_key),
            "largeUrl": self._storage.public_url(
                keys.variant_key(namespace, owner_id, storage_id, "large")
            ),
            "mediumUrl": self._storage.public_url(
                keys.variant_key(namespace, owner_id, storage_id, "medium")
            ),
            "smallUrl": self._storage.public_url(
                keys.variant_key(namespace, owner_id, storage_id, "small")
            ),
            "originalKey": original_key,
            "contentType": "image/jpeg",
            "size": len(rendered.original),
            "width": width,
            "height": height,
            "sha256": sha256(image).hexdigest(),
        }

    def delete(self, owner_id: UUID, storage_id: str, namespace: str = "photos") -> None:
        for key in keys.all_variant_keys(namespace, owner_id, storage_id):
            self._storage.delete(key)

    def download_url(
        self,
        owner_id: UUID,
        storage_id: str,
        variant: str,
        namespace: str = "photos",
    ) -> str:
        return self._storage.presigned_download_url(
            keys.variant_key(namespace, owner_id, storage_id, variant)
        )

    def cleanup_orphaned(
        self,
        owner_id: UUID,
        catalogued_storage_ids: list[str],
        namespace: str = "photos",
    ) -> int:
        catalogued = set(catalogued_storage_ids)
        deleted: set[str] = set()
        try:
            for key in self._storage.list_keys(keys.owner_prefix(namespace, owner_id)):
                storage_id = keys.storage_id_of(key)
                if storage_id in catalogued or storage_id in deleted:
                    continue
                deleted.add(storage_id)
                for variant_key in keys.all_variant_keys(namespace, owner_id, storage_id):
                    self._storage.delete(variant_key)
            return len(deleted)
        except Exception:
            return len(deleted)
