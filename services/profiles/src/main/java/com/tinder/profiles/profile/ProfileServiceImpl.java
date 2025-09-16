package com.tinder.profiles.profile;

import com.tinder.profiles.location.Location;
import com.tinder.profiles.location.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ProfileServiceImpl  {

    private final ProfileRepository repo;
    private final LocationService locationService;
//    @Override
//    public Profile createProfile(ProfileDto profileDto) {
//        UUID id = UUID.randomUUID();
//        Location location = locationService.create(profileDto.getCity());
//
//    }
}
