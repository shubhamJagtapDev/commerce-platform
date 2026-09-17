package com.commerce.catalog.product.application;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

public final class IdempotencyKey {
    private static final int MIN_LENGTH = 16;
    private static final int MAX_LENGTH = 128;
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("[A-Za-z0-9_-]+");

    private final String value;

    public IdempotencyKey(String value) {
        this.value = Objects.requireNonNull(value, "value");
        if (value.length() < MIN_LENGTH
                || value.length() > MAX_LENGTH
                || !ALLOWED_CHARACTERS.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "idempotency key must contain 16 to 128 ASCII letters, digits, underscores, or hyphens");
        }
    }

    public byte[] utf8Bytes() {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IdempotencyKey key && value.equals(key.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "IdempotencyKey[REDACTED]";
    }
}
