package com.commerce.catalog.product.domain.product_models;

public enum ProductLifecycleOperation {
    CREATE_DRAFT,
    REPLACE_CANDIDATE,
    PUBLISH,
    REPUBLISH,
    WITHDRAW_PRODUCT,
    WITHDRAW_VARIANT,
    REACTIVATE,
    DISCARD
}
