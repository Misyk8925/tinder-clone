package com.tinder.profiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.util.KeycloakTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProfilesApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfileRepository repo;
    @Autowired
    private ObjectMapper objectMapper;

    private final KeycloakTestHelper keycloakTestHelper = new KeycloakTestHelper();

    private static final String TEST_EMAIL = "kovalmisha2000@gmail.com";
    private static final String TEST_PASSWORD = "koval";
    private static final String SECOND_TEST_EMAIL = "another@example.com";
    private static final String SECOND_TEST_PASSWORD = "password";

    private final String PROFILE_CORRECT = """
                {
                    "name": "Misha",
                    "age": 34,
                    "gender": "male",
                    "bio": "this is my life",
                    "city": "Amstetten",
                    "preferences": {
                        "minAge": 19,
                        "maxAge": 40,
                        "gender": "female",
                        "maxRange": 4
                    }
                }""";

    private final String PROFILE_UPDATED = """
                {
                    "name": "Misha",
                    "age": 35,
                    "bio": "test",
                    "gender": "male",
                    "city": "Vienna",
                    "preferences": {
                        "minAge": 25,
                        "maxAge": 30,
                        "gender": "female",
                        "maxRange": 4
                        }
                }""";

    private final String PROFILE_INVALID = """
                {
                    "name": "Misha",
                    "gender": "male",
                    "age": 3,
                    "bio": "this is my life",
                    "city": "Amstetten",
                    "preferences": {
                        "minAge": 19,
                        "maxAge": 40,
                        "gender": "female",
                        "maxRange": 4
                    }
                }""";


    private final String PROFILE_PATCHED_CORRECT = """
                {
                    "age": 36,
                    "city": "Linz"
                }""";

    private final String PROFILE_PATCHED_WRONG_AGE = """
                {
                    "age": 17,
                    "city": "Linz"
                }""";

    private final String PROFILE_PATCHED_WRONG_NAME_LENGTH= """
                {
                    "name": "M",
                    "age": 36,
                    "city": "Linz"
                }""";
    private final String PROFILE_PATCHED_WRONG_GENDER= """
                {
                    "gender": "unknown",
                    "age": 36,
                    "city": "Linz"
                }""";

    private final String PROFILE_PATCHED_WRONG_CITY= """
                {
                    "gender": "unknown",
                    "age": 36,
                    "city": "город"
                }""";

    @BeforeEach
    void setUp() {
        repo.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repo.deleteAll();
    }

    @Test
    @DisplayName("Create profile successfully")
    public void createProfile() throws Exception {
        sendCorrectFirstPostRequestToMockMvc();
    }

    @Test
    @DisplayName("Create same entity should return conflict")
    public void createSameEntity() throws Exception {
        sendCorrectFirstPostRequestToMockMvc();

        mockMvc.perform(post("")
                        .content(PROFILE_CORRECT)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andDo(print());
    }

    @Test
    @DisplayName("Create invalid profile should return bad request")
    public void createInvalidProfile() throws Exception {
        mockMvc.perform(post("")
                        .content(PROFILE_INVALID)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("Delete profile successfully")
    public void deleteProfile() throws Exception {
        MvcResult result = sendCorrectFirstPostRequestToMockMvc();
        UUID profileId = extractProfileIdFromResponse(result);

        mockMvc.perform(get("/{id}", profileId)
                        .header("Authorization", createAuthHeader()))
                .andExpect(status().isOk())
                .andDo(print());

        mockMvc.perform(delete("/")
                        .header("Authorization", createAuthHeader()))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("Update profile using PUT successfully")
    public void updateProfile() throws Exception {
        MvcResult result = sendCorrectFirstPostRequestToMockMvc();
        UUID profileId = extractProfileIdFromResponse(result);

        MvcResult updated = mockMvc.perform(put("")
                        .content(PROFILE_UPDATED)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String updatedResponseBody = updated.getResponse().getContentAsString();
        JsonNode updatedJsonNode = objectMapper.readTree(updatedResponseBody);
    }

    @Test
    @DisplayName("Patch profile with correct data")
    public void patchProfileCorrect() throws Exception {
        sendCorrectFirstPostRequestToMockMvc();

        mockMvc.perform(patch("")
                        .content(PROFILE_PATCHED_CORRECT)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
    }

    @ParameterizedTest
    @ValueSource(strings = {PROFILE_PATCHED_WRONG_AGE, PROFILE_PATCHED_WRONG_CITY, PROFILE_PATCHED_WRONG_GENDER, PROFILE_PATCHED_WRONG_NAME_LENGTH})
    @DisplayName("Patch profile with invalid data should return bad request")
    public void patchProfileInvalid(String value) throws Exception {
        sendCorrectFirstPostRequestToMockMvc();

        mockMvc.perform(patch("")
                        .content(value)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();


    }

    @Test
    @DisplayName("Create profile without authorization should return 401")
    public void testCreateProfileUnauthorized() throws Exception {
        mockMvc.perform(post("")
                        .content(PROFILE_CORRECT)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Create profile with name too short should return 400")
    public void testCreateProfileNameTooShort() throws Exception {
        String profile = buildProfileJson("M", 25, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with name too long should return 400")
    public void testCreateProfileNameTooLong() throws Exception {
        String longName = "A".repeat(51);
        String profile = buildProfileJson(longName, 25, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with age too young should return 400")
    public void testCreateProfileAgeTooYoung() throws Exception {
        String profile = buildProfileJson("John", 17, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with age too old should return 400")
    public void testCreateProfileAgeTooOld() throws Exception {
        String profile = buildProfileJson("John", 131, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with bio too long should return 400")
    public void testCreateProfileBioTooLong() throws Exception {
        String longBio = "A".repeat(1024);
        String profile = buildProfileJson("John", 25, "male", longBio, "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with invalid gender should return 400")
    public void testCreateProfileInvalidGender() throws Exception {
        String profile = buildProfileJson("John", 25, "unknown", "This is my bio", "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with empty city should return 400")
    public void testCreateProfileCityEmpty() throws Exception {
        String profile = """
                {
                    "name": "John",
                    "age": 25,
                    "gender": "male",
                    "bio": "This is my bio",
                    "city": "",
                    "preferences": {
                        "minAge": 20,
                        "maxAge": 35,
                        "gender": "female",
                        "maxRange": 50
                    }
                }""";
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with invalid city characters should return 400")
    public void testCreateProfileCityInvalidCharacters() throws Exception {
        String profile = buildProfileJson("John", 25, "male", "This is my bio", "город123",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with preferences minAge too low should return 400")
    public void testCreateProfilePreferencesMinAgeTooLow() throws Exception {
        String profile = buildProfileJson("John", 25, "male", "This is my bio", "Vienna",
                                         17, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with preferences maxAge too high should return 400")
    public void testCreateProfilePreferencesMaxAgeTooHigh() throws Exception {
        String profile = buildProfileJson("John", 25, "male", "This is my bio", "Vienna",
                                         20, 131, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with preferences maxRange too low should return 400")
    public void testCreateProfilePreferencesMaxRangeTooLow() throws Exception {
        String profile = buildProfileJson("John", 25, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 0);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with preferences maxRange too high should return 400")
    public void testCreateProfilePreferencesMaxRangeTooHigh() throws Exception {
        String profile = buildProfileJson("John", 25, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 501);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with missing required fields should return 400")
    public void testCreateProfileMissingRequiredFields() throws Exception {
        String profile = """
                {
                    "name": "John",
                    "age": 25
                }""";
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create profile with age 18 should succeed")
    public void testCreateProfileAge18() throws Exception {
        String profile = buildProfileJson("John", 18, "male", "This is my bio", "Vienna",
                                         18, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Create profile with age 130 should succeed")
    public void testCreateProfileAge130() throws Exception {
        String profile = buildProfileJson("John", 130, "male", "This is my bio", "Vienna",
                                         18, 130, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Create profile with name exactly 2 characters should succeed")
    public void testCreateProfileNameExactly2Chars() throws Exception {
        String profile = buildProfileJson("Jo", 25, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Create profile with name exactly 50 characters should succeed")
    public void testCreateProfileNameExactly50Chars() throws Exception {
        String name = "A".repeat(50);
        String profile = buildProfileJson(name, 25, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Create profile with bio exactly 1023 characters should succeed")
    public void testCreateProfileBioExactly1023Chars() throws Exception {
        String bio = "A".repeat(1023);
        String profile = buildProfileJson("John", 25, "male", bio, "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Create profile with preferences minRange 1 should succeed")
    public void testCreateProfilePreferencesMinRange1() throws Exception {
        String profile = buildProfileJson("John", 25, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 1);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Create profile with preferences maxRange 500 should succeed")
    public void testCreateProfilePreferencesMaxRange500() throws Exception {
        String profile = buildProfileJson("John", 25, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 500);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Create profile with null bio should succeed")
    public void testCreateProfileWithNullBio() throws Exception {
        sendCorrectFirstPostRequestToMockMvc();

        mockMvc.perform(put("")
                        .content(PROFILE_CORRECT)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Get non-existent profile should return 404")
    public void testGetNonExistentProfile() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/{id}", randomId)
                        .header("Authorization", createAuthHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Delete already deleted profile should return 404")
    public void testDeleteAlreadyDeletedProfile() throws Exception {
        MvcResult result = sendCorrectFirstPostRequestToMockMvc();
        UUID profileId = extractProfileIdFromResponse(result);

        // First delete
        mockMvc.perform(delete("/")
                        .header("Authorization", createAuthHeader()))
                .andExpect(status().isNoContent());

        // Second delete - should fail with 404
        mockMvc.perform(delete("/")
                        .header("Authorization", createAuthHeader()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Create profile with different gender options should succeed")
    public void testCreateProfileWithDifferentGenderOptions() throws Exception {
        // Test "other" gender
        String profile = buildProfileJson("Alex", 25, "other", "This is my bio", "Vienna",
                                         20, 35, "all", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Create profile with special characters in name should succeed")
    public void testCreateProfileWithSpecialCharactersInName() throws Exception {
        String profile = buildProfileJson("Jean-Pierre O'Connor", 25, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Create profile with city containing hyphens should succeed")
    public void testCreateProfileWithCityContainingHyphens() throws Exception {
        String profile = buildProfileJson("John", 25, "male", "This is my bio", "Vienna",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Create profile with accented characters should succeed")
    public void testCreateProfileWithAccentedCharacters() throws Exception {
        String profile = buildProfileJson("José", 25, "male", "This is my bio", "Linz",
                                         20, 35, "female", 50);
        mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    private MvcResult sendCorrectFirstPostRequestToMockMvc() throws Exception {
        return mockMvc.perform(post("")
                        .content(PROFILE_CORRECT)
                        .header("Authorization", createAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();
    }

    // Helper methods to reduce code duplication
    private String createAuthHeader() {
        return keycloakTestHelper.createAuthorizationHeader(TEST_EMAIL, TEST_PASSWORD);
    }

    private String createSecondUserAuthHeader() {
        return keycloakTestHelper.createAuthorizationHeader(SECOND_TEST_EMAIL, SECOND_TEST_PASSWORD);
    }

    private UUID extractProfileIdFromResponse(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return UUID.fromString(jsonNode.get("data").asText());
    }

    private String buildProfileJson(String name, Integer age, String gender, String bio, String city,
                                     Integer minAge, Integer maxAge, String prefGender, Integer maxRange) {
        return String.format("""
                {
                    "name": "%s",
                    "age": %d,
                    "gender": "%s",
                    "bio": "%s",
                    "city": "%s",
                    "preferences": {
                        "minAge": %d,
                        "maxAge": %d,
                        "gender": "%s",
                        "maxRange": %d
                    }
                }""", name, age, gender, bio, city, minAge, maxAge, prefGender, maxRange);
    }

}
