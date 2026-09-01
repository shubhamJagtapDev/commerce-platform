package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.RegistrationRateExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** A bounded, per-instance registration limiter keyed only by the direct peer address. */
@Component
public final class RegistrationRateLimiter {
    private static final int MAX_TRACKED_PEERS = 10_000;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger trackedPeers = new AtomicInteger();
    private final AuthProperties properties;
    private final Clock clock;

    public RegistrationRateLimiter(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void check(String peerAddress) {
        Instant now = clock.instant();
        if (trackedPeers.get() >= MAX_TRACKED_PEERS) {
            removeExpiredWindows(now);
        }
        windows.compute(peerAddress, (ignored, current) -> nextWindow(current, now));
    }

    private Window nextWindow(@Nullable Window current, Instant now) {
        if (current == null || !current.expiresAt().isAfter(now)) {
            if (current == null) {
                reservePeerSlot();
            }
            return new Window(1, now.plus(properties.registration().window()));
        }
        Window window = new Window(current.attempts() + 1, current.expiresAt());
        if (window.attempts() > properties.registration().maxAttempts()) {
            throw new RegistrationRateExceededException(Duration.between(now, window.expiresAt()));
        }
        return window;
    }

    private void reservePeerSlot() {
        while (true) {
            int current = trackedPeers.get();
            if (current >= MAX_TRACKED_PEERS) {
                throw new RegistrationRateExceededException(
                        properties.registration().window());
            }
            if (trackedPeers.compareAndSet(current, current + 1)) {
                return;
            }
        }
    }

    private void removeExpiredWindows(Instant now) {
        windows.forEach((peerAddress, window) -> {
            if (!window.expiresAt().isAfter(now) && windows.remove(peerAddress, window)) {
                trackedPeers.decrementAndGet();
            }
        });
    }

    private record Window(int attempts, Instant expiresAt) {}
}
