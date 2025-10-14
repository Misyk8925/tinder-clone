package com.tinder.deck.adapters;

import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfilesHttp {
    private final WebClient profilesWebClient;

     public Flux<SharedProfileDto> searchProfiles(UUID viewerId, SharedPreferencesDto preferences, int limit) {
         return profilesWebClient.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("viewerId", viewerId)
                        .queryParam("gender", preferences.gender())
                        .queryParam("minAge", preferences.minAge())
                        .queryParam("maxAge", preferences.maxAge())
                        .queryParam("limit", limit)
                        .build()).retrieve().bodyToFlux(SharedProfileDto.class) ;
     }

     public Flux<SharedProfileDto> fetchPage(int page, int limit) {

         return profilesWebClient.get()
                 .uri(uri -> uri.path("/page")
                        .queryParam("page", page)
                        .queryParam("limit", limit)
                        .build())
                 .retrieve().bodyToFlux(SharedProfileDto.class) ;
     }
}
