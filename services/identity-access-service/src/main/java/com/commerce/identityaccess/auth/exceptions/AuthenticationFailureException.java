package com.commerce.identityaccess.auth.exceptions;

public final class AuthenticationFailureException extends RuntimeException {
    private final String code;

    public AuthenticationFailureException() {
        this("authentication_failure");
    }

    public AuthenticationFailureException(String code) {
        super("Authentication could not be completed");
        this.code = code;
    }

    public String code() {
        return code;
    }
}
