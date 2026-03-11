package com.tinder.profiles.hobbies;

/**
 * Predefined set of hobbies a user can select for their profile.
 * Stored as strings in the database (EnumType.STRING) via the profile_hobbies join table.
 */
public enum Hobby {
    // Sports & Fitness
    HIKING,
    CYCLING,
    RUNNING,
    GYM,
    YOGA,
    SWIMMING,
    FOOTBALL,
    BASKETBALL,
    TENNIS,
    VOLLEYBALL,

    // Arts & Creativity
    PHOTOGRAPHY,
    PAINTING,
    DRAWING,
    WRITING,
    MUSIC,
    SINGING,
    DANCING,
    COOKING,
    BAKING,
    CRAFTING,

    // Entertainment & Media
    GAMING,
    READING,
    MOVIES,
    TRAVELING,
    PODCASTS,

    // Social & Lifestyle
    VOLUNTEERING,
    PETS,
    GARDENING,
    MEDITATION,
    ASTROLOGY
}

