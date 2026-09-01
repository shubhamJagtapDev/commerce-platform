package com.commerce.keycloak.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class RegistrationIntentVerifierTest {
    private static final byte[] KEY = new byte[32];
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private static final String JTI = "A23456789012345678901234567890123456789012";
    private final RegistrationIntentVerifier verifier =
            new RegistrationIntentVerifier(Base64.getEncoder().encodeToString(KEY), 600);

    @Test
    void acceptsAValidIntentExactlyOnce() throws Exception {
        Set<String> consumed = ConcurrentHashMap.newKeySet();
        String intent = intent(NOW.plusSeconds(600).getEpochSecond());

        assertTrue(validate(intent, (jti, lifespan) -> consumed.add(jti)));
        assertFalse(validate(intent, (jti, lifespan) -> consumed.add(jti)));
    }

    @Test
    void rejectsMissingMalformedTamperedExpiredAndExcessiveLifetimeIntents() throws Exception {
        String valid = intent(NOW.plusSeconds(600).getEpochSecond());
        List<String> rejected = List.of(
                "",
                "not-an-intent",
                valid.substring(0, valid.length() - 1) + "A",
                intent(NOW.getEpochSecond()),
                intent(NOW.plusSeconds(601).getEpochSecond()));

        for (String intent : rejected) {
            assertFalse(validate(intent, (jti, lifespan) -> true));
        }
    }

    @Test
    void rejectsAnIntentOutsideTheRegistrationClientAndPrompt() throws Exception {
        String intent = intent(NOW.plusSeconds(600).getEpochSecond());

        assertFalse(verifier.validateAndConsume(intent, "another-client", "create", NOW, (jti, ttl) -> true));
        assertFalse(verifier.validateAndConsume(
                intent, RegistrationIntentFormAction.EXPECTED_CLIENT_ID, "login", NOW, (jti, ttl) -> true));
    }

    @Test
    void concurrentReplayAllowsExactlyOneConsumer() throws Exception {
        String intent = intent(NOW.plusSeconds(600).getEpochSecond());
        Set<String> consumed = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> outcomes = IntStream.range(0, 20)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        return validate(intent, (jti, lifespan) -> consumed.add(jti));
                    }))
                    .toList();
            start.countDown();

            int accepted = 0;
            for (Future<Boolean> outcome : outcomes) {
                if (outcome.get(5, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(1, accepted);
        }
    }

    private boolean validate(String intent, BiPredicate<String, Long> consumeOnce) {
        return verifier.validateAndConsume(
                intent, RegistrationIntentFormAction.EXPECTED_CLIENT_ID, "create", NOW, consumeOnce);
    }

    private String intent(long expiresAt) throws Exception {
        String payload = "v1." + JTI + "." + expiresAt;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
        String signature = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        return payload + "." + signature;
    }
}
