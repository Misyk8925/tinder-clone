package com.tinder.profiles.domain.profile;

import com.tinder.contracts.dto.Hobby;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Profile aggregate root — the pure domain model.
 *
 * <p>No persistence or framework annotations and <strong>no Lombok</strong>:
 * the JPA mapping lives in {@code infrastructure/persistence/ProfileJpaEntity}
 * and a mapper translates between the two. State is mutated only through
 * behaviour methods (no public setters), so the aggregate's invariants cannot
 * be bypassed. Construction goes through the hand-written {@link Builder}, used
 * by the persistence mapper to reconstitute and by use cases to create.
 *
 * <p>Scope note: {@code photos} are intentionally not part of this aggregate —
 * they are managed by the {@code photos} feature (S3 lifecycle) and no profile
 * business rule touches them.
 *
 * <p>Reference note: {@code position} holds resolved coordinates rather than a
 * {@code Location} entity (owned by the location service). The aggregate only
 * needs coordinates (for movement/distance rules) and the city name.
 */
public class Profile {

    private final UUID id;
    private final String userId;

    private String name;
    private Integer age;
    private String gender;
    private String bio;
    private String city;
    private GeoPoint position;

    private boolean active;
    private boolean premium;
    private LocalDateTime premiumExpiresAt;
    private boolean deleted;
    private LocalDateTime deletedAt;

    private MatchingPreferences preferences;
    private List<Hobby> hobbies;

    private final LocalDateTime createdAt;

    private Profile(Builder b) {
        this.id = b.id;
        this.userId = b.userId;
        this.name = b.name;
        this.age = b.age;
        this.gender = b.gender;
        this.bio = b.bio;
        this.city = b.city;
        this.position = b.position;
        this.active = b.active;
        this.premium = b.premium;
        this.premiumExpiresAt = b.premiumExpiresAt;
        this.deleted = b.deleted;
        this.deletedAt = b.deletedAt;
        this.preferences = b.preferences;
        this.hobbies = (b.hobbies == null) ? new ArrayList<>() : new ArrayList<>(b.hobbies);
        this.createdAt = b.createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    // --- Behaviour -------------------------------------------------------

    /** Updates the editable basic-info fields. */
    public void updateBasicInfo(String name, Integer age, String gender, String bio, String city) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.bio = bio;
        this.city = city;
    }

    /** Moves the profile to a resolved position and (optionally) city. */
    public void relocate(GeoPoint position, String city) {
        this.position = position;
        if (city != null && !city.isBlank()) {
            this.city = city;
        }
    }

    public void changePreferences(MatchingPreferences preferences) {
        this.preferences = preferences;
    }

    public void replaceHobbies(List<Hobby> hobbies) {
        this.hobbies = (hobbies == null) ? new ArrayList<>() : new ArrayList<>(hobbies);
    }

    /** Soft-deletes the profile. */
    public void markAsDeleted() {
        this.deleted = true;
        this.active = false;
        this.deletedAt = LocalDateTime.now();
    }

    /** Reactivates the profile unless it has been deleted. */
    public void activate() {
        if (!this.deleted) {
            this.active = true;
        }
    }

    public void deactivate() {
        this.active = false;
    }

    /**
     * Changes premium status. Expiry is stored only while premium is active and
     * cleared on revocation.
     */
    public void changePremium(boolean premium, LocalDateTime expiresAt) {
        this.premium = premium;
        this.premiumExpiresAt = premium ? expiresAt : null;
    }

    // --- Queries ---------------------------------------------------------

    /**
     * Whether this profile may be soft-deleted. A pure self-state rule today; a
     * future cross-aggregate rule (e.g. "no pending matches") would move to a
     * domain service, since it needs data beyond this aggregate.
     */
    public boolean isDeletable() {
        return !this.deleted;
    }

    /** Current coordinates, if known. */
    public Optional<GeoPoint> currentPosition() {
        return Optional.ofNullable(position);
    }

    /**
     * True if {@code newPosition} is at least {@code thresholdKm} from the
     * current position. Always true when no position is known, so the first
     * coordinate update is accepted. Used to suppress GPS jitter.
     */
    public boolean hasMovedBeyond(GeoPoint newPosition, double thresholdKm) {
        return currentPosition()
                .map(current -> current.distanceKm(newPosition) >= thresholdKm)
                .orElse(true);
    }

    // --- Accessors -------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public Integer getAge() {
        return age;
    }

    public String getGender() {
        return gender;
    }

    public String getBio() {
        return bio;
    }

    public String getCity() {
        return city;
    }

    public GeoPoint getPosition() {
        return position;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPremium() {
        return premium;
    }

    public LocalDateTime getPremiumExpiresAt() {
        return premiumExpiresAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public MatchingPreferences getPreferences() {
        return preferences;
    }

    /** Unmodifiable view; mutate via {@link #replaceHobbies(List)}. */
    public List<Hobby> getHobbies() {
        return Collections.unmodifiableList(hobbies);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // --- Builder ---------------------------------------------------------

    public static final class Builder {
        private UUID id;
        private String userId;
        private String name;
        private Integer age;
        private String gender;
        private String bio;
        private String city;
        private GeoPoint position;
        private boolean active;
        private boolean premium;
        private LocalDateTime premiumExpiresAt;
        private boolean deleted;
        private LocalDateTime deletedAt;
        private MatchingPreferences preferences;
        private List<Hobby> hobbies;
        private LocalDateTime createdAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder age(Integer age) { this.age = age; return this; }
        public Builder gender(String gender) { this.gender = gender; return this; }
        public Builder bio(String bio) { this.bio = bio; return this; }
        public Builder city(String city) { this.city = city; return this; }
        public Builder position(GeoPoint position) { this.position = position; return this; }
        public Builder active(boolean active) { this.active = active; return this; }
        public Builder premium(boolean premium) { this.premium = premium; return this; }
        public Builder premiumExpiresAt(LocalDateTime v) { this.premiumExpiresAt = v; return this; }
        public Builder deleted(boolean deleted) { this.deleted = deleted; return this; }
        public Builder deletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; return this; }
        public Builder preferences(MatchingPreferences p) { this.preferences = p; return this; }
        public Builder hobbies(List<Hobby> hobbies) { this.hobbies = hobbies; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Profile build() {
            return new Profile(this);
        }
    }
}
