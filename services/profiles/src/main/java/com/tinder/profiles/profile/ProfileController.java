package com.tinder.profiles.profile;

import com.tinder.profiles.deck.DeckService;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.dto.profileData.deck.DeckProfileDto;
import com.tinder.profiles.profile.dto.success.ApiResponse;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.dto.profileData.PatchProfileDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.profiles.profile.cache.DeckPageCacheService;
import com.tinder.profiles.profile.internal.InternalProfileService;
import com.tinder.profiles.security.DeckHotPathTokenCache;
import com.tinder.profiles.security.InternalAuthVerifier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileApplicationService applicationService;
    private final DeckService deckService;
    private final InternalProfileService internalProfileService;
    private final IdsQueryParamParser idsQueryParamParser;
    private final DeckPageCacheService deckPageCacheService;
    private final InternalAuthVerifier internalAuthVerifier;
    private final DeckHotPathTokenCache deckHotPathTokenCache;

    @GetMapping("/by-ids")
    public ResponseEntity<List<SharedProfileDto>> getManyByIds(@RequestParam String ids) {
        try {
            List<UUID> uuidList = idsQueryParamParser.parse(ids);
            return ResponseEntity.ok(internalProfileService.getMany(uuidList));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping(value = "/deck", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDeck(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = InternalAuthVerifier.HEADER_NAME, required = false) String internalAuth,
            @RequestHeader(name = InternalAuthVerifier.USER_SUBJECT_HEADER, required = false) String internalUserSubject,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        String userSubject = resolveUserSubject(jwt, internalAuth, internalUserSubject);
        if (userSubject == null || userSubject.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID viewerId = applicationService.getActiveProfileIdByUserId(userSubject);
        if (viewerId == null) {
            return ResponseEntity.notFound().build();
        }
        deckHotPathTokenCache.put(jwt, viewerId);

        log.debug("GET /deck called for userId={}, profileId={}, offset={}, limit={}",
                userSubject, viewerId, offset, limit);

        String cachedJson = deckPageCacheService.get(viewerId, offset, limit);
        if (cachedJson != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cachedJson);
        }

        List<DeckProfileDto> deck = deckService.listDeckCards(viewerId, offset, limit);
        deckPageCacheService.put(viewerId, offset, limit, deck);
        return ResponseEntity.ok(deck);
    }

    private String resolveUserSubject(Jwt jwt, String internalAuth, String internalUserSubject) {
        if (internalAuthVerifier.isValid(internalAuth)) {
            return internalUserSubject;
        }
        return jwt == null ? null : jwt.getSubject();
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetProfileDto> getOne(@PathVariable UUID id) {
        log.info("getOne called with id: {}", id);
        return ResponseEntity.ok(applicationService.getOne(id));
    }

    @GetMapping("/me")
    public ResponseEntity<GetProfileDto> getMe(@AuthenticationPrincipal Jwt jwt) {
        log.debug("getMe called for userId: {}", jwt.getSubject());
        GetProfileDto profileDto = applicationService.getMyProfile(jwt.getSubject());
        if (profileDto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profileDto);
    }

    @PostMapping({"", "/"})
    public ResponseEntity<Object> create(@RequestBody @Valid CreateProfileDtoV1 profile,
                                         @AuthenticationPrincipal Jwt jwt) {
        Profile newProfile = applicationService.create(profile, jwt.getSubject());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Profile created successfully", newProfile.getProfileId()));
    }

    @PutMapping({"", "/"})
    public ResponseEntity<Object> update(@RequestBody @Valid CreateProfileDtoV1 profile,
                                         @AuthenticationPrincipal Jwt jwt) {
        Profile updatedProfile = applicationService.update(profile, jwt.getSubject());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Profile updated successfully", updatedProfile.getProfileId()));
    }

    @PatchMapping({"", "/"})
    public Profile patch(@AuthenticationPrincipal Jwt jwt,
                        @RequestBody @Valid PatchProfileDto patchDto) {
        return applicationService.patch(jwt.getSubject(), patchDto);
    }


    @DeleteMapping({"", "/"})
    public ResponseEntity<Void> delete(
                                       @AuthenticationPrincipal Jwt jwt) {
        applicationService.delete(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete-many")
    public void deleteMany(@RequestParam List<UUID> ids) {
        applicationService.deleteMany(ids);
    }

}
