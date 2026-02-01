package com.tinder.profiles.photos;

import com.tinder.profiles.profile.Profile;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "photo", indexes = {
        @Index(name = "idx_photo_profile_id",
                columnList = "profile_id"),
        @Index(name = "idx_photo_s3_key",
                columnList = "s3_key", unique = true),
        @Index(name = "idx_photo_primary_profile",
                columnList = "profile_id, is_primary"),
})
public class Photo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "photo_id", nullable = false)
    private UUID photoID;

    @Column(name = "s3_key", nullable = false, unique = true)
    private String s3Key;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "position", nullable = false)
    private int position;


    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "content_type")
    private String contentType;

    @Column
    private long size;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

}