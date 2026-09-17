package com.commerce.catalog.product.domain.product_variants;

import com.commerce.catalog.product.domain.product_models.ProductId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record VariantIdentity(
        ProductId productId, VariantId variantId, @Nullable UUID firstPublicationId, Instant createdAt) {
    public VariantIdentity {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
