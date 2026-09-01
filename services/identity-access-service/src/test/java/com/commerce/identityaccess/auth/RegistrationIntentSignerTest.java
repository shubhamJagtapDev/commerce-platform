package com.commerce.identityaccess.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.services.RegistrationIntentSigner;
import com.commerce.identityaccess.auth.services.VersionedCryptoService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.StringTokenizer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class RegistrationIntentSignerTest {
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private static final byte[] INTENT_KEY = new byte[32];

    @Test
    void issuesAValidShortLivedIntentWithoutIdentityData() throws Exception {
        AuthProperties properties = properties(Base64.getEncoder().encodeToString(INTENT_KEY));
        RegistrationIntentSigner signer = new RegistrationIntentSigner(
                new VersionedCryptoService(properties), Clock.fixed(NOW, ZoneOffset.UTC), properties);

        String intent = signer.issue();

        List<String> parts = parts(intent);
        assertEquals(4, parts.size());
        assertEquals("v1", parts.get(0));
        assertEquals(NOW.plus(Duration.ofMinutes(10)).getEpochSecond(), Long.parseLong(parts.get(2)));
        assertEquals(signature(parts.get(0) + "." + parts.get(1) + "." + parts.get(2)), parts.get(3));
    }

    @Test
    void rejectsASecretThatIsNotExactlyThirtyTwoBytes() {
        AuthProperties properties = properties(Base64.getEncoder().encodeToString(new byte[31]));

        assertThrows(
                IllegalArgumentException.class,
                () -> new RegistrationIntentSigner(
                        new VersionedCryptoService(properties), Clock.fixed(NOW, ZoneOffset.UTC), properties));
    }

    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(INTENT_KEY, "HmacSHA256"));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
    }

    private List<String> parts(String intent) {
        StringTokenizer tokenizer = new StringTokenizer(intent, ".");
        List<String> parts = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            parts.add(tokenizer.nextToken());
        }
        return parts;
    }

    private AuthProperties properties(String intentKey) {
        String cryptoKey = Base64.getEncoder().encodeToString(new byte[32]);
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
                new AuthProperties.Registration(true, 5, Duration.ofHours(1), Duration.ofMinutes(10), intentKey),
                new AuthProperties.Crypto("local-aes-2026-01", cryptoKey, "local-hmac-2026-01", cryptoKey));
    }
}
