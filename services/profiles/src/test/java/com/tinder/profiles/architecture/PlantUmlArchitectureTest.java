package com.tinder.profiles.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.plantuml.rules.PlantUmlArchCondition.Configuration.consideringOnlyDependenciesInAnyPackage;
import static com.tngtech.archunit.library.plantuml.rules.PlantUmlArchCondition.adhereToPlantUmlDiagram;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * UML is the law for package arrows. This test reads
 * {@code docs/architecture/profiles-layers.puml} and fails if bytecode
 * grows a dependency the diagram does not allow.
 *
 * <p>Matching-path.puml is not an input here (no shared classpath across services).
 */
@DisplayName("Profiles layers PlantUML")
class PlantUmlArchitectureTest {

    private static final Path SERVICE = Path.of("").toAbsolutePath().normalize();
    private static final Path REPOSITORY = SERVICE.getParent().getParent();

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.tinder.profiles");

    @Test
    @DisplayName("Given the committed layer diagram, when Profiles bytecode is imported, then dependencies follow the arrows")
    void bytecodeFollowsCommittedLayerDiagram() {
        checkDiagram(REPOSITORY.resolve("docs/architecture/profiles-layers.puml"), "com.tinder.profiles..");
    }

    @Test
    @DisplayName("Given the committed feature diagram, when photos and profile modules are imported, then photos may use profile but not the reverse")
    void featureModulesFollowCommittedDiagram() {
        checkDiagram(
                REPOSITORY.resolve("docs/architecture/profiles-features.puml"),
                "com.tinder.profiles.application.profile..",
                "com.tinder.profiles.application.photos..");
    }

    private void checkDiagram(Path diagram, String... packages) {
        assertThat(diagram).exists();
        try {
            classes()
                    .should(adhereToPlantUmlDiagram(
                            diagram.toUri().toURL(),
                            consideringOnlyDependenciesInAnyPackage(packages)))
                    .because(diagram.getFileName() + " is the allowed-arrow catalog")
                    .check(classes);
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException(diagram.toString(), e);
        }
    }
}
