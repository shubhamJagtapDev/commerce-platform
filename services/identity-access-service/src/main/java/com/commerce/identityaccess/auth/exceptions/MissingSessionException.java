package com.commerce.identityaccess.auth.exceptions;

public final class MissingSessionException extends RuntimeException {
    public MissingSessionException() {
        super("An active session is required");
    }
}
