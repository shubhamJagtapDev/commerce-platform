package com.commerce.identityaccess.auth.exceptions;

import java.time.Duration;

public final class RegistrationRateExceededException extends RuntimeException {
    private final Duration retryAfter;

    public RegistrationRateExceededException(Duration retryAfter) {
        super("Registration rate limit exceeded");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
