package com.commerce.catalog.product.application;

import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import com.commerce.catalog.product.domain.product_publication.PublicationCandidate;
import java.util.Objects;

public record ReplaceCandidateMutation(ProductId productId, long expectedVersion, PublicationCandidate candidate)
        implements CatalogMutation {
    public ReplaceCandidateMutation {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(candidate, "candidate");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    @Override
    public ProductLifecycleOperation operation() {
        return ProductLifecycleOperation.REPLACE_CANDIDATE;
    }
}
