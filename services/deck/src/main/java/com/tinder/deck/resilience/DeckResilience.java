package com.tinder.deck.resilience;

import com.tinder.deck.config.DeckResilienceProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.reactivestreams.Publisher;

public final class DeckResilience {

    private final ResiliencePolicy profiles;
    private final ResiliencePolicy swipes;
    private final ResiliencePolicy redis;

    private DeckResilience(ResiliencePolicy profiles, ResiliencePolicy swipes, ResiliencePolicy redis) {
        this.profiles = profiles;
        this.swipes = swipes;
        this.redis = redis;
    }

    public static DeckResilience from(DeckResilienceProperties props) {
        Objects.requireNonNull(props, "props");

        ResiliencePolicy profiles = ResiliencePolicy.forHttp(
                "profiles-http",
                props.getProfiles()
        );
        ResiliencePolicy swipes = ResiliencePolicy.forHttp(
                "swipes-http",
                props.getSwipes()
        );
        ResiliencePolicy redis = ResiliencePolicy.forRedis(
                "redis",
                props.getRedis()
        );

        return new DeckResilience(profiles, swipes, redis);
    }

    public <T> Mono<T> protectProfiles(Mono<T> mono) {
        return profiles.protect(mono);
    }

    public <T> Flux<T> protectProfiles(Flux<T> flux) {
        return profiles.protect(flux);
    }

    public <T> Mono<T> protectSwipes(Mono<T> mono) {
        return swipes.protect(mono);
    }

    public <T> Flux<T> protectSwipes(Flux<T> flux) {
        return swipes.protect(flux);
    }

    public <T> Mono<T> protectRedis(Mono<T> mono) {
        return redis.protect(mono);
    }

    public <T> Flux<T> protectRedis(Flux<T> flux) {
        return redis.protect(flux);
    }

    private static final class ResiliencePolicy {
        private final Duration timeout;
        private final CircuitBreaker circuitBreaker;
        private final Retry retry;
        private final Bulkhead bulkhead;

        private ResiliencePolicy(Duration timeout, CircuitBreaker circuitBreaker, Retry retry, Bulkhead bulkhead) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
            this.retry = Objects.requireNonNull(retry, "retry");
            this.bulkhead = Objects.requireNonNull(bulkhead, "bulkhead");
        }

        static ResiliencePolicy forHttp(String name, DeckResilienceProperties.Client props) {
            return create(name, props, DeckResilience::isHttpFailure, DeckResilience::isHttpRetryable);
        }

        static ResiliencePolicy forRedis(String name, DeckResilienceProperties.Client props) {
            return create(name, props, DeckResilience::isRedisFailure, DeckResilience::isRedisRetryable);
        }

        private static ResiliencePolicy create(
                String name,
                DeckResilienceProperties.Client props,
                Predicate<Throwable> recordFailure,
                Predicate<Throwable> retryOn) {
            Objects.requireNonNull(props, "props");

            CircuitBreakerConfig cbConfig = buildCircuitBreakerConfig(props.getCircuitBreaker(), recordFailure);
            CircuitBreaker circuitBreaker = CircuitBreaker.of(name, cbConfig);

            RetryConfig retryConfig = buildRetryConfig(props.getRetry(), retryOn);
            Retry retry = Retry.of(name, retryConfig);

            Bulkhead bulkhead = Bulkhead.of(name, buildBulkheadConfig(props.getBulkhead()));

            return new ResiliencePolicy(props.getTimeout(), circuitBreaker, retry, bulkhead);
        }

        <T> Mono<T> protect(Mono<T> mono) {
            return applyResilience(mono);
        }

        <T> Flux<T> protect(Flux<T> flux) {
            return applyResilience(flux);
        }

        @SuppressWarnings("unchecked")
        private <T, P extends Publisher<T>> P applyResilience(P publisher) {
            if (publisher instanceof Mono) {
                Mono<T> mono = (Mono<T>) publisher;
                return (P) mono.timeout(timeout)
                        .transformDeferred(RetryOperator.of(retry))
                        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                        .transformDeferred(BulkheadOperator.of(bulkhead));
            } else if (publisher instanceof Flux) {
                Flux<T> flux = (Flux<T>) publisher;
                return (P) flux.timeout(timeout)
                        .transformDeferred(RetryOperator.of(retry))
                        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                        .transformDeferred(BulkheadOperator.of(bulkhead));
            }
            throw new IllegalArgumentException("Unsupported publisher type: " + publisher.getClass());
        }

        private static CircuitBreakerConfig buildCircuitBreakerConfig(
                DeckResilienceProperties.CircuitBreaker props,
                Predicate<Throwable> recordFailure) {
            return CircuitBreakerConfig.custom()
                    .slidingWindowType(SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(props.getSlidingWindowSize())
                    .minimumNumberOfCalls(props.getMinimumNumberOfCalls())
                    .permittedNumberOfCallsInHalfOpenState(props.getPermittedNumberOfCallsInHalfOpenState())
                    .waitDurationInOpenState(props.getWaitDurationInOpenState())
                    .failureRateThreshold(props.getFailureRateThreshold())
                    .slowCallRateThreshold(props.getSlowCallRateThreshold())
                    .slowCallDurationThreshold(props.getSlowCallDurationThreshold())
                    .recordException(recordFailure::test)
                    .ignoreException(ex -> ex instanceof CallNotPermittedException)
                    .build();
        }

        private static RetryConfig buildRetryConfig(
                DeckResilienceProperties.Retry props,
                Predicate<Throwable> retryOn) {
            IntervalFunction intervalFunction = IntervalFunction.ofExponentialRandomBackoff(
                    props.getInitialInterval(),
                    props.getMultiplier(),
                    props.getJitter()
            );
            return RetryConfig.custom()
                    .maxAttempts(props.getMaxAttempts())
                    .intervalFunction(intervalFunction)
                    .retryOnException(retryOn::test)
                    .failAfterMaxAttempts(true)
                    .build();
        }

        private static BulkheadConfig buildBulkheadConfig(DeckResilienceProperties.Bulkhead props) {
            return BulkheadConfig.custom()
                    .maxConcurrentCalls(props.getMaxConcurrentCalls())
                    .maxWaitDuration(props.getMaxWaitDuration())
                    .build();
        }
    }

    private static boolean isCallNotPermitted(Throwable ex) {
        return ex instanceof CallNotPermittedException;
    }

    private static boolean isWebClientRequestException(Throwable ex) {
        return ex instanceof WebClientRequestException;
    }

    private static boolean isTimeoutException(Throwable ex) {
        return ex instanceof TimeoutException;
    }

    private static boolean isServerErrorOrTooManyRequests(Throwable ex) {
        if (ex instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError() || wcre.getStatusCode().value() == 429;
        }
        return false;
    }

    private static boolean isHttpFailure(Throwable ex) {
        if (isCallNotPermitted(ex)) {
            return false;
        }
        return isWebClientRequestException(ex)
               || isTimeoutException(ex)
               || isServerErrorOrTooManyRequests(ex)
               || ex instanceof RuntimeException;
    }

    private static boolean isHttpRetryable(Throwable ex) {
        if (isCallNotPermitted(ex)) {
            return false;
        }
        return isWebClientRequestException(ex)
               || isTimeoutException(ex)
               || isServerErrorOrTooManyRequests(ex);
    }

    private static boolean isRedisFailure(Throwable ex) {
        if (isCallNotPermitted(ex)) {
            return false;
        }
        return ex instanceof RedisConnectionFailureException
                || ex instanceof RedisSystemException
                || isTimeoutException(ex)
                || ex instanceof RuntimeException;
    }

    private static boolean isRedisRetryable(Throwable ex) {
        if (isCallNotPermitted(ex)) {
            return false;
        }
        return ex instanceof RedisConnectionFailureException || isTimeoutException(ex);
    }
}
