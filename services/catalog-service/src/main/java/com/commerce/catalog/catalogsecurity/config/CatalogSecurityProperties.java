package com.commerce.catalog.catalogsecurity.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("commerce.catalog-security")
public record CatalogSecurityProperties(
        @NotBlank String issuer,
        @NotBlank String jwkSetUri,
        @NotBlank String audience,
        @NotBlank String authorizedParty,
        @NotBlank String idempotencyHmacKey,
        @NotNull Duration idempotencyRetention,
        @Nullable String fixtureSubject) {}
