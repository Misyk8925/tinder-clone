package com.tinder.profiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileRepository;

import com.tinder.profiles.util.KeycloakTestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RedisTests {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.2.1-alpine"))
            .withExposedPorts(6379);


    private final String createProfileBody = """
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

    private final String  updateProfileBody = """
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

    KeycloakTestHelper keycloakTestHelper = new KeycloakTestHelper();



    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfileRepository repo;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private ProfileRepository spyRepo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
    }

    @Test
    public void createProfileAndCheckCache() throws Exception {


        MvcResult result = mockMvc.perform(post("/api/v1/profiles")
                        .content(createProfileBody)
                        .header("Authorization", keycloakTestHelper.createAuthorizationHeader("kovalmisha2000@gmail.com", "koval"))

                        .contentType(MediaType.APPLICATION_JSON))

                .andExpect(status().isCreated())
                .andReturn();


        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        UUID profileId = UUID.fromString(jsonNode.get("data").asText());


        Assertions.assertNotNull(profileId);
        Assertions.assertTrue(repo.findById(profileId).isPresent());

        // check cache
        Assertions.assertTrue(cacheManager.getCacheNames().contains("PROFILE_ENTITY_CACHE"));
        Assertions.assertTrue(Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE")).get(profileId) != null);

        Object cached = Objects.requireNonNull(Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE")).get(profileId)).get();

        Assertions.assertTrue(cached instanceof Profile profile1);
    }


}