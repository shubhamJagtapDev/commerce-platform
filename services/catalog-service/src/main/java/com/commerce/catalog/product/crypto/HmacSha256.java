package com.commerce.catalog.product.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.crypto.Mac;
import org.springframework.stereotype.Component;

@Component
public final class HmacSha256 {
    private static final String ALGORITHM = "HmacSHA256";

    public byte[] digest(HmacKey key, String purpose, byte[] message) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(message, "message");
        if (purpose.isBlank() || purpose.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("purpose must be nonblank and must not contain NUL");
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key.secretKey());
            mac.update(purpose.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to calculate a Catalog HMAC", exception);
        }
    }
}
