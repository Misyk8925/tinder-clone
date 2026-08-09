package com.tinder.profiles.application.photos.support;

import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import com.tinder.profiles.application.photos.model.ImageDimensions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/**
 * The upload rules that used to be buried in the S3 service, now stated once and
 * checked here without any storage or image library involved.
 */
@DisplayName("PhotoPolicy")
class PhotoPolicyTest {

    private static final long FIVE_MB = 5L * 1024 * 1024;

    private final PhotoPolicy policy = new PhotoPolicy(
            5, FIVE_MB, List.of("image/jpeg", "image/png", "image/webp"), 300, 4096);

    @Nested
    @DisplayName("content type")
    class ContentType {

        @Test
        void acceptsSupportedTypes() {
            thenNoException().isThrownBy(() -> policy.requireAllowedContentType("image/png"));
        }

        @Test
        void rejectsOtherTypes() {
            thenThrownBy(() -> policy.requireAllowedContentType("application/pdf"))
                    .isInstanceOf(PhotoValidationException.class)
                    .hasMessageContaining("Invalid image type: application/pdf");
        }

        @Test
        void rejectsMissingType() {
            thenThrownBy(() -> policy.requireAllowedContentType(null))
                    .isInstanceOf(PhotoValidationException.class)
                    .hasMessage("Invalid image type");
        }
    }

    @Nested
    @DisplayName("size")
    class Size {

        @Test
        void acceptsExactlyTheLimit() {
            thenNoException().isThrownBy(() -> policy.requireWithinSizeLimit(FIVE_MB));
        }

        @Test
        void rejectsAnythingLarger() {
            thenThrownBy(() -> policy.requireWithinSizeLimit(FIVE_MB + 1))
                    .isInstanceOf(PhotoValidationException.class)
                    .hasMessageContaining("Image too large");
        }
    }

    @Nested
    @DisplayName("dimensions")
    class Dimensions {

        @Test
        void acceptsImagesInsideTheBounds() {
            thenNoException().isThrownBy(
                    () -> policy.requireWithinDimensionLimits(new ImageDimensions(1024, 768)));
        }

        @Test
        void rejectsTooSmall() {
            thenThrownBy(() -> policy.requireWithinDimensionLimits(new ImageDimensions(299, 400)))
                    .isInstanceOf(PhotoValidationException.class)
                    .hasMessage("Image too small");
        }

        @Test
        void rejectsTooLarge() {
            thenThrownBy(() -> policy.requireWithinDimensionLimits(new ImageDimensions(4097, 400)))
                    .isInstanceOf(PhotoValidationException.class)
                    .hasMessage("Image dimensions too large");
        }
    }

    @Nested
    @DisplayName("slots")
    class Slots {

        @Test
        void allowsReplacingAnOccupiedSlot() {
            thenNoException().isThrownBy(() -> policy.requireAssignablePosition(1, 3));
        }

        @Test
        void allowsAppendingToTheNextFreeSlot() {
            thenNoException().isThrownBy(() -> policy.requireAssignablePosition(3, 3));
        }

        @Test
        void rejectsSlotsBeyondTheMaximum() {
            thenThrownBy(() -> policy.requireAssignablePosition(5, 2))
                    .isInstanceOf(PhotoValidationException.class)
                    .hasMessageContaining("Invalid position: 5");
        }

        @Test
        void rejectsNegativeSlots() {
            thenThrownBy(() -> policy.requireAssignablePosition(-1, 2))
                    .isInstanceOf(PhotoValidationException.class)
                    .hasMessageContaining("Invalid position: -1");
        }

        @Test
        void rejectsUploadsOntoAFullAlbum() {
            thenThrownBy(() -> policy.requireFreeSlot(5))
                    .isInstanceOf(PhotoValidationException.class)
                    .hasMessage("Maximum of 5 photos allowed per profile");
        }

        @Test
        void allowsUploadsWhileSlotsRemain() {
            thenNoException().isThrownBy(() -> policy.requireFreeSlot(4));
        }
    }
}
