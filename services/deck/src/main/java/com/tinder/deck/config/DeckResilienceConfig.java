package com.tinder.deck.config;

import com.tinder.deck.resilience.DeckResilience;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeckResilienceProperties.class)
public class DeckResilienceConfig {

    @Bean
    public DeckResilience deckResilience(DeckResilienceProperties properties) {
        return DeckResilience.from(properties);
    }
}

