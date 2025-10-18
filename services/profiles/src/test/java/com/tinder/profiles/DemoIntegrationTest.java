package com.tinder.profiles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.user.NewUserRecord;
import com.tinder.profiles.user.UserService;
import com.tinder.profiles.util.KeycloakTestHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
public class DemoIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DemoIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    private final KeycloakTestHelper keycloakTestHelper = new KeycloakTestHelper();

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${swipes.base-url:http://localhost:8020}")
    private String swipesBaseUrl;

    @Value("${deck.base-url:http://localhost:8030}")
    private String deckBaseUrl;

    public void createUsers() {
        userService.createTestUsers();
    }

    @Test
    public void test() throws Exception {
        // Step 1: Create profiles
        System.out.println("=== Step 1: Creating profiles ===");
        createUsers();
        List<NewUserRecord> users = userService.getUsers();
        List<String> profileIds = new ArrayList<>();
        Map<String, String> profileIdToToken = new HashMap<>();

        for (NewUserRecord user : users) {
            String authHeader = keycloakTestHelper.createAuthorizationHeader(user.username(), user.password());
            String token = authHeader.replace("Bearer ", "");
            Integer age = new Random().nextInt(19, 40);
            String[] genders = {"male", "female"};
            String gender = genders[new Random().nextInt(2)];
            String profile = String.format("""
                    {
                        "name": "%s",
                        "age": "%d",
                        "gender": "%s",
                        "bio": "this is my life",
                        "city": "Amstetten",
                        "preferences": {
                            "minAge": 19,
                            "maxAge": 40,
                            "gender": "female",
                            "maxRange": 4
                        }
                    }""", user.firstName(), age, gender);
            try {
                String response = mockMvc.perform(post("/api/v1/profiles")
                                .content(profile)
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

                Map<String, Object> profileData = objectMapper.readValue(response, new TypeReference<>() {});
                String profileId = (String) profileData.get("data");
                if (profileId == null) {
                    log.error("Profile creation returned null id for user: {}", user.username());
                    continue;
                }
                profileIds.add(profileId);
                profileIdToToken.put(profileId, token);
                System.out.println("Created profile: " + user.firstName() + " (ID: " + profileId + ")");
            } catch (Exception e) {
                log.error("Failed to create profile for user: {}", user.username(), e);
                throw new RuntimeException("Failed to create profile for user: " + user.username(), e);
            }
        }

        // Step 2: Create random swipes between users
        System.out.println("\n=== Step 2: Creating random swipes ===");
        WebClient swipesClient = WebClient.builder().baseUrl(swipesBaseUrl).build();
        Random random = new Random();
        int swipesCount = 0;

        for (int i = 0; i < profileIds.size(); i++) {
            // Each user swipes on 3-7 random other users
            int numSwipes = random.nextInt(3, 8);
            Set<Integer> swipedIndices = new HashSet<>();

            for (int j = 0; j < numSwipes; j++) {

                int targetIndex = random.nextInt(profileIds.size());
                if (targetIndex == i || swipedIndices.contains(targetIndex)) continue;
                swipedIndices.add(targetIndex);

                String profile1Id = profileIds.get(i);
                String token = profileIdToToken.get(profile1Id);
                if (token == null) {
                    log.error("No token found for profile: {}", profile1Id);
                    continue;
                }

                String profile2Id = profileIds.get(targetIndex);
                boolean decision = random.nextBoolean();

                Map<String, Object> swipeData = Map.of(
                        "profile1Id", profile1Id,
                        "profile2Id", profile2Id,
                        "decision", decision
                );

                try {
                    // Send swipe request to swipes service with Authorization header
                    swipesClient.post()
                            .uri("/swipe")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(swipeData)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

                    swipesCount++;
                    System.out.println("Swipe #" + swipesCount + ": " + profile1Id.substring(0, 8) + " -> " + profile2Id.substring(0, 8) + " (" + (decision ? "like" : "dislike") + ")");
                } catch (Exception e) {
                    log.error("Failed to create swipe for {}->{}", profile1Id, profile2Id, e);
                }
            }
        }
        System.out.println("Total swipes created: " + swipesCount);

        // Step 3: Trigger deck rebuild (attempt remote Deck service; if not available, fall back to Profiles /deck)
        System.out.println("\n=== Step 3: Triggering deck rebuild ===");
        WebClient deckClient = WebClient.builder().baseUrl(deckBaseUrl).build();

        for (String profileId : profileIds) {
            try {
                // Trigger remote rebuild
                deckClient.post()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/admin/deck/rebuild").queryParam("viewerId", profileId).build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                // Poll deck size for up to 10 seconds
                long deadline = System.currentTimeMillis() + 10_000;
                long deckSize = 0L;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        Long size = deckClient.get()
                                .uri(uriBuilder -> uriBuilder.path("/api/v1/admin/deck/size").queryParam("viewerId", profileId).build())
                                .retrieve()
                                .bodyToMono(Long.class)
                                .onErrorReturn(0L)
                                .block();
                        if (size != null && size > 0) {
                            deckSize = size;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                    Thread.sleep(500);
                }
                System.out.println("Deck size for user " + profileId.substring(0, 8) + ": " + deckSize);
            } catch (WebClientResponseException.NotFound nf) {
                log.info("Deck rebuild endpoint not found (404) for profile {} — skipping remote rebuild", profileId);
            } catch (Exception e) {
                log.error("Failed to trigger rebuild or poll size for profile {}", profileId, e);
            }
        }

        // Fallback: attempt global manual rebuild once (best-effort)
        try {
            deckClient.get().uri("/api/v1/admin/deck/manual-rebuild").retrieve().bodyToMono(String.class).block();
        } catch (Exception ignored) {
        }

        // Additional fallback: call Profiles service /deck endpoint (builds on-the-fly) and print sizes
        System.out.println("\n=== Step 4: Checking deck sizes via Profiles /deck (fallback) ===");
        for (int i = 0; i < Math.min(5, profileIds.size()); i++) {
            String profileId = profileIds.get(i);
            String token = profileIdToToken.get(profileId);
            try {
                String deckResp = mockMvc.perform(get("/api/v1/profiles/deck").param("viewerId", profileId).param("limit", "100").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

                List<GetProfileDto> deckList = objectMapper.readValue(deckResp, new TypeReference<>() {});
                System.out.println("Profiles /deck size for user " + profileId.substring(0, 8) + ": " + deckList.size());
            } catch (Exception ex) {
                log.error("Failed to fetch on-the-fly deck for {}", profileId, ex);
            }
        }

        System.out.println("\n=== Integration test completed ===");
    }

    private String extractSubFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            return (String) claims.get("sub");
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract sub from token", e);
        }
    }
}
