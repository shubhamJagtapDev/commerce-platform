package com.commerce.catalog.product.application;

import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class CommandExecutionResult {
    private static final String PRODUCT_LOCATION_PREFIX = "/api/v1/catalog/maintenance/products/";

    private final CommandOutcome outcome;
    private final int originalHttpStatus;
    private final boolean replayed;
    private final @Nullable String location;

    public CommandExecutionResult(CommandOutcome outcome, int originalHttpStatus, boolean replayed) {
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        if (originalHttpStatus != 200 && originalHttpStatus != 201) {
            throw new IllegalArgumentException("originalHttpStatus must be 200 or 201");
        }
        if ((outcome.operation() == ProductLifecycleOperation.CREATE_DRAFT) != (originalHttpStatus == 201)) {
            throw new IllegalArgumentException("only CREATE_DRAFT has status 201");
        }
        this.originalHttpStatus = originalHttpStatus;
        this.replayed = replayed;
        this.location = outcome.operation() == ProductLifecycleOperation.CREATE_DRAFT
                ? PRODUCT_LOCATION_PREFIX + outcome.productId().value()
                : null;
    }

    public CommandOutcome outcome() {
        return outcome;
    }

    public int originalHttpStatus() {
        return originalHttpStatus;
    }

    public boolean replayed() {
        return replayed;
    }

    public @Nullable String location() {
        return location;
    }
}
