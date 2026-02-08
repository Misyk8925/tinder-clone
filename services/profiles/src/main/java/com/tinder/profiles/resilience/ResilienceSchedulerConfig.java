package com.tinder.profiles.resilience;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class ResilienceSchedulerConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService resilienceRetryScheduler() {
        return Executors.newScheduledThreadPool(
                2,
                r -> {
                    Thread t = new Thread(r);
                    t.setName("resilience-retry-");
                    t.setDaemon(true);
                    return t;
                }
        );
    }
}