package com.tinder.profiles.config.domain;

import com.tinder.profiles.domain.profile.ProfileDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers framework-free domain services as Spring beans. The domain layer
 * itself carries no Spring annotations, so wiring happens here.
 */
@Configuration
public class DomainConfig {

    @Bean
    public ProfileDomainService profileDomainService() {
        return new ProfileDomainService();
    }
}
