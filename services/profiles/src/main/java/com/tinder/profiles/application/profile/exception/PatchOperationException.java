package com.tinder.profiles.application.profile.exception;

/**
 * Thrown when a PATCH operation has invalid data.
 * For example: no fields provided, invalid field values, etc.
 */
public class PatchOperationException extends ProfileException {

    public PatchOperationException(String message) {
        super(
            message,
            "PATCH_OPERATION_ERROR"
        );
    }

    public static PatchOperationException noFieldsProvided() {
        return new PatchOperationException("At least one field must be provided for update");
    }
}
