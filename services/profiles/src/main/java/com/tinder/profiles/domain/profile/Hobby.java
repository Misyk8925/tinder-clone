package com.tinder.profiles.domain.profile;

/**
 * A hobby selected by a profile owner.
 *
 * <p>The domain owns this vocabulary. API and persistence adapters translate it
 * to the shared-contract enum at their boundaries.
 */
public enum Hobby {
    HIKING, CYCLING, RUNNING, GYM, YOGA, SWIMMING,
    FOOTBALL, BASKETBALL, TENNIS, VOLLEYBALL,
    PHOTOGRAPHY, PAINTING, DRAWING, WRITING, MUSIC,
    SINGING, DANCING, COOKING, BAKING, CRAFTING,
    GAMING, READING, MOVIES, TRAVELING, PODCASTS,
    VOLUNTEERING, PETS, GARDENING, MEDITATION, ASTROLOGY
}
