package com.tinder.profiles.location;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tinder.profiles.profile.Profile;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "location", schema = "public")
@Builder
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @JsonIgnore
    @Column(name = "geo", columnDefinition = "geography(Point,4326)")
    private Point geo;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonBackReference("profile-location")
    @OneToOne(mappedBy = "location", fetch = FetchType.LAZY)
    private Profile profile;


    @JsonProperty("latitude")
    public Double getLatitude() {
        return geo != null ? geo.getY() : null;
    }

    @JsonProperty("longitude")
    public Double getLongitude() {
        return geo != null ? geo.getX() : null;
    }
}