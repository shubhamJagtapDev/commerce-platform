package com.commerce.identityaccess.auth.exceptions;

public final class RegistrationUnavailableException extends RuntimeException {
    public RegistrationUnavailableException() {
        super("Registration is unavailable");
    }
}
