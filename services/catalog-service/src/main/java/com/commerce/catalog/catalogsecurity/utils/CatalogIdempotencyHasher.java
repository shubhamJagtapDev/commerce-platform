package com.commerce.catalog.catalogsecurity.utils;

import com.commerce.catalog.catalogsecurity.config.CatalogSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class CatalogIdempotencyHasher {
    private final SecretKey key;

    public CatalogIdempotencyHasher(CatalogSecurityProperties properties) {
        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(properties.idempotencyHmacKey());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Catalog idempotency HMAC key must be Base64 encoded", exception);
        }
        if (decodedKey.length != 32) {
            throw new IllegalStateException("Catalog idempotency HMAC key must contain exactly 32 bytes");
        }
        key = new SecretKeySpec(decodedKey, "HmacSHA256");
    }

    public byte[] hash(String purpose, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(purpose.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to calculate a Catalog idempotency verifier", exception);
        }
    }
}
