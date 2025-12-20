package com.tinder.profiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.util.KeycloakTestHelper;
import org.junit.jupiter.api.AfterAll;
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

    private final String profile = """
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

    private final String updatedProfile = """
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

    private final String invalidatedProfile = """
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



    @Test
    void contextLoads() {
    }

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
                        .content(profile)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andDo(print());
    }

    @Test
    public void createInvalid() throws Exception {


        mockMvc.perform(post("")
                        .content(invalidatedProfile)
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
                        .content(profile)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))

                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        UUID profileId = UUID.fromString(jsonNode.get("data").asText());

        MvcResult updated = mockMvc.perform(put("/{id}", profileId)
                        .content(updatedProfile)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String updatedResponseBody = updated.getResponse().getContentAsString();
        JsonNode updatedJsonNode = objectMapper.readTree(updatedResponseBody);


    }

    private MvcResult sendCorrectFirstPostRequestToMockMvc() throws Exception {
        return mockMvc.perform(post("")
                        .content(profile)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();
    }

}
