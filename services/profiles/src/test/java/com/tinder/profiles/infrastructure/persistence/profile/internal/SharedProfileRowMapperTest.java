package com.tinder.profiles.infrastructure.persistence.profile.internal;

import com.tinder.contracts.dto.Hobby;
import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.contracts.dto.SharedProfileDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;

@DisplayName("SharedProfileRowMapper")
class SharedProfileRowMapperTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LOCATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 12, 0);

    private final SharedProfileRowMapper mapper = new SharedProfileRowMapper();

    /** A row in the column order of {@code ProfileRepository}'s flat projections. */
    private static Object[] row(UUID profileId, String name) {
        return new Object[]{
                profileId,                          // 0  p.id
                name,                               // 1  p.name
                29,                                 // 2  p.age
                "bio of " + name,                   // 3  p.bio
                "Kyiv",                             // 4  p.city
                true,                               // 5  p.is_active
                false,                              // 6  p.is_deleted
                LOCATION_ID,                        // 7  l.id
                50.45,                              // 8  ST_Y(geo) → latitude
                30.52,                              // 9  ST_X(geo) → longitude
                "Kyiv",                             // 10 l.city
                Timestamp.valueOf(CREATED_AT),      // 11 l.created_at
                Timestamp.valueOf(CREATED_AT),      // 12 l.updated_at
                18,                                 // 13 pref.min_age
                40,                                 // 14 pref.max_age
                "FEMALE",                           // 15 pref.gender
                50                                  // 16 pref.max_range
        };
    }

    private static SharedPhotoDto photo(UUID profileId, int position) {
        return new SharedPhotoDto(
                UUID.randomUUID(), profileId, "s3/key/" + position, position == 0, position,
                "https://cdn/" + position, "image/jpeg", 1024L, CREATED_AT);
    }

    @Nested
    @DisplayName("when the projection's associations are supplied")
    class WithAssociations {

        @Test
        @DisplayName("fills in the photos and hobbies the flat row cannot carry")
        void fillsInPhotosAndHobbies() {
            Map<UUID, List<SharedPhotoDto>> photos =
                    Map.of(PROFILE_ID, List.of(photo(PROFILE_ID, 0), photo(PROFILE_ID, 1)));
            Map<UUID, List<Hobby>> hobbies =
                    Map.of(PROFILE_ID, List.of(Hobby.HIKING, Hobby.GAMING));

            List<SharedProfileDto> dtos = mapper.toDtos(List.<Object[]>of(row(PROFILE_ID, "Ada")), photos, hobbies);

            then(dtos).hasSize(1);
            then(dtos.get(0).photos()).hasSize(2);
            then(dtos.get(0).hobbies()).containsExactly(Hobby.HIKING, Hobby.GAMING);
        }

        @Test
        @DisplayName("gives each profile only its own associations")
        void doesNotLeakAssociationsAcrossProfiles() {
            Map<UUID, List<SharedPhotoDto>> photos = Map.of(PROFILE_ID, List.of(photo(PROFILE_ID, 0)));
            Map<UUID, List<Hobby>> hobbies = Map.of(OTHER_PROFILE_ID, List.of(Hobby.YOGA));

            List<SharedProfileDto> dtos = mapper.toDtos(
                    List.of(row(PROFILE_ID, "Ada"), row(OTHER_PROFILE_ID, "Grace")), photos, hobbies);

            then(dtos.get(0).photos()).hasSize(1);
            then(dtos.get(0).hobbies()).isEmpty();
            then(dtos.get(1).photos()).isEmpty();
            then(dtos.get(1).hobbies()).containsExactly(Hobby.YOGA);
        }

        @Test
        @DisplayName("maps every scalar column to its own field")
        void mapsScalarColumns() {
            SharedProfileDto dto = mapper.toDtos(List.<Object[]>of(row(PROFILE_ID, "Ada")), Map.of(), Map.of()).get(0);

            then(dto.id()).isEqualTo(PROFILE_ID);
            then(dto.name()).isEqualTo("Ada");
            then(dto.age()).isEqualTo(29);
            then(dto.bio()).isEqualTo("bio of Ada");
            then(dto.city()).isEqualTo("Kyiv");
            then(dto.isActive()).isTrue();
            then(dto.isDeleted()).isFalse();
            then(dto.location().id()).isEqualTo(LOCATION_ID);
            then(dto.location().latitude()).isEqualTo(50.45);
            then(dto.location().longitude()).isEqualTo(30.52);
            then(dto.location().createdAt()).isEqualTo(CREATED_AT);
            then(dto.preferences().minAge()).isEqualTo(18);
            then(dto.preferences().maxAge()).isEqualTo(40);
            then(dto.preferences().gender()).isEqualTo("FEMALE");
            then(dto.preferences().maxRange()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("when ordering results against the requested ids")
    class InRequestedOrder {

        @Test
        @DisplayName("returns rows in the caller's order, carrying the associations")
        void ordersByRequestedIdsAndKeepsAssociations() {
            List<Object[]> rows = List.<Object[]>of(row(PROFILE_ID, "Ada"), row(OTHER_PROFILE_ID, "Grace"));
            Map<UUID, List<Hobby>> hobbies = Map.of(OTHER_PROFILE_ID, List.of(Hobby.PETS));

            List<SharedProfileDto> dtos = mapper.toDtosInOrder(
                    List.of(OTHER_PROFILE_ID, PROFILE_ID), rows, Map.of(), hobbies);

            then(dtos).extracting(SharedProfileDto::id)
                    .containsExactly(OTHER_PROFILE_ID, PROFILE_ID);
            then(dtos.get(0).hobbies()).containsExactly(Hobby.PETS);
        }

        @Test
        @DisplayName("drops requested ids that the query did not return")
        void dropsMissingIds() {
            List<SharedProfileDto> dtos = mapper.toDtosInOrder(
                    List.of(PROFILE_ID, OTHER_PROFILE_ID),
                    List.<Object[]>of(row(PROFILE_ID, "Ada")),
                    Map.of(), Map.of());

            then(dtos).extracting(SharedProfileDto::id).containsExactly(PROFILE_ID);
        }
    }

    @Nested
    @DisplayName("when grouping hobby rows")
    class HobbyRows {

        @Test
        @DisplayName("groups stored values per profile")
        void groupsPerProfile() {
            List<Object[]> hobbyRows = List.of(
                    new Object[]{PROFILE_ID, "HIKING"},
                    new Object[]{PROFILE_ID, "GAMING"},
                    new Object[]{OTHER_PROFILE_ID, "YOGA"});

            Map<UUID, List<Hobby>> grouped = mapper.hobbiesByProfileId(hobbyRows);

            then(grouped.get(PROFILE_ID)).containsExactly(Hobby.HIKING, Hobby.GAMING);
            then(grouped.get(OTHER_PROFILE_ID)).containsExactly(Hobby.YOGA);
        }

        @Test
        @DisplayName("skips a stored value that no longer matches a Hobby constant")
        void skipsUnknownStoredValue() {
            List<Object[]> hobbyRows = List.of(
                    new Object[]{PROFILE_ID, "HIKING"},
                    new Object[]{PROFILE_ID, "SPELUNKING"});

            Map<UUID, List<Hobby>> grouped = mapper.hobbiesByProfileId(hobbyRows);

            then(grouped.get(PROFILE_ID)).containsExactly(Hobby.HIKING);
        }
    }

    @Nested
    @DisplayName("when reading the ids off a projection")
    class IdsOf {

        @Test
        @DisplayName("returns one id per row, in row order")
        void returnsIdsInRowOrder() {
            List<UUID> ids = mapper.idsOf(List.of(row(PROFILE_ID, "Ada"), row(OTHER_PROFILE_ID, "Grace")));

            then(ids).containsExactly(PROFILE_ID, OTHER_PROFILE_ID);
        }
    }
}
