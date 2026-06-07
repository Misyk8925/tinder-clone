package com.tinder.profiles.domain.profile;

import com.tinder.contracts.dto.Hobby;

import java.util.List;

/**
 * A requested change to a profile, expressed purely in domain terms. The
 * application layer builds this from an inbound API DTO (after sanitization and
 * after resolving coordinates via the location service), so the domain never
 * sees transport/API types.
 *
 * <p>A {@code null} field means "not provided" (patch semantics); a full update
 * simply populates every field. {@code position} carries already-resolved
 * coordinates when the caller sent GPS data.
 */
public record ProfileEdit(
        String name,
        Integer age,
        String gender,
        String bio,
        String city,
        GeoPoint position,
        MatchingPreferences preferences,
        List<Hobby> hobbies
) {
    public boolean hasCity() {
        return city != null && !city.isBlank();
    }

    public boolean hasPosition() {
        return position != null;
    }
}
