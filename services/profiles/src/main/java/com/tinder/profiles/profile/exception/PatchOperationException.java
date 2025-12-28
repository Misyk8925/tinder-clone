package com.tinder.profiles.profile.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a PATCH operation has invalid data.
 * For example: no fields provided, invalid field values, etc.
 */
public class PatchOperationException extends ProfileException {

    public PatchOperationException(String message) {
        super(
            message,
            HttpStatus.BAD_REQUEST,
            "PATCH_OPERATION_ERROR"
        );
    }

    public static PatchOperationException noFieldsProvided() {
        return new PatchOperationException("At least one field must be provided for update");
    }
}

