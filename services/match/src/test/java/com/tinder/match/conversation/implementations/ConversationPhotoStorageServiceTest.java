package com.tinder.match.conversation.implementations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationPhotoStorageService")
class ConversationPhotoStorageServiceTest {

    @Mock
    private PhotosServiceClient photosServiceClient;

    @Test
    @DisplayName("Given a valid chat photo, when uploaded, then the photos service result is mapped onto the attachment")
    void mapsPhotosServiceResponse() {
        ConversationPhotoStorageService service = new ConversationPhotoStorageService(photosServiceClient);
        UUID conversationId = UUID.randomUUID();
        byte[] image = "png".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "shot.png", "image/png", image);
        given(photosServiceClient.upload(eq(conversationId), eq(image), eq("image/png"), eq("shot.png")))
                .willReturn(new PhotosServiceClient.PhotosUploadResponse(
                        "storage-1",
                        "https://cdn/original.jpg",
                        "https://cdn/large.jpg",
                        "https://cdn/medium.jpg",
                        "https://cdn/small.jpg",
                        "chat/photos/" + conversationId + "/storage-1/original.jpg",
                        "image/jpeg",
                        1234L,
                        1024,
                        768,
                        "abc123"
                ));
        given(photosServiceClient.presignedDownloadUrl(eq(conversationId), eq("storage-1"), eq("original"), eq("chat/photos")))
                .willReturn("https://cdn/original.jpg?X-Amz-Signature=abc");

        ConversationPhotoStorageService.UploadedPhoto uploaded = service.uploadPhoto(
                file, conversationId, UUID.randomUUID(), UUID.randomUUID());

        then(uploaded.storageKey()).endsWith("/original.jpg");
        then(uploaded.url()).isEqualTo("https://cdn/original.jpg?X-Amz-Signature=abc");
        then(uploaded.mimeType()).isEqualTo("image/jpeg");
        then(uploaded.sizeBytes()).isEqualTo(1234L);
        then(uploaded.originalName()).isEqualTo("shot.png");
        then(uploaded.width()).isEqualTo(1024);
        then(uploaded.height()).isEqualTo(768);
        then(uploaded.sha256()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("Given a missing file, when uploaded, then the photos service is not called")
    void rejectsMissingFile() {
        ConversationPhotoStorageService service = new ConversationPhotoStorageService(photosServiceClient);
        MockMultipartFile empty = new MockMultipartFile("file", "shot.png", "image/png", new byte[0]);

        thenThrownBy(() -> service.uploadPhoto(empty, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Photo file is required");

        verify(photosServiceClient, never()).upload(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Given a stored chat photo key, when a download url is requested, then the photos service presign is returned")
    void hydratesPresignedDownloadUrl() {
        ConversationPhotoStorageService service = new ConversationPhotoStorageService(photosServiceClient);
        UUID conversationId = UUID.randomUUID();
        given(photosServiceClient.presignedDownloadUrl(eq(conversationId), eq("storage-1"), eq("original"), eq("chat/photos")))
                .willReturn("https://signed.example/photo");

        String url = service.downloadUrl(
                conversationId,
                "chat/photos/" + conversationId + "/storage-1/original.jpg",
                "https://cdn/original.jpg");

        then(url).isEqualTo("https://signed.example/photo");
    }

    @Test
    @DisplayName("Given several stored keys, when download URLs are requested, then each key is presigned")
    void hydratesPresignedDownloadUrlsInBatch() {
        ConversationPhotoStorageService service = new ConversationPhotoStorageService(photosServiceClient);
        UUID conversationId = UUID.randomUUID();
        String firstKey = "chat/photos/" + conversationId + "/storage-1/original.jpg";
        String secondKey = "chat/photos/" + conversationId + "/storage-2/original.jpg";
        given(photosServiceClient.presignedDownloadUrl(eq(conversationId), eq("storage-1"), eq("original"), eq("chat/photos")))
                .willReturn("https://signed.example/one");
        given(photosServiceClient.presignedDownloadUrl(eq(conversationId), eq("storage-2"), eq("original"), eq("chat/photos")))
                .willReturn("https://signed.example/two");

        var urls = service.downloadUrls(conversationId, List.of(
                new ConversationPhotoStorageService.PhotoRef(firstKey, "https://cdn/one.jpg"),
                new ConversationPhotoStorageService.PhotoRef(secondKey, "https://cdn/two.jpg")
        ));

        then(urls).containsEntry(firstKey, "https://signed.example/one");
        then(urls).containsEntry(secondKey, "https://signed.example/two");
    }

    @Test
    @DisplayName("Given an unsupported type, when uploaded, then the photos service is not called")
    void rejectsUnsupportedType() {
        ConversationPhotoStorageService service = new ConversationPhotoStorageService(photosServiceClient);
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "pdf".getBytes());

        thenThrownBy(() -> service.uploadPhoto(pdf, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid image type");

        verify(photosServiceClient, never()).upload(any(), any(), anyString(), anyString());
    }
}
