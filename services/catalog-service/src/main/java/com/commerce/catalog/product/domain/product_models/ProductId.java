package com.commerce.catalog.product.domain.product_models;

import java.util.Objects;
import java.util.UUID;

public record ProductId(UUID value) {
    public ProductId {
        Objects.requireNonNull(value, "value");
    }
}
