package com.tinder.profiles.preferences;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "composite_key")
    private String compositeKey;



}