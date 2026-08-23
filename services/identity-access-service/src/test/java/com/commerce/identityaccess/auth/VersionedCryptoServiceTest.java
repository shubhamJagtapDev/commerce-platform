package com.commerce.identityaccess.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.AuthCryptoException;
import com.commerce.identityaccess.auth.services.VersionedCryptoService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class VersionedCryptoServiceTest {
    @Test
    void hashesOidcNonceAndPkceValuesUsingUnpaddedBase64Url() {
        VersionedCryptoService crypto = new VersionedCryptoService(properties());

        assertEquals("eDd7UldXtJRCf4kBT5fXmSjzk40U61HiD7XeyYNOswQ", crypto.sha256Url("nonce"));
    }

    @Test
    void decryptRejectsCiphertextTamperedForAnotherPurpose() {
        VersionedCryptoService crypto = new VersionedCryptoService(properties());
        VersionedCryptoService.EncryptedValue encrypted =
                crypto.encrypt("oidc-transaction-nonce", "nonce".getBytes(StandardCharsets.UTF_8));

        assertThrows(
                AuthCryptoException.class,
                () -> crypto.decrypt("bff-token-bundle", encrypted.keyId(), encrypted.ciphertext()));
    }

    @Test
    void decryptRejectsAStoredValueWhoseKeyVersionIsUnavailable() {
        VersionedCryptoService crypto = new VersionedCryptoService(properties());
        VersionedCryptoService.EncryptedValue encrypted =
                crypto.encrypt("bff-token-bundle", "secret".getBytes(StandardCharsets.UTF_8));

        assertThrows(
                AuthCryptoException.class,
                () -> crypto.decrypt("bff-token-bundle", "retired-aes-2025-01", encrypted.ciphertext()));
    }

    @Test
    void roundTripsPurposeBoundCiphertext() {
        VersionedCryptoService crypto = new VersionedCryptoService(properties());
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);
        VersionedCryptoService.EncryptedValue encrypted = crypto.encrypt("bff-token-bundle", plaintext);

        assertArrayEquals(plaintext, crypto.decrypt("bff-token-bundle", encrypted.keyId(), encrypted.ciphertext()));
    }

    private AuthProperties properties() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        return new AuthProperties(
                "http://localhost:8082/realms/commerce",
                "identity-access-bff",
                "http://localhost:8080",
                "http://keycloak:8080/realms/commerce/.well-known/openid-configuration",
                "http://keycloak:8080/realms/commerce/protocol/openid-connect/certs",
                "commerce-session",
                false,
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                Duration.ofHours(8),
                new AuthProperties.Crypto("local-aes-2026-01", key, "local-hmac-2026-01", key));
    }
}
