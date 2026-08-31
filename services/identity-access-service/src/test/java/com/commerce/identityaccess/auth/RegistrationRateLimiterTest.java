package com.commerce.identityaccess.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.RegistrationRateExceededException;
import com.commerce.identityaccess.auth.services.RegistrationRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RegistrationRateLimiterTest {
    @Test
    void rejectsTheSixthAttemptFromTheSameDirectPeerWithinTheWindow() {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(
                properties(), Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertDoesNotThrow(() -> limiter.check("192.0.2.10"));
        }

        assertThrows(RegistrationRateExceededException.class, () -> limiter.check("192.0.2.10"));
        assertDoesNotThrow(() -> limiter.check("192.0.2.11"));
    }

    @Test
    void keepsTheSamePeerLimitWhenAttemptsArriveConcurrently() throws Exception {
        RegistrationRateLimiter limiter = new RegistrationRateLimiter(
                properties(), Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC));
        int attempts = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<Boolean>> outcomes = IntStream.range(0, attempts)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        try {
                            limiter.check("192.0.2.10");
                            return true;
                        } catch (RegistrationRateExceededException exception) {
                            return false;
                        }
                    }))
                    .toList();

            start.countDown();

            int accepted = 0;
            for (Future<Boolean> outcome : outcomes) {
                if (outcome.get(5, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }

            assertEquals(5, accepted);
        } finally {
            executor.shutdownNow();
        }
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
                new AuthProperties.Registration(true, 5, Duration.ofHours(1)),
                new AuthProperties.Crypto("local-aes-2026-01", key, "local-hmac-2026-01", key));
    }
}
