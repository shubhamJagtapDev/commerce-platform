package com.commerce.catalog.catalogsecurity.exceptions;

public final class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("The idempotency key is already in use");
    }
}
