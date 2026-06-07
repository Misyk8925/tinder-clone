package com.tinder.profiles.infrastructure.external.keycloak;

public record CreatedUserRecord(String userId,String username,String password,String firstName, String lastName) {
}
