package com.tinder.clone.consumer.model.embedded;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SwipeRecordId implements Serializable {

    @Column(name = "profile1_id", nullable = false)
    private UUID profile1Id;
    @Column(name = "profile2_id", nullable = false)
    private UUID profile2Id;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SwipeRecordId that = (SwipeRecordId) o;
        return Objects.equals(profile1Id, that.profile1Id) &&
               Objects.equals(profile2Id, that.profile2Id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profile1Id, profile2Id);
    }

    @Override
    public String toString() {
        return String.valueOf(profile1Id)+"-"+String.valueOf(profile2Id);
    }
}
