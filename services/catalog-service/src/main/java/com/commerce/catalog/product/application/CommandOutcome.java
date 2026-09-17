package com.commerce.catalog.product.application;

import com.commerce.catalog.product.domain.product_models.ProductCommandResultState;
import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CommandOutcome(
        UUID commandId,
        ProductLifecycleOperation operation,
        ProductId productId,
        @Nullable VariantId variantId,
        long resultVersion,
        ProductCommandResultState resultState,
        @Nullable UUID publicationId,
        Instant appliedAt) {
    public CommandOutcome {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(resultState, "resultState");
        Objects.requireNonNull(appliedAt, "appliedAt");
        if (resultVersion < 0) {
            throw new IllegalArgumentException("resultVersion must not be negative");
        }
        if ((operation == ProductLifecycleOperation.WITHDRAW_VARIANT) != (variantId != null)) {
            throw new IllegalArgumentException("variantId is required only for WITHDRAW_VARIANT outcomes");
        }
        if (!appliedAt.equals(appliedAt.truncatedTo(ChronoUnit.MICROS))) {
            throw new IllegalArgumentException("appliedAt must use PostgreSQL microsecond precision");
        }
    }
}
