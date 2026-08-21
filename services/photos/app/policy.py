from dataclasses import dataclass


@dataclass(frozen=True)
class PhotoPolicy:
    max_size_bytes: int
    allowed_content_types: list[str]
    min_dimension_px: int
    max_dimension_px: int

    def require_allowed_content_type(self, content_type: str | None) -> None:
        from app.exceptions import PhotoValidationError

        if content_type is None or content_type not in self.allowed_content_types:
            suffix = "" if content_type is None else f": {content_type}"
            raise PhotoValidationError("Invalid image type" + suffix)

    def require_within_size_limit(self, size_bytes: int) -> None:
        from app.exceptions import PhotoValidationError

        if size_bytes > self.max_size_bytes:
            raise PhotoValidationError(f"Image too large ({size_bytes} bytes)")

    def require_within_dimension_limits(self, width: int, height: int) -> None:
        from app.exceptions import PhotoValidationError

        if width < self.min_dimension_px or height < self.min_dimension_px:
            raise PhotoValidationError("Image too small")
        if width > self.max_dimension_px or height > self.max_dimension_px:
            raise PhotoValidationError("Image dimensions too large")
