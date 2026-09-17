package com.commerce.catalog.product.application;

import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;

public sealed interface CatalogMutation permits CreateDraftMutation, LifecycleMutation, ReplaceCandidateMutation {
    ProductLifecycleOperation operation();
}
