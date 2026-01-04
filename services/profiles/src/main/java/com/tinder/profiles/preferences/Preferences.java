package com.tinder.profiles.preferences;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tinder.profiles.profile.Profile;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(
    name = "preferences",
    schema = "public",
    indexes = {
        @Index(name = "idx_preferences_lookup", columnList = "min_age, max_age, gender, max_range")
    },
    uniqueConstraints = {
    @UniqueConstraint(name = "uk_preferences_combination",
            columnNames = {"min_age", "max_age", "gender", "max_range"})
        }
)
public class Preferences {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "gender")
    private String gender;

    @Column(name = "max_range", nullable = false)
    private Integer maxRange;


    @JsonIgnore // Ignore field during serialization
    @OneToMany(mappedBy = "preferences", fetch = FetchType.LAZY)
    private List<Profile> profiles;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Preferences that = (Preferences) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}