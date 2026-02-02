package com.tinder.profiles.photos;

import org.imgscalr.Scalr;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ImageProcessingService {

    /**
     * Create multiple sizes of an uploaded image.
     * Uses pre-read bytes to avoid stream exhaustion issues.
     */
    public ProcessedImages processUploadedImage(MultipartFile file)
            throws IOException {
        return processUploadedImage(file.getBytes());
    }

    /**
     * Create multiple sizes from raw image bytes.
     */
    public ProcessedImages processUploadedImage(byte[] imageBytes)
            throws IOException {

        // Read original image from bytes
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));

        // Validate
        if (original == null) {
            throw new IllegalArgumentException("Invalid image file");
        }

        // Create thumbnails
        BufferedImage large = Scalr.resize(original,
                Scalr.Method.QUALITY,
                Scalr.Mode.FIT_TO_WIDTH,
                800, 800,
                Scalr.OP_ANTIALIAS
        );

        BufferedImage medium = Scalr.resize(original,
                Scalr.Method.BALANCED,
                400, 400,
                Scalr.OP_ANTIALIAS
        );

        BufferedImage small = Scalr.resize(original,
                Scalr.Method.SPEED,
                150, 150,
                Scalr.OP_ANTIALIAS
        );

        // Convert to bytes for S3 upload
        return new ProcessedImages(
                toBytes(original, "jpg"),
                toBytes(large, "jpg"),
                toBytes(medium, "jpg"),
                toBytes(small, "jpg")
        );
    }

    /**
     * Create square thumbnail for profile pictures
     */
    public byte[] createSquareThumbnail(BufferedImage image, int size)
            throws IOException {

        int width = image.getWidth();
        int height = image.getHeight();

        // Crop to square first
        int cropSize = Math.min(width, height);
        int x = (width - cropSize) / 2;
        int y = (height - cropSize) / 2;

        BufferedImage square = Scalr.crop(image, x, y, cropSize, cropSize);

        // Then resize
        BufferedImage thumbnail = Scalr.resize(square,
                Scalr.Method.ULTRA_QUALITY,
                size, size,
                Scalr.OP_ANTIALIAS
        );

        return toBytes(thumbnail, "jpg");
    }

    /**
     * Validate image dimensions and type.
     * Now reads bytes once and delegates to byte-based validation.
     */
    public void validateImage(MultipartFile file) throws IOException {
        // Check MIME type
        String contentType = file.getContentType();
        if (!List.of("image/jpeg", "image/png", "image/webp")
                .contains(contentType)) {
            throw new IllegalArgumentException("Invalid image type"+ (contentType == null ? "" : ": "+contentType));
        }

        // Check file size (5MB max)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Image too large ("+file.getSize()+" bytes)");
        }

        // Read bytes once and validate - THIS IS THE KEY FIX!
        byte[] imageBytes = file.getBytes();
        validateImageBytes(imageBytes);
    }

    /**
     * Validate image from raw bytes
     */
    public void validateImageBytes(byte[] imageBytes) throws IOException {
        // Verify actual image content
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (img == null) {
            throw new IllegalArgumentException("Corrupted image");
        }

        // Check dimensions (min 300x300, max 4096x4096)
        if (img.getWidth() < 300 || img.getHeight() < 300) {
            throw new IllegalArgumentException("Image too small");
        }
        if (img.getWidth() > 4096 || img.getHeight() > 4096) {
            throw new IllegalArgumentException("Image dimensions too large");
        }
    }

    private byte[] toBytes(BufferedImage image, String format)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // If we're converting to JPEG, we need to handle transparency
        if ("jpg".equals(format) || "jpeg".equals(format)) {
            // Create a new RGB image if the original has transparency (PNG)
            if (image.getColorModel().hasAlpha()) {
                BufferedImage rgbImage = new BufferedImage(
                        image.getWidth(),
                        image.getHeight(),
                        BufferedImage.TYPE_INT_RGB
                );

                // Fill with white background and draw the original image
                Graphics2D g2d = rgbImage.createGraphics();
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
                g2d.drawImage(image, 0, 0, null);
                g2d.dispose();

                image = rgbImage;
            }
        }

        boolean written = ImageIO.write(image, format, baos);
        if (!written) {
            throw new IOException("Failed to write image in format: " + format);
        }

        return baos.toByteArray();
    }

    public record ProcessedImages(
            byte[] original,
            byte[] large,
            byte[] medium,
            byte[] small
    ) {}
}
