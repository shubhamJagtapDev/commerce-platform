package com.commerce.catalog.product.domain.product_lifecycle;

import com.commerce.catalog.product.domain.product_models.ProductCommandResultState;
import com.commerce.catalog.product.domain.product_models.ProductState;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record LifecycleDecision(
        boolean accepted,
        ProductCommandResultState resultState,
        boolean incrementsProductVersion,
        @Nullable LifecycleViolationCode violationCode) {
    public LifecycleDecision {
        Objects.requireNonNull(resultState, "resultState");
        if (accepted == (violationCode != null)) {
            throw new IllegalArgumentException("accepted decisions cannot have violations");
        }
    }

    public static LifecycleDecision accepted(ProductCommandResultState resultState, boolean incrementsProductVersion) {
        return new LifecycleDecision(true, resultState, incrementsProductVersion, null);
    }

    public static LifecycleDecision rejected(ProductState currentState, LifecycleViolationCode violationCode) {
        return new LifecycleDecision(
                false, ProductCommandResultState.valueOf(currentState.name()), false, violationCode);
    }
}
