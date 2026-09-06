package com.tinder.profiles.api.profile;

import com.tinder.profiles.api.profile.dto.success.ApiResponse;
import com.tinder.profiles.application.moderation.ProfileContentModerator;
import com.tinder.profiles.application.profile.port.in.ProfileQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileReportController {

    private final ProfileQuery profiles;
    private final ProfileContentModerator moderation;

    @PostMapping("/{id}/report")
    public ResponseEntity<ApiResponse<Void>> report(
            @PathVariable UUID id,
            @Valid @RequestBody ReportProfileRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        profiles.getOne(id);
        String reporter = jwt == null ? "anonymous" : jwt.getSubject();
        String text = "Reported profile " + id + " (" + request.reason() + "): " + request.details();
        moderation.submitReport("report:profile:" + id + ":" + reporter, text, reporter);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Report submitted", null));
    }

    public record ReportProfileRequest(
            @NotBlank @Size(max = 64) String reason,
            @NotBlank @Size(max = 2000) String details
    ) {
    }
}
