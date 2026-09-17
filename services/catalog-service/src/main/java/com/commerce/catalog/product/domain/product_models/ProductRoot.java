package com.commerce.catalog.product.domain.product_models;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ProductRoot(
        ProductId productId,
        ProductState state,
        long version,
        UUID lastCommandId,
        @Nullable UUID currentPublicationId,
        @Nullable Instant firstPublishedAt) {
    public ProductRoot {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(lastCommandId, "lastCommandId");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
