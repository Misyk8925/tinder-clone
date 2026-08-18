package com.tinder.clone.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Given Maven generate-test-resources ran certs/generate-test-certs.sh,
 * When the test classpath is loaded,
 * Then consumer-service.p12 and truststore.jks are present so the internal connector can enable mTLS.
 */
class GeneratedTestMtlsCertsTest {

    @Test
    void generatedKeystoresAreOnTheTestClasspathWithExpectedIdentities() throws Exception {
        assertCn("consumer-service.p12", "consumer-service");
        assertThat(new ClassPathResource("truststore.jks").exists()).isTrue();
    }

    private static void assertCn(String resource, String cn) throws Exception {
        ClassPathResource file = new ClassPathResource(resource);
        assertThat(file.exists()).as(resource).isTrue();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var in = file.getInputStream()) {
            ks.load(in, "changeit".toCharArray());
        }
        X509Certificate cert = (X509Certificate) ks.getCertificate(ks.aliases().nextElement());
        assertThat(cert.getSubjectX500Principal().getName()).contains("CN=" + cn);
    }
}
