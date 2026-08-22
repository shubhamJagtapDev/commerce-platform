package com.commerce.identityaccess.auth.exceptions;

public final class AuthenticationFailureException extends RuntimeException {
    public AuthenticationFailureException() {
        super("Authentication could not be completed");
    }
}
