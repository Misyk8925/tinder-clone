package com.tinder.profiles.profile;

import com.tinder.profiles.outbox.ProfileOutboxService;
import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.preferences.PreferencesRepository;
import com.tinder.profiles.preferences.PreferencesService;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import com.tinder.profiles.profile.mapper.CreateProfileMapper;
import com.tinder.profiles.profile.mapper.GetProfileMapper;
import com.tinder.profiles.redis.ResilientCacheManager;
import com.tinder.profiles.security.InputSanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileApplicationServiceOutboxComponentTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private PreferencesRepository preferencesRepository;
    @Mock
    private ProfileDomainService domainService;
    @Mock
    private CreateProfileMapper createMapper;
    @Mock
    private GetProfileMapper getMapper;
    @Mock
    private ResilientCacheManager resilientCacheManager;
    @Mock
    private InputSanitizationService sanitizationService;
    @Mock
    private PreferencesService preferencesService;
    @Mock
    private ProfileOutboxService profileOutboxService;

    private ProfileApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProfileApplicationService(
                profileRepository,
                preferencesRepository,
                domainService,
                createMapper,
                getMapper,
                resilientCacheManager,
                sanitizationService,
                preferencesService,
                profileOutboxService
        );
    }

    @Test
    void create_ShouldEnqueueOutboxEvent() {
        CreateProfileDtoV1 dto = createDto();
        Profile toSave = new Profile();
        UUID savedProfileId = UUID.randomUUID();
        Profile savedProfile = new Profile();
        savedProfile.setProfileId(savedProfileId);

        Preferences preferences = new Preferences();
        preferences.setId(UUID.randomUUID());

        when(profileRepository.findByUserId("user-1")).thenReturn(null);
        when(domainService.sanitizeProfileData(dto)).thenReturn(dto);
        when(createMapper.toEntity(dto)).thenReturn(toSave);
        when(preferencesService.findOrCreate(dto.preferences())).thenReturn(preferences);
        when(profileRepository.save(toSave)).thenReturn(savedProfile);

        Profile result = service.create(dto, "user-1");

        assertThat(result.getProfileId()).isEqualTo(savedProfileId);
        verify(profileOutboxService).enqueueProfileCreated(
                argThat(event -> event.getProfileId().equals(savedProfileId))
        );
    }

    @Test
    void create_ShouldFailWhenOutboxWriteFails() {
        CreateProfileDtoV1 dto = createDto();
        Profile toSave = new Profile();
        Profile savedProfile = new Profile();
        savedProfile.setProfileId(UUID.randomUUID());

        Preferences preferences = new Preferences();
        preferences.setId(UUID.randomUUID());

        when(profileRepository.findByUserId("user-2")).thenReturn(null);
        when(domainService.sanitizeProfileData(dto)).thenReturn(dto);
        when(createMapper.toEntity(dto)).thenReturn(toSave);
        when(preferencesService.findOrCreate(dto.preferences())).thenReturn(preferences);
        when(profileRepository.save(toSave)).thenReturn(savedProfile);
        doThrow(new IllegalStateException("outbox insert failed"))
                .when(profileOutboxService)
                .enqueueProfileCreated(any());

        assertThatThrownBy(() -> service.create(dto, "user-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox insert failed");
    }

    @Test
    void delete_ShouldEnqueueOutboxEvent() {
        Profile profile = new Profile();
        UUID profileId = UUID.randomUUID();
        profile.setProfileId(profileId);
        profile.setDeleted(false);
        profile.setActive(true);

        when(profileRepository.findByUserId("user-3")).thenReturn(profile);
        when(domainService.canDeleteProfile(profile)).thenReturn(true);
        doAnswer(invocation -> {
            Profile p = invocation.getArgument(0);
            p.setDeleted(true);
            p.setActive(false);
            return null;
        }).when(domainService).markAsDeleted(profile);
        when(profileRepository.save(profile)).thenReturn(profile);

        Profile result = service.delete("user-3");

        assertThat(result.getProfileId()).isEqualTo(profileId);
        verify(profileOutboxService).enqueueProfileDeleted(
                argThat(event -> event.getProfileId().equals(profileId))
        );
        verify(resilientCacheManager).evict(eq("PROFILE_ENTITY_CACHE"), eq(profileId));
    }

    private CreateProfileDtoV1 createDto() {
        return new CreateProfileDtoV1(
                "Alice",
                29,
                "female",
                "bio",
                "Berlin",
                new PreferencesDto(null, 24, 40, "male", 30)
        );
    }
}
