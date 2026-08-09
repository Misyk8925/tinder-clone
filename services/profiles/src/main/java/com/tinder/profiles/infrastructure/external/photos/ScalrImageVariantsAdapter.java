package com.tinder.profiles.infrastructure.external.photos;

import com.tinder.profiles.application.photos.model.ImageDimensions;
import com.tinder.profiles.application.photos.model.PhotoVariants;
import com.tinder.profiles.application.photos.port.out.ImageVariantsPort;
import org.imgscalr.Scalr;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * imgscalr implementation of {@link ImageVariantsPort}: decodes an upload and
 * renders the four JPEG variants. Transparency is flattened onto white because
 * JPEG has no alpha channel.
 */
@Component
public class ScalrImageVariantsAdapter implements ImageVariantsPort {

    private static final int LARGE_PX = 800;
    private static final int MEDIUM_PX = 400;
    private static final int SMALL_PX = 150;

    @Override
    public Optional<ImageDimensions> probe(byte[] imageBytes) {
        return decode(imageBytes)
                .map(image -> new ImageDimensions(image.getWidth(), image.getHeight()));
    }

    @Override
    public PhotoVariants render(byte[] imageBytes) {
        BufferedImage original = decode(imageBytes)
                .orElseThrow(() -> new IllegalArgumentException("Invalid image file"));

        BufferedImage large = Scalr.resize(original, Scalr.Method.QUALITY,
                Scalr.Mode.FIT_TO_WIDTH, LARGE_PX, LARGE_PX, Scalr.OP_ANTIALIAS);
        BufferedImage medium = Scalr.resize(original, Scalr.Method.BALANCED,
                MEDIUM_PX, MEDIUM_PX, Scalr.OP_ANTIALIAS);
        BufferedImage small = Scalr.resize(original, Scalr.Method.SPEED,
                SMALL_PX, SMALL_PX, Scalr.OP_ANTIALIAS);

        return new PhotoVariants(toJpeg(original), toJpeg(large), toJpeg(medium), toJpeg(small));
    }

    private Optional<BufferedImage> decode(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(imageBytes)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private byte[] toJpeg(BufferedImage image) {
        BufferedImage source = image.getColorModel().hasAlpha() ? flattenOnWhite(image) : image;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(source, "jpg", out)) {
                throw new IOException("No JPEG writer available");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode image as JPEG", e);
        }
        return out.toByteArray();
    }

    private BufferedImage flattenOnWhite(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return rgb;
    }
}
