package com.tinder.profiles.application.profile.port.in;

import com.tinder.profiles.application.profile.query.InternalProfileView;

import java.util.List;
import java.util.UUID;

/** Read-side boundary used by the service's internal HTTP API. */
public interface InternalProfileQuery {

    List<InternalProfileView> search(UUID viewerId, SearchCriteria criteria, int limit);

    List<InternalProfileView> getMany(List<UUID> ids);

    List<InternalProfileView> getActiveUsers();

    record SearchCriteria(Integer minAge, Integer maxAge, String gender, Integer maxRange) {
    }
}
