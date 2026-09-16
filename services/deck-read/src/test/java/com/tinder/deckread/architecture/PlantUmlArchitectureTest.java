package com.tinder.deckread.architecture;

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

@DisplayName("Deck Read HTTP PlantUML")
class PlantUmlArchitectureTest {

    private static final Path REPOSITORY = Path.of("").toAbsolutePath().normalize().getParent().getParent();

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.tinder.deckread");

    @Test
    @DisplayName("Given resource and service packages, when bytecode is imported, then HTTP calls query and not the reverse")
    void resourceMayCallService() throws Exception {
        Path diagram = REPOSITORY.resolve("docs/architecture/deck-read-modules.puml");
        assertThat(diagram).exists();
        classes()
                .should(adhereToPlantUmlDiagram(
                        diagram.toUri().toURL(),
                        consideringOnlyDependenciesInAnyPackage(
                                "com.tinder.deckread.resource..",
                                "com.tinder.deckread.service..")))
                .because("docs/architecture/deck-read-modules.puml")
                .check(classes);
    }
}
