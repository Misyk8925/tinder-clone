package com.tinder.profiles;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProfilesApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
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
}
