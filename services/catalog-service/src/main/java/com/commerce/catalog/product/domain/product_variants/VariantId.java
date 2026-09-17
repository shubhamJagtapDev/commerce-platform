package com.commerce.catalog.product.domain.product_variants;

import java.util.Objects;
import java.util.UUID;

public record VariantId(UUID value) {
    public VariantId {
        Objects.requireNonNull(value, "value");
    }
}
