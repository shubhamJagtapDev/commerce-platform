package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Issues opaque, short-lived admission evidence for the Keycloak registration boundary. */
@Component
public final class RegistrationIntentSigner {
    static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int REQUIRED_KEY_BYTES = 32;

    private final VersionedCryptoService cryptoService;
    private final Clock clock;
    private final AuthProperties properties;
    private final SecretKeySpec signingKey;

    public RegistrationIntentSigner(VersionedCryptoService cryptoService, Clock clock, AuthProperties properties) {
        this.cryptoService = cryptoService;
        this.clock = clock;
        this.properties = properties;
        byte[] keyBytes = decodeKey(properties.registration().intentHmacKey());
        this.signingKey = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    public String issue() {
        String jti = cryptoService.randomUrlValue();
        Instant expiresAt = clock.instant().plus(properties.registration().intentTtl());
        String payload = VERSION + "." + jti + "." + expiresAt.getEpochSecond();
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Registration intent signing is unavailable", exception);
        }
    }

    private static byte[] decodeKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            if (keyBytes.length != REQUIRED_KEY_BYTES) {
                throw new IllegalArgumentException("Registration intent HMAC key must decode to exactly 32 bytes");
            }
            return keyBytes;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Registration intent HMAC key must be valid base64 for 32 bytes", exception);
        }
    }
}
