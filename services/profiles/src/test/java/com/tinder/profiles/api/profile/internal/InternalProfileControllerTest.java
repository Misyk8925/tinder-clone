package com.tinder.profiles.api.profile.internal;

import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.profiles.infrastructure.persistence.profile.mapper.SharedProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test for InternalProfileController /by-ids endpoint
 * Tests comma-separated UUID parameter parsing
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private com.tinder.profiles.infrastructure.persistence.location.LocationRepository locationRepository;

    @Autowired
    private com.tinder.profiles.infrastructure.persistence.preferences.PreferencesRepository preferencesRepository;

    private com.tinder.profiles.infrastructure.persistence.location.Location testLocation;

    @Autowired
    private SharedProfileMapper sharedMapper;

    private final List<UUID> testProfileIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Clean up
        profileRepository.deleteAll();
        testProfileIds.clear();

        // Profiles require a Location (location_id NOT NULL since the clean-domain refactor)
        org.locationtech.jts.geom.GeometryFactory gf =
                new org.locationtech.jts.geom.GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 4326);
        org.locationtech.jts.geom.Point pt = gf.createPoint(new org.locationtech.jts.geom.Coordinate(13.405, 52.52));
        pt.setSRID(4326);
        testLocation = locationRepository.findByCity("TestCity").orElseGet(() ->
                locationRepository.save(com.tinder.profiles.infrastructure.persistence.location.Location.builder()
                        .city("TestCity").geo(pt).build()));


        // Unique combination per save: PreferencesService's in-process cache survives
        // context reuse while create-drop wipes the rows, so cached ids would dangle.
        com.tinder.profiles.infrastructure.persistence.preferences.Preferences prefs =
                preferencesRepository.save(com.tinder.profiles.infrastructure.persistence.preferences.Preferences.builder()
                        .minAge(18).maxAge(99).gender("all")
                        .maxRange(1 + new java.util.Random().nextInt(500)).build());
        // Create test profiles
        for (int i = 0; i < 5; i++) {
            ProfileJpaEntity profile = new ProfileJpaEntity();
            profile.setName("TestUser" + i);
            profile.setAge(20 + i);
            profile.setGender("MALE");
            profile.setBio("Test bio " + i);
            profile.setCity("TestCity");
            profile.setUserId("user-" + i);
            profile.setLocation(testLocation);
            profile.setPreferences(prefs);

            ProfileJpaEntity saved = profileRepository.save(profile);
            testProfileIds.add(saved.getProfileId());
        }
    }

    @Test
    @DisplayName("Should fetch profiles by comma-separated UUIDs")
    void testGetManyWithCommaSeparatedIds() throws Exception {
        // Arrange: Create comma-separated UUID string
        String idsParam = String.join(",",
                testProfileIds.get(0).toString(),
                testProfileIds.get(1).toString(),
                testProfileIds.get(2).toString()
        );

        // Act: Call endpoint
        MvcResult result = mockMvc.perform(get("/api/v1/profiles/internal/by-ids").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("deck-service").roles("INTERNAL_CLIENT"))
                        .param("ids", idsParam)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert: Verify response
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).isNotEmpty();

        // Parse response (simplified - just check it's not empty)
        assertThat(responseBody).contains("TestUser0");
        assertThat(responseBody).contains("TestUser1");
        assertThat(responseBody).contains("TestUser2");
    }

    @Test
    @DisplayName("Should handle non-existent UUIDs gracefully")
    void testGetManyWithNonExistentIds() throws Exception {
        // Arrange: Mix of existing and non-existent UUIDs
        UUID nonExistent1 = UUID.randomUUID();
        UUID nonExistent2 = UUID.randomUUID();

        String idsParam = String.join(",",
                testProfileIds.get(0).toString(),
                nonExistent1.toString(),
                testProfileIds.get(1).toString(),
                nonExistent2.toString()
        );

        // Act & Assert: Should still return OK with partial results
        MvcResult result = mockMvc.perform(get("/api/v1/profiles/internal/by-ids").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("deck-service").roles("INTERNAL_CLIENT"))
                        .param("ids", idsParam)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Should contain the existing profiles
        assertThat(responseBody).contains("TestUser0");
        assertThat(responseBody).contains("TestUser1");
    }

    @Test
    @DisplayName("Should reject empty ids parameter")
    void testGetManyWithEmptyIds() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/internal/by-ids").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("deck-service").roles("INTERNAL_CLIENT"))
                        .param("ids", "")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject invalid UUID format")
    void testGetManyWithInvalidUuidFormat() throws Exception {
        mockMvc.perform(get("/api/v1/profiles/internal/by-ids").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("deck-service").roles("INTERNAL_CLIENT"))
                        .param("ids", "not-a-uuid,another-invalid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle whitespace in comma-separated list")
    void testGetManyWithWhitespace() throws Exception {
        // Arrange: UUIDs with extra whitespace
        String idsParam = String.format("%s , %s , %s",
                testProfileIds.get(0),
                testProfileIds.get(1),
                testProfileIds.get(2)
        );

        // Act & Assert: Should trim and parse correctly
        MvcResult result = mockMvc.perform(get("/api/v1/profiles/internal/by-ids").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("deck-service").roles("INTERNAL_CLIENT"))
                        .param("ids", idsParam)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("TestUser0");
        assertThat(responseBody).contains("TestUser1");
        assertThat(responseBody).contains("TestUser2");
    }

    @Test
    @DisplayName("Should reject more than 100 IDs")
    void testGetManyWithTooManyIds() throws Exception {
        // Arrange: Create 101 UUIDs
        List<String> manyIds = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            manyIds.add(UUID.randomUUID().toString());
        }
        String idsParam = String.join(",", manyIds);

        // Act & Assert: Should reject
        mockMvc.perform(get("/api/v1/profiles/internal/by-ids").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("deck-service").roles("INTERNAL_CLIENT"))
                        .param("ids", idsParam)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
