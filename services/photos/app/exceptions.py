class PhotoError(Exception):
    def __init__(self, message: str, code: str, status_code: int) -> None:
        super().__init__(message)
        self.message = message
        self.code = code
        self.status_code = status_code


class PhotoValidationError(PhotoError):
    def __init__(self, message: str) -> None:
        super().__init__(message, "INVALID_IMAGE", 400)


class PhotoStorageError(PhotoError):
    def __init__(self, message: str) -> None:
        super().__init__(message, "PHOTO_STORAGE_ERROR", 503)
