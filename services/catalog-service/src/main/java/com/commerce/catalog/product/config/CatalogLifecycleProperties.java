package com.commerce.catalog.product.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("commerce.catalog-lifecycle")
public record CatalogLifecycleProperties(
        @NotNull Duration receiptRetention,
        @Valid @NotNull HmacKeyRingProperties lifecycleKeys,
        @Valid @NotNull HmacKeyRingProperties auditKeys) {
    private static final Duration REQUIRED_RECEIPT_RETENTION = Duration.ofHours(24);

    public CatalogLifecycleProperties {
        Objects.requireNonNull(receiptRetention, "receiptRetention");
        Objects.requireNonNull(lifecycleKeys, "lifecycleKeys");
        Objects.requireNonNull(auditKeys, "auditKeys");
        if (!receiptRetention.equals(REQUIRED_RECEIPT_RETENTION)) {
            throw new IllegalArgumentException("receiptRetention must be exactly 24 hours");
        }
    }

    @Override
    public String toString() {
        return "CatalogLifecycleProperties[receiptRetention=" + receiptRetention + ", keyRings=REDACTED]";
    }

    public record HmacKeyRingProperties(
            @Valid @NotNull HmacKeyProperties active,
            @Valid @NotNull List<HmacKeyProperties> retained) {
        public HmacKeyRingProperties {
            Objects.requireNonNull(active, "active");
            retained = List.copyOf(Objects.requireNonNull(retained, "retained"));
        }

        @Override
        public String toString() {
            return "HmacKeyRingProperties[REDACTED]";
        }
    }
}
