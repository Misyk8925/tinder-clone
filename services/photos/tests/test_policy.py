from app.exceptions import PhotoValidationError
from app.policy import PhotoPolicy

FIVE_MB = 5 * 1024 * 1024


def policy() -> PhotoPolicy:
    return PhotoPolicy(
        max_size_bytes=FIVE_MB,
        allowed_content_types=["image/jpeg", "image/png", "image/webp"],
        min_dimension_px=300,
        max_dimension_px=4096,
    )


class TestContentType:
    def test_given_a_supported_type_when_validated_then_it_is_accepted(self):
        policy().require_allowed_content_type("image/png")

    def test_given_an_unsupported_type_when_validated_then_it_is_rejected(self):
        try:
            policy().require_allowed_content_type("application/pdf")
            raise AssertionError("expected PhotoValidationError")
        except PhotoValidationError as exc:
            assert "Invalid image type: application/pdf" in exc.message
            assert exc.code == "INVALID_IMAGE"

    def test_given_a_missing_type_when_validated_then_it_is_rejected(self):
        try:
            policy().require_allowed_content_type(None)
            raise AssertionError("expected PhotoValidationError")
        except PhotoValidationError as exc:
            assert exc.message == "Invalid image type"


class TestSize:
    def test_given_an_image_at_the_limit_when_validated_then_it_is_accepted(self):
        policy().require_within_size_limit(FIVE_MB)

    def test_given_an_oversized_image_when_validated_then_it_is_rejected(self):
        try:
            policy().require_within_size_limit(FIVE_MB + 1)
            raise AssertionError("expected PhotoValidationError")
        except PhotoValidationError as exc:
            assert "Image too large" in exc.message


class TestDimensions:
    def test_given_dimensions_inside_the_bounds_when_validated_then_they_are_accepted(self):
        policy().require_within_dimension_limits(1024, 768)

    def test_given_an_image_below_the_minimum_when_validated_then_it_is_rejected(self):
        try:
            policy().require_within_dimension_limits(299, 400)
            raise AssertionError("expected PhotoValidationError")
        except PhotoValidationError as exc:
            assert exc.message == "Image too small"

    def test_given_an_image_above_the_maximum_when_validated_then_it_is_rejected(self):
        try:
            policy().require_within_dimension_limits(4097, 400)
            raise AssertionError("expected PhotoValidationError")
        except PhotoValidationError as exc:
            assert exc.message == "Image dimensions too large"
