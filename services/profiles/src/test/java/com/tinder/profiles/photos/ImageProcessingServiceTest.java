package com.tinder.profiles.photos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for ImageProcessingService to verify the fix for the 0-byte upload issue.
 * Tests that image processing works correctly without stream exhaustion problems.
 */
class ImageProcessingServiceTest {

    private ImageProcessingService imageProcessingService;

    @BeforeEach
    void setUp() {
        imageProcessingService = new ImageProcessingService();
    }

    @Test
    @DisplayName("Should process image bytes without stream exhaustion")
    void testImageProcessingWithBytesDoesNotCauseStreamExhaustion() throws Exception {
        // Load test image from resources
        ClassPathResource imageResource = new ClassPathResource("static/test2.png");
        assertThat(imageResource.exists()).isTrue();

        byte[] originalBytes = Files.readAllBytes(imageResource.getFile().toPath());
        System.out.println("Original file size: " + originalBytes.length + " bytes");

        // Create mock multipart file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test2.png",
                "image/png",
                originalBytes
        );

        // This should NOT fail due to stream exhaustion
        imageProcessingService.validateImage(file);

        // This should also work and produce non-zero sized images
        ImageProcessingService.ProcessedImages processed =
                imageProcessingService.processUploadedImage(originalBytes);

        System.out.println("Processed sizes:");
        System.out.println("  Original: " + processed.original().length);
        System.out.println("  Large: " + processed.large().length);
        System.out.println("  Medium: " + processed.medium().length);
        System.out.println("  Small: " + processed.small().length);

        // Verify all sizes are non-zero
        assertThat(processed.original()).as("Original image bytes").isNotEmpty();
        assertThat(processed.large()).as("Large image bytes").isNotEmpty();
        assertThat(processed.medium()).as("Medium image bytes").isNotEmpty();
        assertThat(processed.small()).as("Small image bytes").isNotEmpty();

        // Verify images can be read back
        BufferedImage originalImg = ImageIO.read(new ByteArrayInputStream(processed.original()));
        BufferedImage largeImg = ImageIO.read(new ByteArrayInputStream(processed.large()));
        BufferedImage mediumImg = ImageIO.read(new ByteArrayInputStream(processed.medium()));
        BufferedImage smallImg = ImageIO.read(new ByteArrayInputStream(processed.small()));

        assertThat(originalImg).isNotNull();
        assertThat(largeImg).isNotNull();
        assertThat(mediumImg).isNotNull();
        assertThat(smallImg).isNotNull();

        // Verify size hierarchy
        assertThat(smallImg.getWidth()).isLessThanOrEqualTo(mediumImg.getWidth());
        assertThat(mediumImg.getWidth()).isLessThanOrEqualTo(largeImg.getWidth());
    }

    @Test
    @DisplayName("Should demonstrate the original stream exhaustion problem")
    void testOriginalStreamExhaustionProblem() throws Exception {
        // Load test image
        ClassPathResource imageResource = new ClassPathResource("static/test2.png");
        byte[] imageBytes = Files.readAllBytes(imageResource.getFile().toPath());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", imageBytes);

        System.out.println("File size: " + file.getSize());
        System.out.println("Image bytes length: " + imageBytes.length);

        // Simulate the original problematic flow:
        // 1. First validateImage() consumes the InputStream
        imageProcessingService.validateImage(file);

        System.out.println("After validateImage, trying processUploadedImage...");

        // Test the ByteArrayInputStream approach directly
        try {
            BufferedImage testImg = ImageIO.read(new ByteArrayInputStream(imageBytes));
            System.out.println("Direct ByteArrayInputStream - Image: " + (testImg != null ? testImg.getWidth() + "x" + testImg.getHeight() : "null"));
        } catch (Exception e) {
            System.out.println("Direct ByteArrayInputStream error: " + e.getMessage());
        }

        // Test file.getInputStream() approach
        try {
            BufferedImage testImg = ImageIO.read(file.getInputStream());
            System.out.println("file.getInputStream() - Image: " + (testImg != null ? testImg.getWidth() + "x" + testImg.getHeight() : "null"));
        } catch (Exception e) {
            System.out.println("file.getInputStream() error: " + e.getMessage());
        }

        // This WOULD fail with the old implementation, but now works with new one
        ImageProcessingService.ProcessedImages processed =
                imageProcessingService.processUploadedImage(file);

        System.out.println("Processed sizes:");
        System.out.println("  Original: " + processed.original().length);
        System.out.println("  Large: " + processed.large().length);
        System.out.println("  Medium: " + processed.medium().length);
        System.out.println("  Small: " + processed.small().length);

        // Let's also test with bytes directly
        ImageProcessingService.ProcessedImages processedBytes =
                imageProcessingService.processUploadedImage(imageBytes);

        System.out.println("Processed with bytes:");
        System.out.println("  Original: " + processedBytes.original().length);
        System.out.println("  Large: " + processedBytes.large().length);
        System.out.println("  Medium: " + processedBytes.medium().length);
        System.out.println("  Small: " + processedBytes.small().length);

        // Verify it works
        assertThat(processedBytes.original()).isNotEmpty();
        assertThat(processedBytes.large()).isNotEmpty();
        assertThat(processedBytes.medium()).isNotEmpty();
        assertThat(processedBytes.small()).isNotEmpty();
    }

    @Test
    @DisplayName("Should validate image correctly using MultipartFile interface")
    void testValidateImageUsingMultipartFile() throws Exception {
        ClassPathResource imageResource = new ClassPathResource("static/test2.png");
        byte[] imageBytes = Files.readAllBytes(imageResource.getFile().toPath());

        MockMultipartFile validFile = new MockMultipartFile(
                "file", "test.png", "image/png", imageBytes);

        // Should not throw exception
        imageProcessingService.validateImage(validFile);
    }

    @Test
    @DisplayName("Should reject invalid content types")
    void testValidateImageRejectsInvalidContentType() {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", "not an image".getBytes());

        assertThatThrownBy(() -> imageProcessingService.validateImage(invalidFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid image type");
    }

    @Test
    @DisplayName("Should reject files that are too large")
    void testValidateImageRejectsLargeFiles() {
        // Create a 6MB fake file (exceeds 5MB limit)
        byte[] largeData = new byte[6 * 1024 * 1024];

        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.png", "image/png", largeData);

        assertThatThrownBy(() -> imageProcessingService.validateImage(largeFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Image too large");
    }

    @Test
    @DisplayName("Should process the same image data multiple times without corruption")
    void testMultipleProcessingCalls() throws Exception {
        ClassPathResource imageResource = new ClassPathResource("static/test2.png");
        byte[] originalBytes = Files.readAllBytes(imageResource.getFile().toPath());

        // Process the same bytes multiple times
        ImageProcessingService.ProcessedImages first =
                imageProcessingService.processUploadedImage(originalBytes);
        ImageProcessingService.ProcessedImages second =
                imageProcessingService.processUploadedImage(originalBytes);

        // Results should be identical
        assertThat(first.original()).isEqualTo(second.original());
        assertThat(first.large()).isEqualTo(second.large());
        assertThat(first.medium()).isEqualTo(second.medium());
        assertThat(first.small()).isEqualTo(second.small());
    }
}
