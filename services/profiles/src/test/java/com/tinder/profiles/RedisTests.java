package com.tinder.profiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtSecurityConfig.class)
class RedisTests extends AbstractPostgresIntegrationTest {


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
                        .header("Authorization", TestJwtSecurityConfig.bearer("kovalmisha2000@gmail.com"))

                        .contentType(MediaType.APPLICATION_JSON))

                .andExpect(status().isCreated())
                .andReturn();


        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        UUID profileId = UUID.fromString(jsonNode.get("data").asText());


        Assertions.assertNotNull(profileId);
        Assertions.assertTrue(repo.findById(profileId).isPresent());

        // The write path evicts rather than populates (CQRS Stage 2); the entity
        // cache fills on the first read, so perform a read before asserting.
        mockMvc.perform(get("/api/v1/profiles/{id}", profileId)
                        .header("Authorization", TestJwtSecurityConfig.bearer("kovalmisha2000@gmail.com")))
                .andExpect(status().isOk());

        // check cache
        Assertions.assertTrue(cacheManager.getCacheNames().contains("PROFILE_ENTITY_CACHE"));
        Assertions.assertTrue(Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE")).get(profileId) != null);

        Object cached = Objects.requireNonNull(Objects.requireNonNull(cacheManager.getCache("PROFILE_ENTITY_CACHE")).get(profileId)).get();

        Assertions.assertTrue(cached instanceof ProfileJpaEntity profile1);
    }


}
