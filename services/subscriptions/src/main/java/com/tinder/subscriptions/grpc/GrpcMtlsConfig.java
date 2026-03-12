package com.tinder.subscriptions.grpc;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * Configures mTLS for the gRPC channel to profiles-service.
 * subscriptions-service acts as the mTLS client:
 *   - presents subscriptions-service.p12 as its identity
 *   - validates the server certificate via truststore.jks
 */
@Slf4j
@Configuration
public class GrpcMtlsConfig {

    // --- Client keystore (subscriptions-service identity) ---
    @Value("${grpc.client.mtls.key-store:classpath:subscriptions-service.p12}")
    private String keyStorePath;

    @Value("${grpc.client.mtls.key-store-password:changeit}")
    private String keyStorePassword;

    @Value("${grpc.client.mtls.key-store-type:PKCS12}")
    private String keyStoreType;

    // --- Truststore (validates server TLS certificate) ---
    @Value("${grpc.client.mtls.trust-store:classpath:truststore.jks}")
    private String trustStorePath;

    @Value("${grpc.client.mtls.trust-store-password:changeit}")
    private String trustStorePassword;

    @Value("${grpc.client.mtls.trust-store-type:JKS}")
    private String trustStoreType;

    // --- TLS authority override (must match CN / SAN in profiles-service cert) ---
    @Value("${grpc.client.mtls.override-authority:profiles-service}")
    private String overrideAuthority;

    @Value("${grpc.client.mtls.enabled:true}")
    private boolean mtlsEnabled;

    /**
     * GrpcChannelConfigurer is picked up by net.devh grpc-client-spring-boot-starter
     * and applied to every managed channel before it is used.
     *
     * <p>Mirrors the mTLS approach used by deck-service (HttpClientConfig): load KMF/TMF
     * from PKCS12/JKS keystores, build an SSL context, apply it to the transport layer.
     * gRPC uses shaded Netty ({@code io.grpc.netty.shaded.*}); deck's HTTP client uses
     * standard Netty ({@code io.netty.*}) — the pattern is identical, only the classes differ.
     */
    @Bean
    public GrpcChannelConfigurer grpcMtlsChannelConfigurer() {
        if (!mtlsEnabled) {
            log.warn("gRPC mTLS is DISABLED — connecting to profiles-service without client certificate");
            return (channelBuilder, name) -> {};
        }

        SslContext sslContext;
        try {
            sslContext = buildSslContext();
            log.info("gRPC mTLS SslContext built — subscriptions-service will present its cert to profiles-service");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build mTLS SslContext for gRPC client", e);
        }

        return (channelBuilder, name) -> {
            // Apply mTLS only to the profiles-service channel
            if (!"profiles-service".equals(name)) {
                return;
            }
            // Pattern-matching instanceof (Java 16+) — safer than a blind cast;
            // throws a clear error if net.devh ever changes the builder implementation
            if (!(channelBuilder instanceof io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder nettyBuilder)) {
                throw new IllegalStateException(
                        "Cannot apply mTLS to channel '" + name + "': expected NettyChannelBuilder but got "
                                + channelBuilder.getClass().getName());
            }
            // Enforce TLS negotiation and attach the custom mTLS SSL context
            nettyBuilder.useTransportSecurity();
            nettyBuilder.sslContext(sslContext);
            // Must match CN / SAN in profiles-service.p12 — required when connecting by IP / localhost
            nettyBuilder.overrideAuthority(overrideAuthority);
            log.debug("mTLS SslContext applied to gRPC channel '{}', authorityOverride='{}'",
                    name, overrideAuthority);
        };
    }

    private SslContext buildSslContext() throws Exception {
        // Load keystore — subscriptions-service presents this cert to profiles-service
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        try (InputStream ks = ResourceUtils.getURL(keyStorePath).openStream()) {
            keyStore.load(ks, keyStorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword.toCharArray());

        // Load truststore — used to verify profiles-service server certificate
        KeyStore trustStore = KeyStore.getInstance(trustStoreType);
        try (InputStream ts = ResourceUtils.getURL(trustStorePath).openStream()) {
            trustStore.load(ts, trustStorePassword.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        return GrpcSslContexts.configure(
                SslContextBuilder.forClient()
                        .keyManager(kmf)
                        .trustManager(tmf)
        ).build();
    }
}

