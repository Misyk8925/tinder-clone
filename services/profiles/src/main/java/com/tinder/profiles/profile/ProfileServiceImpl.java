package com.tinder.profiles.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.location.Location;
import com.tinder.profiles.location.LocationService;
import com.tinder.profiles.preferences.PreferencesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ProfileServiceImpl  {

    private final ProfileRepository repo;
    private final LocationService locationService;
    private final PreferencesService preferencesService;
    private final ProfileMapper mapper;

    private final ObjectMapper objectMapper;

    public Page<Profile> getAll(Pageable pageable) {
        return repo.findAll(pageable);
    }

    public Profile getOne(UUID id) {
        Optional<Profile> profileOptional = repo.findById(id);
        return profileOptional.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id `%s` not found".formatted(id)));
    }

    public Profile getByUsername(String username) {
        return repo.findByName(username);
    }

    public List<Profile> getMany(List<UUID> ids) {
        return repo.findAllById(ids);
    }

    public Profile create(CreateProfileDtoV1 profile) {

        try {
            new Profile();
            Profile profileEntity = mapper.toEntity(profile);
            return repo.save(profileEntity);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }

    }

    public Profile patch(UUID id, JsonNode patchNode) throws IOException {
        Profile profile = repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id `%s` not found".formatted(id)));

        objectMapper.readerForUpdating(profile).readValue(patchNode);

        return repo.save(profile);
    }

    public List<UUID> patchMany(List<UUID> ids, JsonNode patchNode) throws IOException {
        Collection<Profile> profiles = repo.findAllById(ids);

        for (Profile profile : profiles) {
            objectMapper.readerForUpdating(profile).readValue(patchNode);
        }

        List<Profile> resultProfiles = repo.saveAll(profiles);
        return resultProfiles.stream()
                .map(Profile::getProfileId)
                .toList();
    }

    public Profile delete(UUID id) {
        Profile profile = repo.findById(id).orElse(null);
        if (profile != null) {
            repo.delete(profile);
        }
        return profile;
    }

    public void deleteMany(List<UUID> ids) {
        repo.deleteAllById(ids);
    }

//    @Override
//    public Profile createProfile(ProfileDto profileDto) {
//        UUID id = UUID.randomUUID();
//        Location location = locationService.create(profileDto.getCity());
//
//    }
}
