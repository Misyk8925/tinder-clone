package com.tinder.deck.config;

import com.tinder.deck.service.popularity.PopularityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ScoringProperties.class, PopularityProperties.class})
public class ScoringConfig {
}
