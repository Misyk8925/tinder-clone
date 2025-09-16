package com.tinder.profiles.profile;

import com.tinder.profiles.location.LocationService;
import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.preferences.PreferencesDto;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

@Primary
@Component
@RequiredArgsConstructor
public class ProfileMapperImpl1 implements ProfileMapper {

    private final LocationService locationService;

    @Override
    public Profile toEntity(CreateProfileDtoV1 createProfileDtoV1) {
        if ( createProfileDtoV1 == null ) {
            return null;
        }

        Profile.ProfileBuilder profile = Profile.builder();

        profile.name( createProfileDtoV1.getName() );
        profile.age( createProfileDtoV1.getAge() );
        profile.bio( createProfileDtoV1.getBio() );
        profile.city( createProfileDtoV1.getCity() );
        profile.location( locationService.create(createProfileDtoV1.getCity()) );
        profile.preferences( preferencesDtoToPreferences( createProfileDtoV1.getPreferences() ) );

        return profile.build();
    }

    @Override
    public CreateProfileDtoV1 toCreateProfileDtoV1(Profile profile) {
        if ( profile == null ) {
            return null;
        }

        String name = null;
        Integer age = null;
        String bio = null;
        String city = null;
        PreferencesDto preferences = null;

        name = profile.getName();
        age = profile.getAge();
        bio = profile.getBio();
        city = profile.getCity();
        preferences = preferencesToPreferencesDto( profile.getPreferences() );

        CreateProfileDtoV1 createProfileDtoV1 = new CreateProfileDtoV1( name, age, bio, city, preferences );

        return createProfileDtoV1;
    }


    protected Preferences preferencesDtoToPreferences(PreferencesDto preferencesDto) {
        if ( preferencesDto == null ) {
            return null;
        }

        Preferences.PreferencesBuilder preferences = Preferences.builder();

        preferences.minAge( preferencesDto.getMinAge() );
        preferences.maxAge( preferencesDto.getMaxAge() );
        preferences.gender( preferencesDto.getGender() );
        preferences.maxRange( preferencesDto.getMaxRange() );

        return preferences.build();
    }

    protected PreferencesDto preferencesToPreferencesDto(Preferences preferences) {
        if ( preferences == null ) {
            return null;
        }

        Integer minAge = null;
        Integer maxAge = null;
        String gender = null;
        Integer maxRange = null;

        minAge = preferences.getMinAge();
        maxAge = preferences.getMaxAge();
        gender = preferences.getGender();
        maxRange = preferences.getMaxRange();

        PreferencesDto preferencesDto = new PreferencesDto( minAge, maxAge, gender, maxRange );

        return preferencesDto;
    }
}
