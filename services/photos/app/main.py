from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api import get_photo_service, health_router, media_router, router
from app.config import Settings, get_settings
from app.exceptions import PhotoError
from app.policy import PhotoPolicy
from app.service import PhotoService
from app.storage import MediaStorage, MediaUrlSigner, build_storage


def create_app(storage: MediaStorage | None = None, settings: Settings | None = None) -> FastAPI:
    resolved_settings = settings or get_settings()
    resolved_storage = storage or _build_storage(resolved_settings)
    signer = MediaUrlSigner(
        secret=resolved_settings.signing_secret,
        ttl_seconds=resolved_settings.presign_exp_seconds,
    )
    policy = PhotoPolicy(
        max_size_bytes=resolved_settings.max_size_bytes,
        allowed_content_types=resolved_settings.allowed_types,
        min_dimension_px=resolved_settings.min_dimension_px,
        max_dimension_px=resolved_settings.max_dimension_px,
    )
    photo_service = PhotoService(policy, resolved_storage, signer=signer)

    app = FastAPI(title="Photos Service", version="1.0.0")
    app.state.internal_auth_secret = resolved_settings.internal_auth_secret
    app.state.max_upload_bytes = resolved_settings.max_size_bytes
    app.include_router(health_router)
    app.include_router(media_router)
    app.include_router(router)
    app.dependency_overrides[get_photo_service] = lambda: photo_service

    @app.exception_handler(PhotoError)
    async def handle_photo_error(_request: Request, exc: PhotoError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content={"code": exc.code, "message": exc.message},
        )

    return app


def _build_storage(settings: Settings) -> MediaStorage:
    return build_storage(settings)


app = create_app()
