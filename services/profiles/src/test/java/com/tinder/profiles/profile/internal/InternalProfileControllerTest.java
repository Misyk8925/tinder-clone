package com.tinder.profiles.profile.internal;

import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.profile.dto.profileData.shared.SharedProfileDto;
import com.tinder.profiles.profile.mapper.SharedProfileMapper;
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
    private SharedProfileMapper sharedMapper;

    private final List<UUID> testProfileIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Clean up
        profileRepository.deleteAll();
        testProfileIds.clear();

        // Create test profiles
        for (int i = 0; i < 5; i++) {
            Profile profile = new Profile();
            profile.setName("TestUser" + i);
            profile.setAge(20 + i);
            profile.setGender("MALE");
            profile.setBio("Test bio " + i);
            profile.setCity("TestCity");
            profile.setUserId("user-" + i);

            Profile saved = profileRepository.save(profile);
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
        MvcResult result = mockMvc.perform(get("/internal/by-ids")
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
        MvcResult result = mockMvc.perform(get("/internal/by-ids")
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
        mockMvc.perform(get("/internal/by-ids")
                        .param("ids", "")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject invalid UUID format")
    void testGetManyWithInvalidUuidFormat() throws Exception {
        mockMvc.perform(get("/internal/by-ids")
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
        MvcResult result = mockMvc.perform(get("/internal/by-ids")
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
        mockMvc.perform(get("/internal/by-ids")
                        .param("ids", idsParam)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
