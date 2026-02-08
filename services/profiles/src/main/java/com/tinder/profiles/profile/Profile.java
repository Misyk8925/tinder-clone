package com.tinder.profiles.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.tinder.profiles.location.Location;
import com.tinder.profiles.photos.Photo;
import com.tinder.profiles.preferences.Preferences;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "profiles", indexes = {
        @Index(name = "idx_age", columnList = "age"),
        @Index(name = "idx_gender", columnList = "gender"),
        @Index(name = "idx_city", columnList = "city"),
        @Index(name = "idx_active_deleted", columnList = "is_active, is_deleted"),
        @Index(name = "idx_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_search_query",
                columnList = "is_deleted, age, gender"),
        @Index(name = "idx_created_at_deleted",
                columnList = "created_at, is_deleted"),
        @Index(name = "idx_active_created",
                columnList = "is_active, created_at"),
        @Index(name = "idx_name_lower", columnList = "name"),
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties(value = {"hibernateLazyInitializer", "handler"}, ignoreUnknown = true)
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID profileId;

    @Column(name = "user_id", unique = true)
    private String userId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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
    @ColumnDefault("true")
    private boolean isActive;

    @ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE}, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "preferences_id", nullable = false)
    private Preferences preferences;

    @Column(name = "is_deleted", nullable = false)
    @ColumnDefault("false")
    private boolean isDeleted;


    @OneToMany(mappedBy = "profile", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 10)
    private List<Photo> photos;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    // Domain logic methods

    public void updateBasicInfo(String name, Integer age, String gender, String bio, String city) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.bio = bio;
        this.city = city;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsDeleted() {
        this.isDeleted = true;
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        if (!this.isDeleted) {
            this.isActive = true;
            this.updatedAt = LocalDateTime.now();
        }
    }


    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }



}