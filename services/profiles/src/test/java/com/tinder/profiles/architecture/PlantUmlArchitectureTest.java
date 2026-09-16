package com.tinder.profiles.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.plantuml.rules.PlantUmlArchCondition.Configuration.consideringOnlyDependenciesInAnyPackage;
import static com.tngtech.archunit.library.plantuml.rules.PlantUmlArchCondition.adhereToPlantUmlDiagram;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * UML is the law for package arrows. This test reads the committed {@code .puml}
 * and fails if bytecode grows a dependency the diagram does not allow.
 *
 * <p>Only diagram packages are imported. ArchUnit still requires every imported
 * class that touches those packages to sit on a component; kafka/security/moderation
 * cycles stay out by not being imported.
 *
 * <p>{@code matching-path.puml} is not an input here (no shared classpath across services).
 */
@DisplayName("Profiles PlantUML")
class PlantUmlArchitectureTest {

    private static final Path REPOSITORY = Path.of("").toAbsolutePath().normalize().getParent().getParent();

    @Test
    @DisplayName("Given the committed layer diagram, when Profiles bytecode is imported, then dependencies follow the arrows")
    void bytecodeFollowsCommittedLayerDiagram() {
        checkDiagram(
                REPOSITORY.resolve("docs/architecture/profiles-layers.puml"),
                "com.tinder.profiles.domain",
                "com.tinder.profiles.application",
                "com.tinder.profiles.api",
                "com.tinder.profiles.infrastructure",
                "com.tinder.profiles.config");
    }

    @Test
    @DisplayName("Given the committed feature diagram, when photos and profile modules are imported, then photos may use profile but not the reverse")
    void featureModulesFollowCommittedDiagram() {
        checkDiagram(
                REPOSITORY.resolve("docs/architecture/profiles-features.puml"),
                "com.tinder.profiles.application.profile",
                "com.tinder.profiles.application.photos");
    }

    private void checkDiagram(Path diagram, String... packages) {
        assertThat(diagram).exists();
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(packages);
        String[] identifiers = Arrays.stream(packages).map(pkg -> pkg + "..").toArray(String[]::new);
        try {
            classes()
                    .should(adhereToPlantUmlDiagram(
                            diagram.toUri().toURL(),
                            consideringOnlyDependenciesInAnyPackage(
                                    identifiers[0],
                                    Arrays.copyOfRange(identifiers, 1, identifiers.length))))
                    .because(diagram.getFileName() + " is the allowed-arrow catalog")
                    .check(imported);
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException(diagram.toString(), e);
        }
    }
}
