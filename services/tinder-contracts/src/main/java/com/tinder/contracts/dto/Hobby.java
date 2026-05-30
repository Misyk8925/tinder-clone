package com.tinder.contracts.dto;

/**
 * Predefined hobbies a user can select for their profile.
 * Stored as strings in the {@code profile_hobbies} join table in the profiles service.
 */
public enum Hobby {
    // Sports & Fitness
    HIKING, CYCLING, RUNNING, GYM, YOGA, SWIMMING,
    FOOTBALL, BASKETBALL, TENNIS, VOLLEYBALL,

    // Arts & Creativity
    PHOTOGRAPHY, PAINTING, DRAWING, WRITING, MUSIC,
    SINGING, DANCING, COOKING, BAKING, CRAFTING,

    // Entertainment & Media
    GAMING, READING, MOVIES, TRAVELING, PODCASTS,

    // Social & Lifestyle
    VOLUNTEERING, PETS, GARDENING, MEDITATION, ASTROLOGY
}
