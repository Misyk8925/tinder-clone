package com.tinder.profiles.application.photos.command;

import java.util.UUID;

/** Application-layer intent to remove one catalogued photo and its objects. */
public record DeletePhotoCommand(String userId, UUID photoId) {
}
