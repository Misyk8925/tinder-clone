package com.tinder.profiles.api.profile;
import com.tinder.profiles.application.profile.port.in.ProfileQuery;
import com.tinder.profiles.application.profile.port.in.InternalProfileQuery;
import com.tinder.profiles.application.profile.command.DeleteProfileCommand;
import com.tinder.profiles.application.profile.command.DeleteProfilesCommand;
import com.tinder.profiles.application.profile.usecase.CreateProfileService;
import com.tinder.profiles.application.profile.usecase.DeleteProfileService;
import com.tinder.profiles.application.profile.usecase.DeleteProfilesService;
import com.tinder.profiles.application.profile.usecase.PatchProfileService;
import com.tinder.profiles.application.profile.usecase.UpdateProfileService;
import com.tinder.profiles.api.profile.mapper.ProfileApiMapper;

import com.tinder.profiles.api.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.api.profile.dto.success.ApiResponse;
import com.tinder.profiles.api.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.api.profile.dto.profileData.PatchProfileDto;
import com.tinder.contracts.dto.SharedProfileDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

    private final ProfileQuery profileQuery;
    private final CreateProfileService createProfileUseCase;
    private final UpdateProfileService updateProfileUseCase;
    private final PatchProfileService patchProfileUseCase;
    private final DeleteProfileService deleteProfileUseCase;
    private final DeleteProfilesService deleteProfilesUseCase;
    private final ProfileApiMapper apiMapper;
    private final InternalProfileQuery internalProfileQuery;
    private final IdsQueryParamParser idsQueryParamParser;

    @GetMapping("/by-ids")
    public ResponseEntity<List<SharedProfileDto>> getManyByIds(@RequestParam String ids) {
        try {
            List<UUID> uuidList = idsQueryParamParser.parse(ids);
            return ResponseEntity.ok(internalProfileQuery.getMany(uuidList).stream()
                    .map(apiMapper::toSharedProfileDto)
                    .toList());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetProfileDto> getOne(@PathVariable UUID id) {
        log.info("getOne called with id: {}", id);
        return ResponseEntity.ok(apiMapper.toGetProfileDto(profileQuery.getOne(id)));
    }

    @GetMapping("/me")
    public ResponseEntity<GetProfileDto> getMe(@AuthenticationPrincipal Jwt jwt) {
        log.debug("getMe called for userId: {}", jwt.getSubject());
        GetProfileDto profileDto = apiMapper.toGetProfileDto(profileQuery.getMyProfile(jwt.getSubject()));
        if (profileDto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profileDto);
    }

    @PostMapping({"", "/"})
    public ResponseEntity<Object> create(@RequestBody @Valid CreateProfileDtoV1 profile,
                                         @AuthenticationPrincipal Jwt jwt) {
        UUID newProfileId = createProfileUseCase.handle(apiMapper.toCreateCommand(profile, jwt.getSubject()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Profile created successfully", newProfileId));
    }

    @PutMapping({"", "/"})
    public ResponseEntity<Object> update(@RequestBody @Valid CreateProfileDtoV1 profile,
                                         @AuthenticationPrincipal Jwt jwt) {
        UUID updatedProfileId = updateProfileUseCase.handle(apiMapper.toUpdateCommand(profile, jwt.getSubject()));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Profile updated successfully", updatedProfileId));
    }

    @PatchMapping({"", "/"})
    public ResponseEntity<GetProfileDto> patch(@AuthenticationPrincipal Jwt jwt,
                                               @RequestBody @Valid PatchProfileDto patchDto) {
        UUID profileId = patchProfileUseCase.handle(apiMapper.toPatchCommand(patchDto, jwt.getSubject()));
        return ResponseEntity.ok(apiMapper.toGetProfileDto(profileQuery.getOne(profileId)));
    }


    @DeleteMapping({"", "/"})
    public ResponseEntity<Void> delete(
                                       @AuthenticationPrincipal Jwt jwt) {
        deleteProfileUseCase.handle(new DeleteProfileCommand(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/delete-many")
    public void deleteMany(@RequestParam List<UUID> ids) {
        deleteProfilesUseCase.handle(new DeleteProfilesCommand(ids));
    }

}
