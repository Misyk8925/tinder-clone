from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, UploadFile
from pydantic import BaseModel, Field

from app.exceptions import PhotoValidationError
from app.service import PhotoService

router = APIRouter()


class CleanupRequest(BaseModel):
    ownerId: UUID
    cataloguedStorageIds: list[str] = Field(default_factory=list)
    namespace: str = "photos"


class DownloadUrlResponse(BaseModel):
    url: str


class CleanupResponse(BaseModel):
    deleted: int


def get_photo_service() -> PhotoService:
    raise RuntimeError("Photo service is not configured")


@router.post("/api/v1/photos", status_code=201)
async def upload_photo(
    owner_id: UUID = Form(...),
    namespace: str = Form("photos"),
    file: UploadFile = File(...),
    service: PhotoService = Depends(get_photo_service),
) -> dict:
    image = await file.read()
    if not image:
        raise PhotoValidationError("Photo file is required")
    return service.upload(owner_id, image, file.content_type, namespace)


@router.delete("/api/v1/photos/{storage_id}", status_code=204)
def delete_photo(
    storage_id: str,
    owner_id: UUID,
    namespace: str = "photos",
    service: PhotoService = Depends(get_photo_service),
) -> None:
    service.delete(owner_id, storage_id, namespace)


@router.get("/api/v1/photos/{storage_id}/download-url", response_model=DownloadUrlResponse)
def download_url(
    storage_id: str,
    owner_id: UUID,
    size: str = "medium",
    namespace: str = "photos",
    service: PhotoService = Depends(get_photo_service),
) -> DownloadUrlResponse:
    return DownloadUrlResponse(url=service.download_url(owner_id, storage_id, size, namespace))


@router.post("/api/v1/photos/cleanup-orphaned", response_model=CleanupResponse)
def cleanup_orphaned(
    request: CleanupRequest,
    service: PhotoService = Depends(get_photo_service),
) -> CleanupResponse:
    deleted = service.cleanup_orphaned(
        request.ownerId,
        request.cataloguedStorageIds,
        request.namespace,
    )
    return CleanupResponse(deleted=deleted)


@router.get("/health")
@router.get("/actuator/health")
def health() -> dict[str, str]:
    return {"status": "UP"}
