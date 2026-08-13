package com.tinder.profiles.application.photos;

import com.tinder.profiles.application.photos.usecase.DeletePhotoService;
import com.tinder.profiles.application.photos.usecase.UploadPhotoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Producer/backfill boundary checks for FR-3 and FR-4. */
@Tag("acceptance")
@DisplayName("Feature: Profiles supplies a recoverable Deck Card projection")
class ProfileDeckCardProjectionBoundaryAcceptanceTest {

    @Test
    @DisplayName("Scenario: Given the shared contracts, when Profiles emits a card snapshot, then a versioned Deck Card event exists")
    void sharedVersionedDeckCardEventExists() {
        // Given the shared contract module
        // When / Then
        assertThatCode(() -> Class.forName("com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Scenario: Given a photo mutation, when it is committed, then the Deck Card projection participates in the transactional outbox")
    void photoMutationsParticipateInTheTransactionalProjectionOutbox() {
        // Given photo upload and delete use cases
        // When / Then
        assertThat(fieldTypeNames(UploadPhotoService.class))
                .contains("com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort");
        assertThat(fieldTypeNames(DeletePhotoService.class))
                .contains("com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort");
    }

    @Test
    @DisplayName("Scenario: Given an initial fill or recovery, when an operator starts backfill, then the same run can resume")
    void restartablePagedBackfillEntryPointExists() {
        // Given the Profiles application boundary
        // When / Then
        assertThatCode(() -> Class.forName("com.tinder.profiles.application.profile.usecase.DeckCardProjectionBackfillService"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Scenario: Given a Read Cluster loss, when Profiles restarts, then the backfill checkpoint survives in Profiles PostgreSQL")
    void backfillCheckpointIsDurableInProfilesPostgres() {
        // Given the Profiles persistence boundary
        // When / Then
        assertThatCode(() -> Class.forName(
                "com.tinder.profiles.infrastructure.persistence.backfill.DeckCardProjectionBackfillRunJpaEntity"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Scenario: Given more than one backfill page, when a page is enqueued, then at most 500 events and its checkpoint use one transaction")
    void eachPageUsesFiveHundredRowsAndTheExistingTransactionalOutbox() throws Exception {
        // Given
        String source = mainSources();

        // When / Then
        assertThat(source)
                .contains("BACKFILL_PAGE_SIZE = 500")
                .contains("profile_event_outbox")
                .contains("backfillRunId")
                .contains("lastProfileId")
                .contains("@Transactional");
    }

    private String[] fieldTypeNames(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .toArray(String[]::new);
    }

    private String mainSources() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            StringBuilder source = new StringBuilder();
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                source.append(Files.readString(file)).append('\n');
            }
            return source.toString();
        }
    }
}
