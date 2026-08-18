package com.tinder.profiles.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable statement of the layering. Four layers, one direction of travel:
 *
 * <pre>
 *   api (REST, gRPC, schedulers) ──▶ application ──▶ domain
 *   infrastructure (adapters) ──────▶ application ──▶ domain
 *   config ──▶ everything (wiring only)
 * </pre>
 *
 * <p>Inbound adapters speak the application's vocabulary (commands, query views,
 * application exceptions) and never the domain model; outbound adapters
 * implement the application's ports.
 */
@DisplayName("Clean architecture")
class CleanArchitectureTest {

    private static final String DOMAIN = "com.tinder.profiles.domain..";
    private static final String APPLICATION = "com.tinder.profiles.application..";
    private static final String API = "com.tinder.profiles.api..";
    private static final String INFRASTRUCTURE = "com.tinder.profiles.infrastructure..";
    private static final String CONFIG = "com.tinder.profiles.config..";

    private static final DescribedPredicate<JavaAnnotation<?>> FRAMEWORK_ANNOTATION =
            DescribedPredicate.describe("a framework annotation", annotation -> {
                String owner = annotation.getRawType().getPackageName();
                return owner.startsWith("org.springframework")
                        || owner.startsWith("jakarta")
                        || owner.startsWith("lombok");
            });

    private static final DescribedPredicate<JavaClass> AN_APPLICATION_PORT =
            DescribedPredicate.describe("an application port", type ->
                    type.getPackageName().startsWith("com.tinder.profiles.application")
                            && (type.getSimpleName().endsWith("Port")
                                || type.getSimpleName().endsWith("Query")));

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.tinder.profiles");

    @Nested
    @DisplayName("the domain layer")
    class DomainLayer {

        @Test
        @DisplayName("depends on nothing but itself and the JDK")
        void dependsOnlyOnItselfAndTheJdk() {
            noClasses()
                    .that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat()
                    .resideOutsideOfPackages(DOMAIN, "java..")
                    .check(classes);
        }

        @Test
        @DisplayName("carries no framework annotations")
        void carriesNoFrameworkAnnotations() {
            noClasses()
                    .that().resideInAPackage(DOMAIN)
                    .should().beAnnotatedWith(FRAMEWORK_ANNOTATION)
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("the application layer")
    class ApplicationLayer {

        @Test
        @DisplayName("does not depend on adapters, wiring or transport frameworks")
        void doesNotDependOnOuterLayers() {
            noClasses()
                    .that().resideInAPackage(APPLICATION)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            API,
                            INFRASTRUCTURE,
                            CONFIG,
                            "org.springframework.data..",
                            "org.springframework.http..",
                            "org.springframework.web..",
                            "jakarta.persistence..",
                            "com.fasterxml.jackson..")
                    .check(classes);
        }

        @Test
        @DisplayName("reads no configuration properties itself")
        void readsNoConfigurationProperties() {
            noClasses()
                    .that().resideInAPackage(APPLICATION)
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Value")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("inbound adapters")
    class InboundAdapters {

        @Test
        @DisplayName("do not depend on infrastructure")
        void doNotDependOnInfrastructure() {
            noClasses()
                    .that().resideInAPackage(API)
                    .should().dependOnClassesThat()
                    .resideInAPackage(INFRASTRUCTURE)
                    .check(classes);
        }

        @Test
        @DisplayName("speak the application's vocabulary, not the domain model")
        void doNotDependOnTheDomainModel() {
            noClasses()
                    .that().resideInAPackage(API)
                    .should().dependOnClassesThat()
                    .resideInAPackage(DOMAIN)
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("outbound adapters")
    class OutboundAdapters {

        @Test
        @DisplayName("are never called by inbound adapters directly")
        void infrastructureDoesNotDependOnApi() {
            noClasses()
                    .that().resideInAPackage(INFRASTRUCTURE)
                    .should().dependOnClassesThat()
                    .resideInAPackage(API)
                    .check(classes);
        }

        @Test
        @DisplayName("are the only implementations of the application's ports")
        void portsAreImplementedInInfrastructure() {
            classes()
                    .that().resideInAPackage("com.tinder.profiles.application..port.out..")
                    .and().areInterfaces()
                    .should().onlyBeAccessed().byAnyPackage(APPLICATION, INFRASTRUCTURE, CONFIG)
                    .check(classes);
        }

        @Test
        @DisplayName("implementing a port are named *Adapter")
        void portImplementationsAreNamedAdapter() {
            classes()
                    .that().resideInAPackage(INFRASTRUCTURE)
                    .and().implement(AN_APPLICATION_PORT)
                    .should().haveSimpleNameEndingWith("Adapter")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("the configuration layer")
    class ConfigurationLayer {

        @Test
        @DisplayName("owns every @ConfigurationProperties class")
        void ownsEveryPropertiesClass() {
            classes()
                    .that().areAnnotatedWith(
                            "org.springframework.boot.context.properties.ConfigurationProperties")
                    .should().resideInAPackage("com.tinder.profiles.config.props")
                    .andShould().beRecords()
                    .check(classes);
        }

        @Test
        @DisplayName("is depended on by nothing but adapters")
        void isNotUsedByTheApplicationOrDomain() {
            noClasses()
                    .that().resideInAnyPackage(DOMAIN, APPLICATION)
                    .should().dependOnClassesThat()
                    .resideInAPackage(CONFIG)
                    .check(classes);
        }
    }

    @Test
    @DisplayName("layers are free of cycles")
    void layersAreFreeOfCycles() {
        slices()
                .matching("com.tinder.profiles.(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }
}
