from dataclasses import dataclass
from io import BytesIO

from PIL import Image

from app.exceptions import PhotoValidationError

LARGE_PX = 800
MEDIUM_PX = 400
SMALL_PX = 150


@dataclass(frozen=True)
class PhotoVariants:
    original: bytes
    large: bytes
    medium: bytes
    small: bytes

    def of(self, variant: str) -> bytes:
        mapping = {
            "original": self.original,
            "large": self.large,
            "medium": self.medium,
            "small": self.small,
        }
        if variant not in mapping:
            raise PhotoValidationError(f"Unknown photo size: {variant}")
        return mapping[variant]


def probe(image_bytes: bytes | None) -> tuple[int, int] | None:
    image = _decode(image_bytes)
    if image is None:
        return None
    return image.size


def render(image_bytes: bytes) -> PhotoVariants:
    original = _decode(image_bytes)
    if original is None:
        raise PhotoValidationError("Corrupted image")

    large = _resize_fit_width(original, LARGE_PX, Image.Resampling.LANCZOS)
    medium = _resize_contain(original, MEDIUM_PX, Image.Resampling.BILINEAR)
    small = _resize_contain(original, SMALL_PX, Image.Resampling.BILINEAR)

    return PhotoVariants(
        original=_to_jpeg(original),
        large=_to_jpeg(large),
        medium=_to_jpeg(medium),
        small=_to_jpeg(small),
    )


def _decode(image_bytes: bytes | None) -> Image.Image | None:
    if not image_bytes:
        return None
    try:
        with Image.open(BytesIO(image_bytes)) as image:
            image.load()
            return image.convert("RGBA") if image.mode in ("P", "LA") else image.copy()
    except Exception:
        return None


def _resize_fit_width(image: Image.Image, target_width: int, resample: Image.Resampling) -> Image.Image:
    if image.width <= 0:
        return image
    ratio = target_width / image.width
    target_height = max(1, round(image.height * ratio))
    return image.resize((target_width, target_height), resample)


def _resize_contain(image: Image.Image, box: int, resample: Image.Resampling) -> Image.Image:
    if image.width <= 0 or image.height <= 0:
        return image
    ratio = min(box / image.width, box / image.height)
    width = max(1, round(image.width * ratio))
    height = max(1, round(image.height * ratio))
    return image.resize((width, height), resample)


def _to_jpeg(image: Image.Image) -> bytes:
    source = _flatten_on_white(image) if "A" in image.getbands() else image.convert("RGB")
    buffer = BytesIO()
    source.save(buffer, format="JPEG", quality=75)
    return buffer.getvalue()


def _flatten_on_white(image: Image.Image) -> Image.Image:
    rgb = Image.new("RGB", image.size, (255, 255, 255))
    rgba = image.convert("RGBA")
    rgb.paste(rgba, mask=rgba.split()[-1])
    return rgb
