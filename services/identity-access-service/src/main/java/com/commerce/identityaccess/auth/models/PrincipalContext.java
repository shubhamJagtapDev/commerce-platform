package com.commerce.identityaccess.auth.models;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A principal derived solely from an active BFF session, never from a request header. */
public record PrincipalContext(
        UUID sessionId,
        String issuer,
        String subject,
        PrincipalKind kind,
        @Nullable UUID accountId,
        @Nullable Long securityEpoch,
        Instant authenticatedAt,
        Set<String> authorities) {}
