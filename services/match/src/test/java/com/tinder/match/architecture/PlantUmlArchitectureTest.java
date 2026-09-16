package com.tinder.match.architecture;

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

@DisplayName("Match modules PlantUML")
class PlantUmlArchitectureTest {

    private static final Path REPOSITORY = Path.of("").toAbsolutePath().normalize().getParent().getParent();

    @Test
    @DisplayName("Given match, conversation and moderation packages, when bytecode is imported, then chat may call moderation and match stays separate from chat")
    void matchConversationAndModerationFollowCommittedDiagram() throws Exception {
        Path diagram = REPOSITORY.resolve("docs/architecture/match-modules.puml");
        assertThat(diagram).exists();
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(
                        "com.tinder.match.match",
                        "com.tinder.match.conversation",
                        "com.tinder.match.moderation");
        classes()
                .should(adhereToPlantUmlDiagram(
                        diagram.toUri().toURL(),
                        consideringOnlyDependenciesInAnyPackage(
                                "com.tinder.match.match..",
                                "com.tinder.match.conversation..",
                                "com.tinder.match.moderation..")))
                .because("docs/architecture/match-modules.puml: conversation → moderation; no match ↔ conversation")
                .check(imported);
    }
}
