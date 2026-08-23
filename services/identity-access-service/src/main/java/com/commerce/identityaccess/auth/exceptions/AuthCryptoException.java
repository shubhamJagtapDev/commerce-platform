package com.commerce.identityaccess.auth.exceptions;

public final class AuthCryptoException extends RuntimeException {
    public AuthCryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthCryptoException(String message) {
        super(message);
    }
}
