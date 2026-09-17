package com.commerce.catalog.product.application;

import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import com.commerce.catalog.product.domain.product_publication.PublicationCandidate;
import java.util.Objects;

public record CreateDraftMutation(PublicationCandidate candidate) implements CatalogMutation {
    public CreateDraftMutation {
        Objects.requireNonNull(candidate, "candidate");
    }

    @Override
    public ProductLifecycleOperation operation() {
        return ProductLifecycleOperation.CREATE_DRAFT;
    }
}
