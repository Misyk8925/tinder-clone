package com.tinder.profiles.config.mtls;

import com.tinder.profiles.config.props.InternalServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
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
@RequiredArgsConstructor
public class InternalMtlsServerConfig {

    private final InternalServerProperties properties;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> internalMtlsConnector() {
        return factory -> {
            if (!properties.ssl().enabled()) {
                log.warn("Internal mTLS connector is DISABLED — /internal endpoints are unprotected");
                return;
            }
            try {
                factory.addAdditionalTomcatConnectors(buildMtlsConnector());
                log.info("Internal mTLS connector started on port {}", properties.port());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure internal mTLS connector", e);
            }
        };
    }

    private Connector buildMtlsConnector() throws Exception {
        InternalServerProperties.Ssl ssl = properties.ssl();

        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("https");
        connector.setSecure(true);
        connector.setPort(properties.port());

        // Build SSLHostConfig (Spring Boot 3 / Tomcat 10+ API)
        SSLHostConfig sslHostConfig = new SSLHostConfig();

        // Client auth: "required" maps to clientAuth=need
        sslHostConfig.setCertificateVerification(mapClientAuth(ssl.clientAuth()));

        // Truststore — used to validate client certificate
        sslHostConfig.setTruststoreFile(resolveToFilePath(ssl.trustStore(), "ts"));
        sslHostConfig.setTruststorePassword(ssl.trustStorePassword());
        sslHostConfig.setTruststoreType(ssl.trustStoreType());

        // Certificate (server identity)
        SSLHostConfigCertificate cert = new SSLHostConfigCertificate(
                sslHostConfig, SSLHostConfigCertificate.Type.RSA);
        cert.setCertificateKeystoreFile(resolveToFilePath(ssl.keyStore(), "ks"));
        cert.setCertificateKeystorePassword(ssl.keyStorePassword());
        cert.setCertificateKeystoreType(ssl.keyStoreType());

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
