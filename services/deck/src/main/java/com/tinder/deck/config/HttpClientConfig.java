package com.tinder.deck.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.ResourceUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.time.Duration;

@Slf4j
@Configuration
public class HttpClientConfig {

    // --- mTLS keystore (deck identity sent to servers) ---
    @Value("${mtls.client.key-store:classpath:deck-service.p12}")
    private String keyStorePath;

    @Value("${mtls.client.key-store-password:changeit}")
    private String keyStorePassword;

    @Value("${mtls.client.key-store-type:PKCS12}")
    private String keyStoreType;

    // --- truststore (used to verify server certificates) ---
    @Value("${mtls.client.trust-store:classpath:truststore.jks}")
    private String trustStorePath;

    @Value("${mtls.client.trust-store-password:changeit}")
    private String trustStorePassword;

    @Value("${mtls.client.trust-store-type:JKS}")
    private String trustStoreType;

    @Value("${mtls.enabled:true}")
    private boolean mtlsEnabled;

    /**
     * Build a Reactor Netty HttpClient with mTLS support.
     * When mtls.enabled=false (e.g. tests) plain HTTP is used.
     */
    private HttpClient buildHttpClient(int connectTimeout, int responseTimeout) {
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(responseTimeout))
                .compress(true);

        if (!mtlsEnabled) {
            log.warn("mTLS is DISABLED — using plain HTTP for internal service calls");
            return client;
        }

        try {
            SslContext sslContext = buildSslContext();
            return client.secure(sslSpec -> sslSpec.sslContext(sslContext));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure mTLS SSL context", e);
        }
    }

    private SslContext buildSslContext() throws Exception {
        // Load keystore (deck-service identity)
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        try (InputStream ks = ResourceUtils.getURL(keyStorePath).openStream()) {
            keyStore.load(ks, keyStorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword.toCharArray());

        // Load truststore (trusted server certs)
        KeyStore trustStore = KeyStore.getInstance(trustStoreType);
        try (InputStream ts = ResourceUtils.getURL(trustStorePath).openStream()) {
            trustStore.load(ts, trustStorePassword.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        return SslContextBuilder.forClient()
                .keyManager(kmf)
                .trustManager(tmf)
                .build();
    }

    @Bean
    WebClient profilesWebClient(
            @Value("${profiles.base-url}") String profilesUrl,
            @Value("${deck.profiles-connect-timeout-ms:2000}") int connectTimeout,
            @Value("${deck.profiles-response-timeout-ms:5000}") int responseTimeout) {

        return WebClient.builder()
                .baseUrl(profilesUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient(connectTimeout, responseTimeout)))
                .build();
    }

    @Bean
    WebClient swipesWebClient(
            @Value("${swipes.base-url}") String swipesUrl,
            @Value("${deck.swipes-connect-timeout-ms:2000}") int connectTimeout,
            @Value("${deck.swipes-response-timeout-ms:5000}") int responseTimeout) {

        return WebClient.builder()
                .baseUrl(swipesUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient(connectTimeout, responseTimeout)))
                .build();
    }
}
