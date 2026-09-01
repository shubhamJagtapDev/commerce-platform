package com.commerce.keycloak.registration;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class RegistrationIntentVerifier {
    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int REQUIRED_KEY_BYTES = 32;
    private static final Pattern SEPARATOR = Pattern.compile("\\.");
    private static final Pattern JTI = Pattern.compile("[A-Za-z0-9_-]{32,128}");

    private final SecretKeySpec signingKey;
    private final long maximumTtlSeconds;

    RegistrationIntentVerifier(String encodedKey, long maximumTtlSeconds) {
        if (maximumTtlSeconds <= 0) {
            throw new IllegalArgumentException("Registration intent maximum TTL must be positive");
        }
        this.signingKey = new SecretKeySpec(decodeKey(encodedKey), HMAC_ALGORITHM);
        this.maximumTtlSeconds = maximumTtlSeconds;
    }

    boolean validateAndConsume(
            String intent, String clientId, String prompt, Instant now, BiPredicate<String, Long> consumeOnce) {
        if (!RegistrationIntentFormAction.EXPECTED_CLIENT_ID.equals(clientId) || !"create".equals(prompt)) {
            return false;
        }
        List<String> parts = SEPARATOR.splitAsStream(intent).toList();
        if (parts.size() != 4
                || !VERSION.equals(parts.get(0))
                || !JTI.matcher(parts.get(1)).matches()) {
            return false;
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts.get(2));
        } catch (NumberFormatException exception) {
            return false;
        }
        long remainingSeconds = expiresAt - now.getEpochSecond();
        if (remainingSeconds <= 0 || remainingSeconds > maximumTtlSeconds) {
            return false;
        }
        String payload = parts.get(0) + "." + parts.get(1) + "." + parts.get(2);
        byte[] suppliedSignature;
        try {
            suppliedSignature = Base64.getUrlDecoder().decode(parts.get(3));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!MessageDigest.isEqual(sign(payload), suppliedSignature)) {
            return false;
        }
        return consumeOnce.test(parts.get(1), remainingSeconds);
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Registration intent validation is unavailable", exception);
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
