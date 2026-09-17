package com.commerce.catalog.product.crypto;

import com.commerce.catalog.product.config.HmacKeyProperties;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class HmacKey {
    private static final int KEY_BYTES = 32;

    private final String id;
    private final SecretKey secretKey;

    public HmacKey(HmacKeyProperties properties) {
        Objects.requireNonNull(properties, "properties");
        if (properties.id() == null || properties.id().isBlank()) {
            throw new IllegalArgumentException("HMAC key ID must not be blank");
        }
        if (properties.id().length() > 64) {
            throw new IllegalArgumentException("HMAC key ID must not exceed 64 characters");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(Objects.requireNonNull(properties.material(), "material"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("HMAC key material must be valid Base64", exception);
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalArgumentException("HMAC key material must decode to exactly 32 bytes");
        }
        this.id = properties.id();
        this.secretKey = new SecretKeySpec(decoded, "HmacSHA256");
    }

    public String id() {
        return id;
    }

    SecretKey secretKey() {
        return secretKey;
    }

    boolean hasSameMaterial(HmacKey other) {
        Objects.requireNonNull(other, "other");
        return MessageDigest.isEqual(secretKey.getEncoded(), other.secretKey.getEncoded());
    }

    @Override
    public String toString() {
        return "HmacKey[id=" + id + ", material=REDACTED]";
    }
}
