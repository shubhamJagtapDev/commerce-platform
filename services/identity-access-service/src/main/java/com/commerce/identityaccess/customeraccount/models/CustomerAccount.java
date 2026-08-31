package com.commerce.identityaccess.customeraccount.models;

import java.time.Instant;
import java.util.UUID;

/** The durable, email-independent binding between an OIDC principal and a commerce customer account. */
public record CustomerAccount(
        UUID accountId,
        String issuer,
        String subject,
        CustomerAccountStatus status,
        long securityEpoch,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
