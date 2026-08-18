package com.tinder.profiles.infrastructure.external.photos;

import com.tinder.profiles.application.photos.model.ImageDimensions;
import com.tinder.profiles.application.photos.model.PhotoVariants;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * The adapter decodes and resizes; it never decides whether an image is
 * acceptable — that is {@code PhotoPolicy}'s job. Reading bytes (rather than a
 * stream) is what keeps repeated processing of the same upload safe.
 */
@Slf4j
@DisplayName("ScalrImageVariantsAdapter")
class ScalrImageVariantsAdapterTest {

    private ScalrImageVariantsAdapter adapter;
    private byte[] pngBytes;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new ScalrImageVariantsAdapter();
        pngBytes = Files.readAllBytes(new ClassPathResource("static/test2.png").getFile().toPath());
    }

    @Test
    @DisplayName("probes the dimensions of a real image")
    void probesDimensions() {
        ImageDimensions dimensions = adapter.probe(pngBytes).orElseThrow();

        then(dimensions.width()).isPositive();
        then(dimensions.height()).isPositive();
    }

    @Test
    @DisplayName("reports undecodable bytes as absent rather than throwing")
    void probeReturnsEmptyForNonImages() {
        then(adapter.probe("not an image".getBytes())).isEmpty();
        then(adapter.probe(new byte[0])).isEmpty();
        then(adapter.probe(null)).isEmpty();
    }

    @Test
    @DisplayName("renders four non-empty JPEG variants in descending size")
    void rendersAllVariants() throws Exception {
        PhotoVariants variants = adapter.render(pngBytes);

        then(variants.original()).isNotEmpty();
        then(variants.large()).isNotEmpty();
        then(variants.medium()).isNotEmpty();
        then(variants.small()).isNotEmpty();

        BufferedImage large = read(variants.large());
        BufferedImage medium = read(variants.medium());
        BufferedImage small = read(variants.small());
        then(small.getWidth()).isLessThanOrEqualTo(medium.getWidth());
        then(medium.getWidth()).isLessThanOrEqualTo(large.getWidth());
    }

    @Test
    @DisplayName("renders the same bytes repeatedly without corruption")
    void renderIsRepeatable() {
        PhotoVariants first = adapter.render(pngBytes);
        PhotoVariants second = adapter.render(pngBytes);

        then(first.original()).isEqualTo(second.original());
        then(first.large()).isEqualTo(second.large());
        then(first.medium()).isEqualTo(second.medium());
        then(first.small()).isEqualTo(second.small());
    }

    @Test
    @DisplayName("exposes each variant by name")
    void exposesVariantsByName() {
        PhotoVariants variants = adapter.render(pngBytes);

        then(variants.of("original")).isEqualTo(variants.original());
        then(variants.of("large")).isEqualTo(variants.large());
        then(variants.of("medium")).isEqualTo(variants.medium());
        then(variants.of("small")).isEqualTo(variants.small());
    }

    private BufferedImage read(byte[] jpeg) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
        then(image).isNotNull();
        return image;
    }
}
