package com.tinder.profiles.preferences;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tinder.profiles.profile.Profile;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "preferences", schema = "public")
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


    @JsonIgnore // Игнорируем поле при сериализации
    @OneToMany(mappedBy = "preferences", fetch = FetchType.LAZY)
    private List<Profile> profiles;

}