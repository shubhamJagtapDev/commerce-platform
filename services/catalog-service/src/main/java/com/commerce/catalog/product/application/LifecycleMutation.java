package com.commerce.catalog.product.application;

import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record LifecycleMutation(
        ProductLifecycleOperation operation,
        ProductId productId,
        @Nullable VariantId variantId,
        long expectedVersion) implements CatalogMutation {
    public LifecycleMutation {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(productId, "productId");
        if (operation == ProductLifecycleOperation.CREATE_DRAFT
                || operation == ProductLifecycleOperation.REPLACE_CANDIDATE) {
            throw new IllegalArgumentException("operation is not a lifecycle mutation");
        }
        if ((operation == ProductLifecycleOperation.WITHDRAW_VARIANT) != (variantId != null)) {
            throw new IllegalArgumentException("variantId is required only for WITHDRAW_VARIANT");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
