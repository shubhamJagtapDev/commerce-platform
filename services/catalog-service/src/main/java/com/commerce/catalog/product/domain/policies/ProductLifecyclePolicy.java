package com.commerce.catalog.product.domain.policies;

import com.commerce.catalog.product.domain.product_lifecycle.LifecycleDecision;
import com.commerce.catalog.product.domain.product_lifecycle.LifecycleViolationCode;
import com.commerce.catalog.product.domain.product_models.ProductCommandResultState;
import com.commerce.catalog.product.domain.product_models.ProductState;
import com.commerce.catalog.product.domain.product_publication.PublicationCandidate;
import java.util.Objects;

public final class ProductLifecyclePolicy {

    public LifecycleDecision createDraft() {
        return LifecycleDecision.accepted(ProductCommandResultState.DRAFT, false);
    }

    public LifecycleDecision replaceCandidate(ProductState currentState) {
        Objects.requireNonNull(currentState, "currentState");
        return LifecycleDecision.accepted(ProductCommandResultState.valueOf(currentState.name()), true);
    }

    public LifecycleDecision publish(ProductState currentState) {
        Objects.requireNonNull(currentState, "currentState");
        if (currentState != ProductState.DRAFT) {
            return LifecycleDecision.rejected(currentState, LifecycleViolationCode.INVALID_STATE);
        }
        return LifecycleDecision.accepted(ProductCommandResultState.PUBLISHED, true);
    }

    public LifecycleDecision republish(ProductState currentState) {
        Objects.requireNonNull(currentState, "currentState");
        if (currentState != ProductState.PUBLISHED) {
            return LifecycleDecision.rejected(currentState, LifecycleViolationCode.INVALID_STATE);
        }
        return LifecycleDecision.accepted(ProductCommandResultState.PUBLISHED, true);
    }

    public LifecycleDecision withdrawProduct(ProductState currentState) {
        Objects.requireNonNull(currentState, "currentState");
        if (currentState != ProductState.PUBLISHED) {
            return LifecycleDecision.rejected(currentState, LifecycleViolationCode.INVALID_STATE);
        }
        return LifecycleDecision.accepted(ProductCommandResultState.WITHDRAWN, true);
    }

    public LifecycleDecision withdrawVariant(
            ProductState currentState, int currentSellableCount, boolean targetSellable) {
        Objects.requireNonNull(currentState, "currentState");
        if (currentState != ProductState.PUBLISHED) {
            return LifecycleDecision.rejected(currentState, LifecycleViolationCode.INVALID_STATE);
        }
        if (!targetSellable) {
            return LifecycleDecision.rejected(currentState, LifecycleViolationCode.TARGET_VARIANT_NOT_SELLABLE);
        }
        if (currentSellableCount < 1 || currentSellableCount > PublicationCandidate.MAX_VARIANTS) {
            return LifecycleDecision.rejected(currentState, LifecycleViolationCode.INVALID_SELLABLE_COUNT);
        }
        ProductCommandResultState resultState =
                currentSellableCount == 1 ? ProductCommandResultState.WITHDRAWN : ProductCommandResultState.PUBLISHED;
        return LifecycleDecision.accepted(resultState, true);
    }

    public LifecycleDecision reactivate(ProductState currentState) {
        Objects.requireNonNull(currentState, "currentState");
        if (currentState != ProductState.WITHDRAWN) {
            return LifecycleDecision.rejected(currentState, LifecycleViolationCode.INVALID_STATE);
        }
        return LifecycleDecision.accepted(ProductCommandResultState.PUBLISHED, true);
    }

    public LifecycleDecision discard(ProductState currentState, boolean everPublished) {
        Objects.requireNonNull(currentState, "currentState");
        if (currentState != ProductState.DRAFT) {
            return LifecycleDecision.rejected(currentState, LifecycleViolationCode.INVALID_STATE);
        }
        if (everPublished) {
            return LifecycleDecision.rejected(currentState, LifecycleViolationCode.NEVER_PUBLISHED_REQUIRED);
        }
        return LifecycleDecision.accepted(ProductCommandResultState.DISCARDED, true);
    }
}
