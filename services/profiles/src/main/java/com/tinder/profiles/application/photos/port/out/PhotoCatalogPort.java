package com.tinder.profiles.application.photos.port.out;

import com.tinder.profiles.application.photos.model.PhotoDraft;
import com.tinder.profiles.application.photos.model.StoredPhoto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for photo metadata: the catalogue of what a profile owns. */
public interface PhotoCatalogPort {

    int countForProfile(UUID profileId);

    /** Photos of a profile ordered by slot position. */
    List<StoredPhoto> findForProfile(UUID profileId);

    Optional<StoredPhoto> findById(UUID photoId);

    StoredPhoto save(PhotoDraft draft);

    void deleteById(UUID photoId);
}
