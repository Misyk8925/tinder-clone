package com.tinder.profiles.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Configures a second Tomcat HTTPS connector on the internal port.
 * mTLS is enforced: client certificate (deck-service.p12) is required.
 * Only /internal/** routes are intended for this port.
 */
@Slf4j
@Configuration
public class InternalMtlsServerConfig {

    @Value("${internal.server.port:8011}")
    private int internalPort;

    @Value("${internal.server.ssl.enabled:true}")
    private boolean sslEnabled;

    @Value("${internal.server.ssl.key-store:classpath:profiles-service.p12}")
    private String keyStore;

    @Value("${internal.server.ssl.key-store-password:changeit}")
    private String keyStorePassword;

    @Value("${internal.server.ssl.key-store-type:PKCS12}")
    private String keyStoreType;

    @Value("${internal.server.ssl.trust-store:classpath:truststore.jks}")
    private String trustStore;

    @Value("${internal.server.ssl.trust-store-password:changeit}")
    private String trustStorePassword;

    @Value("${internal.server.ssl.trust-store-type:JKS}")
    private String trustStoreType;

    // "need" = require client cert; "want" = optional; "none" = disabled
    @Value("${internal.server.ssl.client-auth:need}")
    private String clientAuth;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> internalMtlsConnector() {
        return factory -> {
            if (!sslEnabled) {
                log.warn("Internal mTLS connector is DISABLED — /internal endpoints are unprotected");
                return;
            }
            try {
                factory.addAdditionalTomcatConnectors(buildMtlsConnector());
                log.info("Internal mTLS connector started on port {}", internalPort);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure internal mTLS connector", e);
            }
        };
    }

    private Connector buildMtlsConnector() throws Exception {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("https");
        connector.setSecure(true);
        connector.setPort(internalPort);

        // Build SSLHostConfig (Spring Boot 3 / Tomcat 10+ API)
        SSLHostConfig sslHostConfig = new SSLHostConfig();

        // Client auth: "required" maps to clientAuth=need
        sslHostConfig.setCertificateVerification(mapClientAuth(clientAuth));

        // Truststore — used to validate client certificate
        sslHostConfig.setTruststoreFile(resolveToFilePath(trustStore, "ts"));
        sslHostConfig.setTruststorePassword(trustStorePassword);
        sslHostConfig.setTruststoreType(trustStoreType);

        // Certificate (server identity)
        SSLHostConfigCertificate cert = new SSLHostConfigCertificate(
                sslHostConfig, SSLHostConfigCertificate.Type.RSA);
        cert.setCertificateKeystoreFile(resolveToFilePath(keyStore, "ks"));
        cert.setCertificateKeystorePassword(keyStorePassword);
        cert.setCertificateKeystoreType(keyStoreType);

        sslHostConfig.addCertificate(cert);
        connector.addSslHostConfig(sslHostConfig);
        connector.setProperty("SSLEnabled", "true");

        return connector;
    }

    /** Maps Spring-style clientAuth values to Tomcat certificateVerification values */
    private String mapClientAuth(String auth) {
        return switch (auth.toLowerCase()) {
            case "need", "required" -> "required";
            case "want", "optional" -> "optional";
            default -> "none";
        };
    }

    /**
     * Resolves a Spring resource path (classpath:, file:, or plain path) to an
     * absolute file path that Tomcat can read directly.
     * Classpath resources inside a JAR are extracted to a temp file.
     */
    private String resolveToFilePath(String resourcePath, String tempPrefix) throws Exception {
        ResourceLoader loader = new DefaultResourceLoader();
        Resource resource = loader.getResource(resourcePath);
        if (resource.isFile()) {
            return resource.getFile().getAbsolutePath();
        }
        // Extract from JAR/classpath to a temp file
        File tmp = File.createTempFile("mtls-" + tempPrefix + "-", ".tmp");
        tmp.deleteOnExit();
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp.getAbsolutePath();
    }
}
