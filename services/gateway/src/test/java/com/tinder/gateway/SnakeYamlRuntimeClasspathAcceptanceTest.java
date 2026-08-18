package com.tinder.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot loads {@code application.yml} only when SnakeYAML is on the runtime classpath.
 * A test-scoped {@code snakeyaml} dependency overrides Boot's transitive runtime jar and the
 * Docker image then dies with "snakeyaml was not found on the classpath".
 */
@Tag("acceptance")
@DisplayName("Feature: Gateway runtime image can load application.yml")
class SnakeYamlRuntimeClasspathAcceptanceTest {

    private static final Pattern SNAKEYAML_DEPENDENCY = Pattern.compile(
            "<dependency>\\s*<groupId>org\\.yaml</groupId>\\s*<artifactId>snakeyaml</artifactId>(.*?)</dependency>",
            Pattern.DOTALL);

    @Test
    @DisplayName("Scenario: Given application.yml in the fat JAR, when Maven resolves snakeyaml, then it is not test-scoped")
    void snakeyamlIsARuntimeDependency() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        Matcher dependency = SNAKEYAML_DEPENDENCY.matcher(pom);
        assertThat(dependency.find())
                .as("snakeyaml must be a direct runtime dependency so the Docker image can load application.yml")
                .isTrue();
        assertThat(dependency.group(1))
                .as("test-scoped snakeyaml hides Spring Boot's YAML parser from the packaged application")
                .doesNotContain("<scope>test</scope>");
    }
}
