package com.tinder.deckread.readmodel;

import java.util.UUID;

/** Key schema owned exclusively by Deck Read. */
public final class ReadModelKeys {

    private ReadModelKeys() {
    }

    public static String profile(UUID profileId) {
        return "dr:profile:{" + profileId + "}:card";
    }

    public static String userToProfile(String viewerUserId) {
        return "dr:user:{" + viewerUserId + "}:profile";
    }

    public static String viewerMeta(UUID viewerProfileId) {
        return viewer(viewerProfileId) + ":meta";
    }

    public static String materializedOrder(UUID viewerProfileId, long generation) {
        return viewer(viewerProfileId) + ":m:order:" + generation;
    }

    public static String materializedCards(UUID viewerProfileId, long generation) {
        return viewer(viewerProfileId) + ":m:cards:" + generation;
    }

    public static String materializedTail(UUID viewerProfileId, long generation) {
        return viewer(viewerProfileId) + ":m:tail:" + generation;
    }

    public static String materializedMeta(UUID viewerProfileId) {
        return viewer(viewerProfileId) + ":m:meta";
    }

    public static String hotViewers(UUID profileId) {
        return "dr:profile:{" + profileId + "}:hot-viewers";
    }

    public static String fresh(UUID viewerProfileId, long generation) {
        return viewer(viewerProfileId) + ":fresh:" + generation;
    }

    public static String repeat(UUID viewerProfileId, long generation) {
        return viewer(viewerProfileId) + ":repeat:" + generation;
    }

    public static String swipes(UUID viewerProfileId) {
        return viewer(viewerProfileId) + ":swipes";
    }

    public static String repeatCandidates(UUID viewerProfileId) {
        return viewer(viewerProfileId) + ":repeat-candidates";
    }

    public static String matched(UUID viewerProfileId) {
        return viewer(viewerProfileId) + ":matched";
    }

    public static String suppressed(UUID viewerProfileId) {
        return viewer(viewerProfileId) + ":suppressed";
    }

    public static String buildLock(UUID viewerProfileId) {
        return viewer(viewerProfileId) + ":build-lock";
    }

    public static String readiness() {
        return "dr:read-model:ready";
    }

    public static String repeatReadiness() {
        return "dr:read-model:repeat-ready";
    }

    private static String viewer(UUID viewerProfileId) {
        // The viewer profileId is the Redis Cluster hash tag, co-locating all
        // fresh/repeat/meta/mutation keys needed by atomic viewer operations.
        return "dr:viewer:{" + viewerProfileId + "}";
    }
}
