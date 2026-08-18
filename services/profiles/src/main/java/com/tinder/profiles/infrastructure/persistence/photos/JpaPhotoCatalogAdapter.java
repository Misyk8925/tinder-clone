package com.tinder.profiles.infrastructure.persistence.photos;

import com.tinder.profiles.application.photos.model.PhotoDraft;
import com.tinder.profiles.application.photos.model.StoredPhoto;
import com.tinder.profiles.application.photos.port.out.PhotoCatalogPort;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of {@link PhotoCatalogPort}, mapping the {@link Photo}
 * entity to the application's {@link StoredPhoto} read model.
 */
@Component
@RequiredArgsConstructor
public class JpaPhotoCatalogAdapter implements PhotoCatalogPort {

    private final PhotoRepository photoRepository;
    private final ProfileRepository profileRepository;

    @Override
    public int countForProfile(UUID profileId) {
        return photoRepository.countByProfile_ProfileId(profileId);
    }

    @Override
    public List<StoredPhoto> findForProfile(UUID profileId) {
        return photoRepository.findAllByProfile_ProfileIdOrderByPositionAsc(profileId).stream()
                .map(this::toStoredPhoto)
                .toList();
    }

    @Override
    public Optional<StoredPhoto> findById(UUID photoId) {
        return photoRepository.findById(photoId).map(this::toStoredPhoto);
    }

    @Override
    public StoredPhoto save(PhotoDraft draft) {
        Photo photo = new Photo();
        // Only the FK is needed, so a lazy reference avoids loading the profile row.
        photo.setProfile(profileRepository.getReferenceById(draft.profileId()));
        photo.setS3Key(draft.s3Key());
        photo.setPrimary(draft.primary());
        photo.setPosition(draft.position());
        photo.setUrl(draft.url());
        photo.setContentType(draft.contentType());
        photo.setSize(draft.size());
        photo.setCreatedAt(LocalDateTime.now());

        return toStoredPhoto(photoRepository.save(photo));
    }

    @Override
    public void deleteById(UUID photoId) {
        photoRepository.deleteById(photoId);
    }

    private StoredPhoto toStoredPhoto(Photo photo) {
        return new StoredPhoto(
                photo.getPhotoID(),
                photo.getProfile().getProfileId(),
                photo.getS3Key(),
                photo.isPrimary(),
                photo.getPosition(),
                photo.getUrl(),
                photo.getContentType(),
                photo.getSize(),
                photo.getCreatedAt());
    }
}
