package com.commerce.catalog.product.domain;

import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductState;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record MaintainerProductListItem(
        ProductId productId,
        long version,
        ProductState state,
        @Nullable String candidateName,
        @Nullable UUID currentPublicationId) {
    public MaintainerProductListItem {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(state, "state");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
