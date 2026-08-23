package com.commerce.identityaccess.auth.exceptions;

public final class AuthenticationFailureException extends RuntimeException {
    private static final String SAFE_MESSAGE = "Authentication could not be completed";
    private final String code;

    public AuthenticationFailureException() {
        this("authentication_failure");
    }

    public AuthenticationFailureException(String code) {
        super(SAFE_MESSAGE);
        this.code = code;
    }

    public AuthenticationFailureException(String code, Throwable cause) {
        super(SAFE_MESSAGE, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
