package com.tinder.profiles.photos;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3PhotoService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final PhotoRepository photoRepository;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.presign-exp-seconds:3000}")
    private int presignExpSeconds;

    public void uploadAndSave(byte[] data, String key, String contentType, String userId) {
        UUID uuid = UUID.fromString(userId);
        upload(data, key, contentType);

        Photo photo = new Photo();

        photo.setS3Key(key);
        photo.setContentType(contentType);
        photo.setSize(data.length);
        photo.setUrl("https://" + bucket + ".s3.amazonaws.com/" + key);
        photo.setPrimary(false);
        photo.setProfileId(uuid); // Uncomment and set userId if Photo entity has userId field
        photo.setCreatedAt(LocalDateTime.now());

        photoRepository.save(photo);
    }

    public int getPhotoCountForUser(String userId) {
        UUID uuid = UUID.fromString(userId);
        return photoRepository.countByProfileId(uuid);
    }

    public String getPhotoUrl(String key) {
        byte[] check = download(key);
        if (check == null) {
            return null;
        }
        return "https://" + bucket + ".s3.amazonaws.com/" + key;
    }

    public String upload(byte[] data, String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .metadata(Map.of("x-origin", "spring-boot"))
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));
        return key;
    }

    public byte[] download(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        return s3Client.getObjectAsBytes(request).asByteArray();
    }

    public URL presignUpload(String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignExpSeconds))
                .putObjectRequest(request)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url();
    }

    public URL presignDownload(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignExpSeconds))
                .getObjectRequest(request)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url();
    }

    public void delete(String key) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
        );
    }

}