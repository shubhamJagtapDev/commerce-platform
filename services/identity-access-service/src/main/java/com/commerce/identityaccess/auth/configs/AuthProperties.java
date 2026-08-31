package com.commerce.identityaccess.auth.configs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("commerce.auth")
public record AuthProperties(
        @NotBlank String publicIssuer,
        @NotBlank String clientId,
        @NotBlank String publicOrigin,
        @NotBlank String privateDiscoveryUri,
        @NotBlank String privateJwksUri,
        @NotBlank String sessionCookieName,
        boolean secureCookie,
        @NotNull Duration transactionTtl,
        @NotNull Duration sessionIdleTtl,
        @NotNull Duration sessionAbsoluteTtl,
        @Valid @NotNull Registration registration,
        @Valid @NotNull Crypto crypto) {

    public record Registration(
            boolean enabled,
            @Positive int maxAttempts,
            @NotNull Duration window) {}

    public record Crypto(
            @NotBlank String encryptionKeyId,
            @NotBlank String encryptionKey,
            @NotBlank String hmacKeyId,
            @NotBlank String hmacKey) {}
}
