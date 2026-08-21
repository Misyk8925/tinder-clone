from io import BytesIO

from PIL import Image


def png_bytes(width: int = 1024, height: int = 768, color: tuple[int, int, int] = (10, 20, 30)) -> bytes:
    image = Image.new("RGB", (width, height), color)
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def rgba_png_bytes(width: int = 400, height: int = 400) -> bytes:
    image = Image.new("RGBA", (width, height), (10, 20, 30, 128))
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()
