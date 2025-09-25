package com.tinder.profiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.profile.ProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

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
        String profile = """
                {
                    "name": "Misha",
                    "age": 34,
                    "bio": "this is my life",
                    "city": "Amstetten",
                    "preferences": {
                        "minAge": 19,
                        "maxAge": 40,
                        "gender": "female",
                        "maxRange": 4
                    }
                }""";

        mockMvc.perform(post("/api/v1/profiles")
                        .content(profile)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andDo(print());

        mockMvc.perform(post("/api/v1/profiles")
                        .content(profile)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andDo(print());
    }

    @Test
    public void createSameEntity() throws Exception {
        String profile = """
                {
                    "name": "Misha",
                    "age": 34,
                    "bio": "this is my life",
                    "city": "Amstetten",
                    "preferences": {
                        "minAge": 19,
                        "maxAge": 40,
                        "gender": "female",
                        "maxRange": 4
                    }
                }""";

        mockMvc.perform(post("/api/v1/profiles")
                        .content(profile)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andDo(print());
    }

    @Test
    public void createInvalid() throws Exception {
        String profile = """
                {
                    "name": "Misha",
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

        mockMvc.perform(post("/api/v1/profiles")
                        .content(profile)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    public void checkDeleted() throws Exception {
        String profile = """
                {
                    "name": "Misha",
                    "age": 34,
                    "bio": "this is my life",
                    "city": "Amstetten",
                    "preferences": {
                        "minAge": 19,
                        "maxAge": 40,
                        "gender": "female",
                        "maxRange": 4
                    }
                }""";

        MvcResult result = mockMvc.perform(post("/api/v1/profiles")
                        .content(profile)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        UUID profileId = UUID.fromString(jsonNode.get("data").asText());
        System.out.println(profileId);

        mockMvc.perform(get("/api/v1/profiles/{id}", profileId))
                .andExpect(status().isOk())
                .andDo(print());

        mockMvc.perform(delete("/api/v1/profiles/{id}", profileId))
                .andExpect(status().isNoContent())
                .andDo(print());

    }

    @Test
    public void checkPutProfile() throws Exception {
        String profile = """
                {
                    "name": "Misha",
                    "age": 34,
                    "bio": "this is my life",
                    "city": "Amstetten",
                    "preferences": {
                        "minAge": 19,
                        "maxAge": 40,
                        "gender": "female",
                        "maxRange": 4
                    }
                }""";

        MvcResult result = mockMvc.perform(post("/api/v1/profiles")
                        .content(profile)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        String updatedProfile = """
                {
                    "name": "Misha",
                    "age": 35,
                    "bio": "test",
                    "city": "Vienna",
                    "preferences": {
                        "minAge": 25,
                        "maxAge": 30,
                        "gender": "female",
                }""";

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        UUID profileId = UUID.fromString(jsonNode.get("data").asText());

        MvcResult updated = mockMvc.perform(put("/api/v1/profiles/{id}", profileId)
                        .content(updatedProfile)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String updatedResponseBody = updated.getResponse().getContentAsString();
        JsonNode updatedJsonNode = objectMapper.readTree(updatedResponseBody);
        String updatedProfileBody = updatedJsonNode.get("body").asText();

    }
}
