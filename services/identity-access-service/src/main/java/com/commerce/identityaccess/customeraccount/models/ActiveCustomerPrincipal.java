package com.commerce.identityaccess.customeraccount.models;

import java.util.UUID;

/** An authorization input derived from a validated BFF session and an active account row. */
public record ActiveCustomerPrincipal(UUID accountId, String issuer, String subject, long securityEpoch) {}
