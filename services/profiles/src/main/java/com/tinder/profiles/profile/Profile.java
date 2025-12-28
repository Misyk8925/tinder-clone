package com.tinder.profiles.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.tinder.profiles.location.Location;
import com.tinder.profiles.photos.Photo;
import com.tinder.profiles.preferences.Preferences;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "profiles")
@JsonIgnoreProperties(value = {"hibernateLazyInitializer", "handler"}, ignoreUnknown = true)
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID profileId;

    @Column(name = "user_id", unique = true)
    private String userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "age", nullable = false)
    private Integer age;

    @Column(name = "gender", nullable = false)
    private String gender;

    @Column(name = "bio", length = 1023)
    private String bio;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @JsonManagedReference("profile-location")
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, optional = false, orphanRemoval = true)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "preferences_id", nullable = false)
    private Preferences preferences;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;


    @OneToMany(mappedBy = "profile", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Photo> photos;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Domain logic methods

    /**
     * Updates basic profile information
     */
    public void updateBasicInfo(String name, Integer age, String gender, String bio, String city) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.bio = bio;
        this.city = city;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks profile as deleted (soft delete)
     */
    public void markAsDeleted() {
        this.isDeleted = true;
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Activates the profile
     */
    public void activate() {
        if (!this.isDeleted) {
            this.isActive = true;
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * Deactivates the profile
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }



}