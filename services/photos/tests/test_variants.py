from io import BytesIO

from PIL import Image

from app.variants import probe, render
from tests.image_fixtures import png_bytes, rgba_png_bytes


def test_given_a_real_image_when_probed_then_its_dimensions_are_returned():
    width, height = probe(png_bytes(640, 480))
    assert width == 640
    assert height == 480


def test_given_undecodable_bytes_when_probed_then_the_result_is_absent():
    assert probe(b"not an image") is None
    assert probe(b"") is None
    assert probe(None) is None


def test_given_a_png_when_rendered_then_four_jpeg_variants_are_produced_in_descending_size():
    rendered = render(png_bytes(1024, 768))

    large = Image.open(BytesIO(rendered.large))
    medium = Image.open(BytesIO(rendered.medium))
    small = Image.open(BytesIO(rendered.small))
    original = Image.open(BytesIO(rendered.original))

    assert original.format == "JPEG"
    assert large.width <= 800
    assert small.width <= medium.width <= large.width


def test_given_the_same_bytes_when_rendered_twice_then_the_jpegs_match():
    image = png_bytes(512, 400)
    first = render(image)
    second = render(image)
    assert first.original == second.original
    assert first.large == second.large
    assert first.medium == second.medium
    assert first.small == second.small


def test_given_a_transparent_png_when_rendered_then_alpha_is_flattened_onto_white():
    rendered = render(rgba_png_bytes())
    original = Image.open(BytesIO(rendered.original)).convert("RGB")
    assert original.getpixel((0, 0))[0] > 100
