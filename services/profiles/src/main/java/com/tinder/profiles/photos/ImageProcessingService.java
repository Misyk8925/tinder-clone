package com.tinder.profiles.photos;

import org.imgscalr.Scalr;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ImageProcessingService {

    /**
     * Create multiple sizes of an uploaded image
     */
    public ProcessedImages processUploadedImage(MultipartFile file)
            throws IOException {

        // Read original image
        BufferedImage original = ImageIO.read(file.getInputStream());

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
     * Validate image dimensions and type
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

        // Verify actual image content
        BufferedImage img = ImageIO.read(file.getInputStream());
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
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    public record ProcessedImages(
            byte[] original,
            byte[] large,
            byte[] medium,
            byte[] small
    ) {}
}
