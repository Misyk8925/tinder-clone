package com.tinder.profiles.photos;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class S3PhotoController {

    private final S3PhotoService service;

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Map<String, String> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam @NotBlank(message = "missing key") String key
    ) throws IOException {
        service.upload(file.getBytes(), key, file.getContentType());
        return Map.of("key", key);

    }
}