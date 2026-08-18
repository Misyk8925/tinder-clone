package com.tinder.profiles.infrastructure.external.keycloak;

public record NewUserRecord(String username,String password,String firstName, String lastName) {
}
