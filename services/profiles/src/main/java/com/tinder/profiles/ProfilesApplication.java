package com.tinder.profiles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = "com.tinder.profiles")
@EnableCaching
@EnableJpaAuditing
public class ProfilesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfilesApplication.class, args);
    }

}
