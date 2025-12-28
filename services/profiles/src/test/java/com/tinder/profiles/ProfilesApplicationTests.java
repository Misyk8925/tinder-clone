package com.tinder.profiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.util.KeycloakTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    public void create() throws Exception {

        sendCorrectFirstPostRequestToMockMvc();
    }



    @Test
    public void createSameEntity() throws Exception {
        sendCorrectFirstPostRequestToMockMvc();

        mockMvc.perform(post("")
                        .content(PROFILE_CORRECT)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andDo(print());
    }

    @Test
    public void createInvalid() throws Exception {


        mockMvc.perform(post("")
                        .content(PROFILE_INVALID)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    public void checkDeleted() throws Exception {

        MvcResult result = sendCorrectFirstPostRequestToMockMvc();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        UUID profileId = UUID.fromString(jsonNode.get("data").asText());
        System.out.println(profileId);

        mockMvc.perform(get("/{id}", profileId)
                        .header(
                                "Authorization",
                                keycloakTestHelper.createAuthorizationHeader(
                                        "kovalmisha2000@gmail.com",
                                        "koval")
                        ))
                .andExpect(status().isOk())
                .andDo(print());

        mockMvc.perform(delete("/{id}", profileId).header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval")))
                .andExpect(status().isNoContent())
                .andDo(print());

    }

    @Test
    public void checkPutProfile() throws Exception {

        MvcResult result = mockMvc.perform(post("")
                        .content(PROFILE_CORRECT)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))

                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        UUID profileId = UUID.fromString(jsonNode.get("data").asText());

        MvcResult updated = mockMvc.perform(put("")
                        .content(PROFILE_UPDATED)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String updatedResponseBody = updated.getResponse().getContentAsString();
        JsonNode updatedJsonNode = objectMapper.readTree(updatedResponseBody);
    }

    @Test
    public void checkPatchProfileCorrect() throws  Exception {

        MvcResult result = sendCorrectFirstPostRequestToMockMvc();

        MvcResult patched = mockMvc.perform(patch("")
                        .content(PROFILE_PATCHED_CORRECT)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

    }

    @Test
    public void checkPatchProfileWrong() throws  Exception {
        MvcResult result = sendCorrectFirstPostRequestToMockMvc();

        mockMvc.perform(patch("")
                        .content(PROFILE_PATCHED_WRONG_AGE)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();
        mockMvc.perform(patch("")
                        .content(PROFILE_PATCHED_WRONG_CITY)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();
        mockMvc.perform(patch("")
                        .content(PROFILE_PATCHED_WRONG_GENDER)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();

        mockMvc.perform(patch("")
                        .content(PROFILE_PATCHED_WRONG_NAME_LENGTH)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andDo(print())
                .andReturn();


    }

    private MvcResult sendCorrectFirstPostRequestToMockMvc() throws Exception {
        return mockMvc.perform(post("")
                        .content(PROFILE_CORRECT)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();
    }

}
